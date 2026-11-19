package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonDirectoryTest {
    @Test
    fun readsManifestUrlEntries() {
        val payload = """
            {
              "metas": [
                {
                  "manifestUrl": "https://source.example/addon-a/manifest.json",
                  "name": "Add-on A",
                  "description": "First",
                  "types": ["movie", "series"]
                }
              ]
            }
        """.trimIndent()

        val entries = AddonDirectoryParser.parse(payload, emptySet())

        assertEquals(1, entries.size)
        assertEquals("https://source.example/addon-a/manifest.json", entries[0].manifestUrl)
        assertEquals("Add-on A", entries[0].name)
        assertEquals(listOf("movie", "series"), entries[0].types)
        assertFalse(entries[0].isInstalled)
    }

    @Test
    fun transportUrlGetsTheManifestSuffix() {
        val payload = """{"metas":[{"transportUrl":"https://source.example/addon-b","name":"B"}]}"""

        val entries = AddonDirectoryParser.parse(payload, emptySet())

        assertEquals("https://source.example/addon-b/manifest.json", entries[0].manifestUrl)
    }

    @Test
    fun readsTheEmbeddedManifest() {
        val payload = """
            {
              "addons": [
                {
                  "transportUrl": "https://source.example/c",
                  "manifest": {
                    "name": "Add-on C",
                    "description": "Embedded",
                    "types": ["channel"],
                    "behaviorHints": {"p2p": true, "adult": true}
                  }
                }
              ]
            }
        """.trimIndent()

        val entries = AddonDirectoryParser.parse(payload, emptySet())

        assertEquals("Add-on C", entries[0].name)
        assertEquals(listOf("channel"), entries[0].types)
        assertTrue(entries[0].isP2p)
        assertTrue(entries[0].isAdult)
    }

    @Test
    fun dropsEntriesWithoutAUrlAndDuplicates() {
        val payload = """
            {
              "metas": [
                {"name": "No URL"},
                {"manifestUrl": "https://source.example/d", "name": "D"},
                {"manifestUrl": "https://source.example/d/manifest.json", "name": "D again"}
              ]
            }
        """.trimIndent()

        val entries = AddonDirectoryParser.parse(payload, emptySet())

        assertEquals(1, entries.size)
        assertEquals("https://source.example/d/manifest.json", entries[0].manifestUrl)
    }

    @Test
    fun marksAnInstalledEntry() {
        val payload = """{"metas":[{"manifestUrl":"https://source.example/e","name":"E"}]}"""

        val entries = AddonDirectoryParser.parse(
            payload = payload,
            installedManifestUrls = setOf("https://source.example/e/manifest.json"),
        )

        assertTrue(entries.single().isInstalled)
    }

    @Test
    fun configuredManifestUrlCarriesTheFieldsAsQuery() {
        val url = buildConfiguredManifestUrl(
            manifestUrl = "https://source.example/abc?profile=42",
            values = listOf("sort" to "quality", "limit" to "200"),
        )

        assertEquals("https://source.example/abc/manifest.json?profile=42&sort=quality&limit=200", url)
    }

    @Test
    fun configuredManifestUrlDropsEmptyValuesAndEncodesThem() {
        val url = buildConfiguredManifestUrl(
            manifestUrl = "https://source.example/abc/manifest.json",
            values = listOf("language" to "ar", "empty" to " ", "region" to "sa ud"),
        )

        assertEquals("https://source.example/abc/manifest.json?language=ar&region=sa%20ud", url)
    }

    @Test
    fun configuredManifestUrlReplacesAKeyItAlreadyHad() {
        val url = buildConfiguredManifestUrl(
            manifestUrl = "https://source.example/abc/manifest.json?sort=seeders&profile=42",
            values = listOf("sort" to "quality"),
        )

        assertEquals("https://source.example/abc/manifest.json?profile=42&sort=quality", url)
    }

    @Test
    fun requiredFieldsMustBeFilled() {
        val fields = listOf(
            AddonConfigField(key = "apiKey", required = true),
            AddonConfigField(key = "region", required = false, default = "sa"),
        )

        assertFalse(fields.hasAllRequiredValues(mapOf("region" to "sa")))
        assertTrue(fields.hasAllRequiredValues(mapOf("apiKey" to "x", "region" to "sa")))
    }
}
