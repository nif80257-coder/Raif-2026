package com.nuvio.app.features.player

internal enum class SubtitleTextEncoding {
    Utf8,
    Utf16LittleEndian,
    Utf16BigEndian,

    /** Arabic subtitle files written by Windows tools: code page 1256. */
    Windows1256,
}

internal data class DecodedSubtitleText(
    val text: String,
    val encoding: SubtitleTextEncoding,
)

/**
 * Decodes a subtitle file that may not be UTF-8.
 *
 * Most Arabic subtitle files online are still written in Windows-1256, and a few are UTF-16 with a
 * byte order mark. Decoding either as UTF-8 yields replacement characters, which then look like a
 * broken subtitle rather than a wrong charset: every cue is written, nothing can be searched, and
 * auto-sync finds no lines at all. So the bytes are read strictly here, and only a file that is not
 * valid UTF-8 falls back to code page 1256.
 */
internal object SubtitleTextDecoder {
    fun decode(bytes: ByteArray): DecodedSubtitleText {
        if (bytes.isEmpty()) return DecodedSubtitleText(text = "", encoding = SubtitleTextEncoding.Utf8)

        if (bytes.size >= 2) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 0xFF && second == 0xFE) {
                return DecodedSubtitleText(
                    text = decodeUtf16(bytes = bytes, startIndex = 2, littleEndian = true),
                    encoding = SubtitleTextEncoding.Utf16LittleEndian,
                )
            }
            if (first == 0xFE && second == 0xFF) {
                return DecodedSubtitleText(
                    text = decodeUtf16(bytes = bytes, startIndex = 2, littleEndian = false),
                    encoding = SubtitleTextEncoding.Utf16BigEndian,
                )
            }
        }

        val bomLength = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }

        val strictUtf8 = runCatching {
            bytes.decodeToString(
                startIndex = bomLength,
                endIndex = bytes.size,
                throwOnInvalidSequence = true,
            )
        }.getOrNull()
        if (strictUtf8 != null && !strictUtf8.contains('\uFFFD')) {
            return DecodedSubtitleText(text = strictUtf8, encoding = SubtitleTextEncoding.Utf8)
        }

        return DecodedSubtitleText(text = decodeWindows1256(bytes), encoding = SubtitleTextEncoding.Windows1256)
    }

    /** Code points 0x80..0xFF of Windows-1256, in code page order (CP1256.TXT). */
    private val windows1256High: CharArray = intArrayOf(
        0x20AC, 0x067E, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021,
        0x02C6, 0x2030, 0x0679, 0x2039, 0x0152, 0x0686, 0x0698, 0x0688,
        0x06AF, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014,
        0x06A9, 0x2122, 0x0691, 0x203A, 0x0153, 0x200C, 0x200D, 0x06BA,
        0x00A0, 0x060C, 0x00A2, 0x00A3, 0x00A4, 0x00A5, 0x00A6, 0x00A7,
        0x00A8, 0x00A9, 0x06BE, 0x00AB, 0x00AC, 0x00AD, 0x00AE, 0x00AF,
        0x00B0, 0x00B1, 0x00B2, 0x00B3, 0x00B4, 0x00B5, 0x00B6, 0x00B7,
        0x00B8, 0x00B9, 0x061B, 0x00BB, 0x00BC, 0x00BD, 0x00BE, 0x061F,
        0x06C1, 0x0621, 0x0622, 0x0623, 0x0624, 0x0625, 0x0626, 0x0627,
        0x0628, 0x0629, 0x062A, 0x062B, 0x062C, 0x062D, 0x062E, 0x062F,
        0x0630, 0x0631, 0x0632, 0x0633, 0x0634, 0x0635, 0x0636, 0x00D7,
        0x0637, 0x0638, 0x0639, 0x063A, 0x0640, 0x0641, 0x0642, 0x0643,
        0x00E0, 0x0644, 0x00E2, 0x0645, 0x0646, 0x0647, 0x0648, 0x00E7,
        0x00E8, 0x00E9, 0x00EA, 0x00EB, 0x0649, 0x064A, 0x00EE, 0x00EF,
        0x064B, 0x064C, 0x064D, 0x064E, 0x00F4, 0x064F, 0x0650, 0x00F7,
        0x0651, 0x00F9, 0x0652, 0x00FB, 0x00FC, 0x200E, 0x200F, 0x06D2,
    ).map { codePoint -> codePoint.toChar() }.toCharArray()

    private fun decodeWindows1256(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            builder.append(if (value < 0x80) value.toChar() else windows1256High[value - 0x80])
        }
        return builder.toString()
    }

    private fun decodeUtf16(bytes: ByteArray, startIndex: Int, littleEndian: Boolean): String {
        val builder = StringBuilder(bytes.size / 2)
        var index = startIndex
        while (index + 1 < bytes.size) {
            val unit = utf16UnitAt(bytes, index, littleEndian)
            index += 2
            if (unit in 0xD800..0xDBFF && index + 1 < bytes.size) {
                val low = utf16UnitAt(bytes, index, littleEndian)
                if (low in 0xDC00..0xDFFF) {
                    index += 2
                    builder.append(unit.toChar())
                    builder.append(low.toChar())
                    continue
                }
            }
            builder.append(unit.toChar())
        }
        return builder.toString()
    }

    private fun utf16UnitAt(bytes: ByteArray, index: Int, littleEndian: Boolean): Int {
        val first = bytes[index].toInt() and 0xFF
        val second = bytes[index + 1].toInt() and 0xFF
        return if (littleEndian) (second shl 8) or first else (first shl 8) or second
    }
}
