package com.nuvio.app.features.search

import com.nuvio.app.features.library.LibraryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibrarySearchSectionTest {
    private fun item(
        id: String,
        name: String,
        type: String = "movie",
    ): LibraryItem = LibraryItem(
        id = id,
        type = type,
        name = name,
        savedAtEpochMs = 0L,
    )

    @Test
    fun `a blank query has no library row`() {
        assertNull(
            librarySearchSection(
                items = listOf(item("tt1", "Spider-Man")),
                query = "   ",
                title = "In your library",
            ),
        )
    }

    @Test
    fun `only saved titles that match the query are listed`() {
        val section = librarySearchSection(
            items = listOf(
                item("tt1", "Spider-Man"),
                item("tt2", "Spider-Man 2"),
                item("tt3", "Heat"),
            ),
            query = "spider man",
            title = "In your library",
        )

        assertEquals(listOf("tt1", "tt2"), section?.items?.map { saved -> saved.id })
        assertEquals("In your library", section?.title)
    }

    @Test
    fun `a query that matches nothing leaves the row out`() {
        assertNull(
            librarySearchSection(
                items = listOf(item("tt1", "Spider-Man")),
                query = "dune",
                title = "In your library",
            ),
        )
    }

    @Test
    fun `the row stops at the limit`() {
        val items = (1..30).map { index -> item("tt$index", "Show $index") }

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
}
