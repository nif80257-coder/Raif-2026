package com.cinemax.app.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeDatesTest {
    private val today = LocalDate(2024, 6, 15)

    @Test
    fun readsTheDateOutOfAnIsoTimestamp() {
        assertEquals(LocalDate(2020, 1, 5), parseReleaseDate("2020-01-05T00:00:00.000Z"))
        assertEquals(LocalDate(2020, 1, 5), parseReleaseDate("2020-01-05"))
    }

    @Test
    fun anUnusableValueIsNotADate() {
        assertNull(parseReleaseDate(null))
        assertNull(parseReleaseDate(""))
        assertNull(parseReleaseDate("2020"))
        assertNull(parseReleaseDate("not a date"))
        assertNull(parseReleaseDate("2020-13-45T00:00:00Z"))
    }

    @Test
    fun aPastEpisodeShowsOnlyItsDate() {
        val release = episodeReleaseLabel("2020-01-05T00:00:00.000Z", today)

        assertEquals("5 يناير 2020", release?.text)
        assertFalse(release?.upcoming == true)
    }

    @Test
    fun aFutureEpisodeSaysItIsComing() {
        val release = episodeReleaseLabel("2024-12-25T00:00:00.000Z", today)

        assertEquals("قريبًا • 25 ديسمبر 2024", release?.text)
        assertTrue(release?.upcoming == true)
    }

    @Test
    fun anEpisodeReleasedTodayIsNotUpcoming() {
        val release = episodeReleaseLabel("2024-06-15", today)

        assertFalse(release?.upcoming == true)
        assertEquals("15 يونيو 2024", release?.text)
    }

    @Test
    fun noDateGivesNoLabel() {
        assertNull(episodeReleaseLabel(null, today))
    }
}
