package com.cinemax.app.core

import com.cinemax.app.addons.StremioMetaPreview
import com.cinemax.app.addons.StremioVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaIdentityTest {

    private fun item(id: String, name: String, type: String = "movie", release: String? = null) =
        StremioMetaPreview(id = id, type = type, name = name, releaseInfo = release)

    @Test
    fun imdbAndTmdbIdsAreReadFromStremioIds() {
        assertEquals("tt0944947", imdbIdOf("tt0944947:1:2"))
        assertEquals("tt0944947", imdbIdOf("tt0944947"))
        assertEquals(null, imdbIdOf("ttp123"))
        assertEquals("550", tmdbIdOf("tmdb:550"))
        assertEquals("550", tmdbIdOf("tmdb:550:1:2"))
        assertEquals(null, tmdbIdOf("tt0944947"))
    }

    @Test
    fun theYearIsReadFromReleaseText() {
        assertEquals("2019", releaseYear("2019–2021"))
        assertEquals("2020", releaseYear("2020 • 1h 40m"))
        assertEquals(null, releaseYear("The 100"))
        assertEquals(null, releaseYear(null))
    }

    @Test
    fun theImdbIdWinsOverATmdbIdAndOverTheTitle() {
        assertEquals("imdb:tt1", canonicalMediaKey(item("tt1", "الأسد")))
        assertEquals("tmdb:5", canonicalMediaKey(item("tmdb:5", "الأسد")))
        assertEquals("title:الاسد:2019", canonicalMediaKey(item("local-1", "الأسد", release = "2019")))
    }

    @Test
    fun theSameTitleWithASimilarYearIsTheSameMedia() {
        val arabic = item("tt1", "الأسد", release = "2019")
        val latin = item("tt2", "الاسد", release = "2020")
        assertTrue(sameMedia(arabic, latin))
    }

    @Test
    fun differentYearsOrTypesAreNotMerged() {
        val old = item("tt1", "العار", release = "1982")
        val newer = item("tt2", "العار", release = "2019")
        assertFalse(sameMedia(old, newer))
        assertFalse(sameMedia(item("a", "حيوانات", type = "movie"), item("b", "حيوانات", type = "series")))
    }

    @Test
    fun distinctTitlesCollapsesTheDuplicates() {
        val merged = distinctTitles(
            listOf(
                item("tt1", "الأسد", release = "2019"),
                item("tt2", "الاسد", release = "2019"),
                item("tt3", "فيلم آخر", release = "2019")
            )
        )
        assertEquals(listOf("tt1", "tt3"), merged.map { it.id })
    }

    @Test
    fun anEpisodePositionIsNotAnIdentity() {
        // Episodes share their series' IMDb id, so callers must key them by id.
        val episode: StremioVideo = StremioVideo(id = "tt1:1:2", season = 1, episode = 2)
        assertEquals("tt1", imdbIdOf(episode.id))
    }
}
