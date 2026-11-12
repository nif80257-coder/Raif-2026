package com.cinemax.app.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class StremioManifestCapabilitiesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun manifest(raw: String): StremioManifest = json.decodeFromString(raw)

    private fun addon(
        resources: List<String> = emptyList(),
        types: List<String> = emptyList(),
        idPrefixes: List<String> = emptyList()
    ) = InstalledAddon(
        manifestUrl = "https://addon.example/manifest.json",
        id = "org.example",
        name = "Example",
        version = "1.0.0",
        installedAtEpochMillis = 0L,
        resources = resources,
        types = types,
        idPrefixes = idPrefixes
    )

    @Test
    fun parsesResourceNamesFromStringsAndObjects() {
        val manifest = manifest(
            """
            {
              "id": "org.example",
              "version": "1.0.0",
              "name": "Example",
              "resources": ["catalog", {"name": "stream", "types": ["movie"]}],
              "types": ["movie"],
              "catalogs": []
            }
            """.trimIndent()
        )

        assertEquals(setOf("catalog", "stream"), manifest.resourceNames())
    }

    @Test
    fun readsSeriesEpisodesFromMetadata() {
        val meta = json.decodeFromString<StremioMetaResponse>(
            """
            {
              "meta": {
                "id": "tt100",
                "type": "series",
                "name": "Example Series",
                "videos": [
                  {"id": "tt100:1:2", "season": 1, "episode": 2, "name": "Second"},
                  {"id": "tt100:1:1", "season": 1, "episode": 1, "name": "First"}
                ]
              }
            }
            """.trimIndent()
        ).meta!!

        assertEquals(listOf("tt100:1:2", "tt100:1:1"), meta.videos.map { it.id })
        assertEquals(1, meta.videos[0].season)
        assertEquals(2, meta.videos[0].episode)
    }

    @Test
    fun declaredResourcesDecideWhichAddonIsAsked() {
        val streamsOnly = addon(resources = listOf("stream"), types = listOf("movie", "series"))

        assertTrue(AddonCapabilities.supportsStreams(streamsOnly, "movie", "tt123"))
        assertFalse(AddonCapabilities.supportsMeta(streamsOnly, "movie"))
        assertFalse(AddonCapabilities.supportsStreams(streamsOnly, "channel", "tt123"))
    }

    @Test
    fun addonsWithoutStoredCapabilitiesStayUsable() {
        val legacy = addon()

        assertTrue(AddonCapabilities.supportsMeta(legacy, "series"))
        assertTrue(AddonCapabilities.supportsStreams(legacy, "series", "tt100:1:2"))
    }

    @Test
    fun idPrefixesLimitStreamAddonsToMatchingIds() {
        val imdbOnly = addon(
            resources = listOf("stream"),
            types = listOf("movie", "series"),
            idPrefixes = listOf("tt")
        )

        assertTrue(AddonCapabilities.supportsStreams(imdbOnly, "series", "tt100:1:2"))
        assertFalse(AddonCapabilities.supportsStreams(imdbOnly, "movie", "kitsu:12"))
    }
}
