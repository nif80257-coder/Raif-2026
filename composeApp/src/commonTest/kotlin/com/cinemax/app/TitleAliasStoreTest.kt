package com.cinemax.app

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TitleAliasStoreTest {

    @Test
    fun theFirstIdBecomesTheOnlyAlias() {
        val store = TitleAliasStore(MapSettings())
        assertEquals(listOf("tt1"), store.add("imdb:tt1", "tt1"))
        assertEquals(listOf("tt1"), store.ids("imdb:tt1"))
    }

    @Test
    fun aSecondCatalogIdIsRememberedAndTheNewestComesFirst() {
        val store = TitleAliasStore(MapSettings())
        store.add("title:الاسد:2019", "tt1")
        assertEquals(listOf("tmdb:5", "tt1"), store.add("title:الاسد:2019", "tmdb:5"))
        assertEquals(listOf("tmdb:5", "tt1"), store.add("title:الاسد:2019", "tt1"))
    }

    @Test
    fun theAliasListStaysBoundedAndUnknownKeysAreEmpty() {
        val store = TitleAliasStore(MapSettings())
        repeat(6) { store.add("imdb:tt1", "id-$it") }
        assertEquals(4, store.ids("imdb:tt1").size)
        assertTrue(store.ids("nothing").isEmpty())
    }

    @Test
    fun clearForgetsEveryTitle() {
        val store = TitleAliasStore(MapSettings())
        store.add("imdb:tt1", "tt1")
        store.clear()
        assertTrue(store.ids("imdb:tt1").isEmpty())
    }
}
