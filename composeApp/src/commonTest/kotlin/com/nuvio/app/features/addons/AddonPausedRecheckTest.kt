package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonPausedRecheckTest {

    @Test
    fun nothingIsCheckedWhileNoSourceIsPaused() {
        assertFalse(shouldRecheckPausedSources(nowMs = 10 * PausedRecheckIntervalMs, lastRecheckAtMs = 0L, pausedCount = 0))
    }

    @Test
    fun aPausedSourceIsCheckedOnceADay() {
        val last = 1_000_000L

        assertFalse(shouldRecheckPausedSources(nowMs = last + PausedRecheckIntervalMs - 1, lastRecheckAtMs = last, pausedCount = 2))
        assertTrue(shouldRecheckPausedSources(nowMs = last + PausedRecheckIntervalMs, lastRecheckAtMs = last, pausedCount = 2))
        assertTrue(shouldRecheckPausedSources(nowMs = last + 3 * PausedRecheckIntervalMs, lastRecheckAtMs = last, pausedCount = 1))
    }

    @Test
    fun aFirstRunWithNoRecordIsDue() {
        assertTrue(shouldRecheckPausedSources(nowMs = 5_000L, lastRecheckAtMs = 0L, pausedCount = 1))
    }

    @Test
    fun aClockThatMovedBackwardsIsTreatedAsDue() {
        assertTrue(
            shouldRecheckPausedSources(
                nowMs = 1_000L,
                lastRecheckAtMs = 1_000L + PausedRecheckIntervalMs,
                pausedCount = 1,
            ),
        )
    }
}
