package com.cinemax.app

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class CinemaxPreferencesStoreTest {
    @Test
    public fun savesAndRestoresAppearanceAndPlaybackPreferences() {
        val store = CinemaxPreferencesStore(MapSettings())
        val expected = CinemaxPreferences(
            accent = "Blue",
            oledBlack = false,
            compactCards = true,
            rememberPlaybackPosition = false,
            autoplayNextEpisode = true,
            preferredSubtitleLanguage = "en",
            preferredAudioLanguage = "ar",
            subtitleSize = "large",
            preferredSpeed = 1.25f,
            preferredPlayer = "system"
        )

        store.save(expected)

        assertEquals(expected, store.load())
    }

    @Test
    public fun playbackSpeedRoundTripsOnItsOwn() {
        val store = CinemaxPreferencesStore(MapSettings())
        store.save(CinemaxPreferences(preferredSpeed = 1.5f))

        assertEquals(1.5f, store.load().preferredSpeed)
    }

    @Test
    public fun hidingDeadSourcesRoundTrips() {
        val store = CinemaxPreferencesStore(MapSettings())
        store.save(CinemaxPreferences(hideDeadSources = true))

        assertTrue(store.load().hideDeadSources)
    }

    @Test
    public fun missingPreferencesUseSafeDefaults() {
        val preferences = CinemaxPreferencesStore(MapSettings()).load()

        assertEquals("Violet", preferences.accent)
        assertTrue(preferences.oledBlack)
        assertFalse(preferences.compactCards)
        assertTrue(preferences.rememberPlaybackPosition)
        assertFalse(preferences.autoplayNextEpisode)
        assertEquals("ar", preferences.preferredSubtitleLanguage)
        assertEquals("medium", preferences.subtitleSize)
        assertEquals(1f, preferences.preferredSpeed)
        assertEquals("mpv", preferences.preferredPlayer)
        assertFalse(preferences.hideDeadSources)
    }
}
