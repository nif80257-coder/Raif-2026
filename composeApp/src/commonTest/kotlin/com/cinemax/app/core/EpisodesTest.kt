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

    @Test
    fun absoluteIndexCountsThroughTheWholeSeries() {
        val videos = listOf(episode(1, 1), episode(1, 2), episode(2, 1))

        assertEquals(3, absoluteEpisodeIndex(episode(2, 1), videos))
        assertNull(absoluteEpisodeIndex(StremioVideo(id = "unknown"), videos))
    }

    @Test
    fun anSxxExxTitleFillsMissingSeasonAndEpisodeFields() {
        val tagged = StremioVideo(id = "tt1:2:5", name = "Show S02E05")

        assertEquals(2, episodeSeason(tagged))
        assertEquals(5, episodeNumber(tagged))
        assertEquals("الموسم 2 • الحلقة 5 — Show S02E05", episodeLabel(tagged))
    }
}
