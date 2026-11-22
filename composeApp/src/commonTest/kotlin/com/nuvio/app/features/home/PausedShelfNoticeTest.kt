package com.nuvio.app.features.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PausedShelfNoticeTest {
    private fun shelf(
        manifestUrl: String,
        addonName: String,
        catalogId: String = "top",
    ): HomeCatalogDefinition = HomeCatalogDefinition(
        key = "$manifestUrl:$catalogId",
        defaultTitle = "Title",
        catalogName = "Catalog",
        addonName = addonName,
        manifestUrl = manifestUrl,
        type = "movie",
        catalogId = catalogId,
        supportsPagination = false,
        descriptorSignature = "signature",
    )

    @Test
    fun nothingPausedMeansNoNotice() {
        val definitions = listOf(shelf("https://a/manifest.json", "A"))

        assertNull(
            pausedShelfNotice(
                definitions = definitions,
                pausedManifestUrls = emptySet(),
                fetchedDefinitions = definitions,
            ),
        )
    }

    @Test
    fun namesTheSourceThatLostTheMostShelves() {
        val firstShelves = listOf(
            shelf("https://a/manifest.json", "Alpha", "one"),
            shelf("https://a/manifest.json", "Alpha", "two"),
        )
        val otherShelf = shelf("https://b/manifest.json", "Beta", "three")
        val definitions = firstShelves + otherShelf

        val notice = pausedShelfNotice(
            definitions = definitions,
            pausedManifestUrls = setOf("https://a/manifest.json"),
            fetchedDefinitions = listOf(otherShelf),
        )

        assertEquals("https://a/manifest.json", notice?.manifestUrl)
        assertEquals("Alpha", notice?.sourceName)
        assertEquals(2, notice?.hiddenShelfCount)
    }

    @Test
    fun saysNothingWhenTheFilterKeptEveryShelf() {
        // The pause filter puts the whole list back rather than leave the screen empty, so the
        // shelves are still on screen and a notice would be untrue.
        val definitions = listOf(shelf("https://a/manifest.json", "Alpha"))

        assertNull(
            pausedShelfNotice(
                definitions = definitions,
                pausedManifestUrls = setOf("https://a/manifest.json"),
                fetchedDefinitions = definitions,
            ),
        )
    }

    @Test
    fun aPausedSourceWithNoShelvesIsNotMentioned() {
        val definitions = listOf(
            shelf("https://a/manifest.json", "Alpha"),
            shelf("https://b/manifest.json", "Beta"),
        )

        assertNull(
            pausedShelfNotice(
                definitions = definitions,
                pausedManifestUrls = setOf("https://c/manifest.json"),
                fetchedDefinitions = definitions,
            ),
        )
    }

    @Test
    fun tiesGoToTheNameSoTheSameSituationAlwaysNamesOneSource() {
        val alpha = shelf("https://a/manifest.json", "Alpha")
        val beta = shelf("https://b/manifest.json", "Beta")

        val notice = pausedShelfNotice(
            definitions = listOf(beta, alpha),
            pausedManifestUrls = setOf("https://b/manifest.json", "https://a/manifest.json"),
            fetchedDefinitions = emptyList(),
        )

        assertEquals("Alpha", notice?.sourceName)
    }

    @Test
    fun fallsBackToTheHostWhenTheSourceHasNoName() {
        val anonymous = shelf("https://www.example.org/manifest.json", "")
        val named = shelf("https://b/manifest.json", "Beta", "other")

        val notice = pausedShelfNotice(
            definitions = listOf(anonymous, named),
            pausedManifestUrls = setOf("https://www.example.org/manifest.json"),
            fetchedDefinitions = listOf(named),
        )

        assertEquals("example.org", notice?.sourceName)
    }
}
