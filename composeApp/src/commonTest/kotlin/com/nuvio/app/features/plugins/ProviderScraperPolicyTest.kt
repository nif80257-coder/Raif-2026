package com.nuvio.app.features.plugins

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderScraperPolicyTest {

    @Test
    fun `the anime scrapers in the bundled repository are excluded`() {
        listOf("animepahe", "hianime", "kurage", "anizone", "vidnest-anime").forEach { id ->
            assertTrue(isExcludedProviderScraper(id), id)
        }
    }

    @Test
    fun `a stored id with the repository prefix is still recognised`() {
        assertTrue(
            isExcludedProviderScraper(
                "https://raw.githubusercontent.com/tapframe/nuvio-providers/main/manifest.json:hianime",
            ),
        )
    }

    @Test
    fun `the film and series scrapers are kept`() {
        listOf("4khdhub", "uhdmovies", "moviebox", "vixsrc", "hdhub4u", "mycima", "castle").forEach { id ->
            assertFalse(isExcludedProviderScraper(id), id)
        }
    }

    @Test
    fun `a scraper added upstream that calls itself anime is excluded`() {
        assertTrue(isExcludedProviderScraper("animeworld-2"))
        assertFalse(isExcludedProviderScraper("showbox"))
    }
}
