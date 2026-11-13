package com.cinemax.app.core

import com.cinemax.app.WatchProgress
import com.cinemax.app.addons.StremioMetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PosterStatusTest {

    private fun item(id: String) = StremioMetaPreview(id = id, type = "movie", name = id)

    private fun progress(
        id: String,
        position: Long,
        duration: Long = 100_000L,
        updated: Long = 1L,
        videoId: String? = null
    ) = WatchProgress(
        item = item(id),
        positionMillis = position,
        durationMillis = duration,
        updatedAtEpochMillis = updated,
        videoId = videoId
    )

    @Test
    fun emptyHistoryAndNoFavouritesGiveNoStatus() {
        assertTrue(posterStatusById(emptyList(), emptySet()).isEmpty())
    }

    @Test
    fun aSavedTitleWithNoHistoryIsMarkedSaved() {
        val status = posterStatusById(emptyList(), setOf("tt1"))["tt1"]

        assertTrue(status?.saved == true)
        assertNull(status?.progress)
    }

    @Test
    fun aStartedTitleReportsItsFraction() {
        val status = posterStatusById(listOf(progress("tt1", 25_000L)), emptySet())["tt1"]

        assertEquals(0.25f, status?.progress)
        assertFalse(status?.saved == true)
    }

    @Test
    fun theMostRecentlyUpdatedEpisodeWins() {
        val entries = listOf(
            progress("tt9", 10_000L, updated = 1L, videoId = "tt9:1:1"),
            progress("tt9", 80_000L, updated = 5L, videoId = "tt9:1:2")
        )

        assertEquals(0.8f, posterStatusById(entries, emptySet())["tt9"]?.progress)
    }

    @Test
    fun aBarelyStartedTitleShowsNoBar() {
        val status = posterStatusById(listOf(progress("tt1", 100L)), emptySet())["tt1"]

        assertNull(status?.progress)
    }
}
