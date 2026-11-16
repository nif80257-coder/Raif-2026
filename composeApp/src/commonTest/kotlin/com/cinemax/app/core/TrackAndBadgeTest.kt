package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackAndBadgeTest {
    @Test
    fun matchesExactAndRegionalLanguageCodes() {
        assertEquals(0, languageIndex(listOf("en", "ar"), "ar"))
        assertEquals(1, languageIndex(listOf("en-US", "ar-SA"), "ar"))
        assertNull(languageIndex(listOf("en", "fr"), "ar"))
        assertNull(languageIndex(listOf("ar"), null))
        assertNull(languageIndex(emptyList(), "ar"))
    }

    @Test
    fun readsQualityAndFormatBadgesFromStreamText() {
        assertEquals(listOf("1080p", "HDR"), streamBadges("1080p HDR", "BluRay"))
        assertEquals(listOf("4K", "HEVC"), streamBadges("4K", "x265 10bit"))
        assertTrue(streamBadges("1080p", "WEB-DL").contains("1080p"))
        assertTrue(streamBadges(null, null).isEmpty())
    }

    @Test
    fun labelsArabicAndDubbedStreamsFromTheirText() {
        assertEquals("مدبلج", streamLanguageLabel("1080p مدبلج", null))
        assertEquals("مدبلج", streamLanguageLabel("1080p Arabic DUB", null))
        assertEquals("عربي", streamLanguageLabel("Arabic", "1080p"))
        assertEquals("متعدد اللغات", streamLanguageLabel("Multi audio", null))
        assertNull(streamLanguageLabel("1080p WEB-DL", null))
        assertNull(streamLanguageLabel(null, null))
    }
}
