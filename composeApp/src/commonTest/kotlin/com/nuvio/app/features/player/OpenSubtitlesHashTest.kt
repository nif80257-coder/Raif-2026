package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenSubtitlesHashTest {

    private fun block(vararg words: Long): ByteArray {
        val bytes = ByteArray(OpenSubtitlesHashBlockBytes)
        words.forEachIndexed { wordIndex, word ->
            for (offset in 0 until 8) {
                bytes[wordIndex * 8 + offset] = ((word shr (8 * offset)) and 0xFF).toByte()
            }
        }
        return bytes
    }

    @Test
    fun aZeroFilledFileHashesToItsOwnSize() {
        val hash = opensubtitlesVideoHash(
            fileSize = 131_072L,
            head = block(),
            tail = block(),
        )

        assertEquals("0000000000020000", hash)
    }

    @Test
    fun littleEndianWordsAreAddedToTheSize() {
        val hash = opensubtitlesVideoHash(
            fileSize = 131_072L,
            head = block(1L),
            tail = block(),
        )

        assertEquals("0000000000020001", hash)
    }

    @Test
    fun bothBlocksContribute() {
        val hash = opensubtitlesVideoHash(
            fileSize = 131_072L,
            head = block(1L),
            tail = block(2L),
        )

        assertEquals("0000000000020003", hash)
    }

    @Test
    fun aFileSmallerThanTwoBlocksHasNoHash() {
        assertNull(opensubtitlesVideoHash(fileSize = 65_536L, head = block(), tail = block()))
    }

    @Test
    fun aShortReadProducesNoHash() {
        assertNull(
            opensubtitlesVideoHash(
                fileSize = 1_000_000L,
                head = ByteArray(100),
                tail = block(),
            ),
        )
        assertNull(
            opensubtitlesVideoHash(
                fileSize = 1_000_000L,
                head = block(),
                tail = block().copyOf(1_024),
            ),
        )
    }

    @Test
    fun theTotalSizeIsReadFromContentRange() {
        assertEquals(1_234_567L, parseContentRangeTotal("bytes 0-65535/1234567"))
        assertEquals(1_234_567L, parseContentRangeTotal("bytes 999-1063/1234567"))
        assertNull(parseContentRangeTotal("bytes 0-65535/*"))
        assertNull(parseContentRangeTotal("bytes 0-65535/"))
        assertNull(parseContentRangeTotal(null))
        assertNull(parseContentRangeTotal(""))
        assertNull(parseContentRangeTotal("bytes 0-65535/not-a-number"))
    }
}
