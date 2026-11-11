package com.nuvio.app.features.player

import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerSearchTest {

    private fun stream(
        name: String? = null,
        description: String? = null,
        addonName: String = "Addon",
    ): StreamItem = StreamItem(
        name = name,
        description = description,
        addonName = addonName,
        addonId = "addon:$addonName",
    )

    private fun episode(season: Int, number: Int, title: String): MetaVideo = MetaVideo(
        id = "video-${season}x$number",
        title = title,
        season = season,
        episode = number,
    )

    @Test
    fun blankStreamQueryKeepsEveryStream() {
        val streams = listOf(stream(name = "1080p"), stream(name = "4K"))

        assertEquals(streams, filterPlayerStreams(streams, ""))
        assertEquals(streams, filterPlayerStreams(streams, "   "))
    }

    @Test
    fun streamQueryMatchesNameIgnoringCase() {
        val streams = listOf(stream(name = "1080p H264"), stream(name = "4K DV"))

        assertEquals(listOf(streams[1]), filterPlayerStreams(streams, "4k dv"))
    }

    @Test
    fun streamQueryMatchesQualityInsideDescription() {
        val streams = listOf(
            stream(name = "Release A", description = "2160p • 20 GB"),
            stream(name = "Release B", description = "720p • 1 GB"),
        )

        assertEquals(listOf(streams[0]), filterPlayerStreams(streams, "2160p"))
    }

    @Test
    fun streamQueryMatchesAddonName() {
        val streams = listOf(
            stream(name = "Release A", addonName = "Torrentio"),
            stream(name = "Release B", addonName = "Comet"),
        )

        assertEquals(listOf(streams[1]), filterPlayerStreams(streams, "comet"))
    }

    @Test
    fun blankEpisodeQueryKeepsSeasonEpisodes() {
        val episodes = listOf(episode(1, 1, "Pilot"), episode(1, 2, "Second"))

        assertEquals(episodes, filterSeasonEpisodes(episodes, ""))
    }

    @Test
    fun episodeQueryMatchesTitle() {
        val episodes = listOf(
            episode(1, 1, "Pilot"),
            episode(1, 2, "The Long Night"),
        )

        assertEquals(listOf(episodes[1]), filterSeasonEpisodes(episodes, "long night"))
    }

    @Test
    fun bareNumberMatchesThatEpisodeOnly() {
        val episodes = listOf(
            episode(1, 3, "Three"),
            episode(1, 13, "Thirteen"),
            episode(1, 23, "Twenty three"),
        )

        assertEquals(listOf(episodes[0]), filterSeasonEpisodes(episodes, "3"))
    }

    @Test
    fun episodeCodeQueryMatches() {
        val episodes = listOf(
            episode(2, 4, "Fourth"),
            episode(2, 5, "Fifth"),
        )

        assertEquals(listOf(episodes[0]), filterSeasonEpisodes(episodes, "E4"))
        assertEquals(listOf(episodes[0]), filterSeasonEpisodes(episodes, "s2e4"))
    }

    @Test
    fun unmatchedEpisodeQueryReturnsNothing() {
        val episodes = listOf(episode(1, 1, "Pilot"))

        assertTrue(filterSeasonEpisodes(episodes, "zzz").isEmpty())
    }
}
