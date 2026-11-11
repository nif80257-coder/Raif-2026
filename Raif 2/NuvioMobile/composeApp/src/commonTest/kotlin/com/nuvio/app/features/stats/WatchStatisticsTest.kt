package com.nuvio.app.features.stats

import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchStatisticsTest {

    private val dayMs = 86_400_000L
    private val nowEpochMs = 30L * dayMs

    @Test
    fun emptyHistoryHasNoData() {
        val statistics = buildWatchStatistics(
            progressEntries = emptyList(),
            watchedItems = emptyList(),
            favoritesCount = 0,
            nowEpochMs = nowEpochMs,
        )

        assertFalse(statistics.hasData)
        assertEquals(0L, statistics.totalWatchTimeMs)
        assertTrue(statistics.last7Days.isEmpty())
        assertTrue(statistics.mostWatched.isEmpty())
    }

    @Test
    fun favoritesAloneAreNotEnoughData() {
        val statistics = buildWatchStatistics(
            progressEntries = emptyList(),
            watchedItems = emptyList(),
            favoritesCount = 3,
            nowEpochMs = nowEpochMs,
        )

        assertFalse(statistics.hasData)
        assertEquals(3, statistics.favoritesCount)
    }

    @Test
    fun countsCompletionsWatchTimeAndDailyBuckets() {
        val entries = listOf(
            movieEntry(
                id = "m1",
                title = "Movie One",
                positionMs = 6_000_000L,
                durationMs = 6_000_000L,
                updatedAtMs = nowEpochMs,
            ),
            episodeEntry(
                parentId = "show1",
                title = "Show One",
                videoId = "e1",
                positionMs = 1_500_000L,
                durationMs = 3_000_000L,
                updatedAtMs = nowEpochMs - dayMs,
            ),
            episodeEntry(
                parentId = "show1",
                title = "Show One",
                videoId = "e2",
                positionMs = 3_000_000L,
                durationMs = 3_000_000L,
                updatedAtMs = nowEpochMs - dayMs,
            ),
        )
        val watched = listOf(
            WatchedItem(
                id = "m2",
                type = "movie",
                name = "Movie Two",
                markedAtEpochMs = nowEpochMs - 3L * dayMs,
            ),
        )

        val statistics = buildWatchStatistics(
            progressEntries = entries,
            watchedItems = watched,
            favoritesCount = 1,
            nowEpochMs = nowEpochMs,
        )

        assertTrue(statistics.hasData)
        assertEquals(2, statistics.completedMovies)
        assertEquals(1, statistics.completedEpisodes)
        assertEquals(1, statistics.inProgressCount)
        assertEquals(10_500_000L, statistics.totalWatchTimeMs)
        assertEquals(7, statistics.last7Days.size)
        assertEquals(30, statistics.last30Days.size)
        assertEquals(1, statistics.last7Days.last().itemsCount)
        assertEquals(2, statistics.last7Days[5].itemsCount)
        assertEquals("Show One", statistics.mostWatched.first().title)
        assertEquals(2, statistics.mostWatched.first().count)
    }

    @Test
    fun formatsWatchTimeIntoHoursAndMinutes() {
        val (hours, minutes) = formatWatchTimeParts(10_500_000L)

        assertEquals(2L, hours)
        assertEquals(55L, minutes)
    }

    private fun movieEntry(
        id: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtMs: Long,
    ): WatchProgressEntry = WatchProgressEntry(
        contentType = "movie",
        parentMetaId = id,
        parentMetaType = "movie",
        videoId = id,
        title = title,
        lastPositionMs = positionMs,
        durationMs = durationMs,
        lastUpdatedEpochMs = updatedAtMs,
    )

    private fun episodeEntry(
        parentId: String,
        title: String,
        videoId: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtMs: Long,
    ): WatchProgressEntry = WatchProgressEntry(
        contentType = "series",
        parentMetaId = parentId,
        parentMetaType = "series",
        videoId = videoId,
        title = title,
        seasonNumber = 1,
        episodeNumber = 2,
        lastPositionMs = positionMs,
        durationMs = durationMs,
        lastUpdatedEpochMs = updatedAtMs,
    )
}
