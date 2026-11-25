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
    fun dropsASourceServedFromThisDevice() {
        val payload = """
            {
              "metas": [
                {
                  "manifestUrl": "http://127.0.0.1:11470/local-addon/manifest.json",
                  "name": "Local Files"
                },
                {
                  "manifestUrl": "http://localhost:7000/manifest.json",
                  "name": "On this phone"
                },
                {
                  "manifestUrl": "https://source.example/f",
                  "name": "F"
                }
              ]
            }
        """.trimIndent()

        val entries = AddonDirectoryParser.parse(payload, emptySet())

        assertEquals(listOf("https://source.example/f/manifest.json"), entries.map { it.manifestUrl })
    }

    @Test
    fun dropsASourceThatCarriesNoStreams() {
        val payload = """
            {
              "metas": [
                {
                  "manifestUrl": "https://source.example/catalogs-only",
                  "name": "Catalogs only",
                  "manifest": {
                    "name": "Catalogs only",
                    "resources": [
                      {"name": "catalog", "types": ["movie"]},
                      {"name": "meta", "types": ["movie"]}
                    ]
                  }
                },
                {
                  "manifestUrl": "https://source.example/streams",
                  "name": "Streams",
                  "manifest": {
                    "name": "Streams",
                    "resources": [
                      {"name": "catalog", "types": ["movie"]},
                      {"name": "stream", "types": ["movie"], "idPrefixes": ["tt"]}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val entries = AddonDirectoryParser.parse(payload, emptySet())

        assertEquals(listOf("https://source.example/streams/manifest.json"), entries.map { it.manifestUrl })
    }

    @Test
    fun aLocalLinkIsNotOffered() {
        assertTrue("http://127.0.0.1:11470/local-addon/manifest.json".isLocalDeviceUrl())
        assertTrue("http://192.168.1.4:7000/manifest.json".isLocalDeviceUrl())
        assertFalse("https://source.example/manifest.json".isLocalDeviceUrl())
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
    fun directoryRequestUsesTheAddonCatalogResourcePath() {
        val url = buildAddonResourceUrl(
            manifestUrl = "https://source.example/abc/manifest.json",
            resource = "addon_catalog",
            type = "other",
            id = "directory",
        )

        assertEquals("https://source.example/abc/addon_catalog/other/directory.json", url)
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
