package com.nuvio.app.features.addons

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
}

data class RawHttpResponse(
    val status: Int,
    val statusText: String,
    val url: String,
    val body: String,
    val headers: Map<String, String>,
    /** The response body before charset decoding. */
    val bodyBytes: ByteArray = body.encodeToByteArray(),
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
