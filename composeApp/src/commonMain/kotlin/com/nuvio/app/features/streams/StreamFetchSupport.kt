package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.plugins.PluginRepositoryItem
import com.nuvio.app.features.plugins.PluginRuntimeResult
import com.nuvio.app.features.plugins.PluginScraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.streams_plugin_repository_fallback
import org.jetbrains.compose.resources.getString

internal data class InstalledStreamAddonTarget(
    val addonName: String,
    val addonId: String,
    /** Manifest URL of the installed add-on, the key its fetch failures are remembered under. */
    val manifestUrl: String,
    val manifest: AddonManifest,
)

internal fun ManagedAddon.streamAddonInstanceId(manifestId: String): String =
    "addon:$manifestId:$manifestUrl"

internal data class PluginProviderGroup(
    val addonId: String,
    val addonName: String,
    val scrapers: List<PluginScraper>,
)

internal sealed interface StreamLoadCompletion {
    data class Addon(val group: AddonStreamGroup) : StreamLoadCompletion
    data class PluginScraper(
        val addonId: String,
        val streams: List<StreamItem>,
        val error: String?,
    ) : StreamLoadCompletion
}

internal fun List<PluginScraper>.toPluginProviderGroups(
    repositories: List<PluginRepositoryItem>,
    groupByRepository: Boolean,
): List<PluginProviderGroup> {
    if (!groupByRepository) {
        return map { scraper ->
            PluginProviderGroup(
                addonId = "plugin:${scraper.id}",
                addonName = scraper.name,
                scrapers = listOf(scraper),
            )
        }
    }

    val repoNameByUrl = repositories.associate { it.manifestUrl to it.name }
    return groupBy { it.repositoryUrl }
        .map { (repositoryUrl, scrapers) ->
            PluginProviderGroup(
                addonId = "plugin-repo:${repositoryUrl.lowercase()}",
                addonName = repoNameByUrl[repositoryUrl].orEmpty().ifBlank { repositoryUrl.fallbackRepositoryLabel() },
                scrapers = scrapers.sortedBy { it.name.lowercase() },
            )
        }
        .sortedBy { it.addonName.lowercase() }
}

internal fun List<AddonStreamGroup>.toEmptyStateReason(anyLoading: Boolean): StreamsEmptyStateReason? {
    if (anyLoading || any { it.streams.isNotEmpty() }) {
        return null
    }

    return if (isNotEmpty() && all { !it.error.isNullOrBlank() }) {
        StreamsEmptyStateReason.StreamFetchFailed
    } else {
        StreamsEmptyStateReason.NoStreamsFound
    }
}

internal suspend fun <T> runCatchingUnlessCancelled(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

internal fun PluginRuntimeResult.toStreamItem(
    scraper: PluginScraper,
    addonName: String = scraper.name,
    addonId: String = "plugin:${scraper.id}",
    includeScraperNameInSubtitle: Boolean = false,
): StreamItem {
    val subtitleParts = listOfNotNull(
        scraper.name.takeIf { includeScraperNameInSubtitle && it.isNotBlank() },
        quality?.takeIf { it.isNotBlank() },
        size?.takeIf { it.isNotBlank() },
        language?.takeIf { it.isNotBlank() },
    )
    val requestHeaders = headers
        .orEmpty()
        .mapNotNull { (key, value) ->
            val headerName = key.trim()
            val headerValue = value.trim()
            if (headerName.isBlank() || headerValue.isBlank() || headerName.equals("Range", ignoreCase = true)) {
                null
            } else {
                headerName to headerValue
            }
        }
        .toMap()

    return StreamItem(
        name = name ?: title,
        description = subtitleParts.joinToString(" • ").ifBlank { null },
        url = url,
        infoHash = infoHash,
        sourceName = scraper.name,
        addonName = addonName,
        addonId = addonId,
        streamType = normalizeStreamType(type),
        behaviorHints = if (requestHeaders.isEmpty()) {
            StreamBehaviorHints()
        } else {
            StreamBehaviorHints(
                notWebReady = true,
                proxyHeaders = StreamProxyHeaders(request = requestHeaders),
            )
        },
        externalSubtitles = subtitles?.map {
            StreamSubtitle(
                url = it.url,
                language = it.language,
                name = it.name,
                headers = it.headers
            )
        } ?: emptyList()
    )
}

internal fun List<StreamItem>.sortedForGroupedDisplay(): List<StreamItem> =
    sortedWith(
        compareByDescending<StreamItem> { stream -> stream.qualityRank() }
            .thenByDescending { stream -> stream.playbackReadinessRank() }
            .thenBy { it.sourceName.orEmpty().lowercase() }
            .thenBy { it.streamLabel.lowercase() }
            .thenBy { it.streamSubtitle.orEmpty().lowercase() },
    ).distinctBy { stream -> stream.dedupeKey() }

/**
 * The same file is often offered by two add-ons under different labels. Because this
 * runs after the quality sort, the copy that survives is the strongest one.
 */
private fun StreamItem.dedupeKey(): String {
    playableDirectUrl?.let { url -> return "url:$url" }
    p2pInfoHash?.let { hash -> return "hash:$hash:${p2pFileIdx ?: 0}" }
    externalOpenUrl?.let { url -> return "external:$url" }
    youTubeIdToResolve?.let { id -> return "youtube:$id" }
    return "label:$addonId:$streamLabel:${streamSubtitle.orEmpty()}"
}

/**
 * The quality a source advertises in the text it shows. Sources are not obliged to
 * list themselves best-first, and this is what "a strong source" means to the user:
 * 4K before 1080p, HDR above plain, a remux above a re-encode.
 */
private fun StreamItem.qualityRank(): Int {
    val text = listOf(streamLabel, streamSubtitle.orEmpty())
        .joinToString(separator = " ")
        .lowercase()
    val resolution = when {
        text.contains("2160") || text.contains("4k") || text.contains("uhd") -> 4
        text.contains("1080") -> 3
        text.contains("720") -> 2
        text.contains("576") || text.contains("480") || text.contains("360") -> 1
        else -> 0
    }
    val hdr = if (
        text.contains("hdr") || text.contains("dolby vision") || text.contains("dovi")
    ) {
        1
    } else {
        0
    }
    val remux = if (
        text.contains("remux") || text.contains("bluray") || text.contains("blu-ray")
    ) {
        1
    } else {
        0
    }
    return resolution * 100 + hdr * 10 + remux * 5
}

/** A source that plays right away outranks one that still needs a debrid or torrent resolve. */
private fun StreamItem.playbackReadinessRank(): Int = when {
    isCachedDebridTorrentStream -> 3
    playableDirectUrl != null || isDirectDebridStream -> 2
    isTorrentStream -> 1
    else -> 0
}

private fun String.fallbackRepositoryLabel(): String {
    val withoutQuery = substringBefore("?")
    val withoutManifest = withoutQuery.removeSuffix("/manifest.json")
    val host = withoutManifest.substringAfter("://", withoutManifest).substringBefore('/')
    return host.ifBlank {
        withoutManifest.substringAfterLast('/').ifBlank {
            runBlocking { getString(Res.string.streams_plugin_repository_fallback) }
        }
    }
}
