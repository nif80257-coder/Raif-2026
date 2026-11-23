package com.nuvio.app.features.player

/** OpenSubtitles hashes the first and the last 64 KiB of the file; nothing else is read. */
internal const val OpenSubtitlesHashBlockBytes = 65_536

/**
 * The hash OpenSubtitles matches a subtitle file to a video file by.
 *
 * It is the file size plus every little-endian 64-bit word of the first and last [block][OpenSubtitlesHashBlockBytes]
 * bytes, wrapped to 64 bits and written as sixteen hex digits. The app had no implementation of it on
 * iOS, so every OpenSubtitles request went out without a hash: the provider could only match by title
 * and year, which is exactly the case where it returns a dozen tracks for the wrong release and none
 * for the right one. Arabic tracks from it were therefore mostly unusable even though the provider is
 * the largest of the five bundled ones.
 *
 * Returns null when the two blocks are not exactly the expected size, because a short read would
 * produce a hash that matches nothing instead of no hash at all.
 */
internal fun opensubtitlesVideoHash(fileSize: Long, head: ByteArray, tail: ByteArray): String? {
    if (fileSize < OpenSubtitlesHashBlockBytes * 2L) return null
    if (head.size != OpenSubtitlesHashBlockBytes || tail.size != OpenSubtitlesHashBlockBytes) return null
    var hash = fileSize
    hash = addLittleEndianWords(hash, head)
    hash = addLittleEndianWords(hash, tail)
    return hash.toULong().toString(16).padStart(16, '0')
}

private fun addLittleEndianWords(seed: Long, bytes: ByteArray): Long {
    var hash = seed
    var index = 0
    while (index + 8 <= bytes.size) {
        var word = 0L
        for (offset in 0 until 8) {
            word = word or ((bytes[index + offset].toLong() and 0xFF) shl (8 * offset))
        }
        hash += word
        index += 8
    }
    return hash
}


/** Reads the total file size out of a `Content-Range` header such as `bytes 0-65535/1234567`. */
internal fun parseContentRangeTotal(header: String?): Long? {
    val raw = header?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val total = raw.substringAfter('/', missingDelimiterValue = "").trim()
    if (total.isEmpty() || total == "*") return null
    return total.toLongOrNull()?.takeIf { it > 0L }
}
