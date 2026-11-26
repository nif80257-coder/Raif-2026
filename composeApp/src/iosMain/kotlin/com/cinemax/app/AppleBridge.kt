package com.cinemax.app

import com.cinemax.app.addons.AddonCapabilities
import com.cinemax.app.addons.AddonRepository
import com.cinemax.app.addons.AddonSettingsStore
import com.cinemax.app.addons.StremioMetaPreview
import com.cinemax.app.addons.createCinemaxHttpClient
import com.cinemax.app.core.appVersionLabel
import com.cinemax.app.core.dedupeSources
import com.cinemax.app.core.parseSuggestedSources
import com.cinemax.app.core.streamBadges
import com.cinemax.app.nuvio.NuvioProviderClient
import com.cinemax.app.nuvio.NuvioProviderEntry
import com.cinemax.app.nuvio.NuvioProviderStore
import com.cinemax.app.nuvio.TmdbIdResolver
import com.cinemax.app.nuvio.createNuvioHttpClient
import com.cinemax.app.nuvio.createNuvioScriptRuntime
import com.cinemax.app.nuvio.nuvioSourcesFrom
import com.cinemax.app.nuvio.runsOnIos
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults

/** One catalogue entry as the SwiftUI interface needs it. */
@Serializable
data class AppleItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val releaseInfo: String? = null,
    val rating: String? = null,
    val overview: String? = null
)

@Serializable
data class AppleShelf(val id: String, val title: String, val items: List<AppleItem>)

@Serializable
data class AppleShelves(val shelves: List<AppleShelf>)

@Serializable
data class AppleProgress(
    val item: AppleItem,
    val positionMillis: Long,
    val durationMillis: Long,
    val episodeLabel: String? = null,
    val fraction: Double
)

@Serializable
data class AppleEpisodes(val episodes: List<AppleItem>)

@Serializable
data class AppleStream(
    val url: String,
    val addon: String,
    val title: String,
    val quality: String?,
    /** Referer / User-Agent the host demands; empty for an open link. */
    val headers: Map<String, String> = emptyMap(),
    /** A file size in MB or GB, when the source reports one. */
    val size: String? = null,
    /** A page or app to open when the addon has no direct link at all. */
    val externalUrl: String? = null,
    /** Why a row cannot be played here, shown instead of dropping it silently. */
    val note: String? = null
)

@Serializable
data class AppleStreams(
    val streams: List<AppleStream>,
    /** "<addon>: reason" for every source that failed or timed out. */
    val failures: List<String> = emptyList()
)

@Serializable
data class AppleDetail(
    val item: AppleItem,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    val director: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val seasons: List<Int> = emptyList(),
    /** Episodes as catalogue entries; their id is the stream id ("tt1:1:2"). */
    val episodes: List<AppleItem> = emptyList(),
    /** Episode ids in the same order as [episodes], without their catalogue title. */
    val episodeLabels: List<String> = emptyList(),
    /** Trailer links the meta addon offers, ready to open. */
    val trailers: List<String> = emptyList()
)

/**
 * The data layer the SwiftUI interface reads.
 *
 * It talks to the same addon store, HTTP caches and history files the Compose
 * interface uses, and serialises everything to JSON so the Swift side needs no
 * knowledge of the Kotlin model types. Every call answers on the main thread.
 */
/**
 * How many Nuvio providers a single source fetch runs. Their scripts are
 * evaluated on the main thread, so this number is what decides whether the list
 * appears at once or after a freeze of many seconds.
 */
private const val MAX_PROVIDERS_PER_FETCH = 12

class CinemaxAppleBridge {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    /** Live source fetches, keyed by the token the interface hands in. */
    private val streamJobs = mutableMapOf<String, Job>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val addonStore = AddonSettingsStore()
    private val repository = AddonRepository(createCinemaxHttpClient())
    private val progressStore = WatchProgressStore()
    private val favoritesStore = FavoritesStore()
    private val nuvioStore = NuvioProviderStore()
    private val tmdbHttp = createCinemaxHttpClient()
    // Nuvio providers run as scripts, so the bridge needs their own client and
    // JavaScript runtime to join their sources with the Stremio ones.
    private val nuvioHttp = createNuvioHttpClient()
    private val nuvioClient = NuvioProviderClient(nuvioHttp)
    private val nuvioRuntime = createNuvioScriptRuntime(nuvioHttp)
    private val tmdbResolver = TmdbIdResolver(nuvioHttp, nuvioStore)

    /** The optional TMDB key, shared with the Nuvio providers. */
    fun tmdbKey(): String = nuvioStore.loadTmdbApiKey()

    fun saveTmdbKey(value: String) {
        nuvioStore.saveTmdbApiKey(value)
    }

    private fun itemOf(meta: StremioMetaPreview) = AppleItem(
        id = meta.id,
        type = meta.type,
        name = meta.name,
        poster = meta.poster,
        background = meta.background,
        releaseInfo = meta.releaseInfo,
        rating = meta.imdbRating,
        overview = meta.description
    )

    private fun previewOf(item: AppleItem) = StremioMetaPreview(
        id = item.id,
        type = item.type,
        name = item.name,
        poster = item.poster,
        background = item.background,
        description = item.overview,
        releaseInfo = item.releaseInfo,
        imdbRating = item.rating
    )

    /** The last successful catalogue, so the home never opens empty while it refreshes. */
    fun cachedShelves(): String =
        NSUserDefaults.standardUserDefaults.stringForKey(SHELVES_CACHE_KEY) ?: "[]"

    /**
     * Two catalogue rows per installed addon — one for films and one for series when
     * it has both — so a viewer with many addons sees what they actually installed
     * instead of a handful of rows.
     */
    fun loadShelves(onDone: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val limit = NSUserDefaults.standardUserDefaults.integerForKey("cinemax.maxShelves")
                    .let { if (it <= 0L) 28 else it.toInt() }
                val shelves = coroutineScope {
                    addonStore.load().map { addon ->
                        async {
                            val picks = buildList {
                                addon.catalogs.firstOrNull { it.type == "movie" }?.let(::add)
                                addon.catalogs.firstOrNull { it.type == "series" }?.let(::add)
                                if (isEmpty()) addon.catalogs.firstOrNull()?.let(::add)
                            }.distinctBy { it.type + ":" + it.id }
                            picks.mapNotNull { catalog ->
                                val items = try {
                                    withTimeoutOrNull(10_000) {
                                        repository.loadCatalog(addon.manifestUrl, catalog.type, catalog.id)
                                    } ?: emptyList()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Throwable) {
                                    emptyList()
                                }
                                if (items.isEmpty()) {
                                    null
                                } else {
                                    val label = catalog.name?.takeIf { it.isNotBlank() } ?: addon.name
                                    AppleShelf(
                                        id = addon.id + ":" + catalog.id,
                                        title = if (picks.size > 1) {
                                            "$label · " + if (catalog.type == "movie") "أفلام" else "مسلسلات"
                                        } else {
                                            label
                                        },
                                        items = items.take(30).map(::itemOf)
                                    )
                                }
                            }
                        }
                    }.awaitAll().flatten().take(limit)
                }
                // With a TMDB key, four curated rows ride above the addon ones: the
                // only free catalogue that answers with Arabic titles, Arabic
                // overviews and a real vote average.
                val payload = json.encodeToString(AppleShelves(tmdbRows() + shelves))
                if (shelves.isNotEmpty()) {
                    NSUserDefaults.standardUserDefaults.setObject(payload, SHELVES_CACHE_KEY)
                }
                onDone(payload)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر تحميل الكتالوج")
            }
        }
    }

    private suspend fun tmdbRows(): List<AppleShelf> {
        val key = nuvioStore.loadTmdbApiKey()
        if (key.isBlank()) return emptyList()
        val rows = listOf(
            "trending/all/week" to "الأكثر تداولًا هذا الأسبوع",
            "movie/popular" to "أفلام شائعة",
            "tv/popular" to "مسلسلات شائعة",
            "discover/movie?with_original_language=ar&sort_by=popularity.desc" to "سينما عربية",
            "movie/top_rated" to "أعلى تقييمًا",
            "movie/upcoming" to "قريبًا"
        )
        return coroutineScope {
            rows.map { (path, title) ->
                async {
                    val items = try {
                        withTimeoutOrNull(10_000) { fetchTmdbList(key, path) } ?: emptyList()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    if (items.isEmpty()) null else AppleShelf("tmdb:$path", title, items)
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun fetchTmdbList(key: String, path: String): List<AppleItem> {
        // A path may already carry its own query (discover), so the separator varies.
        val separator = if (path.contains("?")) "&" else "?"
        val body = tmdbHttp.get(
            "https://api.themoviedb.org/3/$path${separator}language=ar&api_key=$key"
        ).bodyAsText()
        val results = Json.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val obj = element.jsonObject
            val id = (obj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val isMovie = obj["title"] != null
            val name = (obj[if (isMovie) "title" else "name"] as? JsonPrimitive)?.content
                ?: return@mapNotNull null
            val poster = (obj["poster_path"] as? JsonPrimitive)?.content
            val backdrop = (obj["backdrop_path"] as? JsonPrimitive)?.content
            val overview = (obj["overview"] as? JsonPrimitive)?.content
            val rating = (obj["vote_average"] as? JsonPrimitive)?.doubleOrNull
            val date = (obj[if (isMovie) "release_date" else "first_air_date"] as? JsonPrimitive)?.content
            AppleItem(
                id = "tmdb:$id:" + if (isMovie) "movie" else "series",
                type = if (isMovie) "movie" else "series",
                name = name,
                poster = poster?.let { "https://image.tmdb.org/t/p/w500$it" },
                background = (backdrop ?: poster)?.let { "https://image.tmdb.org/t/p/w780$it" },
                releaseInfo = date?.take(4),
                rating = if (rating != null && rating > 0.0) {
                    ((rating * 10).toInt() / 10.0).toString()
                } else {
                    null
                },
                overview = overview?.takeIf { it.isNotBlank() }
            )
        }
    }

    /**
     * Titles TMDB considers close to this one, so a finished film leads somewhere.
     * An addon entry carries an IMDb id, so its TMDB id comes from a title search.
     */
    fun loadSimilar(itemId: String, type: String, name: String, onDone: (String) -> Unit) {
        scope.launch {
            val items = try {
                withTimeoutOrNull(12_000) { fetchSimilar(itemId, type, name) } ?: emptyList()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                emptyList()
            }
            val shelf = AppleShelf("similar", "أعمال مشابهة", items)
            onDone(json.encodeToString(AppleShelves(listOf(shelf))))
        }
    }

    private suspend fun fetchSimilar(itemId: String, type: String, name: String): List<AppleItem> {
        val key = nuvioStore.loadTmdbApiKey()
        if (key.isBlank()) return emptyList()
        val kind = if (type == "series") "tv" else "movie"
        val tmdbId = when {
            itemId.startsWith("tmdb:") -> itemId.split(":").getOrNull(1)
            else -> resolveTmdbIdByTitle(name, kind, key)
        } ?: return emptyList()
        if (tmdbId.isBlank()) return emptyList()
        return fetchTmdbList(key, "$kind/$tmdbId/similar")
    }

    /** Finds the TMDB id of an addon title by searching for its name. */
    private suspend fun resolveTmdbIdByTitle(name: String, kind: String, key: String): String? {
        if (name.isBlank()) return null
        val query = name.encodeURLParameter()
        val body = tmdbHttp.get(
            "https://api.themoviedb.org/3/search/$kind?language=ar&api_key=$key&query=$query"
        ).bodyAsText()
        val results = Json.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return null
        return (results.firstOrNull()?.jsonObject?.get("id") as? JsonPrimitive)?.content
    }

    /** Full TMDB detail, including Arabic episodes, for a row that came from TMDB. */
    private suspend fun tmdbDetail(itemId: String): AppleDetail? {
        val parts = itemId.split(":")
        if (parts.size < 3 || parts[0] != "tmdb") return null
        val id = parts[1]
        val isMovie = parts[2] == "movie"
        val key = nuvioStore.loadTmdbApiKey()
        if (key.isBlank()) return null
        val path = if (isMovie) "movie/$id" else "tv/$id"
        val body = tmdbHttp.get("https://api.themoviedb.org/3/$path?language=ar&api_key=$key").bodyAsText()
        val obj = Json.parseToJsonElement(body).jsonObject
        val name = (obj[if (isMovie) "title" else "name"] as? JsonPrimitive)?.content ?: return null
        val poster = (obj["poster_path"] as? JsonPrimitive)?.content
        val backdrop = (obj["backdrop_path"] as? JsonPrimitive)?.content
        val overview = (obj["overview"] as? JsonPrimitive)?.content
        val rating = (obj["vote_average"] as? JsonPrimitive)?.doubleOrNull
        val date = (obj[if (isMovie) "release_date" else "first_air_date"] as? JsonPrimitive)?.content
        val genres = (obj["genres"] as? JsonArray)?.mapNotNull {
            (it.jsonObject["name"] as? JsonPrimitive)?.content
        } ?: emptyList()
        val runtimeMinutes = (obj["runtime"] as? JsonPrimitive)?.intOrNull
        val seasons = (obj["seasons"] as? JsonArray)?.mapNotNull {
            (it.jsonObject["season_number"] as? JsonPrimitive)?.intOrNull
        }?.filter { it > 0 } ?: emptyList()
        val episodes = mutableListOf<AppleItem>()
        if (!isMovie) {
            for (season in seasons.take(4)) {
                val seasonBody = try {
                    tmdbHttp.get("https://api.themoviedb.org/3/tv/$id/season/$season?language=ar&api_key=$key").bodyAsText()
                } catch (_: Throwable) {
                    null
                } ?: continue
                val seasonObject = Json.parseToJsonElement(seasonBody).jsonObject
                (seasonObject["episodes"] as? JsonArray)?.forEach { element ->
                    val episode = element.jsonObject
                    val number = (episode["episode_number"] as? JsonPrimitive)?.intOrNull ?: return@forEach
                    episodes += AppleItem(
                        id = "$itemId:$season:$number",
                        type = "episode",
                        name = (episode["name"] as? JsonPrimitive)?.content ?: "الحلقة $number",
                        poster = (episode["still_path"] as? JsonPrimitive)?.content
                            ?.let { "https://image.tmdb.org/t/p/w300$it" },
                        overview = (episode["overview"] as? JsonPrimitive)?.content,
                        releaseInfo = (episode["air_date"] as? JsonPrimitive)?.content
                    )
                }
            }
        }
        return AppleDetail(
            item = AppleItem(
                id = itemId,
                type = if (isMovie) "movie" else "series",
                name = name,
                poster = poster?.let { "https://image.tmdb.org/t/p/w500$it" },
                background = (backdrop ?: poster)?.let { "https://image.tmdb.org/t/p/w780$it" },
                releaseInfo = date?.take(4),
                rating = if (rating != null && rating > 0.0) {
                    ((rating * 10).toInt() / 10.0).toString()
                } else {
                    null
                },
                overview = overview?.takeIf { it.isNotBlank() }
            ),
            genres = genres,
            runtime = runtimeMinutes?.let { "$it دقيقة" },
            // Only the seasons whose episodes were actually fetched are offered, so
            // no season chip can lead to an empty list.
            seasons = if (isMovie) emptyList() else seasons.take(4),
            episodes = episodes
        )
    }

    /**
     * Cross-addon search, run in parallel over every catalogue that advertises it,
     * with the free TMDB search in front when a key is set: those rows carry Arabic
     * titles and a translated summary, which the addons rarely do.
     */
    fun search(query: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        val text = query.trim()
        if (text.length < 2) {
            onDone(json.encodeToString(AppleShelves(emptyList())))
            return
        }
        scope.launch {
            try {
                val found = mutableListOf<AppleItem>()
                val seen = mutableSetOf<String>()
                val key = nuvioStore.loadTmdbApiKey()
                if (key.isNotBlank()) {
                    val query = text.encodeURLParameter()
                    val tmdb = coroutineScope {
                        listOf("movie", "tv").map { kind ->
                            async {
                                try {
                                    withTimeoutOrNull(9_000) {
                                        fetchTmdbList(
                                            key,
                                            "search/$kind?include_adult=false&language=ar-SA&query=$query"
                                        )
                                    } ?: emptyList()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Throwable) {
                                    emptyList()
                                }
                            }
                        }.awaitAll().flatten()
                    }
                    for (item in tmdb) {
                        if (seen.add(item.id)) found += item
                    }
                }
                val fromAddons = coroutineScope {
                    addonStore.load().map { addon ->
                        async {
                            addon.catalogs
                                .filter { it.supportsExtra("search") }
                                .take(2)
                                .map { catalog ->
                                    try {
                                        withTimeoutOrNull(6_000) {
                                            repository.searchCatalog(
                                                addon.manifestUrl,
                                                catalog.type,
                                                catalog.id,
                                                text
                                            )
                                        } ?: emptyList()
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (_: Throwable) {
                                        emptyList()
                                    }
                                }
                                .flatten()
                        }
                    }.awaitAll().flatten()
                }
                for (item in fromAddons) {
                    if (found.size >= 120) break
                    if (seen.add(item.id)) found += itemOf(item)
                }
                onDone(
                    json.encodeToString(
                        AppleShelves(listOf(AppleShelf("search", "نتائج البحث", found.take(120))))
                    )
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر البحث")
            }
        }
    }

    /** Live searches, keyed by the token the interface hands in. */
    private val searchJobs = mutableMapOf<String, Job>()

    /**
     * The same cross-addon search, but every source's hits are handed to
     * [onUpdate] the moment they arrive, so one slow addon no longer holds the
     * screen back. The token cancels the run before it, so an older answer can
     * never replace a newer one.
     */
    fun searchStreaming(
        query: String,
        token: String,
        onUpdate: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        searchJobs.remove(token)?.cancel()
        val text = query.trim()
        if (text.length < 2) {
            onDone(json.encodeToString(AppleShelves(emptyList())))
            return
        }
        searchJobs[token] = scope.launch {
            try {
                val found = mutableListOf<AppleItem>()
                val seen = mutableSetOf<String>()
                fun publish() {
                    onUpdate(
                        json.encodeToString(
                            AppleShelves(listOf(AppleShelf("search", "نتائج البحث", found.toList())))
                        )
                    )
                }
                val key = nuvioStore.loadTmdbApiKey()
                if (key.isNotBlank()) {
                    val encoded = text.encodeURLParameter()
                    val tmdb = coroutineScope {
                        listOf("movie", "tv").map { kind ->
                            async {
                                try {
                                    withTimeoutOrNull(9_000) {
                                        fetchTmdbList(
                                            key,
                                            "search/$kind?include_adult=false&language=ar-SA&query=$encoded"
                                        )
                                    } ?: emptyList()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Throwable) {
                                    emptyList()
                                }
                            }
                        }.awaitAll().flatten()
                    }
                    for (item in tmdb) {
                        if (seen.add(item.id)) found += item
                    }
                    publish()
                }
                val addons = addonStore.load()
                val perAddon = addons.map { addon ->
                    async {
                        addon.catalogs
                            .filter { it.supportsExtra("search") }
                            .take(2)
                            .map { catalog ->
                                try {
                                    withTimeoutOrNull(6_000) {
                                        repository.searchCatalog(
                                            addon.manifestUrl,
                                            catalog.type,
                                            catalog.id,
                                            text
                                        )
                                    } ?: emptyList()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Throwable) {
                                    emptyList()
                                }
                            }
                            .flatten()
                            .map { itemOf(it) }
                    }
                }
                for (hits in perAddon) {
                    for (item in hits.await()) {
                        if (found.size >= 120) break
                        if (seen.add(item.id)) found += item
                    }
                    publish()
                }
                onDone(
                    json.encodeToString(
                        AppleShelves(listOf(AppleShelf("search", "نتائج البحث", found.take(120))))
                    )
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر البحث")
            } finally {
                searchJobs.remove(token)
            }
        }
    }

    /** Stops a live search the interface no longer wants. */
    fun cancelSearch(token: String) {
        searchJobs.remove(token)?.cancel()
    }

    /** Full metadata, seasons and episodes for the detail page. */
    fun loadDetail(itemId: String, type: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                if (itemId.startsWith("tmdb:")) {
                    val fromTmdb = tmdbDetail(itemId)
                    if (fromTmdb == null) {
                        onError("تعذر تحميل بيانات TMDB لهذا العنوان")
                    } else {
                        onDone(json.encodeToString(fromTmdb))
                    }
                    return@launch
                }
                var meta: com.cinemax.app.addons.StremioMeta? = null
                for (addon in addonStore.load()) {
                    if (!AddonCapabilities.supportsMeta(addon, type)) continue
                    if (addon.idPrefixes.isNotEmpty() && addon.idPrefixes.none { itemId.startsWith(it) }) continue
                    meta = try {
                        repository.loadMeta(addon.manifestUrl, type, itemId)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        null
                    }
                    if (meta != null) break
                }
                val loaded = meta
                if (loaded == null) {
                    onError("لا توجد بيانات لهذا العنوان")
                    return@launch
                }
                val seasons = loaded.videos.mapNotNull { it.season }.distinct().sorted()
                val episodes = loaded.videos
                    .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
                    .map { video ->
                        AppleItem(
                            id = video.id,
                            type = "episode",
                            name = video.name ?: video.title ?: video.id,
                            poster = video.thumbnail,
                            background = video.thumbnail,
                            releaseInfo = video.released,
                            overview = video.overview ?: video.title
                        )
                    }
                val detail = AppleDetail(
                    item = AppleItem(
                        id = loaded.id,
                        type = loaded.type,
                        name = loaded.name,
                        poster = loaded.poster,
                        background = loaded.background,
                        releaseInfo = loaded.releaseInfo,
                        rating = loaded.imdbRating,
                        overview = loaded.description
                    ),
                    genres = loaded.genres,
                    runtime = loaded.runtime,
                    director = loaded.director,
                    cast = loaded.cast,
                    seasons = seasons,
                    episodes = episodes,
                    trailers = loaded.trailers
                        .map { it.source }
                        .filter { it.startsWith("https://") }
                        .distinct()
                        .take(3)
                )
                onDone(json.encodeToString(detail))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر تحميل التفاصيل")
            }
        }
    }

    /**
     * Every playable link for an item or an episode id: the Stremio addons and the
     * enabled Nuvio providers are asked in parallel, and whichever source fails is
     * reported instead of disappearing silently.
     */
    fun loadStreams(itemId: String, type: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                // A TMDB row (or episode) has no id any Stremio addon knows, so it is
                // translated to its IMDb id first — otherwise the list stays empty.
                val queryId = if (itemId.startsWith("tmdb:")) {
                    resolveTmdbStreamId(itemId) ?: run {
                        onDone(json.encodeToString(AppleStreams(emptyList())))
                        return@launch
                    }
                } else {
                    itemId
                }
                val addons = addonStore.load()
                    .filter { AddonCapabilities.supportsStreams(it, type, queryId) && it.name !in offSourceNames() }
                val failures = mutableListOf<String>()
                val found = coroutineScope {
                    addons.map { addon ->
                        async {
                            try {
                                withTimeoutOrNull(15_000) {
                                    repository.loadStreams(addon.manifestUrl, type, queryId)
                                }?.mapNotNull { stream ->
                                    val url = stream.url ?: return@mapNotNull null
                                    // mpv carries its own network stack, so a plain
                                    // http link plays just as well as an https one.
                                    if (!url.startsWith("https://") && !url.startsWith("http://")) {
                                        return@mapNotNull null
                                    }
                                    AppleStream(
                                        url = url,
                                        addon = addon.name,
                                        title = stream.title ?: stream.name ?: url,
                                        quality = streamBadges(stream.name, stream.title).firstOrNull(),
                                        headers = stream.behaviorHints.proxyHeaders?.request
                                            ?: emptyMap(),
                                        size = sizeLabel(stream.behaviorHints)
                                    )
                                } ?: run {
                                    failures += "${addon.name}: لا يستجيب"
                                    emptyList()
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                failures += "${addon.name}: " + (error.message ?: "خطأ").take(90)
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten()
                }
                val fromProviders = nuvioStreams(itemId, type, failures)
                // Direct MP4/HLS answers faster than a big MKV, a known CDN is
                // quicker than someone's home server, and a link routed through a
                // free public proxy is the slowest of all. The score mixes those
                // with the quality tier so the best link is the first row.
                val sorted = orderStreams(found + fromProviders)
                onDone(json.encodeToString(AppleStreams(sorted, failures.distinct())))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر جلب المصادر")
            }
        }
    }

    /**
     * Turns one addon stream into what the interface shows. A link points either at a
     * file — the normal case, played here — or at a page the addon wants opened. The
     * second kind is still listed, so a source that only offers a page no longer
     * vanishes without a word; only a bare torrent, which needs a debrid service
     * this app does not use, is left out and counted.
     */
    private fun mapStream(
        addonName: String,
        stream: com.cinemax.app.addons.StremioStream
    ): AppleStream? {
        val url = stream.url
        val quality = streamBadges(stream.name, stream.title).firstOrNull()
        if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
            // mpv carries its own network stack, so a plain http link plays as well.
            return AppleStream(
                url = url,
                addon = addonName,
                title = stream.title ?: stream.name ?: url,
                quality = quality,
                headers = stream.behaviorHints.proxyHeaders?.request ?: emptyMap(),
                size = sizeLabel(stream.behaviorHints)
            )
        }
        val page = stream.externalUrl
            ?: stream.ytId?.takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/watch?v=$it" }
        if (page != null && page.startsWith("http")) {
            return AppleStream(
                url = page,
                addon = addonName,
                title = stream.title ?: stream.name ?: page,
                quality = quality,
                externalUrl = page,
                note = "صفحة خارجية"
            )
        }
        return null
    }

    /** The order a viewer sees: playable links first, then quality, then the addon. */
    private fun orderStreams(streams: List<AppleStream>): List<AppleStream> =
        streams.distinctBy { it.url }.sortedWith(
            compareBy<AppleStream> { it.externalUrl != null }
                .thenByDescending { streamScore(it) }
                .thenByDescending { qualityRank(it.quality) }
                .thenBy { it.addon }
        )

    /** Stops a live fetch whose result the interface no longer shows. */
    fun cancelStreams(token: String) {
        streamJobs.remove(token)?.cancel()
    }

    /**
     * A live version of [loadStreams]. Every addon answers in its own coroutine and
     * the list is handed over the moment that answer lands, so one slow addon never
     * holds back the twelve that already replied. A hard ceiling ends the wait, and
     * `token` lets the caller cancel a fetch it has moved on from.
     */
    fun loadStreamsFast(
        itemId: String,
        type: String,
        token: String,
        onUpdate: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        streamJobs.remove(token)?.cancel()
        streamJobs[token] = scope.launch {
            val found = mutableListOf<AppleStream>()
            val failures = mutableListOf<String>()
            var announced = 0
            var lastPublishAt = 0L
            var unplayable = 0

            fun snapshot(): String =
                json.encodeToString(AppleStreams(orderStreams(found.toList()), failures.distinct()))

            /**
             * The first answer is shown at once; later ones are grouped so a burst of
             * twelve addons does not repaint the list a dozen times in a second. The
             * final snapshot always carries everything.
             */
            fun publish() {
                if (found.size == announced) return
                val now = Clock.System.now().toEpochMilliseconds()
                if (lastPublishAt != 0L && now - lastPublishAt < PUBLISH_INTERVAL_MS) return
                lastPublishAt = now
                announced = found.size
                onUpdate(snapshot())
            }

            try {
                // A TMDB row (or episode) has no id an addon knows, so it is
                // translated to its IMDb id first — otherwise nothing is found.
                val queryId = if (itemId.startsWith("tmdb:")) {
                    resolveTmdbStreamId(itemId) ?: run {
                        onDone(json.encodeToString(AppleStreams(emptyList())))
                        return@launch
                    }
                } else {
                    itemId
                }
                val off = offSourceNames()
                val available = addonStore.load()
                    .filter { AddonCapabilities.supportsStreams(it, type, queryId) && it.name !in off }
                // A source that failed three times in a row rests for a while; if every
                // one of them is resting, they are all tried rather than showing nothing.
                val health = healthEntries()
                val addons = available.filterNot { breakerOpen(it.name, health) }.ifEmpty { available }

                val workers = addons.map { addon ->
                    launch {
                        val startedAt = Clock.System.now().toEpochMilliseconds()
                        val streams = try {
                            withTimeoutOrNull(ADDON_BUDGET_MS) {
                                repository.loadStreams(addon.manifestUrl, type, queryId)
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            failures += addon.name + ": " + (error.message ?: "خطأ").take(90)
                            null
                        }
                        recordHealth(
                            addon.name,
                            Clock.System.now().toEpochMilliseconds() - startedAt,
                            streams != null
                        )
                        if (streams == null) {
                            if (failures.none { it.startsWith(addon.name + ":") }) {
                                failures += "${addon.name}: لا يستجيب"
                            }
                        } else {
                            if (streams.isEmpty()) {
                                failures += "${addon.name}: لا يوفّر رابطًا لهذا العنوان"
                            }
                            val mapped = streams.mapNotNull { mapStream(addon.name, it) }
                            unplayable += streams.size - mapped.size
                            found += mapped
                        }
                        publish()
                    }
                }
                val providers = launch {
                    val fromProviders = try {
                        nuvioStreams(itemId, type, failures)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    found += fromProviders
                    publish()
                }

                withTimeoutOrNull(TOTAL_BUDGET_MS) {
                    (workers + providers).forEach { it.join() }
                }
                (workers + providers).forEach { it.cancel() }
                if (unplayable > 0) {
                    failures += "$unplayable رابطًا بلا ملف مباشر (تورنت يحتاج خدمة debrid)"
                }
                onDone(snapshot())
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر جلب المصادر")
            } finally {
                streamJobs.remove(token)
            }
        }
    }

    /** How one source behaves, as the settings screen reports it. */
    @Serializable
    data class AppleSourceHealth(
        val name: String,
        val ms: Long,
        val failures: Int,
        val skipped: Boolean
    )

    /** What the app remembers about one source between launches. */
    @Serializable
    data class SourceHealthEntry(
        val ms: Long = 0,
        val failures: Int = 0,
        val last: Long = 0
    )

    private fun healthEntries(): MutableMap<String, SourceHealthEntry> {
        val raw = NSUserDefaults.standardUserDefaults.stringForKey(SOURCE_HEALTH_KEY)
            ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, SourceHealthEntry>>(raw).toMutableMap()
        } catch (_: Throwable) {
            mutableMapOf()
        }
    }

    private fun saveHealth(entries: Map<String, SourceHealthEntry>) {
        val raw = runCatching { json.encodeToString(entries) }.getOrNull() ?: return
        NSUserDefaults.standardUserDefaults.setObject(raw, forKey = SOURCE_HEALTH_KEY)
    }

    /**
     * Records one answer: a smoothed average of how long the addon takes, and how
     * many times it has failed in a row. The average is what the settings screen
     * shows, the count is what keeps a dead addon from being asked again.
     */
    private fun recordHealth(name: String, elapsedMs: Long, ok: Boolean) {
        val entries = healthEntries()
        val previous = entries[name]
        val smoothed = when {
            previous == null || previous.ms <= 0 -> elapsedMs
            ok -> (previous.ms * 2 + elapsedMs) / 3
            else -> previous.ms
        }
        entries[name] = SourceHealthEntry(
            ms = smoothed.coerceAtLeast(0),
            failures = if (ok) 0 else (previous?.failures ?: 0) + 1,
            last = Clock.System.now().toEpochMilliseconds()
        )
        saveHealth(entries)
    }

    /**
     * A circuit breaker: three failures in a row keep the addon out of the next
     * fetches for ten minutes, so one broken source stops costing time on every title.
     * A single answer brings it straight back.
     */
    private fun breakerOpen(name: String, health: Map<String, SourceHealthEntry>): Boolean {
        val entry = health[name] ?: return false
        if (entry.failures < BREAKER_FAILURES) return false
        val age = Clock.System.now().toEpochMilliseconds() - entry.last
        return age in 0 until BREAKER_OPEN_MS
    }

    /** The sources screen: every addon with its average answer time and failures. */
    fun sourceHealth(onDone: (String) -> Unit) {
        val health = healthEntries()
        val rows = health.map { (name, entry) ->
            AppleSourceHealth(
                name = name,
                ms = entry.ms,
                failures = entry.failures,
                skipped = breakerOpen(name, health)
            )
        }.sortedByDescending { it.ms }
        onDone(json.encodeToString(rows))
    }

    fun resetSourceHealth() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(SOURCE_HEALTH_KEY)
    }

    /**
     * Addons and providers the viewer switched off in the sources list. They are not
     * merely hidden: they are never asked, which also shortens the wait.
     */
    private fun offSourceNames(): Set<String> =
        NSUserDefaults.standardUserDefaults
            .stringArrayForKey("cinemax.offSourceAddons")
            ?.filterIsInstance<String>()
            ?.toSet()
            ?: emptySet()

    /** Runs every enabled Nuvio provider and returns its links, reporting failures. */
    private suspend fun nuvioStreams(
        itemId: String,
        type: String,
        failures: MutableList<String>
    ): List<AppleStream> {
        val enabled = runCatching { nuvioStore.loadEnabledProviders() }.getOrDefault(emptySet())
        val providers = runCatching { nuvioStore.loadRepositories() }.getOrDefault(emptyList())
            .flatMap { repository ->
                repository.scrapers
                    // A provider the viewer turned off, or one built for another
                    // platform, is never asked: it only costs time and answers
                    // with nothing on this device.
                    .filter { scraper ->
                        scraper.enabled && scraper.runsOnIos &&
                            "${repository.manifestUrl}#${scraper.id}" in enabled
                    }
                    .map { scraper ->
                        NuvioProviderEntry(
                            "${repository.manifestUrl}#${scraper.id}",
                            repository.name,
                            repository,
                            scraper
                        )
                    }
            }
            // Two repositories carry the same provider under the same id, so the
            // first copy answers a title and the second is not run for it again.
            .distinctBy { it.descriptor.id }
        // A provider the viewer switched off is never asked, so it costs no time.
        // Each provider's script runs on the main thread, so ninety of them was a
        // freeze of many seconds before a single link appeared; the first twelve
        // enabled ones are asked here, and the rest stay for the viewer to pick.
        val active = providers
            .filter { it.displayName !in offSourceNames() }
            .take(MAX_PROVIDERS_PER_FETCH)
        if (active.isEmpty()) return emptyList()
        val (season, episode) = seasonEpisodeOf(itemId, type)
        val request = try {
            tmdbResolver.request(itemId, type, season, episode)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        } ?: run {
            failures += "Nuvio: تعذر تحويل المعرّف إلى TMDB (أضف مفتاح TMDB)"
            return emptyList()
        }
        val report = try {
            nuvioSourcesFrom(nuvioClient, nuvioRuntime, active, request)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            failures += "Nuvio: " + (error.message ?: "خطأ").take(90)
            return emptyList()
        }
        failures += report.failures
        return report.streams.mapNotNull { option ->
            val url = option.stream.url ?: return@mapNotNull null
            if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapNotNull null
            AppleStream(
                url = url,
                addon = option.addonName,
                title = option.stream.title ?: option.stream.name ?: url,
                quality = streamBadges(option.stream.name, option.stream.title).firstOrNull(),
                headers = option.stream.behaviorHints.proxyHeaders?.request ?: emptyMap(),
                size = sizeLabel(option.stream.behaviorHints)
            )
        }
    }

    /** Season and episode numbers carried at the end of an id, when it has them. */
    private fun seasonEpisodeOf(itemId: String, type: String): Pair<Int?, Int?> {
        if (type != "series" && type != "episode") return null to null
        val parts = itemId.split(":")
        if (parts.size < 3) return null to null
        val season = parts[parts.size - 2].toIntOrNull()
        val episode = parts[parts.size - 1].toIntOrNull()
        if (season == null || episode == null) return null to null
        return season to episode
    }

    /**
     * Turns "tmdb:123:series:1:2" into the "tt…:1:2" id stream addons expect.
     * A title without an IMDb id answers null, and the list stays honestly empty.
     */
    private suspend fun resolveTmdbStreamId(itemId: String): String? {
        val parts = itemId.split(":")
        if (parts.size < 3) return null
        val key = nuvioStore.loadTmdbApiKey()
        if (key.isBlank()) return null
        val kind = if (parts[2] == "movie") "movie" else "tv"
        val body = try {
            tmdbHttp.get("https://api.themoviedb.org/3/$kind/${parts[1]}/external_ids?api_key=$key").bodyAsText()
        } catch (_: Throwable) {
            return null
        }
        val imdb = (Json.parseToJsonElement(body).jsonObject["imdb_id"] as? JsonPrimitive)?.content
            ?.takeIf { it.startsWith("tt") }
            ?: return null
        return if (parts.size >= 5) "$imdb:${parts[3]}:${parts[4]}" else imdb
    }

    /** A short size label from the hints, or from the byte count when present. */
    private fun sizeLabel(hints: com.cinemax.app.addons.StremioBehaviorHints): String? {
        hints.videoSizeLabel?.takeIf { it.isNotBlank() }?.let { return it }
        val bytes = hints.videoSize ?: return null
        if (bytes <= 0L) return null
        val mb = bytes / 1_048_576.0
        return if (mb >= 1024) {
            val gb = (mb / 1024.0 * 10).toInt() / 10.0
            "$gb GB"
        } else {
            "${mb.toInt()} MB"
        }
    }

    private fun qualityRank(quality: String?): Int = when (quality) {        "4K" -> 5
        "1080p" -> 4
        "720p" -> 3
        "480p" -> 2
        else -> 1
    }

    private fun isProxied(stream: AppleStream): Boolean {
        val url = stream.url.lowercase()
        val title = (stream.title + " " + stream.addon).lowercase()
        val host = url.substringAfter("://", "").substringBefore('/')
        return "proxy" in title || "prox" in host || "proxy" in url.substringBefore("?") ||
            "?proxy" in url || "&proxy" in url
    }

    /**
     * Free speed heuristics on top of the quality tier: a direct MP4/HLS link
     * starts sooner than a large MKV, a CDN host answers faster than a personal
     * server, and a link wrapped in a free public proxy is demoted.
     */
    private fun streamScore(stream: AppleStream): Int {
        val url = stream.url.lowercase()
        var score = qualityRank(stream.quality) * 4
        if (".m3u8" in url) score += 3
        if (".mp4" in url) score += 2
        val host = url.substringAfter("://", "").substringBefore('/')
        val fastHosts = listOf(
            "r2.dev", "workers.dev", "cloudfront", "fastly", "bunnycdn",
            "storage.googleapis.com", "googleusercontent.com", "akamai", "b-cdn"
        )
        if (fastHosts.any { it in host }) score += 2
        if (isProxied(stream)) score -= 3
        if (!url.startsWith("https://")) score -= 2
        return score
    }

    fun loadProgress(onDone: (String) -> Unit) {
        val items = progressStore.load().map { entry ->
            AppleProgress(
                item = itemOf(entry.item),
                positionMillis = entry.positionMillis,
                durationMillis = entry.durationMillis,
                episodeLabel = entry.videoLabel,
                fraction = entry.progressFraction.toDouble()
            )
        }
        onDone(json.encodeToString(items))
    }

    fun loadFavorites(onDone: (String) -> Unit) {
        val items = favoritesStore.load().map(::itemOf)
        onDone(json.encodeToString(items))
    }

    /** Adds or removes one title from the watchlist and answers the new state. */
    fun toggleFavorite(itemJson: String, onDone: (Boolean) -> Unit) {
        val item = runCatching { json.decodeFromString<AppleItem>(itemJson) }.getOrNull()
        if (item == null) {
            onDone(false)
            return
        }
        val current = favoritesStore.load()
        val present = current.any { it.id == item.id }
        val next = if (present) {
            current.filterNot { it.id == item.id }
        } else {
            current + previewOf(item)
        }
        favoritesStore.save(next)
        onDone(!present)
    }

    fun isFavorite(itemId: String): Boolean = favoritesStore.load().any { it.id == itemId }

    /** Empties the resume list; the answers are the counts after the change. */
    fun clearWatchProgress(onDone: (String) -> Unit) {
        progressStore.clear()
        favoritesStore.load()
        onDone("{\"progress\":${progressStore.load().size},\"favorites\":${favoritesStore.load().size}}")
    }

    /** Empties the watchlist and answers the counts after the change. */
    fun clearFavorites(onDone: (String) -> Unit) {
        favoritesStore.save(emptyList())
        onDone("{\"progress\":${progressStore.load().size},\"favorites\":${favoritesStore.load().size}}")
    }

    /** How many titles and resume points are stored, for the data screen. */
    fun libraryCounts(onDone: (String) -> Unit) {
        onDone("{\"progress\":${progressStore.load().size},\"favorites\":${favoritesStore.load().size}}")
    }

    @Serializable
    data class AppleNuvioProvider(
        val key: String,
        val name: String,
        val description: String?,
        val version: String,
        val types: List<String>,
        val enabled: Boolean,
        val hasSettings: Boolean = false,
        val disabledOnIos: Boolean = false
    )

    @Serializable
    data class AppleNuvioRepository(
        val url: String,
        val name: String,
        val version: String,
        val description: String?,
        val providers: List<AppleNuvioProvider>
    )

    /** Installed Nuvio repositories with their providers and which are enabled. */
    fun nuvioRepositories(onDone: (String) -> Unit) {
        onDone(json.encodeToString(nuvioSnapshot()))
    }

    private fun nuvioSnapshot(): List<AppleNuvioRepository> {
        val enabled = runCatching { nuvioStore.loadEnabledProviders() }.getOrDefault(emptySet())
        val repositories = runCatching { nuvioStore.loadRepositories() }.getOrDefault(emptyList())
        return repositories.map { repository ->
            AppleNuvioRepository(
                url = repository.manifestUrl,
                name = repository.name,
                version = repository.version,
                description = repository.description,
                providers = repository.scrapers.map { scraper ->
                    val key = repository.manifestUrl + "#" + scraper.id
                    AppleNuvioProvider(
                        key = key,
                        name = scraper.name,
                        description = scraper.description,
                        version = scraper.version,
                        types = scraper.supportedTypes,
                        enabled = scraper.enabled && scraper.runsOnIos && key in enabled,
                        hasSettings = scraper.hasSettings,
                        disabledOnIos = !scraper.runsOnIos
                    )
                }
            )
        }
    }

    /** Reads a provider repository and enables every provider it offers. */
    fun addNuvioRepository(url: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val repository = nuvioClient.loadRepository(url)
                val current = runCatching { nuvioStore.loadRepositories() }.getOrDefault(emptyList())
                    .filterNot { it.manifestUrl == repository.manifestUrl }
                nuvioStore.saveRepositories(current + repository)
                val keys = runCatching { nuvioStore.loadEnabledProviders() }
                    .getOrDefault(emptySet()).toMutableSet()
                repository.scrapers.filter { it.enabled && it.runsOnIos }.forEach { scraper ->
                    keys.add(repository.manifestUrl + "#" + scraper.id)
                }
                nuvioStore.saveEnabledProviders(keys)
                nuvioClient.clearScriptCache()
                onDone(json.encodeToString(nuvioSnapshot()))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر إضافة المستودع")
            }
        }
    }

    fun removeNuvioRepository(url: String, onDone: (String) -> Unit) {
        val current = runCatching { nuvioStore.loadRepositories() }.getOrDefault(emptyList())
        nuvioStore.saveRepositories(current.filterNot { it.manifestUrl == url })
        val keys = runCatching { nuvioStore.loadEnabledProviders() }.getOrDefault(emptySet())
        nuvioStore.saveEnabledProviders(keys.filterNot { it.startsWith(url + "#") }.toSet())
        nuvioClient.clearScriptCache()
        onDone(json.encodeToString(nuvioSnapshot()))
    }

    fun setNuvioProvider(key: String, enabled: Boolean, onDone: (String) -> Unit) {
        val keys = runCatching { nuvioStore.loadEnabledProviders() }
            .getOrDefault(emptySet()).toMutableSet()
        if (enabled) keys.add(key) else keys.remove(key)
        nuvioStore.saveEnabledProviders(keys)
        onDone(json.encodeToString(nuvioSnapshot()))
    }

    @Serializable
    data class AppleCatalogPage(
        val items: List<AppleItem>,
        val hasMore: Boolean,
        val genres: List<String> = emptyList()
    )

    @Serializable
    data class AppleSubtitle(
        val id: String,
        val url: String,
        val lang: String,
        val label: String,
        val addon: String
    )

    /**
     * One page of a catalogue, which is what turns a home row into a browser: the
     * `skip` extra walks the rest of the list and `genre` narrows it when the addon
     * declares genres.
     */
    fun loadCatalogPage(
        addonId: String,
        catalogId: String,
        type: String,
        skip: Int,
        genre: String?,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val addon = addonStore.load().firstOrNull { it.id == addonId }
                if (addon == null) {
                    onError("الإضافة غير مثبتة بعد")
                    return@launch
                }
                val catalog = addon.catalogs.firstOrNull { it.id == catalogId && it.type == type }
                    ?: addon.catalogs.firstOrNull { it.id == catalogId }
                if (catalog == null) {
                    onError("لا كتالوج بهذا الاسم في الإضافة")
                    return@launch
                }
                val extra = mutableMapOf<String, String>()
                if (skip > 0 && catalog.supportsExtra("skip")) extra["skip"] = skip.toString()
                if (!genre.isNullOrBlank() && catalog.supportsExtra("genre")) extra["genre"] = genre
                val items = try {
                    withTimeoutOrNull(15_000) {
                        repository.loadCatalog(addon.manifestUrl, catalog.type, catalog.id, extra)
                    } ?: emptyList()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    emptyList()
                }
                val genres = catalog.extra.firstOrNull { it.name == "genre" }?.options ?: emptyList()
                onDone(
                    json.encodeToString(
                        AppleCatalogPage(
                            items = items.take(60).map(::itemOf),
                            hasMore = items.isNotEmpty(),
                            genres = genres
                        )
                    )
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر تحميل الصفحة")
            }
        }
    }

    /** Subtitle addons that answer for this id, gathered in parallel. */
    fun loadSubtitles(itemId: String, type: String, onDone: (String) -> Unit) {
        scope.launch {
            val addons = addonStore.load().filter { AddonCapabilities.supports(it, "subtitles", type) }
            val found = coroutineScope {
                addons.map { addon ->
                    async {
                        val subtitles = try {
                            withTimeoutOrNull(12_000) {
                                repository.loadSubtitles(addon.manifestUrl, type, itemId)
                            } ?: emptyList()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            emptyList()
                        }
                        subtitles.mapNotNull { subtitle ->
                            val url = subtitle.url.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            AppleSubtitle(
                                id = addon.id + "|" + url,
                                url = url,
                                lang = subtitle.lang,
                                label = subtitle.label?.takeIf { it.isNotBlank() } ?: subtitle.lang,
                                addon = addon.name
                            )
                        }
                    }
                }.awaitAll().flatten()
            }
            val sorted = found
                .distinctBy { it.url }
                .sortedWith(compareByDescending<AppleSubtitle> { it.lang.lowercase().startsWith("ar") }
                    .thenBy { it.lang })
            onDone(json.encodeToString(sorted))
        }
    }

    /** Another small snapshot, for the statistics screen. */
    fun extraCounts(onDone: (String) -> Unit) {
        val watched = runCatching { WatchedEpisodesStore().load().size }.getOrDefault(0)
        val addons = runCatching { addonStore.load().size }.getOrDefault(0)
        onDone("{\"watched\":$watched,\"addons\":$addons}")
    }

    /** Drops one resume point, used by the history screen. */
    fun removeProgress(itemId: String, videoLabel: String?, onDone: (String) -> Unit) {
        val target = progressStore.load().firstOrNull { entry ->
            entry.item.id == itemId && (videoLabel == null || entry.videoLabel == videoLabel)
        }
        if (target != null) progressStore.remove(target.key)
        onDone("{\"progress\":${progressStore.load().size},\"favorites\":${favoritesStore.load().size}}")
    }

    /** A whole snapshot (saved list, resume points, marked episodes, addons). */
    fun exportBackup(onDone: (String) -> Unit) {
        val watched = runCatching { WatchedEpisodesStore().load() }.getOrDefault(emptySet())
        val backup = CinemaxBackup(
            favorites = favoritesStore.load(),
            progress = progressStore.load(),
            watched = watched.toList(),
            addons = addonStore.load()
        )
        onDone(encodeBackup(backup))
    }

    /** Restores a snapshot pasted into the clipboard, and answers what it held. */
    fun importBackup(raw: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        val backup = decodeBackup(raw)
        if (backup == null) {
            onError("النسخة غير صالحة")
            return
        }
        favoritesStore.save(backup.favorites)
        progressStore.restore(backup.progress)
        if (backup.watched.isNotEmpty()) WatchedEpisodesStore().save(backup.watched.toSet())
        if (backup.addons.isNotEmpty()) addonStore.save(backup.addons)
        repository.clearCaches()
        onDone(backup.summary())
    }

    /** Records where the SwiftUI player stopped, so "أكمل المشاهدة" stays right. */
    fun saveProgress(
        itemJson: String,
        positionMillis: Long,
        durationMillis: Long,
        videoId: String?,
        videoLabel: String?
    ) {
        val item = runCatching { json.decodeFromString<AppleItem>(itemJson) }.getOrNull() ?: return
        if (durationMillis <= 0L) return
        progressStore.save(
            WatchProgress(
                item = previewOf(item),
                positionMillis = positionMillis,
                durationMillis = durationMillis,
                updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                videoId = videoId,
                videoLabel = videoLabel
            )
        )
    }

    /** The saved position for a title or episode, as JSON {"positionMillis":n}. */
    fun resumeJson(itemId: String, videoId: String?, onDone: (String) -> Unit) {
        val entry = progressStore.load().firstOrNull { saved ->
            saved.item.id == itemId && (videoId == null || saved.videoId == videoId)
        }
        onDone("{\"positionMillis\":${entry?.positionMillis ?: 0L},\"durationMillis\":${entry?.durationMillis ?: 0L}}")
    }

    @Serializable
    data class AppleAddon(val id: String, val name: String, val url: String, val version: String)

    @Serializable
    data class AppleSuggestedAddon(val name: String, val url: String)

    /** Installed addons, for the addon manager in the SwiftUI settings. */
    fun installedAddons(onDone: (String) -> Unit) {
        val list = addonStore.load().map { AppleAddon(it.id, it.name, it.manifestUrl, it.version) }
        onDone(json.encodeToString(list))
    }

    /** The sources shipped inside the app bundle, de-duplicated and normalised. */
    fun suggestedAddons(onDone: (String) -> Unit) {
        val lists = listOf("raif-sources", "raif2-sources")
            .mapNotNull { readBundledText(it, "json") }
            .map { parseSuggestedSources(it) }
        val merged = dedupeSources(lists).mapNotNull { source ->
            val normalized = com.cinemax.app.addons.StremioAddonClient.manifestUrl(source.url)
                ?: return@mapNotNull null
            AppleSuggestedAddon(name = source.name, url = normalized)
        }
        onDone(json.encodeToString(merged))
    }

    fun installAddon(url: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val manifest = repository.loadManifest(url)
                val normalized = com.cinemax.app.addons.StremioAddonClient.manifestUrl(url) ?: url
                val installed = com.cinemax.app.addons.InstalledAddon(
                    manifestUrl = normalized,
                    id = manifest.id,
                    name = manifest.name,
                    version = manifest.version,
                    description = manifest.description,
                    installedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    resources = manifest.resourceNames().toList(),
                    types = manifest.types,
                    idPrefixes = manifest.idPrefixes,
                    catalogs = manifest.catalogs,
                    configurable = manifest.behaviorHints.configurable
                )
                val current = addonStore.load().filterNot { it.id == installed.id }
                addonStore.save(current + installed)
                repository.clearCaches()
                onDone(manifest.name)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر تثبيت الإضافة")
            }
        }
    }

    fun removeAddon(url: String) {
        addonStore.save(addonStore.load().filterNot { it.manifestUrl == url })
        repository.clearCaches()
    }

    /**
     * Moves an installed addon up or down the list. The order decides which addon's
     * catalogues are asked first, so this is how a viewer promotes a source.
     */
    fun moveAddon(url: String, delta: Int, onDone: (String) -> Unit) {
        val list = addonStore.load().toMutableList()
        val index = list.indexOfFirst { it.manifestUrl == url }
        if (index >= 0 && list.isNotEmpty()) {
            val target = (index + delta).coerceIn(0, list.lastIndex)
            if (target != index) {
                val moved = list.removeAt(index)
                list.add(target, moved)
                addonStore.save(list)
                repository.clearCaches()
            }
        }
        onDone(json.encodeToString(list.map { AppleAddon(it.id, it.name, it.manifestUrl, it.version) }))
    }

    @Serializable
    data class AppleAddonHealth(
        val id: String,
        val name: String,
        val url: String,
        val ok: Boolean,
        val ms: Long,
        val version: String,
        val storedVersion: String
    )

    /**
     * Asks every installed addon for its manifest again, timing the answer, so the
     * screen can show which ones are alive and which advertise a newer version.
     */
    fun checkAddons(onDone: (String) -> Unit) {
        scope.launch {
            val installed = addonStore.load()
            val results = coroutineScope {
                installed.map { addon ->
                    async {
                        val started = Clock.System.now().toEpochMilliseconds()
                        val manifest = try {
                            withTimeoutOrNull(8_000) { repository.loadManifest(addon.manifestUrl) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            null
                        }
                        val elapsed = Clock.System.now().toEpochMilliseconds() - started
                        AppleAddonHealth(
                            id = addon.id,
                            name = addon.name,
                            url = addon.manifestUrl,
                            ok = manifest != null,
                            ms = elapsed,
                            version = manifest?.version ?: "",
                            storedVersion = addon.version
                        )
                    }
                }.awaitAll()
            }
            onDone(json.encodeToString(results))
        }
    }

    /** Re-fetches one addon's manifest and stores whatever it now advertises. */
    fun updateAddon(url: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val manifest = repository.loadManifest(url)
                val current = addonStore.load()
                val existing = current.firstOrNull { it.manifestUrl == url }
                val refreshed = com.cinemax.app.addons.InstalledAddon(
                    manifestUrl = url,
                    id = manifest.id,
                    name = manifest.name,
                    version = manifest.version,
                    description = manifest.description,
                    installedAtEpochMillis = existing?.installedAtEpochMillis
                        ?: Clock.System.now().toEpochMilliseconds(),
                    resources = manifest.resourceNames().toList(),
                    types = manifest.types,
                    idPrefixes = manifest.idPrefixes,
                    catalogs = manifest.catalogs,
                    configurable = manifest.behaviorHints.configurable
                )
                addonStore.save(current.filterNot { it.manifestUrl == url } + refreshed)
                repository.clearCaches()
                onDone("${manifest.name}|${manifest.version}")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر تحديث الإضافة")
            }
        }
    }

    /** Refreshes every addon that advertises a newer version and answers how many. */
    fun updateAllAddons(onDone: (String) -> Unit) {
        scope.launch {
            val installed = addonStore.load()
            var updated = 0
            val kept = installed.toMutableList()
            for (addon in installed) {
                val manifest = try {
                    withTimeoutOrNull(8_000) { repository.loadManifest(addon.manifestUrl) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    null
                } ?: continue
                if (manifest.version == addon.version) continue
                kept.removeAll { it.manifestUrl == addon.manifestUrl }
                kept.add(
                    com.cinemax.app.addons.InstalledAddon(
                        manifestUrl = addon.manifestUrl,
                        id = manifest.id,
                        name = manifest.name,
                        version = manifest.version,
                        description = manifest.description,
                        installedAtEpochMillis = addon.installedAtEpochMillis,
                        resources = manifest.resourceNames().toList(),
                        types = manifest.types,
                        idPrefixes = manifest.idPrefixes,
                        catalogs = manifest.catalogs,
                        configurable = manifest.behaviorHints.configurable
                    )
                )
                updated += 1
            }
            addonStore.save(kept)
            repository.clearCaches()
            onDone("{\"updated\":$updated}")
        }
    }

    /** Installs every shipped source that is still missing, one after another. */
    fun installAllSuggested(onDone: (String) -> Unit) {
        scope.launch {
            val lists = listOf("raif-sources", "raif2-sources")
                .mapNotNull { readBundledText(it, "json") }
                .map { parseSuggestedSources(it) }
            val urls = dedupeSources(lists)
                .mapNotNull { com.cinemax.app.addons.StremioAddonClient.manifestUrl(it.url) }
            val installed = addonStore.load().toMutableList()
            var added = 0
            var failed = 0
            for (url in urls) {
                if (installed.any { it.manifestUrl == url }) continue
                try {
                    val manifest = repository.loadManifest(url)
                    installed.removeAll { it.id == manifest.id }
                    installed.add(
                        com.cinemax.app.addons.InstalledAddon(
                            manifestUrl = url,
                            id = manifest.id,
                            name = manifest.name,
                            version = manifest.version,
                            description = manifest.description,
                            installedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                            resources = manifest.resourceNames().toList(),
                            types = manifest.types,
                            idPrefixes = manifest.idPrefixes,
                            catalogs = manifest.catalogs,
                            configurable = manifest.behaviorHints.configurable
                        )
                    )
                    added += 1
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    failed += 1
                }
            }
            addonStore.save(installed)
            repository.clearCaches()
            onDone("{\"added\":$added,\"failed\":$failed}")
        }
    }

    /** One catalogue an installed addon brings, for that addon's own page. */
    @Serializable
    data class AppleAddonCatalog(
        val id: String,
        val type: String,
        val name: String,
        val genres: List<String> = emptyList()
    )

    /** Everything one addon page shows, read from the stored manifest. */
    @Serializable
    data class AppleAddonProfile(
        val id: String,
        val name: String,
        val url: String,
        val version: String,
        val description: String,
        val installedAt: Long,
        val resources: List<String>,
        val types: List<String>,
        val catalogs: List<AppleAddonCatalog>,
        val configurable: Boolean
    )

    /** What a manifest URL offers, read once before it is installed. */
    @Serializable
    data class AppleAddonPreview(
        val name: String,
        val id: String,
        val version: String,
        val description: String,
        val resources: List<String>,
        val types: List<String>,
        val catalogs: Int,
        val configurable: Boolean,
        val installed: Boolean
    )

    /**
     * The full picture of every installed addon: what it can answer, which
     * catalogues it brings and when it was added. The addon manager needs this to
     * open a page per addon and to narrow the list by capability.
     */
    fun addonProfiles(onDone: (String) -> Unit) {
        val profiles = addonStore.load().map { addon ->
            AppleAddonProfile(
                id = addon.id,
                name = addon.name,
                url = addon.manifestUrl,
                version = addon.version,
                description = addon.description ?: "",
                installedAt = addon.installedAtEpochMillis,
                resources = addon.resources,
                types = addon.types,
                catalogs = addon.catalogs.map { catalog ->
                    AppleAddonCatalog(
                        id = catalog.id,
                        type = catalog.type,
                        name = catalog.name?.takeIf { it.isNotBlank() } ?: catalog.id,
                        genres = catalog.extra.firstOrNull { it.name == "genre" }?.options ?: emptyList()
                    )
                },
                configurable = addon.configurable
            )
        }
        onDone(json.encodeToString(profiles))
    }

    /**
     * Reads a manifest without installing it, so a pasted link can be checked
     * first: what it offers, how many catalogues, and whether it is here already.
     */
    fun previewAddon(url: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val normalized = com.cinemax.app.addons.StremioAddonClient.manifestUrl(url) ?: url
                val manifest = withTimeoutOrNull(15_000) { repository.loadManifest(url) }
                if (manifest == null) {
                    onError("لم تستجب الإضافة")
                    return@launch
                }
                val installed = addonStore.load()
                    .any { it.manifestUrl == normalized || it.id == manifest.id }
                onDone(
                    json.encodeToString(
                        AppleAddonPreview(
                            name = manifest.name,
                            id = manifest.id,
                            version = manifest.version,
                            description = manifest.description ?: "",
                            resources = manifest.resourceNames().sorted(),
                            types = manifest.types,
                            catalogs = manifest.catalogs.size,
                            configurable = manifest.behaviorHints.configurable,
                            installed = installed
                        )
                    )
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر قراءة الإضافة")
            }
        }
    }

    /**
     * Remembers which interface the viewer chose and tells the host to rebuild its
     * root: the SwiftUI shell observes CN_SWITCH_NOTIFICATION.
     */
    fun switchInterface(classic: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(classic, CLASSIC_INTERFACE_KEY)
        NSNotificationCenter.defaultCenter.postNotificationName(CN_SWITCH_NOTIFICATION, null)
    }

    fun isClassicInterfaceSelected(): Boolean =
        NSUserDefaults.standardUserDefaults.boolForKey(CLASSIC_INTERFACE_KEY)

    fun versionLabel(): String = appVersionLabel()

    private companion object {
        const val CLASSIC_INTERFACE_KEY = "cinemax.ui.classic"
        const val CN_SWITCH_NOTIFICATION = "CinemaxSwitchInterface"
        const val SHELVES_CACHE_KEY = "cinemax.apple.shelves.v1"
        /** How long one addon may take before the list moves on without it. */
        const val ADDON_BUDGET_MS = 7_000L
        /** The whole wait, so a viewer never stares at a spinner for long. */
        const val TOTAL_BUDGET_MS = 9_000L
        /** How often the growing list may be handed over, in milliseconds. */
        const val PUBLISH_INTERVAL_MS = 450L
        /** Failures in a row before a source is left alone for a while. */
        const val BREAKER_FAILURES = 3
        /** How long it is left alone, in milliseconds. */
        const val BREAKER_OPEN_MS = 10L * 60L * 1000L
        const val SOURCE_HEALTH_KEY = "cinemax.sourceHealth.v1"
    }
}
