package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerSleepTimerTest {

    @Test
    fun formatsRemainingTime() {
        assertEquals("15:00", formatSleepTimerRemaining(15L * 60_000L))
        assertEquals("1:00", formatSleepTimerRemaining(60_000L))
        assertEquals("0:01", formatSleepTimerRemaining(1L))
        assertEquals("0:00", formatSleepTimerRemaining(0L))
        assertEquals("1:02:05", formatSleepTimerRemaining(3_725_000L))
    }

    @Test
    fun episodeEndOnlyAppliesToTheEpisodeOption() {
        val ended = PlayerPlaybackSnapshot(isEnded = true, durationMs = 100_000L, positionMs = 100_000L)

        assertTrue(shouldSleepAtEpisodeEnd(PlayerSleepTimerOption.END_OF_EPISODE, ended))
        assertFalse(shouldSleepAtEpisodeEnd(PlayerSleepTimerOption.MINUTES_30, ended))
        assertFalse(shouldSleepAtEpisodeEnd(null, ended))
    }

    @Test
    fun episodeEndFiresNearTheEndAndNotBefore() {
        val episode = PlayerSleepTimerOption.END_OF_EPISODE
        val nearEnd = PlayerPlaybackSnapshot(
            isPlaying = true,
            durationMs = 1_000_000L,
            positionMs = 1_000_000L - 500L,
        )
        val midEpisode = PlayerPlaybackSnapshot(
            isPlaying = true,
            durationMs = 1_000_000L,
            positionMs = 500_000L,
        )

        assertTrue(shouldSleepAtEpisodeEnd(episode, nearEnd))
        assertFalse(shouldSleepAtEpisodeEnd(episode, midEpisode))
    }

    @Test
    fun unknownDurationDoesNotEndTheEpisodeEarly() {
        val noDuration = PlayerPlaybackSnapshot(isPlaying = true, durationMs = 0L, positionMs = 10_000L)

        assertFalse(shouldSleepAtEpisodeEnd(PlayerSleepTimerOption.END_OF_EPISODE, noDuration))
    }
}
