package com.nuvio.app.features.player

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.withTimeoutOrNull

/** A subtitle lookup should never wait on a host that ignores range requests. */
private const val VideoHashRequestTimeoutMs = 8_000L

/**
 * Reads the two 64 KiB blocks OpenSubtitles hashes a file by, over HTTP range requests.
 *
 * Two requests of 64 KiB against a host that supports ranges, nothing else: the player does not have
 * to download the file to learn its hash. A host that answers 200 to a range request (ignoring it) or
 * that refuses the range gives no hash at all, which is the previous behaviour rather than a wrong one.
 */
internal actual suspend fun computeVideoHash(
    url: String,
    headers: Map<String, String>,
): Pair<String, Long>? {
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        return null
    }

    val head = withTimeoutOrNull(VideoHashRequestTimeoutMs) {
        fetchHashBlock(url = url, headers = headers, startByte = 0L)
    } ?: return null
    val fileSize = head.totalSize ?: return null
    if (fileSize < OpenSubtitlesHashBlockBytes * 2L) return null

    val tail = withTimeoutOrNull(VideoHashRequestTimeoutMs) {
        fetchHashBlock(
            url = url,
            headers = headers,
            startByte = fileSize - OpenSubtitlesHashBlockBytes,
        )
    } ?: return null

    val hash = opensubtitlesVideoHash(fileSize = fileSize, head = head.bytes, tail = tail.bytes) ?: return null
    return hash to fileSize
}

private class HashBlock(val bytes: ByteArray, val totalSize: Long?)

private suspend fun fetchHashBlock(
    url: String,
    headers: Map<String, String>,
    startByte: Long,
): HashBlock? {
    val endByte = startByte + OpenSubtitlesHashBlockBytes - 1
    val response = httpRequestRaw(
        method = "GET",
        url = url,
        headers = headers + mapOf(
            "Range" to "bytes=$startByte-$endByte",
            "Accept-Encoding" to "identity",
        ),
        body = "",
        maxResponseBodyBytes = OpenSubtitlesHashBlockBytes,
    )
    // 206 is the only answer that proves the requested bytes came back; a 200 means the host sent
    // the file from the start and the tail block would be the opening of the file again.
    if (response.status != 206) return null
    if (response.bodyBytes.size != OpenSubtitlesHashBlockBytes) return null
    return HashBlock(
        bytes = response.bodyBytes,
        totalSize = parseContentRangeTotal(response.headers["content-range"]),
    )
}
