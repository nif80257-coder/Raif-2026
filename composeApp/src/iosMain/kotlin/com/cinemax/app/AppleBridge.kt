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
import com.cinemax.app.nuvio.NuvioProviderStore
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
data class AppleStream(val url: String, val addon: String, val title: String, val quality: String?)

@Serializable
data class AppleStreams(val streams: List<AppleStream>)

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
    val episodeLabels: List<String> = emptyList()
)

/**
 * The data layer the SwiftUI interface reads.
 *
 * It talks to the same addon store, HTTP caches and history files the Compose
 * interface uses, and serialises everything to JSON so the Swift side needs no
 * knowledge of the Kotlin model types. Every call answers on the main thread.
 */
class CinemaxAppleBridge {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val addonStore = AddonSettingsStore()
    private val repository = AddonRepository(createCinemaxHttpClient())
    private val progressStore = WatchProgressStore()
    private val favoritesStore = FavoritesStore()
    private val nuvioStore = NuvioProviderStore()
    private val tmdbHttp = createCinemaxHttpClient()

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

    /** The first catalogue of every installed addon, one shelf each, in parallel. */
    fun loadShelves(onDone: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val shelves = coroutineScope {
                    addonStore.load().map { addon ->
                        async {
                            val catalog = addon.catalogs.firstOrNull { it.type == "movie" || it.type == "series" }
                                ?: addon.catalogs.firstOrNull()
                                ?: return@async null
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
                                AppleShelf(
                                    id = addon.id + ":" + catalog.id,
                                    title = catalog.name?.takeIf { it.isNotBlank() } ?: addon.name,
                                    items = items.take(24).map(::itemOf)
                                )
                            }
                        }
                    }.awaitAll().filterNotNull().take(14)
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
            "movie/top_rated" to "أعلى تقييمًا"
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
        val body = tmdbHttp.get("https://api.themoviedb.org/3/$path?language=ar&api_key=$key").bodyAsText()
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

    /** Cross-addon search over every catalogue that advertises it. */
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
                for (addon in addonStore.load()) {
                    for (catalog in addon.catalogs.filter { it.supportsExtra("search") }) {
                        val items = try {
                            repository.searchCatalog(addon.manifestUrl, catalog.type, catalog.id, text)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            emptyList()
                        }
                        for (item in items) {
                            if (seen.add(item.id)) found += itemOf(item)
                        }
                    }
                    if (found.size >= 80) break
                }
                onDone(
                    json.encodeToString(
                        AppleShelves(listOf(AppleShelf("search", "نتائج البحث", found.take(80))))
                    )
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر البحث")
            }
        }
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
                    episodes = episodes
                )
                onDone(json.encodeToString(detail))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر تحميل التفاصيل")
            }
        }
    }

    /** Every playable HTTPS link for an item or an episode id, fetched in parallel. */
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
                val addons = addonStore.load().filter { AddonCapabilities.supportsStreams(it, type, queryId) }
                val found = coroutineScope {
                    addons.map { addon ->
                        async {
                            try {
                                withTimeoutOrNull(12_000) {
                                    repository.loadStreams(addon.manifestUrl, type, queryId)
                                }?.mapNotNull { stream ->
                                    val url = stream.url ?: return@mapNotNull null
                                    if (!url.startsWith("https://")) return@mapNotNull null
                                    AppleStream(
                                        url = url,
                                        addon = addon.name,
                                        title = stream.title ?: stream.name ?: url,
                                        quality = streamBadges(stream.name, stream.title).firstOrNull()
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
                // Direct MP4/HLS answers faster than a big MKV, a known CDN is
                // quicker than someone's home server, and a link routed through a
                // free public proxy is the slowest of all. The score mixes those
                // with the quality tier so the best link is the first row.
                val sorted = found
                    .distinctBy { it.url }
                    .sortedWith(
                        compareByDescending<AppleStream> { streamScore(it) }
                            .thenByDescending { qualityRank(it.quality) }
                            .thenBy { it.addon }
                    )
                onDone(json.encodeToString(AppleStreams(sorted)))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر جلب المصادر")
            }
        }
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

    private fun qualityRank(quality: String?): Int = when (quality) {
        "4K" -> 5
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
    }
}
