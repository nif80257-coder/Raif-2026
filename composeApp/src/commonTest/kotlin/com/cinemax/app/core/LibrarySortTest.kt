package com.cinemax.app.core

import com.cinemax.app.addons.StremioMetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySortTest {

    private fun item(id: String, name: String = id, rating: String? = null) =
        StremioMetaPreview(id = id, type = "movie", name = name, imdbRating = rating)

    private val saved = listOf(
        item("tt1", name = "Charlie", rating = "7.1"),
        item("tt2", name = "Alpha", rating = "8.4"),
        item("tt3", name = "Bravo", rating = null)
    )

    @Test
    fun recentlyWatchedComesFirstAndTheRestKeepTheirOrder() {
        val sorted = sortLibrary(saved, LibrarySort.Recent, listOf("tt3"))

        assertEquals(listOf("tt3", "tt1", "tt2"), sorted.map { it.id })
    }

    @Test
    fun withoutHistoryTheSavedOrderIsKept() {
        assertEquals(
            listOf("tt1", "tt2", "tt3"),
            sortLibrary(saved, LibrarySort.Recent, emptyList()).map { it.id }
        )
    }

    @Test
    fun theNewestSavedTitleComesFirst() {
        assertEquals(
            listOf("tt3", "tt2", "tt1"),
            sortLibrary(saved, LibrarySort.Added, emptyList()).map { it.id }
        )
    }

    @Test
    fun titlesSortAlphabetically() {
        assertEquals(
            listOf("tt2", "tt3", "tt1"),
            sortLibrary(saved, LibrarySort.Title, emptyList()).map { it.id }
        )
    }

    @Test
    fun theHighestRatingComesFirstAndAMissingRatingSortsLast() {
        assertEquals(
            listOf("tt2", "tt1", "tt3"),
            sortLibrary(saved, LibrarySort.Rating, emptyList()).map { it.id }
        )
    }
}
