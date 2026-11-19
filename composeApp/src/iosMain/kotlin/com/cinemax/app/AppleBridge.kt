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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

    /** The first catalogue of every installed addon, one shelf each. */
    fun loadShelves(onDone: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val shelves = mutableListOf<AppleShelf>()
                for (addon in addonStore.load()) {
                    val catalog = addon.catalogs.firstOrNull { it.type == "movie" || it.type == "series" }
                        ?: addon.catalogs.firstOrNull()
                        ?: continue
                    val items = try {
                        repository.loadCatalog(addon.manifestUrl, catalog.type, catalog.id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    if (items.isNotEmpty()) {
                        shelves += AppleShelf(
                            id = addon.id + ":" + catalog.id,
                            title = catalog.name?.takeIf { it.isNotBlank() } ?: addon.name,
                            items = items.take(24).map(::itemOf)
                        )
                    }
                    if (shelves.size >= 14) break
                }
                onDone(json.encodeToString(AppleShelves(shelves)))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر تحميل الكتالوج")
            }
        }
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

    /** Every playable HTTPS link for an item or an episode id. */
    fun loadStreams(itemId: String, type: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val streams = mutableListOf<AppleStream>()
                for (addon in addonStore.load()) {
                    if (!AddonCapabilities.supportsStreams(addon, type, itemId)) continue
                    val found = try {
                        repository.loadStreams(addon.manifestUrl, type, itemId)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    for (stream in found) {
                        val url = stream.url ?: continue
                        if (!url.startsWith("https://")) continue
                        val quality = streamBadges(stream.name, stream.title).firstOrNull()
                        streams += AppleStream(
                            url = url,
                            addon = addon.name,
                            title = stream.title ?: stream.name ?: url,
                            quality = quality
                        )
                    }
                }
                onDone(json.encodeToString(AppleStreams(streams.distinctBy { it.url })))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error.message ?: "تعذر جلب المصادر")
            }
        }
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
    }
}
