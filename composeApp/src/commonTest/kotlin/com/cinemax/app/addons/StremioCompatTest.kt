package com.cinemax.app.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Compatibility checks for the manifest shapes real Stremio addons use. Both the
 * current `extra` list and the legacy `extraSupported`/`extraRequired` fields
 * must be understood, otherwise search and pagination are hidden for many addons.
 */
class StremioCompatTest {
    @Test
    fun readsLegacyExtraFields() {
        val catalog = StremioCatalog(
            type = "movie",
            id = "top",
            extraSupported = listOf("search", "skip", "genre"),
            extraRequired = listOf("skip")
        )
        assertTrue(catalog.supportsExtra("search"))
        assertTrue(catalog.supportsExtra("skip"))
        assertTrue(catalog.supportsExtra("genre"))
        assertFalse(catalog.supportsExtra("year"))
    }

    @Test
    fun readsCurrentExtraListAndMergesBothForms() {
        val catalog = StremioCatalog(
            type = "series",
            id = "top",
            extra = listOf(StremioExtra(name = "search")),
            extraSupported = listOf("skip")
        )
        assertEquals(setOf("search", "skip"), catalog.extraNames())
    }

    @Test
    fun catalogWithoutExtrasSupportsNone() {
        assertTrue(StremioCatalog(type = "movie", id = "top").extraNames().isEmpty())
    }

    @Test
    fun buildsEpisodeStreamPathWithUnencodedColon() {
        val url = StremioAddonClient.resourceUrl(
            "https://v3-cinemeta.strem.io/manifest.json",
            "stream",
            "series",
            "tt0944947:1:2"
        )
        assertEquals("https://v3-cinemeta.strem.io/stream/series/tt0944947:1:2.json", url)
    }

    @Test
    fun putsSearchAndSkipInOneExtraSegment() {
        val url = StremioAddonClient.resourceUrl(
            "https://example.com/manifest.json",
            "catalog",
            "movie",
            "top",
            mapOf("search" to "dune part two", "skip" to "100")
        )
        assertEquals(
            "https://example.com/catalog/movie/top/search=dune%20part%20two&skip=100.json",
            url
        )
    }

    @Test
    fun keepsAddonConfigurationQuery() {
        val url = StremioAddonClient.resourceUrl(
            "https://example.com/config/manifest.json?token=abc",
            "stream",
            "movie",
            "tt1234567"
        )
        assertEquals("https://example.com/config/stream/movie/tt1234567.json?token=abc", url)
    }
}
