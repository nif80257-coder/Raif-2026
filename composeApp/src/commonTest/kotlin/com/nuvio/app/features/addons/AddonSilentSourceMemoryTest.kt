package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddonSilentSourceMemoryTest {

    private val hour = 60 * 60 * 1000L
    private val now = 1_700_000_000_000L

    @Test
    fun `two empty answers are not enough to skip a source`() {
        assertFalse(
            shouldSkipSilentSource(
                emptyAnswers = 2,
                lastEmptyAtMs = now - hour,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `the third empty answer inside the window skips the source`() {
        assertTrue(
            shouldSkipSilentSource(
                emptyAnswers = AddonSilentSourceMemory.EmptyAnswersBeforeSkip,
                lastEmptyAtMs = now - hour,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `a window that already passed asks the source again`() {
        assertFalse(
            shouldSkipSilentSource(
                emptyAnswers = 9,
                lastEmptyAtMs = now - AddonSilentSourceMemory.SkipDurationMs,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `a record without a time never skips`() {
        assertFalse(shouldSkipSilentSource(emptyAnswers = 9, lastEmptyAtMs = 0L, nowMs = now))
    }

    @Test
    fun `a stored record comes back as it went in`() {
        val encoded = encodeSilentRecord(emptyAnswers = 4, lastEmptyAtMs = now)

        assertEquals(
            SilentSourceRecord(emptyAnswers = 4, lastEmptyAtMs = now),
            decodeSilentRecord(encoded),
        )
    }

    @Test
    fun `an unreadable record is dropped instead of guessed`() {
        assertNull(decodeSilentRecord(null))
        assertNull(decodeSilentRecord(""))
        assertNull(decodeSilentRecord("not a record"))
        assertNull(decodeSilentRecord("0|$now"))
        assertNull(decodeSilentRecord("3"))
    }
}
