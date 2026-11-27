package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddonSpeedMemoryTest {

    @Test
    fun aRecordWithoutAResultCountReadsAsUnknown() {
        assertNull(parseResultCount(null))
        assertNull(parseResultCount(""))
        assertNull(parseResultCount("640|1700000000000"))
        assertNull(parseResultCount("640|1700000000000|nope"))
        assertNull(parseResultCount("640|1700000000000|-3"))
    }

    @Test
    fun theResultCountIsTheThirdFieldAndSurvivesZero() {
        assertEquals(12, parseResultCount("640|1700000000000|12"))
        assertEquals(0, parseResultCount("900|1700000000000|0"))
    }

    @Test
    fun theSpeedStaysTheFirstFieldWhateverFollowsIt() {
        val encoded = "1234|1700000000000|7"

        assertEquals(1234L, encoded.substringBefore('|').toLongOrNull())
    }

    @Test
    fun aFirstMeasurementIsKeptAsItIs() {
        assertEquals(900L, AddonSpeedMemory.nextSpeedMs(previousMs = null, elapsedMs = 900L))
    }

    @Test
    fun aLaterMeasurementIsSmoothed() {
        // The newest sample weighs Smoothing (0.4): 1500 + (500 - 1500) * 0.4 and
        // 2000 + (3000 - 2000) * 0.4.
        assertEquals(1_100L, AddonSpeedMemory.nextSpeedMs(previousMs = 1_500L, elapsedMs = 500L))
        assertEquals(2_400L, AddonSpeedMemory.nextSpeedMs(previousMs = 2_000L, elapsedMs = 3_000L))
    }

    @Test
    fun anAnswerAfterAFailureStartsFromItsOwnTimeRatherThanThePenalty() {
        // The failure penalty is 15 s. Smoothing from it would leave this source measured at 9 s
        // after an answer that took 0.6 s, and it would stay behind every other source for loads.
        assertEquals(
            600L,
            AddonSpeedMemory.nextSpeedMs(previousMs = 15_000L, elapsedMs = 600L),
        )
    }
}
