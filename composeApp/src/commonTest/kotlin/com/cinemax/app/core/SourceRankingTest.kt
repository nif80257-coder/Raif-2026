package com.cinemax.app.core

import com.cinemax.app.addons.StreamOption
import com.cinemax.app.addons.StremioStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceRankingTest {

    private fun option(name: String, url: String? = "https://cdn.example.com/file.mp4") =
        StreamOption("addon", "Addon", StremioStream(name = name, url = url))

    @Test
    fun directLinkOutranksTorrentOnlyEntry() {
        val direct = option("1080p")
        val torrent = option("2160p REMUX HDR", url = null)
        assertTrue(sourceScore(direct) > sourceScore(torrent))
    }

    @Test
    fun betterResolutionComesFirst() {
        val ranks = rankedSources(listOf(option("720p"), option("2160p 4K"), option("1080p")))
        assertEquals(listOf("2160p 4K", "1080p", "720p"), ranks.map { it.stream.name })
    }

    @Test
    fun cameraReleaseIsPenalised() {
        assertTrue(sourceScore(option("1080p CAM")) < sourceScore(option("1080p WEB-DL")))
    }

    @Test
    fun lastPlayedLinkIsPinnedToTheTop() {
        val first = option("1080p")
        val second = option("2160p 4K")
        val ranks = rankedSources(listOf(first, second), lastPlayedUrl = first.stream.url)
        assertEquals(first.stream.url, ranks.first().stream.url)
    }

    @Test
    fun onlyDirectFiltersTorrentOnlySources() {
        val ranks = rankedSources(
            listOf(option("1080p"), option("2160p", url = null)),
            onlyDirect = true
        )
        assertEquals(1, ranks.size)
        assertTrue(ranks.all { it.stream.url != null })
    }
}
