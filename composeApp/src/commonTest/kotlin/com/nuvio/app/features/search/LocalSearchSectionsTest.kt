package com.nuvio.app.features.search

import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalSearchSectionsTest {
    private fun saved(
        id: String,
        name: String,
        type: String = "movie",
    ): LibraryItem = LibraryItem(
        id = id,
        type = type,
        name = name,
        savedAtEpochMs = 0L,
    )

    private fun watched(
        parentId: String,
        name: String,
        videoId: String = "$parentId:1:1",
        type: String = "series",
    ): WatchProgressEntry = WatchProgressEntry(
        contentType = type,
        parentMetaId = parentId,
        parentMetaType = type,
        videoId = videoId,
        title = name,
        lastPositionMs = 60_000L,
        durationMs = 1_440_000L,
        lastUpdatedEpochMs = 0L,
    )

    @Test
    fun `a blank query has no library row`() {
        assertNull(
            librarySearchSection(
                items = listOf(saved("tt1", "Spider-Man")),
                query = "   ",
                title = "In your library",
            ),
        )
    }

    @Test
    fun `only saved titles that match the query are listed`() {
        val section = librarySearchSection(
            items = listOf(
                saved("tt1", "Spider-Man"),
                saved("tt2", "Spider-Man 2"),
                saved("tt3", "Heat"),
            ),
            query = "spider man",
            title = "In your library",
        )

        assertEquals(listOf("tt1", "tt2"), section?.items?.map { item -> item.id })
        assertEquals("In your library", section?.title)
    }

    @Test
    fun `a query that matches nothing leaves the library row out`() {
        assertNull(
            librarySearchSection(
                items = listOf(saved("tt1", "Spider-Man")),
                query = "dune",
                title = "In your library",
            ),
        )
    }

    @Test
    fun `the library row stops at the limit`() {
        val items = (1..30).map { index -> saved("tt$index", "Show $index") }

        val section = librarySearchSection(
            items = items,
            query = "show",
            title = "In your library",
            limit = 5,
        )

        assertEquals(5, section?.items?.size)
    }

    @Test
    fun `an empty library has no row`() {
        assertNull(
            librarySearchSection(
                items = emptyList(),
                query = "spider",
                title = "In your library",
            ),
        )
    }

    @Test
    fun `the history row keeps one entry per title`() {
        val section = historySearchSection(
            entries = listOf(
                watched("tt9", "Breaking Bad", videoId = "tt9:1:1"),
                watched("tt9", "Breaking Bad", videoId = "tt9:1:2"),
                watched("tt8", "Better Call Saul", videoId = "tt8:1:1"),
            ),
            query = "breaking",
            title = "From your history",
        )

        assertEquals(listOf("tt9"), section?.items?.map { item -> item.id })
    }

    @Test
    fun `a history row carries the title's own artwork`() {
        val section = historySearchSection(
            entries = listOf(watched("tt9", "Breaking Bad")),
            query = "breaking",
            title = "From your history",
        )

        assertEquals("series", section?.items?.single()?.type)
        assertEquals("From your history", section?.title)
    }

    @Test
    fun `an empty history has no row`() {
        assertNull(
            historySearchSection(
                entries = emptyList(),
                query = "breaking",
                title = "From your history",
            ),
        )
    }
}
