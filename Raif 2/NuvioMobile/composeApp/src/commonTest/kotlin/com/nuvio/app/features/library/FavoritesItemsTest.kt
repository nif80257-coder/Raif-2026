package com.nuvio.app.features.library

import kotlin.test.Test
import kotlin.test.assertEquals

class FavoritesItemsTest {

    private fun item(id: String, name: String, savedAt: Long): LibraryItem = LibraryItem(
        id = id,
        type = "movie",
        name = name,
        savedAtEpochMs = savedAt,
    )

    private val items = listOf(
        item(id = "b", name = "Beta", savedAt = 200L),
        item(id = "a", name = "Alpha", savedAt = 100L),
        item(id = "c", name = "Charlie", savedAt = 300L),
    )

    @Test
    fun newestSortPutsMostRecentlySavedFirst() {
        val favorites = buildFavoriteItems(items, query = "", sort = FavoritesSortOption.RECENT)

        assertEquals(listOf("c", "b", "a"), favorites.map { it.id })
    }

    @Test
    fun nameSortUsesCaseInsensitiveNameOrder() {
        val favorites = buildFavoriteItems(
            items = items + item(id = "d", name = "delta", savedAt = 400L),
            query = "",
            sort = FavoritesSortOption.NAME,
        )

        assertEquals(listOf("a", "b", "c", "d"), favorites.map { it.id })
    }

    @Test
    fun searchFiltersByNameIgnoringCaseAndSpaces() {
        val favorites = buildFavoriteItems(items, query = "  alp ", sort = FavoritesSortOption.RECENT)

        assertEquals(listOf("a"), favorites.map { it.id })
    }

    @Test
    fun emptyQueryKeepsEveryFavorite() {
        val favorites = buildFavoriteItems(items, query = "   ", sort = FavoritesSortOption.RECENT)

        assertEquals(3, favorites.size)
    }
}
