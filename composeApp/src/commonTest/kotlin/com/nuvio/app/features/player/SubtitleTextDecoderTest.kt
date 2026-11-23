package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtitleTextDecoderTest {

    @Test
    fun utf8ArabicPassesThroughUntouched() {
        val text = "1\n00:00:01,000 --> 00:00:03,000\nمرحبا بالعالم\n"

        val decoded = SubtitleTextDecoder.decode(text.encodeToByteArray())

        assertEquals(text, decoded.text)
        assertEquals(SubtitleTextEncoding.Utf8, decoded.encoding)
    }

    @Test
    fun windows1256ArabicDecodesToReadableText() {
        // "مرحبا" written by a Windows tool: م ر ح ب ا
        val bytes = byteArrayOf(
            0xE3.toByte(),
            0xD1.toByte(),
            0xCD.toByte(),
            0xC8.toByte(),
            0xC7.toByte(),
        )

        val decoded = SubtitleTextDecoder.decode(bytes)

        assertEquals("مرحبا", decoded.text)
        assertEquals(SubtitleTextEncoding.Windows1256, decoded.encoding)
    }

    @Test
    fun everyWindows1256HighByteDecodesToOneCharacter() {
        val bytes = ByteArray(128) { index -> (0x80 + index).toByte() }

        val decoded = SubtitleTextDecoder.decode(bytes)

        assertEquals(128, decoded.text.length)
        assertEquals('،', decoded.text[0xA1 - 0x80])
        assertEquals('\u0645', decoded.text[0xE3 - 0x80])
        assertEquals('\u06D2', decoded.text[0xFF - 0x80])
    }

    @Test
    fun utf8ByteOrderMarkIsRemoved() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "WEBVTT".encodeToByteArray()

        val decoded = SubtitleTextDecoder.decode(bytes)

        assertEquals("WEBVTT", decoded.text)
        assertEquals(SubtitleTextEncoding.Utf8, decoded.encoding)
    }

    @Test
    fun utf16LittleEndianWithBomDecodes() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x45, 0x06)

        val decoded = SubtitleTextDecoder.decode(bytes)

        assertEquals("م", decoded.text)
        assertEquals(SubtitleTextEncoding.Utf16LittleEndian, decoded.encoding)
    }

    @Test
    fun utf16BigEndianWithBomDecodes() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x06, 0x45)

        val decoded = SubtitleTextDecoder.decode(bytes)

        assertEquals("م", decoded.text)
        assertEquals(SubtitleTextEncoding.Utf16BigEndian, decoded.encoding)
    }

    @Test
    fun windows1256FileKeepsItsSrtStructure() {
        val srt = "1\n00:00:01,000 --> 00:00:03,000\nم\n"
        val bytes = ByteArray(srt.length) { index ->
            when (val char = srt[index]) {
                'م' -> 0xE3.toByte()
                else -> char.code.toByte()
            }
        }

        val cues = PlayerSubtitleCueParser.parse(SubtitleTextDecoder.decode(bytes).text, "sub.srt")

        assertEquals(1, cues.size)
        assertEquals("م", cues.single().text)
    }

    @Test
    fun emptyBytesDecodeToEmptyText() {
        val decoded = SubtitleTextDecoder.decode(ByteArray(0))

        assertTrue(decoded.text.isEmpty())
        assertEquals(SubtitleTextEncoding.Utf8, decoded.encoding)
    }

    @Test
    fun plainAsciiIsNotTreatedAsArabic() {
        val decoded = SubtitleTextDecoder.decode("Hello, world".encodeToByteArray())

        assertEquals("Hello, world", decoded.text)
        assertEquals(SubtitleTextEncoding.Utf8, decoded.encoding)
    }
}
