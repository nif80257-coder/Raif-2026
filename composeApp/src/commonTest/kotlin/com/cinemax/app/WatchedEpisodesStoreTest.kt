package com.cinemax.app

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchedEpisodesStoreTest {

    @Test
    fun keepsTheMarkedEpisodeIds() {
        val store = WatchedEpisodesStore(MapSettings())
        assertTrue(store.load().isEmpty())

        store.save(setOf("tt1:1:1", "tt1:1:2"))

        assertEquals(setOf("tt1:1:1", "tt1:1:2"), store.load())
    }

    @Test
    fun anEmptySetClearsTheList() {
        val store = WatchedEpisodesStore(MapSettings())
        store.save(setOf("tt1:1:1"))
        store.save(emptySet())

        assertTrue(store.load().isEmpty())
    }
}
