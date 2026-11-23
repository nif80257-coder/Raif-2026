package com.nuvio.app.features.player

import com.nuvio.app.features.addons.AddonHttpStatusException
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.addons.httpRequestRaw

/** A subtitle file is a few hundred kilobytes; this only stops a hostile or broken response. */
private const val MaxSubtitlePayloadBytes = 8 * 1024 * 1024

/**
 * Fetches a subtitle file and decodes it with the charset it was written in.
 *
 * [httpGetTextWithHeaders] decodes as UTF-8, which turns a Windows-1256 Arabic file into
 * replacement characters before the SRT parser ever sees it: the cue lines disappear and the file is
 * treated as empty. This reads the bytes instead and decodes them with [SubtitleTextDecoder].
 */
internal suspend fun fetchSubtitleText(
    url: String,
    headers: Map<String, String> = emptyMap(),
): String {
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        // Local files and other schemes go through the existing reader unchanged.
        return httpGetTextWithHeaders(url = url, headers = headers)
    }

    val response = httpRequestRaw(
        method = "GET",
        url = url,
        headers = headers,
        body = "",
        maxResponseBodyBytes = MaxSubtitlePayloadBytes,
    )
    if (response.status !in 200..299) {
        throw AddonHttpStatusException(
            statusCode = response.status,
            retryAfterHeader = null,
            message = "Subtitle request failed with HTTP ${response.status}",
        )
    }
    if (response.bodyBytes.isEmpty()) {
        throw IllegalStateException("Subtitle response was empty")
    }
    return SubtitleTextDecoder.decode(response.bodyBytes).text
}
