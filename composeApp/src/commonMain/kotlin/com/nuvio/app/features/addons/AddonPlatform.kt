package com.nuvio.app.features.addons

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable

internal expect object AddonStorage {
    fun loadInstalledAddonUrls(profileId: Int): List<String>
    fun saveInstalledAddonUrls(profileId: Int, urls: List<String>)
    fun loadAddonEnabledStates(profileId: Int): Map<String, Boolean>
    fun saveAddonEnabledStates(profileId: Int, states: Map<String, Boolean>)
    fun loadPendingSyncPayload(accountId: String, profileId: Int): String?
    fun savePendingSyncPayload(accountId: String, profileId: Int, payload: String)
    fun clearPendingSyncPayload(accountId: String, profileId: Int)

    /** True once the bundled default add-ons were installed for this profile. */
    fun areDefaultAddonsSeeded(profileId: Int): Boolean
    fun markDefaultAddonsSeeded(profileId: Int)

    /**
     * An opaque blob kept between launches so a cold start does not begin with nothing
     * on screen, scoped to the profile. The callers own the payload format.
     */
    fun loadStartupCache(profileId: Int, name: String): String?
    fun saveStartupCache(profileId: Int, name: String, payload: String)
}

internal data class LimitedAddonResponseBody(
    val bytes: ByteArray,
    val truncated: Boolean,
)

/** Reads at most [maxBytes] plus one probe byte, never buffering an unbounded response. */
internal suspend fun readLimitedAddonResponseBody(
    channel: ByteReadChannel,
    maxBytes: Int,
): LimitedAddonResponseBody {
    val limit = maxBytes.coerceAtLeast(0)
    val bytes = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val count = channel.readAvailable(bytes, total, limit - total)
        if (count == -1) return LimitedAddonResponseBody(bytes.copyOf(total), truncated = false)
        if (count == 0) continue
        total += count
    }

    val probe = ByteArray(1)
    var probeCount: Int
    do {
        probeCount = channel.readAvailable(probe, 0, probe.size)
    } while (probeCount == 0)

    return LimitedAddonResponseBody(bytes.copyOf(total), truncated = probeCount > 0)
}

data class RawHttpResponse(
    val status: Int,
    val statusText: String,
    val url: String,
    val body: String,
    val headers: Map<String, String>,
    /** The response body before charset decoding. */
    val bodyBytes: ByteArray = body.encodeToByteArray(),
    val bodyTruncated: Boolean = false,
)

/** Default safety limit for generic and plugin-provided HTTP responses. */
internal const val DefaultRawHttpResponseMaxBytes = 1024 * 1024

expect suspend fun httpGetText(url: String): String

expect suspend fun httpPostJson(url: String, body: String): String

expect suspend fun httpGetTextWithHeaders(
    url: String,
    headers: Map<String, String>,
): String

expect suspend fun httpPostJsonWithHeaders(
    url: String,
    body: String,
    headers: Map<String, String>,
): String

expect suspend fun httpRequestRaw(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: String,
    followRedirects: Boolean = true,
    maxResponseBodyBytes: Int = DefaultRawHttpResponseMaxBytes,
    /** When present, sends these bytes verbatim instead of UTF-8 encoding [body]. */
    bodyBytes: ByteArray? = null,
): RawHttpResponse
