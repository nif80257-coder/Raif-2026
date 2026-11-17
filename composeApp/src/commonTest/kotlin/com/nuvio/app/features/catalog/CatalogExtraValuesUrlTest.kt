package com.nuvio.app.features.catalog

import com.nuvio.app.features.addons.AddonExtraProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogExtraValuesUrlTest {

    @Test
    fun buildCatalogUrlAppendsSavedExtraValuesEncoded() {
        val url = buildCatalogUrl(
            manifestUrl = "https://example.com/manifest.json",
            type = "movie",
            catalogId = "top",
            genre = "action",
            search = null,
            skip = 100,
            extraValues = mapOf("language" to "arabic", "region" to "us east"),
        )

        assertTrue(url.contains("genre=action"))
        assertTrue(url.contains("skip=100"))
        assertTrue(url.contains("language=arabic"))
        assertTrue(url.contains("region=us%20east"))
    }

    @Test
    fun buildCatalogUrlDoesNotLetExtrasOverrideCoreParameters() {
        val url = buildCatalogUrl(
            manifestUrl = "https://example.com/manifest.json",
            type = "movie",
            catalogId = "top",
            genre = "action",
            search = null,
            skip = null,
            extraValues = mapOf(
                "genre" to "thriller",
                "search" to "batman",
                "skip" to "999",
                "Genre" to "western",
                " valid" to "yes",
            ),
        )

        assertTrue(url.contains("genre=action"))
        assertFalse(url.contains("thriller"))
        assertFalse(url.contains("batman"))
        assertFalse(url.contains("skip=999"))
        assertTrue(url.contains("valid=yes"))
    }

    @Test
    fun buildCatalogUrlWithoutExtrasKeepsShape() {
        val plain = buildCatalogUrl(
            manifestUrl = "https://example.com/manifest.json",
            type = "series",
            catalogId = "top",
            genre = null,
            search = null,
            skip = null,
        )
        val withEmptyExtras = buildCatalogUrl(
            manifestUrl = "https://example.com/manifest.json",
            type = "series",
            catalogId = "top",
            genre = null,
            search = null,
            skip = null,
            extraValues = emptyMap(),
        )

        assertEquals(plain, withEmptyExtras)
    }

    @Test
    fun supportsPaginationStillUsesSkipExtra() {
        val catalog = com.nuvio.app.features.addons.AddonCatalog(
            type = "movie",
            id = "top",
            name = "Top",
            extra = listOf(AddonExtraProperty(name = "skip")),
        )

        assertTrue(catalog.supportsPagination())
    }
}
