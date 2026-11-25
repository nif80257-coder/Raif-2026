package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.features.plugins.PluginRepositoryItem
import com.nuvio.app.features.plugins.PluginRuntimeResult
import com.nuvio.app.features.plugins.PluginScraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.streams_plugin_repository_fallback
import com.nuvio.app.core.i18n.localizedUiText
import org.jetbrains.compose.resources.getString

/**
 * Upper bound on how many stream sources are being fetched at the same time.
 *
 * Sources live on different hosts, so the client's per-host connection limit is not what caps this.
 * Twelve keeps a phone's radio busy without turning an open of the source sheet into a burst of
 * thirty-three requests, and the first answers still appear while the rest are in flight.
 */
internal val streamSourceFetchSemaphore = Semaphore(12)

/**
 * How long one source may take to answer a stream request.
 *
 * The HTTP client waits 60 seconds and retries transient failures, which is right for a manifest
 * that may still arrive but wrong here: a scraper that needs more than fifteen seconds almost never
 * returns a playable link, and while it holds a fetch slot every source behind it waits. Capping it
 * is what keeps opening a title bounded instead of creeping towards minutes.
 */
internal const val STREAM_SOURCE_TIMEOUT_MS = 15_000L

/**
 * The shortest time a measured source is given, however fast it has answered so far.
 *
 * Half the budget: a host that answered in a fraction of a second every time may still be
 * having a slow minute, and six seconds is enough for it to show that it is alive.
 */
internal const val MIN_STREAM_SOURCE_TIMEOUT_MS = 6_000L

/**
 * How long a source that usually answers in [averageElapsedMs] may take this time.
 *
 * Six times its own measured average plus three seconds, never less than
 * [MIN_STREAM_SOURCE_TIMEOUT_MS] and never more than [STREAM_SOURCE_TIMEOUT_MS]: a source
 * measured at half a second gets six seconds, one measured at two seconds keeps the whole budget.
 * A source that has always answered in a fraction of a second does not suddenly need fifteen, and
 * holding its slot that long is exactly what makes the sources behind it wait. A source that does
 * need the full budget — or that has never been measured — still gets it.
 */
internal fun streamSourceTimeoutMs(averageElapsedMs: Long?): Long {
    val average = averageElapsedMs?.takeIf { it > 0L } ?: return STREAM_SOURCE_TIMEOUT_MS
    return (average * 6L + 3_000L).coerceIn(MIN_STREAM_SOURCE_TIMEOUT_MS, STREAM_SOURCE_TIMEOUT_MS)
}

/** Named so the failure memory records it as a timeout rather than a generic failure. */
internal class StreamSourceTimeoutException : IllegalStateException("Stream source timed out")

/**
 * Fetches one source's stream list inside [STREAM_SOURCE_TIMEOUT_MS]. A source that does not answer
 * in time fails with [StreamSourceTimeoutException], which both frees its slot and is remembered as
 * a timeout in the add-on failure memory.
 *
 * One attempt only: the timeout above is the whole budget for this source, so a retry does not add a
 * chance of an answer, it only keeps a fetch slot occupied while the sources behind it wait.
 */
/**
 * Fetches one source's stream list inside [timeoutMs]. A source that does not answer in time
 * fails with [StreamSourceTimeoutException], which both frees its slot and is remembered as
 * a timeout in the add-on failure memory.
 *
 * One attempt only: the timeout is the whole budget for this source, so a retry does not add a
 * chance of an answer, it only keeps a fetch slot occupied while the sources behind it wait.
 */
internal suspend fun fetchStreamSourcePayload(
    url: String,
    forceRefresh: Boolean = false,
    timeoutMs: Long = STREAM_SOURCE_TIMEOUT_MS,
): String =
    withTimeoutOrNull(timeoutMs) {
        fetchAddonResponseText(url = url, forceRefresh = forceRefresh, maxAttempts = 1)
    } ?: throw StreamSourceTimeoutException()

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

/** One stream with the sort keys it is ranked by, built once instead of inside the comparator. */
private data class RankedStream(
    val stream: StreamItem,
    val quality: Int,
    val readiness: Int,
    val sourceKey: String,
    val labelKey: String,
    val subtitleKey: String,
)

internal fun List<StreamItem>.sortedForGroupedDisplay(
    preferredQualityRank: Int? = null,
): List<StreamItem> {
    if (isEmpty()) return this
    // The comparator used to decode the quality out of each title on every comparison, which is
    // O(n log n) string scans over a list that can carry hundreds of streams. Rank each stream
    // once, then sort on plain integers.
    val ranked = map { stream ->
        RankedStream(
            stream = stream,
            quality = stream.qualityRank(),
            readiness = stream.playbackReadinessRank(),
            sourceKey = stream.sourceName.orEmpty().lowercase(),
            labelKey = stream.streamLabel.lowercase(),
            subtitleKey = stream.streamSubtitle.orEmpty().lowercase(),
        )
    }
    return ranked.sortedWith(
        compareBy<RankedStream> { item ->
            preferredQualityRank?.let { preferred -> abs(item.quality - preferred) }
                ?: -item.quality
        }
            .thenByDescending { item -> item.readiness }
            .thenBy { item -> item.sourceKey }
            .thenBy { item -> item.labelKey }
            .thenBy { item -> item.subtitleKey },
    ).map { item -> item.stream }
        .distinctBy { stream -> stream.dedupeKey() }
}

internal fun List<AddonStreamGroup>.deduplicatedAcrossSources(): List<AddonStreamGroup> {
    if (size < 2) return this
    val retained = Array(size) { mutableListOf<StreamItem>() }
    val candidates = flatMapIndexed { groupIndex, group ->
        group.streams.mapIndexed { streamIndex, stream ->
            Triple(groupIndex, streamIndex, stream)
        }
    }.sortedWith(
        compareByDescending<Triple<Int, Int, StreamItem>> { it.third.qualityRank() }
            .thenByDescending { it.third.playbackReadinessRank() }
            .thenBy { it.first }
            .thenBy { it.second },
    )
    val seen = mutableSetOf<String>()
    candidates.forEach { (groupIndex, _, stream) ->
        if (seen.add(stream.dedupeKey())) retained[groupIndex] += stream
    }
    return mapIndexed { index, group ->
        group.copy(streams = retained[index].sortedForGroupedDisplay())
    }
}

/**
 * The same file is often offered by two add-ons under different labels. Because this
 * runs after the quality sort, the copy that survives is the strongest one.
 */
private fun StreamItem.dedupeKey(): String {
    serverTarget?.let { target ->
        return "server:${target.item.encode()}:${target.mediaSourceId.orEmpty()}"
    }
    val requestHeaders = behaviorHints.proxyHeaders?.request.normalizedDedupeHeaders()
    val responseHeaders = behaviorHints.proxyHeaders?.response.normalizedDedupeHeaders()
    val resolveIdentity = clientResolve?.let { resolve ->
        listOf(
            resolve.service.orEmpty(),
            resolve.serviceIndex?.toString().orEmpty(),
            resolve.infoHash.orEmpty(),
            resolve.fileIdx?.toString().orEmpty(),
        ).joinToString("|")
    }.orEmpty()
    playableDirectUrl?.let { url ->
        return "url:$url|type:${streamType.orEmpty()}|request:$requestHeaders|response:$responseHeaders|resolve:$resolveIdentity"
    }
    p2pInfoHash?.let { hash ->
        return "hash:${hash.lowercase()}:${p2pFileIdx ?: -1}:${p2pTrackers.sorted().joinToString(",")}|resolve:$resolveIdentity"
    }
    externalOpenUrl?.let { url -> return "external:$url|request:$requestHeaders" }
    youTubeIdToResolve?.let { id -> return "youtube:$id" }
    return "label:$addonId:$streamLabel:${streamSubtitle.orEmpty()}"
}

private fun Map<String, String>?.normalizedDedupeHeaders(): String =
    orEmpty().entries
        .sortedBy { it.key.lowercase() }
        .joinToString("&") { (key, value) -> "${key.lowercase()}=$value" }

/**
 * The quality a source advertises in the text it shows. Sources are not obliged to
 * list themselves best-first, and this is what "a strong source" means to the user:
 * 4K before 1080p, HDR above plain, a remux above a re-encode.
 */
internal fun StreamItem.qualityRank(): Int {
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
    // How the file was made separates a remux from a web re-encode that carries the same
    // resolution: the resolution alone would call both "4K".
    val tier = ReleaseTier.of(text)
    return resolution * 100 + hdr * 10 + (tier?.rank ?: 0) * 2
}

/**
 * The audio tracks a stream's own text names, so a dubbed copy is never read as a repeat.
 *
 * A source often answers several times at the same quality because the copies differ in audio: one
 * carries the original English, another a Spanish or Arabic dub, another both. [qualityRank] cannot
 * see that difference — the resolution and the cut are the same — so without this a shorter list
 * keeps one of them and hides the dub the viewer was looking for.
 *
 * Read as whole words, not as substrings, so "spa" is a language only when it stands alone.
 */
internal fun StreamItem.audioTrackKey(): String {
    val text = buildList {
        add(streamLabel)
        streamSubtitle?.let { subtitle -> add(subtitle) }
        val parsed = clientResolve?.stream?.raw?.parsed
        addAll(parsed?.audio.orEmpty())
        addAll(parsed?.languages.orEmpty())
    }.joinToString(separator = " ").lowercase()
    val words = buildString(text.length) {
        text.forEach { character ->
            append(if (character.isLetterOrDigit()) character else ' ')
        }
    }.split(' ').filter { it.isNotBlank() }.toSet()
    return audioTrackMarkers
        .filter { marker -> marker in words }
        .sorted()
        .joinToString(separator = "+")
}

private val audioTrackMarkers = setOf(
    "dual",
    "multi",
    "dub",
    "dubbed",
    "dublado",
    "dublada",
    "vos",
    "english",
    "eng",
    "ingles",
    "inglés",
    "spanish",
    "spa",
    "español",
    "espanol",
    "latino",
    "castellano",
    "arabic",
    "ara",
    "عربي",
    "مدبلج",
    "مدبلجة",
    "دبلج",
    "french",
    "fre",
    "français",
    "truefrench",
    "german",
    "ger",
    "deutsch",
    "italian",
    "ita",
    "italiano",
    "portuguese",
    "português",
    "turkey",
    "turkish",
    "türkçe",
    "russian",
    "русский",
    "hindi",
    "tamil",
    "telugu",
    "korean",
    "japanese",
    "jpn",
)

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
            localizedUiText("Plugin repository") { getString(Res.string.streams_plugin_repository_fallback) }
        }
    }
}
