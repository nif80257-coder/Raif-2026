package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestedSourcesTest {

    private val first = """
        {"version":1,"sources":[
          {"type":"addon","name":"Torrentio","url":"https://torrentio.strem.fun/x/manifest.json","enabled":true,"order":1},
          {"type":"addon","name":"WatchHub","url":"https://watchhub.strem.io/manifest.json","enabled":true,"order":2}
        ]}
    """.trimIndent()

    private val second = """
        {"sources":[
          {"name":"Torrentio","url":"https://torrentio.strem.fun/x/manifest.json/"},
          {"name":" ArabCity ","url":" https://arabcity.fly.dev/YWxs/manifest.json "},
          {"name":"No url"}
        ]}
    """.trimIndent()

    @Test
    fun parsesSourcesAndDropsEntriesWithoutUrl() {
        assertEquals(2, parseSuggestedSources(first).size)
        assertEquals(2, parseSuggestedSources(second).size)
        assertEquals(emptyList(), parseSuggestedSources("not json"))
    }

    @Test
    fun namesAreTrimmedAndFallBackToTheUrl() {
        val parsed = parseSuggestedSources(second)
        assertEquals("ArabCity", parsed.first { it.name.contains("ArabCity") }.name)
        assertEquals("https://arabcity.fly.dev/YWxs/manifest.json", parsed.first { it.name == "ArabCity" }.url)
    }

    @Test
    fun dedupeKeepsOneEntryPerUrlIgnoringTrailingSlashAndCase() {
        val merged = dedupeSources(listOf(parseSuggestedSources(first), parseSuggestedSources(second)))
        assertEquals(3, merged.size)
        assertEquals(listOf("ArabCity", "Torrentio", "WatchHub"), merged.map { it.name })
    }

    @Test
    fun mergedListIsSortedByName() {
        val merged = dedupeSources(listOf(parseSuggestedSources(first), parseSuggestedSources(second)))
        assertTrue(merged.map { it.name } == merged.map { it.name }.sortedBy { it.lowercase() })
    }
}
