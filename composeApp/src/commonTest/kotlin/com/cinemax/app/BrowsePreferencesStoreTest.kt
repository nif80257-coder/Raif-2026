package com.cinemax.app

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowsePreferencesStoreTest {

    @Test
    fun savesAndRestoresGenreAndSort() {
        val store = BrowsePreferencesStore(MapSettings())
        store.saveGenre("غربي")
        store.saveSort("الأحدث إضافة")

        assertEquals("غربي", store.loadGenre())
        assertEquals("الأحدث إضافة", store.loadSort())
    }

    @Test
    fun missingValuesAreNull() {
        val store = BrowsePreferencesStore(MapSettings())
        assertNull(store.loadGenre())
        assertNull(store.loadSort())
    }

    @Test
    fun clearingAGenreRemovesIt() {
        val store = BrowsePreferencesStore(MapSettings())
        store.saveGenre("رعب")
        store.saveGenre(null)

        assertNull(store.loadGenre())
    }

    @Test
    fun clearForgetsBoth() {
        val store = BrowsePreferencesStore(MapSettings())
        store.saveGenre("دراما")
        store.saveSort("أبجديًا")
        store.clear()

        assertNull(store.loadGenre())
        assertNull(store.loadSort())
    }
}
