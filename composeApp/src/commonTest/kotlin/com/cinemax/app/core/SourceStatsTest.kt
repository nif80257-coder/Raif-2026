package com.cinemax.app.core

import com.cinemax.app.addons.StremioStream
import com.cinemax.app.addons.StreamOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceStatsTest {

    private val hour = 3_600_000L
    private val now = 1_000_000_000L

    private fun option(url: String, name: String = "2160p 4K") = StreamOption(
        addonId = "addon",
        addonName = "Addon",
        stream = StremioStream(name = name, url = url)
    )

    @Test
    fun aRecentFailureIsPenalised() {
        val stats = SourceStats(lastFailureAtEpochMillis = now - hour)
        assertTrue(stats.penalty(now) < -100)
    }

    @Test
    fun anOldFailureFadesAway() {
        val stats = SourceStats(lastFailureAtEpochMillis = now - 5L * 24L * hour)
        assertEquals(0, stats.penalty(now))
    }

    @Test
    fun aPreviousSuccessRaisesTheLink() {
        val stats = SourceStats(lastSuccessAtEpochMillis = now - hour, timeToFirstFrameMs = 800L)
        assertTrue(stats.penalty(now) > 0)
    }

    @Test
    fun rankSourcesPutsARecentlyFailedLinkBelowAWorkingOne() {
        val dead = option("https://cdn.example.com/dead.mp4")
        val good = option("https://cdn.example.com/good.mp4")
        val stats = mapOf(streamFingerprint(dead) to SourceStats(lastFailureAtEpochMillis = now - hour))
        val ranked = rankSources(listOf(dead, good), stats = stats, nowEpochMillis = now)
        assertEquals(good.stream.url, ranked.first().stream.url)
    }

    @Test
    fun theLastPlayedLinkStaysOnTopEvenAfterAFailure() {
        val played = option("https://cdn.example.com/played.mp4", name = "1080p")
        val other = option("https://cdn.example.com/other.mp4")
        val stats = mapOf(streamFingerprint(played) to SourceStats(lastFailureAtEpochMillis = now - hour))
        val ranked = rankSources(
            listOf(other, played),
            stats = stats,
            lastPlayedUrl = played.stream.url,
            nowEpochMillis = now
        )
        assertEquals(played.stream.url, ranked.first().stream.url)
    }
}
