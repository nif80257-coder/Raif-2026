package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AbsoluteEpisodesTest {

    private val lengths = listOf(10, 12, 8)

    @Test
    fun numbersInsideTheFirstSeasonStayThere() {
        assertEquals(SeasonEpisode(1, 1), absoluteToSeasonEpisode(lengths, 1))
        assertEquals(SeasonEpisode(1, 10), absoluteToSeasonEpisode(lengths, 10))
    }

    @Test
    fun theNextNumberRollsIntoTheNextSeason() {
        assertEquals(SeasonEpisode(2, 1), absoluteToSeasonEpisode(lengths, 11))
        assertEquals(SeasonEpisode(2, 12), absoluteToSeasonEpisode(lengths, 22))
        assertEquals(SeasonEpisode(3, 1), absoluteToSeasonEpisode(lengths, 23))
    }

    @Test
    fun numbersOutsideTheSeriesAreRejected() {
        assertNull(absoluteToSeasonEpisode(lengths, 31))
        assertNull(absoluteToSeasonEpisode(lengths, 0))
        assertNull(absoluteToSeasonEpisode(emptyList(), 1))
    }

    @Test
    fun aGapInTheSeasonNumbersKeepsTheSeasonIndex() {
        val withGap = seasonLengthsOf(mapOf(1 to 10, 3 to 8))

        assertEquals(listOf(10, 0, 8), withGap)
        assertEquals(SeasonEpisode(3, 1), absoluteToSeasonEpisode(withGap, 11))
    }
}
