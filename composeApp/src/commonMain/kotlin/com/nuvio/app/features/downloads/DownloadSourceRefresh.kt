package com.nuvio.app.features.downloads

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginScraper
import com.nuvio.app.features.plugins.pluginContentId
import com.nuvio.app.features.servers.SERVER_SOURCE_ADDON_PREFIX
import com.nuvio.app.features.servers.ServerMatcher
import com.nuvio.app.features.servers.ServerRepository
import com.nuvio.app.features.servers.ServerStreams
import com.nuvio.app.features.streams.PLUGIN_REPOSITORY_ADDON_PREFIX
import com.nuvio.app.features.streams.PLUGIN_SOURCE_ADDON_PREFIX
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamListEngine
import com.nuvio.app.features.streams.StreamParser
import com.nuvio.app.features.streams.fetchStreamSourcePayload
import com.nuvio.app.features.streams.runCatchingUnlessCancelled
import com.nuvio.app.features.streams.streamAddonInstanceId
import com.nuvio.app.features.streams.streamSourceFetchSemaphore
import com.nuvio.app.features.streams.toStreamItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit

/**
 * Asks the source that produced a download row for the same title again, and returns its link today.
 *
 * A signed source address dies on its own schedule, so retrying the stored one only repeats the 403.
 * The row remembers which source it came from (`providerAddonId`), and that prefix decides how to ask
 * it again:
 * - a Stremio add-on is reached at its own `/stream/` address;
 * - one provider of a bundled repository is executed again through the plugin runtime;
 * - a provider list shown grouped by repository runs its providers for this title and takes the first
 *   playable answer, which is what the list itself did when the row was created;
 * - a media server is matched again with `forceRefresh`, so its catalog is re-read rather than the
 *   cached answer being replayed.
 *
 * Every path keeps the same shape as the live source load: the same fetch semaphore, the same
 * ten-second provider budget and the same fifteen-second add-on or server budget, so a retry cannot
 * occupy the app for minutes. A failure returns null, and the caller keeps the stored address.
 */
/** What produced a download row, read back from the `addonId` the source list stored with it. */
internal enum class DownloadSourceKind {
    MediaServer,
    PluginProvider,
    PluginRepository,
    StremioAddon,
    Unknown,
}

/**
 * The kind of source behind [addonId].
 *
 * The two provider prefixes are shared with the source list (`toPluginProviderGroups`), and the
 * order matters: a list grouped by repository is named `plugin-repo:`, which does **not** start
 * with `plugin:`, so one provider and a whole repository are never confused.
 */
internal fun downloadSourceKind(addonId: String?): DownloadSourceKind {
    val value = addonId?.trim().orEmpty()
    return when {
        value.isEmpty() -> DownloadSourceKind.Unknown
        ServerStreams.isServerSourceId(value) -> DownloadSourceKind.MediaServer
        value.startsWith(PLUGIN_SOURCE_ADDON_PREFIX) -> DownloadSourceKind.PluginProvider
        value.startsWith(PLUGIN_REPOSITORY_ADDON_PREFIX) -> DownloadSourceKind.PluginRepository
        else -> DownloadSourceKind.StremioAddon
    }
}

internal suspend fun resolveFreshDownloadUrl(item: DownloadItem): String? {
    val sourceId = item.providerAddonId?.trim().orEmpty()
    if (sourceId.isEmpty()) return null

    return runCatchingUnlessCancelled {
        when (downloadSourceKind(sourceId)) {
            DownloadSourceKind.MediaServer ->
                fromMediaServer(item, sourceId.removePrefix(SERVER_SOURCE_ADDON_PREFIX))

            DownloadSourceKind.PluginProvider ->
                fromPluginScraper(item, sourceId.removePrefix(PLUGIN_SOURCE_ADDON_PREFIX))

            DownloadSourceKind.PluginRepository ->
                fromPluginRepository(item, sourceId.removePrefix(PLUGIN_REPOSITORY_ADDON_PREFIX))

            DownloadSourceKind.StremioAddon -> fromStremioAddon(item, sourceId)

            DownloadSourceKind.Unknown -> null
        }
    }.getOrNull()
}

private suspend fun fromStremioAddon(item: DownloadItem, addonId: String): String? {
    val target = AddonRepository.uiState.value.addons.firstOrNull { addon ->
        val manifest = addon.manifest ?: return@firstOrNull false
        addon.streamAddonInstanceId(manifest.id) == addonId
    } ?: return null
    val manifest = target.manifest ?: return null
    val url = buildAddonResourceUrl(
        manifestUrl = manifest.transportUrl,
        resource = "stream",
        type = item.contentType,
        id = item.videoId,
    )
    val payload = fetchStreamSourcePayload(url = url, forceRefresh = true)
    return StreamParser.parse(
        payload = payload,
        addonName = item.providerName,
        addonId = addonId,
        addonLogo = manifest.logoUrl,
    ).freshLinkFor(item)
}

private suspend fun fromPluginScraper(item: DownloadItem, scraperId: String): String? {
    val state = PluginRepository.uiState.value
    if (!state.pluginsEnabled) return null
    val scraper = state.scrapers.firstOrNull { scraper -> scraper.id == scraperId } ?: return null
    return runCatchingUnlessCancelled { pluginStreams(item, scraper) }
        .getOrNull()
        .orEmpty()
        .freshLinkFor(item)
}

/**
 * A provider list shown grouped by repository names its rows after the repository, not the provider.
 *
 * The providers of that list are asked together, under the same fetch semaphore the list itself
 * uses, and the first playable answer ends the attempt: asking them one after another would cost a
 * timeout for every dead provider of a large list.
 */
private suspend fun fromPluginRepository(item: DownloadItem, repositoryUrl: String): String? {
    val state = PluginRepository.uiState.value
    if (!state.pluginsEnabled) return null
    val scrapers = state.scrapers.filter { scraper ->
        scraper.enabled && scraper.repositoryUrl.lowercase() == repositoryUrl
    }
    if (scrapers.isEmpty()) return null

    return coroutineScope {
        val answers = Channel<String?>(capacity = Channel.UNLIMITED)
        val jobs = scrapers.map { scraper ->
            launch {
                val link = streamSourceFetchSemaphore.withPermit {
                    runCatchingUnlessCancelled { pluginStreams(item, scraper) }
                        .getOrNull()
                        .orEmpty()
                        .freshLinkFor(item)
                }
                answers.send(link)
            }
        }
        var found: String? = null
        repeat(jobs.size) {
            if (found == null) found = answers.receive()
        }
        jobs.forEach { job -> job.cancel() }
        found
    }
}

private suspend fun fromMediaServer(item: DownloadItem, connectionId: String): String? {
    val connection = ServerRepository.connection(connectionId) ?: return null
    val request = ServerMatcher.shared.request(
        type = item.contentType,
        videoId = item.videoId,
        season = item.seasonNumber,
        episode = item.episodeNumber,
    ) ?: return null
    val refs = ServerMatcher.shared.match(connection, request, forceRefresh = true)
    return refs.flatMap { ref -> ServerStreams.candidates(ref) }.freshLinkFor(item)
}

private suspend fun pluginStreams(item: DownloadItem, scraper: PluginScraper): List<StreamItem> {
    val addonId = item.providerAddonId.orEmpty()
    return PluginRepository.executeScraper(
        scraper = scraper,
        tmdbId = pluginContentId(
            videoId = item.videoId,
            season = item.seasonNumber,
            episode = item.episodeNumber,
        ),
        mediaType = item.contentType,
        season = item.seasonNumber,
        episode = item.episodeNumber,
        timeoutMs = StreamListEngine.STREAM_PLUGIN_TIMEOUT_MS,
    ).getOrThrow().map { result ->
        result.toStreamItem(
            scraper = scraper,
            addonName = item.providerName,
            addonId = addonId,
        )
    }
}

/**
 * The row that carried the same label is the one the download came from; any other playable link is
 * better than the dead address it replaces.
 */
private fun List<StreamItem>.freshLinkFor(item: DownloadItem): String? {
    val sameLabel = firstOrNull { stream ->
        stream.streamLabel == item.streamTitle && stream.playableDirectUrl != null
    }
    return sameLabel?.playableDirectUrl
        ?: firstNotNullOfOrNull { stream -> stream.playableDirectUrl }
}
