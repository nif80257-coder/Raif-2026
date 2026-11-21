package com.nuvio.app.features.search

import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonManifestParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A search may only ask a catalogue that declares it takes a query, so the three shapes manifests
 * write that declaration in all have to end up searchable — otherwise the search screen answers
 * from a handful of sources and looks like it only searches them.
 */
class SearchCatalogSupportTest {
    private fun catalogs(vararg catalog: String): List<com.nuvio.app.features.addons.AddonCatalog> {
        val manifest: AddonManifest = AddonManifestParser.parse(
            manifestUrl = "https://example.com/manifest.json",
            payload = """
                {
                  "id": "org.example.search",
                  "version": "1.0.0",
                  "name": "Example",
                  "resources": ["catalog"],
                  "types": ["movie"],
                  "catalogs": [${catalog.joinToString(",")}]
                }
            """.trimIndent(),
        )
        return manifest.catalogs
    }

    @Test
    fun `the current object form of extra marks a catalogue searchable`() {
        val catalog = catalogs(
            """{"type":"movie","id":"top","name":"Top","extra":[{"name":"search","isRequired":false}]}""",
        ).single()

        assertTrue(catalog.supportsSearch())
    }

    @Test
    fun `extra written as plain names marks a catalogue searchable`() {
        val catalog = catalogs(
            """{"type":"movie","id":"top","name":"Top","extra":["search"]}""",
        ).single()

        assertTrue(catalog.supportsSearch())
    }

    @Test
    fun `extraSupported without an extra array marks a catalogue searchable`() {
        val catalog = catalogs(
            """{"type":"movie","id":"top","name":"Top","extraSupported":["search","skip"]}""",
        ).single()

        assertTrue(catalog.supportsSearch())
    }

    @Test
    fun `a capitalised search name is still the search extra`() {
        val catalog = catalogs(
            """{"type":"movie","id":"top","name":"Top","extra":[{"name":"Search"}]}""",
        ).single()

        assertTrue(catalog.supportsSearch())
    }

    @Test
    fun `a catalogue taking nothing extra is not searched`() {
        val catalog = catalogs(
            """{"type":"movie","id":"top","name":"Top"}""",
        ).single()

        assertFalse(catalog.supportsSearch())
    }

    @Test
    fun `a catalogue that needs a genre before answering is not searched`() {
        val catalog = catalogs(
            """{"type":"movie","id":"top","name":"Top","extra":[{"name":"search"},{"name":"genre","isRequired":true}]}""",
        ).single()

        assertFalse(catalog.supportsSearch())
    }

    @Test
    fun `every catalogue of a manifest declares itself so the search covers all of them`() {
        val found = catalogs(
            """{"type":"movie","id":"top","name":"Top","extra":["search"]}""",
            """{"type":"series","id":"top","name":"Top","extraSupported":["search"]}""",
            """{"type":"movie","id":"year","name":"By year","extra":[{"name":"search"}]}""",
        ).filter { it.supportsSearch() }

        assertEquals(listOf("top", "top", "year"), found.map { it.id })
        assertEquals(listOf("movie", "series", "movie"), found.map { it.type })
    }
}
