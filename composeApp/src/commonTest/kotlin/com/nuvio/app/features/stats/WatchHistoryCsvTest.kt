package com.nuvio.app.features.stats

import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchHistoryCsvTest {

    private val labels = WatchHistoryCsvLabels(
        titleColumn = "Title",
        typeColumn = "Type",
        seasonColumn = "Season",
        episodeColumn = "Episode",
        lastWatchedColumn = "Last watched",
        positionColumn = "Position",
        durationColumn = "Duration",
        completedColumn = "Completed",
        movieType = "Movie",
        episodeType = "Episode",
        completedYes = "Yes",
        completedNo = "No",
    )

    private val fixedTimestamp: (Long) -> String = { "2024-05-01" }

    @Test
    fun buildsOneRowPerProgressEntryNewestFirst() {
        val rows = buildWatchHistoryRows(
            progressEntries = listOf(
                movieEntry(
                    id = "m1",
                    title = "Dune",
                    positionMs = 2_000_000L,
                    durationMs = 2_000_000L,
                    updatedAtMs = 100L,
                ),
                episodeEntry(
                    parentId = "show1",
                    videoId = "v1",
                    positionMs = 500_000L,
                    durationMs = 2_000_000L,
                    updatedAtMs = 300L,
                ),
            ),
            watchedItems = emptyList(),
        )

        assertEquals(2, rows.size)
        assertEquals(WatchHistoryContentType.EPISODE, rows[0].contentType)
        assertEquals("Show One", rows[0].title)
        assertEquals(1, rows[0].seasonNumber)
        assertEquals(2, rows[0].episodeNumber)
        assertEquals(false, rows[0].isCompleted)
        assertEquals(WatchHistoryContentType.MOVIE, rows[1].contentType)
        assertEquals("Dune", rows[1].title)
        assertEquals(true, rows[1].isCompleted)
    }

    @Test
    fun mergesWatchedItemsAndSkipsThoseAlreadyInProgress() {
        val rows = buildWatchHistoryRows(
            progressEntries = listOf(
                episodeEntry(
                    parentId = "show1",
                    videoId = "v1",
                    positionMs = 500_000L,
                    durationMs = 2_000_000L,
                    updatedAtMs = 300L,
                ),
            ),
            watchedItems = listOf(
                WatchedItem(
                    id = "show1",
                    type = "series",
                    name = "Show One",
                    season = 1,
                    episode = 2,
                    videoId = "v1",
                    markedAtEpochMs = 400L,
                ),
                WatchedItem(
                    id = "m2",
                    type = "movie",
                    name = "Arrival",
                    markedAtEpochMs = 200L,
                ),
                WatchedItem(
                    id = "show2",
                    type = "series",
                    name = "Whole Show",
                    markedAtEpochMs = 500L,
                ),
            ),
        )

        // The watched item for the episode is folded into its progress row, and the newest row
        // comes first.
        assertEquals(2, rows.size)
        assertEquals("Show One", rows[0].title)
        assertEquals(WatchHistoryContentType.EPISODE, rows[0].contentType)
        assertEquals(false, rows[0].isCompleted)
        assertEquals("Arrival", rows[1].title)
        assertEquals(WatchHistoryContentType.MOVIE, rows[1].contentType)
        assertEquals(true, rows[1].isCompleted)
    }

    @Test
    fun writesHeaderAndEscapesSeparators() {
        val csv = buildWatchHistoryCsv(
            rows = listOf(
                WatchHistoryCsvRow(
                    title = "Title, with comma",
                    contentType = WatchHistoryContentType.MOVIE,
                    seasonNumber = null,
                    episodeNumber = null,
                    lastWatchedEpochMs = 1_000L,
                    positionMs = 65_000L,
                    durationMs = 3_725_000L,
                    isCompleted = true,
                ),
                WatchHistoryCsvRow(
                    title = "He said \"hi\"",
                    contentType = WatchHistoryContentType.EPISODE,
                    seasonNumber = 2,
                    episodeNumber = 5,
                    lastWatchedEpochMs = 2_000L,
                    positionMs = 0L,
                    durationMs = 0L,
                    isCompleted = false,
                ),
            ),
            labels = labels,
            formatTimestamp = fixedTimestamp,
        )

        val lines = csv.split("\n")
        assertEquals(3, lines.size)
        assertEquals(
            "Title,Type,Season,Episode,Last watched,Position,Duration,Completed",
            lines[0],
        )
        assertEquals(
            "\"Title, with comma\",Movie,,,2024-05-01,1:05,1:02:05,Yes",
            lines[1],
        )
        assertEquals(
            "\"He said \"\"hi\"\"\",Episode,2,5,2024-05-01,0:00,0:00,No",
            lines[2],
        )
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:00", formatWatchHistoryDuration(0L))
        assertEquals("1:05", formatWatchHistoryDuration(65_000L))
        assertEquals("1:02:05", formatWatchHistoryDuration(3_725_000L))
    }

    @Test
    fun stampsFileNameWithExportDate() {
        assertEquals("1970-01-01", isoDateFromEpochMs(0L))
        assertEquals("2023-11-14", isoDateFromEpochMs(1_700_000_000_000L))
        assertEquals(
            "raif-watch-history-2023-11-14.csv",
            watchHistoryCsvFileName(1_700_000_000_000L),
        )
    }

    @Test
    fun emptyHistoryProducesNoRows() {
        val rows = buildWatchHistoryRows(
            progressEntries = emptyList(),
            watchedItems = emptyList(),
        )

        assertTrue(rows.isEmpty())
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
        videoId: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtMs: Long,
    ): WatchProgressEntry = WatchProgressEntry(
        contentType = "series",
        parentMetaId = parentId,
        parentMetaType = "series",
        videoId = videoId,
        title = "Show One",
        seasonNumber = 1,
        episodeNumber = 2,
        lastPositionMs = positionMs,
        durationMs = durationMs,
        lastUpdatedEpochMs = updatedAtMs,
    )
}
