package com.nuvio.app.features.streams

import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.servers.ServerPlaybackTarget
import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable
data class StreamSubtitle(
    val url: String,
    val language: String,
    val name: String? = null,
    val headers: Map<String, String>? = null
)

data class StreamItem(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val externalUrl: String? = null,
    val ytId: String? = null,
    val sources: List<String> = emptyList(),
    val sourceName: String? = null,
    val addonName: String,
    val addonId: String,
    val addonLogo: String? = null,
    val streamType: String? = null,
    val behaviorHints: StreamBehaviorHints = StreamBehaviorHints(),
    val clientResolve: StreamClientResolve? = null,
    val debridCacheStatus: StreamDebridCacheStatus? = null,
    val externalSubtitles: List<StreamSubtitle> = emptyList(),
    val badges: List<StreamBadge> = emptyList(),
    val serverTarget: ServerPlaybackTarget? = null,
) {
    val streamLabel: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Stream"

    val streamSubtitle: String?
        get() = description

    val directPlaybackUrl: String?
        get() = url?.trim()?.takeIf { value ->
            value.isNotEmpty() && (value.isMagnetLink() || value.isTorrentSchemeUrl() || value.isSafeStreamHttpUrl())
        }

    /**
     * First URL that can be handed directly to a player or HTTP consumer.
     * `magnet:` and `torrent://` URLs are filtered out. `externalUrl` is not
     * a media URL in the Stremio SDK contract and must be opened externally.
     */
    val playableDirectUrl: String?
        get() = directPlaybackUrl?.takeIf { !it.isMagnetLink() && !it.isTorrentSchemeUrl() }

    /**
     * Where an availability source points: an official service page for the title.
     *
     * Both `https` and plain `http` web pages are accepted, and the destination's host is shown
     * before the app hands the user over. A non-web scheme (a magnet, a `torrent:` link or an app
     * deep link) is refused: the protocol only defines this field as an external webpage, and the
     * app must not open a scheme it has not verified.
     */
    val externalOpenUrl: String?
        get() = externalUrl?.takeIf { it.isSafeStreamHttpUrl() }

    /** The destination's host, shown before the app hands the user to another service. */
    val externalOpenHost: String?
        get() = externalOpenUrl
            ?.substringAfter("://")
            ?.substringBefore('/')
            ?.substringBefore('?')
            ?.removePrefix("www.")
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }

    val shouldOpenExternally: Boolean
        get() = url.isNullOrBlank() &&
            infoHash.isNullOrBlank() &&
            clientResolve == null &&
            externalOpenUrl != null

    /**
     * YouTube video id to resolve on the device, set only when `ytId` is the
     * stream's only way to play. Streams that also carry a url, externalUrl,
     * torrent or debrid resolve keep using those.
     */
    val youTubeIdToResolve: String?
        get() = ytId
            ?.trim()
            ?.takeIf {
                it.isNotEmpty() &&
                    url.isNullOrBlank() &&
                    externalUrl.isNullOrBlank() &&
                    infoHash.isNullOrBlank() &&
                    clientResolve == null
            }

    val torrentMagnetUri: String?
        get() = listOfNotNull(url, externalUrl)
            .firstOrNull { it.isMagnetLink() }

    val torrentSchemeUri: String?
        get() = listOfNotNull(url, externalUrl)
            .firstOrNull { it.isTorrentSchemeUrl() }

    val isDirectDebridStream: Boolean
        get() = clientResolve?.isDirectDebridCandidate == true

    val isInstalledAddonStream: Boolean
        get() = addonId.startsWith("addon:")

    val isTorrentStream: Boolean
        get() = !isDirectDebridStream && (
            !infoHash.isNullOrBlank() ||
            url.isMagnetLink() ||
            externalUrl.isMagnetLink() ||
            url.isTorrentSchemeUrl() ||
            externalUrl.isTorrentSchemeUrl()
        )

    val isCachedDebridTorrentStream: Boolean
        get() = isTorrentStream && debridCacheStatus?.state == StreamDebridCacheState.CACHED

    val needsLocalDebridResolve: Boolean
        get() = isTorrentStream && playableDirectUrl == null

    val p2pInfoHash: String?
        get() = infoHash.normalizedInfoHash()
            ?: clientResolve?.infoHash.normalizedInfoHash()
            ?: torrentMagnetUri.extractBtihInfoHash()
            ?: torrentSchemeUri.extractTorrentSchemeInfoHash()

    val p2pFileIdx: Int?
        get() = fileIdx ?: torrentSchemeUri.extractTorrentSchemeFileIdx()

    val p2pTrackers: List<String>
        get() = sources
            .asSequence()
            .filter { it.startsWith("tracker:") }
            .map { it.removePrefix("tracker:").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    val isAddonDebridCandidate: Boolean
        get() = isInstalledAddonStream && (needsLocalDebridResolve || isDirectDebridStream)

    val hasPlayableSource: Boolean
        get() = url != null || infoHash != null || externalUrl != null || clientResolve != null || ytId != null ||
            serverTarget != null

    val needsServerPreparation: Boolean
        get() = serverTarget != null && playableDirectUrl == null
}

@Serializable
data class StreamBadge(
    val name: String,
    val imageURL: String = "",
    val tagColor: String = "",
    val tagStyle: String = "",
    val textColor: String = "",
    val borderColor: String = "",
)

fun normalizeStreamType(raw: String?): String? =
    raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

private fun String?.isMagnetLink(): Boolean =
    this?.trimStart()?.startsWith("magnet:", ignoreCase = true) == true

private fun String?.isTorrentSchemeUrl(): Boolean =
    this?.trimStart()?.startsWith("torrent://", ignoreCase = true) == true

/**
 * An `externalUrl` is handed to the system, which may open any app the page is linked to, so only
 * a web page is accepted. Both schemes the protocol allows (`https` and plain `http`) count, and
 * anything else is dropped rather than opened on trust.
 */
internal fun String?.isWebUrl(): Boolean = isSafeStreamHttpUrl()

internal fun String?.isSafeStreamHttpUrl(): Boolean {
    val candidate = this?.trim() ?: return false
    if (candidate.isEmpty() || candidate.length > 8_192) return false
    if (candidate.any { it.isWhitespace() || it.isISOControl() } || '\\' in candidate) return false
    val parsed = runCatching { Url(candidate) }.getOrNull() ?: return false
    if (parsed.protocol.name.lowercase() !in setOf("http", "https")) return false
    val authority = candidate.substringAfter("://", "").substringBeforeAny('/', '?', '#')
    if (authority.isBlank() || '@' in authority || parsed.host.isBlank()) return false
    return hasValidStreamAuthorityPort(authority)
}

internal fun String?.isValidStreamSourceUrl(): Boolean {
    val value = this?.trim() ?: return false
    return value.isMagnetLink() || value.isTorrentSchemeUrl() || value.isSafeStreamHttpUrl()
}

private fun hasValidStreamAuthorityPort(authority: String): Boolean {
    val portText = when {
        authority.startsWith("[") -> {
            val closingBracket = authority.indexOf(']')
            if (closingBracket <= 1) return false
            val remainder = authority.substring(closingBracket + 1)
            if (remainder.isEmpty()) return true
            if (!remainder.startsWith(':')) return false
            remainder.substring(1)
        }
        authority.count { it == ':' } == 0 -> return true
        authority.count { it == ':' } == 1 -> authority.substringAfterLast(':')
        else -> return false
    }
    if (portText.isEmpty() || !portText.all(Char::isDigit)) return false
    return portText.toIntOrNull()?.let { it in 1..65_535 } == true
}

private fun String.substringBeforeAny(vararg delimiters: Char): String {
    val end = indexOfAny(delimiters).takeIf { it >= 0 } ?: length
    return substring(0, end)
}

private fun String?.extractTorrentSchemeInfoHash(): String? {
    val raw = this?.trimStart()?.takeIf { it.isTorrentSchemeUrl() } ?: return null
    return raw.removeRange(0, "torrent://".length)
        .substringBefore('/')
        .substringBefore('?')
        .trim()
        .takeIf { it.isValidInfoHash() }
}

private fun String?.extractTorrentSchemeFileIdx(): Int? {
    val raw = this?.trimStart()?.takeIf { it.isTorrentSchemeUrl() } ?: return null
    val path = raw.removeRange(0, "torrent://".length).substringBefore('?')
    if ('/' !in path) return null
    return path.substringAfter('/')
        .trim()
        .takeIf { segment -> segment.isNotEmpty() && segment.all { it.isDigit() } }
        ?.toIntOrNull()
}

private fun String.isValidInfoHash(): Boolean =
    (length == 40 && all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) ||
        (length == 32 && all { it in '2'..'7' || it.lowercaseChar() in 'a'..'z' })

private fun String?.normalizedInfoHash(): String? =
    this
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun String?.extractBtihInfoHash(): String? {
    val raw = this?.trim()?.takeIf { it.startsWith("magnet:", ignoreCase = true) } ?: return null
    val marker = "btih:"
    val markerIndex = raw.indexOf(marker, ignoreCase = true)
    if (markerIndex < 0) return null
    val start = markerIndex + marker.length
    val end = raw.indexOf('&', start).takeIf { it >= 0 } ?: raw.length
    return raw.substring(start, end)
        .trim()
        .takeIf { it.isNotEmpty() }
}

fun StreamItem.isSelectableForPlayback(debridEnabled: Boolean): Boolean =
    playableDirectUrl != null ||
        serverTarget != null ||
        shouldOpenExternally ||
        youTubeIdToResolve != null ||
        (AppFeaturePolicy.p2pEnabled && needsLocalDebridResolve && p2pInfoHash != null) ||
        (debridEnabled && isAddonDebridCandidate)

data class StreamBehaviorHints(
    val bingeGroup: String? = null,
    val notWebReady: Boolean = false,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null,
    val proxyHeaders: StreamProxyHeaders? = null,
)

data class StreamProxyHeaders(
    val request: Map<String, String>? = null,
    val response: Map<String, String>? = null,
)

enum class StreamDebridCacheState {
    CHECKING,
    CACHED,
    NOT_CACHED,
    UNKNOWN,
}

data class StreamDebridCacheStatus(
    val providerId: String,
    val providerName: String,
    val state: StreamDebridCacheState,
    val cachedName: String? = null,
    val cachedSize: Long? = null,
)

data class StreamClientResolve(
    val type: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val magnetUri: String? = null,
    val sources: List<String> = emptyList(),
    val torrentName: String? = null,
    val filename: String? = null,
    val mediaType: String? = null,
    val mediaId: String? = null,
    val mediaOnlyId: String? = null,
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val service: String? = null,
    val serviceIndex: Int? = null,
    val serviceExtension: String? = null,
    val isCached: Boolean? = null,
    val stream: StreamClientResolveStream? = null,
) {
    val isDirectDebridCandidate: Boolean
        get() = type.equals("debrid", ignoreCase = true) &&
            !service.isNullOrBlank() &&
            isCached == true
}

data class StreamClientResolveStream(
    val raw: StreamClientResolveRaw? = null,
)

data class StreamClientResolveRaw(
    val torrentName: String? = null,
    val filename: String? = null,
    val size: Long? = null,
    val folderSize: Long? = null,
    val tracker: String? = null,
    val indexer: String? = null,
    val network: String? = null,
    val parsed: StreamClientResolveParsed? = null,
)

data class StreamClientResolveParsed(
    val rawTitle: String? = null,
    val parsedTitle: String? = null,
    val year: Int? = null,
    val resolution: String? = null,
    val seasons: List<Int> = emptyList(),
    val episodes: List<Int> = emptyList(),
    val quality: String? = null,
    val hdr: List<String> = emptyList(),
    val codec: String? = null,
    val audio: List<String> = emptyList(),
    val channels: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val group: String? = null,
    val network: String? = null,
    val edition: String? = null,
    val duration: Long? = null,
    val bitDepth: String? = null,
    val extended: Boolean? = null,
    val theatrical: Boolean? = null,
    val remastered: Boolean? = null,
    val unrated: Boolean? = null,
)

data class AddonStreamGroup(
    val addonName: String,
    val addonId: String,
    val streams: List<StreamItem>,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** How long this source took to answer, when this load measured it. */
    val speedMs: Long? = null,
)

/** A source the app asked that answered successfully without a single playable link. */
data class StreamEmptyAnswer(
    val addonId: String,
    val addonName: String,
)

/**
 * The sources that answered the request with nothing to play.
 *
 * A group that is still loading has not answered yet, and one that carries an error already says
 * why it failed. What remains is the case the user cannot see: a source that replied "there is
 * nothing here" and left the sheet shorter with no explanation at all, which reads as the app
 * having a bug or the title being missing rather than those sources having no links.
 */
internal fun List<AddonStreamGroup>.emptyAnswerSources(): List<StreamEmptyAnswer> =
    mapNotNull { group ->
        if (group.isLoading || !group.error.isNullOrBlank() || group.streams.isNotEmpty()) {
            null
        } else {
            StreamEmptyAnswer(addonId = group.addonId, addonName = group.addonName)
        }
    }

enum class StreamsEmptyStateReason {
    NoAddonsInstalled,
    NoCompatibleAddons,
    NoStreamsFound,
    StreamFetchFailed,
}

data class StreamsUiState(
    val requestToken: String? = null,
    val autoPlayDecided: Boolean = false,
    val groups: List<AddonStreamGroup> = emptyList(),
    val activeAddonIds: Set<String> = emptySet(),
    val selectedFilter: String? = null,
    val isAnyLoading: Boolean = false,
    val emptyStateReason: StreamsEmptyStateReason? = null,
    val autoPlayStream: StreamItem? = null,
    val autoPlayCandidates: List<StreamItem> = emptyList(),
    val isDirectAutoPlayFlow: Boolean = false,
    val showDirectAutoPlayOverlay: Boolean = false,
    val overlayMessage: String? = null,
) {
    val filteredGroups: List<AddonStreamGroup>
        get() = if (selectedFilter == null) groups
                else groups.filter { it.addonId == selectedFilter }

    /**
     * The groups as the list draws them: the user's own filter, then one link for every quality a
     * source offers. Every quality stays, because which of them plays depends on the connection and
     * the screen; only a source answering twice at the same quality is cut to its better row.
     * Autoplay and the warm-up read [groups] instead — they need every candidate.
     */
    val visibleGroups: List<AddonStreamGroup>
        get() = if (StreamDisplayPreferences.oneLinkPerQualityPerSource(ProfileRepository.activeProfileId)) {
            filteredGroups.showingOneLinkPerQualityPerSource()
        } else {
            filteredGroups
        }

    val allStreams: List<StreamItem>
        get() = filteredGroups.flatMap { it.streams }

    val hasAnyStreams: Boolean
        get() = groups.any { it.streams.isNotEmpty() }

    /** The sources that answered this request with nothing to play, in the list's own order. */
    val emptyAnswerSources: List<StreamEmptyAnswer>
        get() = groups.emptyAnswerSources()
}
