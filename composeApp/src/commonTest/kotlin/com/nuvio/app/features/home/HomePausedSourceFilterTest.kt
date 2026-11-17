package com.nuvio.app.features.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomePausedSourceFilterTest {
    private fun definition(manifestUrl: String, catalogId: String) =
        HomeCatalogDefinition(
            key = "$manifestUrl:$catalogId",
            defaultTitle = catalogId,
            catalogName = catalogId,
            addonName = manifestUrl,
            manifestUrl = manifestUrl,
            type = "movie",
            catalogId = catalogId,
            supportsPagination = false,
            descriptorSignature = "sig",
        )

    private val healthy = definition("https://healthy.example/manifest.json", "top")
    private val deadFirst = definition("https://dead.example/manifest.json", "movies")
    private val deadSecond = definition("https://dead.example/manifest.json", "series")

    @Test
    fun `nothing is dropped when no source is paused`() {
        val definitions = listOf(healthy, deadFirst)

        assertEquals(definitions, definitions.excludingPausedSources(emptySet()))
    }

    @Test
    fun `the catalogs of a paused source are dropped`() {
        val definitions = listOf(healthy, deadFirst, deadSecond)

        assertEquals(
            listOf(healthy),
            definitions.excludingPausedSources(setOf("https://dead.example/manifest.json")),
        )
    }

    @Test
    fun `a filter that would drop everything keeps the whole list`() {
        val definitions = listOf(deadFirst, deadSecond)
        val paused = setOf("https://dead.example/manifest.json")

        assertEquals(definitions, definitions.excludingPausedSources(paused))
    }

    @Test
    fun `an empty list stays empty`() {
        assertEquals(
            emptyList(),
            emptyList<HomeCatalogDefinition>().excludingPausedSources(setOf("https://dead.example/manifest.json")),
        )
    }
}
