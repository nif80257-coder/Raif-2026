package com.cinemax.app.core

import com.cinemax.app.addons.StremioVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodesTest {
    private fun episode(season: Int, number: Int) = StremioVideo(
        id = "tt100:$season:$number",
        season = season,
        episode = number
    )

    @Test
    fun picksTheNextEpisodeInTheSameSeason() {
        val videos = listOf(episode(1, 2), episode(1, 1), episode(1, 3))

        assertEquals("tt100:1:3", nextEpisode(episode(1, 2), videos)?.id)
    }

    @Test
    fun movesToTheNextSeasonAfterTheLastEpisode() {
        val videos = listOf(episode(1, 1), episode(1, 2), episode(2, 1))

        assertEquals("tt100:2:1", nextEpisode(episode(1, 2), videos)?.id)
    }

    @Test
    fun lastEpisodeHasNoSuccessor() {
        val videos = listOf(episode(1, 1), episode(1, 2))

        assertNull(nextEpisode(episode(1, 2), videos))
        assertNull(nextEpisode(null, videos))
        assertNull(nextEpisode(episode(1, 1), emptyList()))
    }
}
