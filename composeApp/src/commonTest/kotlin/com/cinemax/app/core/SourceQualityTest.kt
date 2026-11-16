package com.cinemax.app.core

import com.cinemax.app.addons.StremioStream
import com.cinemax.app.addons.StreamOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceQualityTest {

    private fun option(url: String, name: String) = StreamOption(
        addonId = "addon",
        addonName = "Addon",
        stream = StremioStream(name = name, url = url)
    )

    @Test
    fun autoMatchesEverythingAndTiersMatchTheirBadge() {
        val hd = option("https://cdn.example.com/hd.mp4", "1080p WEB-DL")
        assertTrue(matchesQualityPreference(hd, "auto"))
        assertTrue(matchesQualityPreference(hd, "1080p"))
        assertFalse(matchesQualityPreference(hd, "4K"))
    }

    @Test
    fun bestSourceObeysThePreferredQualityWhenItExists() {
        val uhd = option("https://cdn.example.com/uhd.mp4", "2160p 4K")
        val hd = option("https://cdn.example.com/hd.mp4", "1080p")
        val picked = bestSource(listOf(uhd, hd), preference = "1080p")
        assertEquals(hd.stream.url, picked?.stream?.url)
    }

    @Test
    fun bestSourceFallsBackToTheTopWhenTheTierIsMissing() {
        val uhd = option("https://cdn.example.com/uhd.mp4", "2160p 4K")
        val picked = bestSource(listOf(uhd), preference = "720p")
        assertEquals(uhd.stream.url, picked?.stream?.url)
    }

    @Test
    fun aPinnedSourceWinsOverTheQualityPreference() {
        val uhd = option("https://cdn.example.com/uhd.mp4", "2160p 4K")
        val hd = option("https://cdn.example.com/hd.mp4", "1080p")
        val picked = bestSource(listOf(uhd, hd), preference = "4K", pinnedUrl = hd.stream.url)
        assertEquals(hd.stream.url, picked?.stream?.url)
    }
}
