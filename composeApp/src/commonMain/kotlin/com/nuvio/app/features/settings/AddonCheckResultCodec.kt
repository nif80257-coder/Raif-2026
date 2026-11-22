package com.nuvio.app.features.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The last check of every source, written down so the screen still answers after a restart.
 *
 * The check itself costs a manifest request plus a known-title probe per source, which is far too
 * much to repeat every time the page opens. Keeping the outcome means the user sees what the last
 * run found — and when it found it — instead of a screen that says "not tested" about every source
 * the moment the app is relaunched.
 *
 * The stored shape is a DTO rather than the model itself: renaming a field of [AddonCheckResult]
 * must not silently lose the answers already on the device, and an unknown field from a newer build
 * is ignored instead of failing the whole record.
 */
@Serializable
private data class StoredAddonCheckResult(
    val addonId: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val status: String = "OK",
    val responseTimeMs: Long? = null,
    val manifestParsed: Boolean = false,
    val manifestError: String? = null,
    val manifestVersion: String? = null,
    val supportedTypes: List<String> = emptyList(),
    val catalogsCount: Int = 0,
    val videoId: String = "",
    val streamCount: Int? = null,
    val subtitleCount: Int? = null,
    val streamsTimedOut: Boolean = false,
    val subtitlesTimedOut: Boolean = false,
    val streamsError: String? = null,
    val subtitlesError: String? = null,
    val changedSinceLastCheck: Boolean? = null,
    val checkedAtEpochMs: Long = 0L,
)

private val checkResultJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** One result as the text stored for its source. */
internal fun encodeAddonCheckResult(result: AddonCheckResult): String {
    val stored = StoredAddonCheckResult(
        addonId = result.addonId,
        name = result.name,
        enabled = result.enabled,
        status = result.status.name,
        responseTimeMs = result.responseTimeMs,
        manifestParsed = result.manifestParsed,
        manifestError = result.manifestError,
        manifestVersion = result.manifestVersion,
        supportedTypes = result.supportedTypes.map { type -> type.name },
        catalogsCount = result.catalogsCount,
        videoId = result.knownTest.videoId,
        streamCount = result.knownTest.streamCount,
        subtitleCount = result.knownTest.subtitleCount,
        streamsTimedOut = result.knownTest.streamsTimedOut,
        subtitlesTimedOut = result.knownTest.subtitlesTimedOut,
        streamsError = result.knownTest.streamsError,
        subtitlesError = result.knownTest.subtitlesError,
        changedSinceLastCheck = result.changedSinceLastCheck,
        checkedAtEpochMs = result.checkedAtEpochMs,
    )
    return checkResultJson.encodeToString(StoredAddonCheckResult.serializer(), stored)
}

/**
 * One stored result, or null when the payload is unreadable.
 *
 * A record written by an older build that no longer parses is dropped rather than shown with made
 * up values: the screen then says "not tested" for that source and the next check fills it in.
 */
internal fun decodeAddonCheckResult(manifestUrl: String, payload: String): AddonCheckResult? {
    if (manifestUrl.isBlank() || payload.isBlank()) return null
    val stored = runCatching {
        checkResultJson.decodeFromString(StoredAddonCheckResult.serializer(), payload)
    }.getOrNull() ?: return null
    return AddonCheckResult(
        manifestUrl = manifestUrl,
        addonId = stored.addonId,
        name = stored.name,
        enabled = stored.enabled,
        status = AddonHealthStatus.entries.firstOrNull { it.name == stored.status }
            ?: AddonHealthStatus.OK,
        responseTimeMs = stored.responseTimeMs,
        manifestParsed = stored.manifestParsed,
        manifestError = stored.manifestError,
        manifestVersion = stored.manifestVersion,
        supportedTypes = stored.supportedTypes.mapNotNullTo(mutableSetOf()) { type ->
            AddonSupportedType.entries.firstOrNull { it.name == type }
        },
        catalogsCount = stored.catalogsCount,
        knownTest = AddonKnownContentTest(
            videoId = stored.videoId.ifBlank { AddonHealthCheckKnownVideoId },
            streamCount = stored.streamCount,
            subtitleCount = stored.subtitleCount,
            streamsTimedOut = stored.streamsTimedOut,
            subtitlesTimedOut = stored.subtitlesTimedOut,
            streamsError = stored.streamsError,
            subtitlesError = stored.subtitlesError,
        ),
        changedSinceLastCheck = stored.changedSinceLastCheck,
        checkedAtEpochMs = stored.checkedAtEpochMs,
    )
}

/** The whole map as storage keeps it: one text per manifest URL. */
internal fun encodeAddonCheckResults(results: Map<String, AddonCheckResult>): Map<String, String> =
    results.mapValues { (_, result) -> encodeAddonCheckResult(result) }

/** The stored map back, skipping entries that no longer decode. */
internal fun decodeAddonCheckResults(stored: Map<String, String>): Map<String, AddonCheckResult> =
    stored.mapNotNull { (manifestUrl, payload) ->
        decodeAddonCheckResult(manifestUrl, payload)?.let { result -> manifestUrl to result }
    }.toMap()
