package com.cinemax.app

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreferredSourceStoreTest {

    @Test
    fun aPinnedLinkIsRememberedPerTitle() {
        val store = PreferredSourceStore(MapSettings())
        store.save("tt1234", "https://cdn.example.com/a.mp4")

        assertEquals("https://cdn.example.com/a.mp4", store.load("tt1234"))
        assertNull(store.load("tt9999"))
    }

    @Test
    fun pinningAgainReplacesTheLinkAndClearingRemovesIt() {
        val store = PreferredSourceStore(MapSettings())
        store.save("tt1234", "https://cdn.example.com/a.mp4")
        store.save("tt1234", "https://cdn.example.com/b.mp4")
        assertEquals("https://cdn.example.com/b.mp4", store.load("tt1234"))

        store.clear("tt1234")
        assertNull(store.load("tt1234"))
    }

    @Test
    fun aBlankLinkIsNotStored() {
        val store = PreferredSourceStore(MapSettings())
        store.save("tt1234", "   ")
        assertNull(store.load("tt1234"))
    }
}
