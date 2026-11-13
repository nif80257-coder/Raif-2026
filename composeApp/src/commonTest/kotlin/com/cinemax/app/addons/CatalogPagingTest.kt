package com.cinemax.app.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogPagingTest {

    private fun item(id: String) = StremioMetaPreview(id = id, type = "movie", name = id)

    @Test
    fun appendsANewPageAfterTheExistingItems() {
        val page = appendCatalogPage(listOf(item("a"), item("b")), listOf(item("c"), item("d")))

        assertEquals(listOf("a", "b", "c", "d"), page.items.map { it.id })
        assertFalse(page.exhausted)
    }

    @Test
    fun dropsItemsThatAreAlreadyListed() {
        val page = appendCatalogPage(listOf(item("a"), item("b")), listOf(item("b"), item("c")))

        assertEquals(listOf("a", "b", "c"), page.items.map { it.id })
        assertFalse(page.exhausted)
    }

    @Test
    fun anEmptyPageEndsTheList() {
        val page = appendCatalogPage(listOf(item("a")), emptyList())

        assertEquals(listOf("a"), page.items.map { it.id })
        assertTrue(page.exhausted)
    }

    @Test
    fun aPageOfOnlyKnownItemsEndsTheList() {
        // What a server that ignores `skip` returns: the same page again.
        val page = appendCatalogPage(listOf(item("a"), item("b")), listOf(item("a"), item("b")))

        assertEquals(listOf("a", "b"), page.items.map { it.id })
        assertTrue(page.exhausted)
    }

    @Test
    fun aRepeatedIdInsideOnePageIsKeptOnce() {
        val page = appendCatalogPage(emptyList(), listOf(item("a"), item("a")))

        assertEquals(listOf("a"), page.items.map { it.id })
        assertFalse(page.exhausted)
    }

    @Test
    fun theFirstPageOfAGenreCarriesNoSkip() {
        assertEquals(mapOf("genre" to "Action"), catalogPageExtra("Action", 0))
    }

    @Test
    fun aLaterPageCarriesBothTheGenreAndTheSkip() {
        assertEquals(
            mapOf("genre" to "Action", "skip" to "100"),
            catalogPageExtra("Action", 100)
        )
    }

    @Test
    fun withoutAGenreOnlyTheSkipIsSent() {
        assertEquals(mapOf("skip" to "100"), catalogPageExtra(null, 100))
        assertEquals(emptyMap(), catalogPageExtra("  ", 0))
    }
}
