package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamTopLinkPerSourceTest {
    private fun stream(url: String, sourceName: String? = null, addonName: String = "Source"): StreamItem =
        StreamItem(
            name = url,
            url = url,
            sourceName = sourceName,
            addonName = addonName,
            addonId = "addon:$addonName",
        )

    @Test
    fun `one link per source keeps the top link of every source`() {
        val group = AddonStreamGroup(
            addonName = "Nuvio Providers",
            addonId = "plugin-repo:providers",
            streams = listOf(
                stream("https://a.example/1080p.mkv", sourceName = "Alpha"),
                stream("https://a.example/720p.mkv", sourceName = "Alpha"),
                stream("https://b.example/1080p.mkv", sourceName = "Beta"),
                stream("https://b.example/480p.mkv", sourceName = "Beta"),
            ),
        )

        val visible = listOf(group).showingTopLinksPerSource(1).single().streams

        assertEquals(
            listOf("https://a.example/1080p.mkv", "https://b.example/1080p.mkv"),
            visible.map { it.url },
        )
    }

    @Test
    fun `two links per source keeps the first two of every source`() {
        val group = AddonStreamGroup(
            addonName = "Source",
            addonId = "addon:source",
            streams = listOf(
                stream("https://a.example/1.mkv", sourceName = "Alpha"),
                stream("https://a.example/2.mkv", sourceName = "Alpha"),
                stream("https://a.example/3.mkv", sourceName = "Alpha"),
            ),
        )

        assertEquals(
            listOf("https://a.example/1.mkv", "https://a.example/2.mkv"),
            listOf(group).showingTopLinksPerSource(2).single().streams.map { it.url },
        )
    }

    @Test
    fun `zero keeps every link`() {
        val group = AddonStreamGroup(
            addonName = "Source",
            addonId = "addon:source",
            streams = listOf(
                stream("https://a.example/1.mkv", sourceName = "Alpha"),
                stream("https://a.example/2.mkv", sourceName = "Alpha"),
            ),
        )

        assertEquals(
            group,
            listOf(group).showingTopLinksPerSource(0).single(),
        )
    }

    @Test
    fun `links without a source name are capped by their add-on`() {
        val first = AddonStreamGroup(
            addonName = "Alpha",
            addonId = "addon:alpha",
            streams = listOf(stream("https://a.example/1.mkv"), stream("https://a.example/2.mkv")),
        )
        val second = AddonStreamGroup(
            addonName = "Beta",
            addonId = "addon:beta",
            streams = listOf(stream("https://b.example/1.mkv")),
        )

        val visible = listOf(first, second).showingTopLinksPerSource(1)

        assertEquals(
            listOf("https://a.example/1.mkv"),
            visible.first().streams.map { it.url },
        )
        assertEquals(
            listOf("https://b.example/1.mkv"),
            visible.last().streams.map { it.url },
        )
    }

    @Test
    fun `the switch is on until it is stored off`() {
        assertTrue(storedSwitchIsOff(null))
        assertTrue(storedSwitchIsOff(""))
        assertTrue(storedSwitchIsOff("1"))
        assertFalse(storedSwitchIsOff("0"))
    }
}
