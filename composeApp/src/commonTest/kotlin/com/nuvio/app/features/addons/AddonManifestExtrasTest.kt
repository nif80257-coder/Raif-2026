package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonManifestExtrasTest {
    private val directoryManifest = """
        {
          "id": "com.linvo.cinemeta",
          "version": "3.0.14",
          "name": "Cinemeta",
          "resources": ["catalog", "meta", "addon_catalog"],
          "types": ["movie", "series"],
          "addonCatalogs": [
            {"type": "all", "id": "official", "name": "Official"},
            {"type": "all", "id": "community", "name": "Community"}
          ],
          "catalogs": [{"type": "movie", "id": "top", "name": "Popular"}]
        }
    """.trimIndent()

    private val configuredManifest = """
        {
          "id": "community.aiostatus",
          "version": "2.3.0",
          "name": "AIOStatus",
          "resources": ["stream"],
          "types": ["movie"],
          "config": [
            {"key": "cache_minutes", "type": "number", "title": "Cache minutes", "default": "45"},
            {
              "key": "visibility_mode",
              "type": "select",
              "title": "When to show",
              "options": ["always", "never"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `addonCatalogs entries are parsed and mark the manifest as a directory`() {
        val manifest = AddonManifestParser.parse(
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            payload = directoryManifest,
        )

        assertTrue(manifest.servesAddonDirectory())
        assertEquals(2, manifest.addonCatalogs.size)
        assertEquals("official", manifest.addonCatalogs.first().id)
        assertEquals("all", manifest.addonCatalogs.first().type)
    }

    @Test
    fun `a manifest without addonCatalogs is not a directory`() {
        val manifest = AddonManifestParser.parse(
            manifestUrl = "https://example.com/manifest.json",
            payload = configuredManifest,
        )

        assertFalse(manifest.servesAddonDirectory())
        assertTrue(manifest.addonCatalogs.isEmpty())
    }

    @Test
    fun `config fields keep their key and default and options`() {
        val manifest = AddonManifestParser.parse(
            manifestUrl = "https://example.com/manifest.json",
            payload = configuredManifest,
        )

        assertEquals(2, manifest.configFields.size)
        val cache = manifest.configFields.first()
        assertEquals("cache_minutes", cache.key)
        assertEquals("45", cache.default)
        assertFalse(cache.isChoice)

        val visibility = manifest.configFields.last()
        assertTrue(visibility.isChoice)
        assertEquals(listOf("always", "never"), visibility.options)
    }
}
