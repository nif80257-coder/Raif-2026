package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamTopLinkPerSourceTest {
    private fun stream(url: String, label: String, sourceName: String? = null, addonName: String = "Source"): StreamItem =
        StreamItem(
            name = label,
            url = url,
            sourceName = sourceName,
            addonName = addonName,
            addonId = "addon:$addonName",
        )

    @Test
    fun `every quality of a source stays and only the same quality twice is cut`() {
        val group = AddonStreamGroup(
            addonName = "Nuvio Providers",
            addonId = "plugin-repo:providers",
            streams = listOf(
                stream("https://a.example/1.mkv", label = "2160p", sourceName = "Alpha"),
                stream("https://a.example/2.mkv", label = "1080p", sourceName = "Alpha"),
                stream("https://a.example/3.mkv", label = "1080p", sourceName = "Alpha"),
                stream("https://a.example/4.mkv", label = "720p", sourceName = "Alpha"),
            ),
        )

        assertEquals(
            listOf(
                "https://a.example/1.mkv",
                "https://a.example/2.mkv",
                "https://a.example/4.mkv",
            ),
            listOf(group).showingOneLinkPerQualityPerSource().single().streams.map { it.url },
        )
    }

    @Test
    fun `two sources at the same quality both stay`() {
        val group = AddonStreamGroup(
            addonName = "Nuvio Providers",
            addonId = "plugin-repo:providers",
            streams = listOf(
                stream("https://a.example/1080p.mkv", label = "1080p", sourceName = "Alpha"),
                stream("https://b.example/1080p.mkv", label = "1080p", sourceName = "Beta"),
            ),
        )

        assertEquals(
            listOf("https://a.example/1080p.mkv", "https://b.example/1080p.mkv"),
            listOf(group).showingOneLinkPerQualityPerSource().single().streams.map { it.url },
        )
    }

    @Test
    fun `links without a source name are capped by their add-on and quality`() {
        val first = AddonStreamGroup(
            addonName = "Alpha",
            addonId = "addon:alpha",
            streams = listOf(
                stream("https://a.example/1.mkv", label = "1080p"),
                stream("https://a.example/2.mkv", label = "1080p"),
            ),
        )
        val second = AddonStreamGroup(
            addonName = "Beta",
            addonId = "addon:beta",
            streams = listOf(stream("https://b.example/1.mkv", label = "1080p")),
        )

        val visible = listOf(first, second).showingOneLinkPerQualityPerSource()

        assertEquals(listOf("https://a.example/1.mkv"), visible.first().streams.map { it.url })
        assertEquals(listOf("https://b.example/1.mkv"), visible.last().streams.map { it.url })
    }

    @Test
    fun `a group of one link is returned as it is`() {
        val group = AddonStreamGroup(
            addonName = "Alpha",
            addonId = "addon:alpha",
            streams = listOf(stream("https://a.example/1.mkv", label = "1080p")),
        )

        assertEquals(group, listOf(group).showingOneLinkPerQualityPerSource().single())
    }

    @Test
    fun `the switch is on until it is stored off`() {
        assertTrue(storedSwitchIsOff(null))
        assertTrue(storedSwitchIsOff(""))
        assertTrue(storedSwitchIsOff("1"))
        assertFalse(storedSwitchIsOff("0"))
    }
}
