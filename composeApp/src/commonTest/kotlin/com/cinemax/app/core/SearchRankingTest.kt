package com.cinemax.app.core

import com.cinemax.app.addons.CatalogEntry
import com.cinemax.app.addons.StremioMetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchRankingTest {

    private fun entry(id: String, name: String) = CatalogEntry(
        addonId = "addon",
        addonName = "addon",
        item = StremioMetaPreview(id = id, type = "movie", name = name)
    )

    @Test
    fun anExactNameComesFirstThenPrefixThenContainment() {
        val results = listOf(
            entry("3", "The Batman Returns"),
            entry("1", "Batman"),
            entry("2", "Batman Begins")
        )

        assertEquals(
            listOf("1", "2", "3"),
            rankSearchResults(results, "Batman").map { it.item.id }
        )
    }

    @Test
    fun titlesThatDoNotMatchStayLastInTheirOwnOrder() {
        val results = listOf(
            entry("a", "Dune"),
            entry("b", "Batman"),
            entry("c", "Arrival")
        )

        assertEquals(
            listOf("b", "a", "c"),
            rankSearchResults(results, "bat").map { it.item.id }
        )
    }

    @Test
    fun theSortIsStableInsideOneGroup() {
        val results = listOf(
            entry("x", "Batman 1"),
            entry("y", "Batman 2"),
            entry("z", "Batman 3")
        )

        assertEquals(
            listOf("x", "y", "z"),
            rankSearchResults(results, "Batman").map { it.item.id }
        )
    }

    @Test
    fun alefFormsAndHarakatDoNotHideAMatch() {
        val results = listOf(entry("1", "الأسد الملك"))

        assertEquals(
            listOf("1"),
            rankSearchResults(results, "الاسد").map { it.item.id }
        )
        assertEquals(
            listOf("1"),
            rankSearchResults(results, "الأسد").map { it.item.id }
        )
    }

    @Test
    fun taMarbutaAndAlefMaqsuraAreFolded() {
        assertEquals("مصطفي", normalizeForSearch("مصطفى"))
        assertEquals("حياه", normalizeForSearch("حياة"))
        assertEquals("batman", normalizeForSearch("  BATMAN "))
    }

    @Test
    fun anEmptyQueryLeavesTheOrderAlone() {
        val results = listOf(entry("1", "B"), entry("2", "A"))

        assertEquals(
            listOf("1", "2"),
            rankSearchResults(results, "   ").map { it.item.id }
        )
    }
}
