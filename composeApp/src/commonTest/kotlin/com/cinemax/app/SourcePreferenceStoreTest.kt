package com.cinemax.app

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class SourcePreferenceStoreTest {

    @Test
    fun anUnknownAddonFallsBackToAuto() {
        val store = SourcePreferenceStore(MapSettings())
        assertEquals(SourcePreference(quality = "auto"), store.load("addon-a"))
    }

    @Test
    fun aPlayedTierIsRememberedPerAddon() {
        val store = SourcePreferenceStore(MapSettings())
        store.recordPlayedQuality("addon-a", "1080p")
        store.recordPlayedQuality("addon-b", "4K")

        assertEquals("1080p", store.load("addon-a").quality)
        assertEquals("4K", store.load("addon-b").quality)
        assertEquals(2, store.loadAll().size)
    }

    @Test
    fun autoIsNotRememberedAndEmptyAudioStaysEmpty() {
        val store = SourcePreferenceStore(MapSettings())
        store.recordPlayedQuality("addon-a", "auto")
        assertEquals("auto", store.load("addon-a").quality)

        store.save("addon-a", SourcePreference(quality = "720p", audio = "", subtitle = "ar"))
        assertEquals(SourcePreference(quality = "720p", audio = "", subtitle = "ar"), store.load("addon-a"))
    }

    @Test
    fun clearForgetsEveryAddon() {
        val store = SourcePreferenceStore(MapSettings())
        store.recordPlayedQuality("addon-a", "1080p")
        store.clear()
        assertEquals(emptyMap(), store.loadAll())
    }
}
