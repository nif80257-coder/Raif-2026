package com.nuvio.app.features.watching.application

import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchLogUndoTest {

    @Test
    fun markingAnUnwatchedEpisodeAsksToUnmarkIt() {
        val snapshot = buildWatchLogUndoSnapshot(
            listOf(
                bundle(
                    watchedBefore = null,
                    watchedAfter = episodeItem(markedAtEpochMs = MARKED_AT_MS + 5_000L),
                ),
            ),
        )

        assertTrue(snapshot.watchedRestore.isEmpty())
        assertEquals(listOf(episodeItem()), snapshot.watchedRemove)
        assertTrue(snapshot.progressRestore.isEmpty())
        assertTrue(snapshot.progressClear.isEmpty())
        assertFalse(snapshot.isEmpty)
    }

    @Test
    fun unmarkingWatchedEpisodeRestoresMarkerAndProgress() {
        val item = episodeItem()
        val entry = episodeProgress(isCompleted = true)
        val snapshot = buildWatchLogUndoSnapshot(
            listOf(
                bundle(
                    watchedBefore = item,
                    watchedAfter = null,
                    progressBefore = entry,
                    progressAfter = null,
                ),
            ),
        )

        assertEquals(listOf(item), snapshot.watchedRestore)
        assertEquals(listOf(entry), snapshot.progressRestore)
        assertTrue(snapshot.watchedRemove.isEmpty())
        assertTrue(snapshot.progressClear.isEmpty())
    }

    @Test
    fun clearingProgressWhenMarkingWatchedIsRestored() {
        val entry = episodeProgress(positionMs = 300_000L)
        val snapshot = buildWatchLogUndoSnapshot(
            listOf(
                bundle(
                    watchedBefore = null,
                    watchedAfter = episodeItem(markedAtEpochMs = MARKED_AT_MS + 5_000L),
                    progressBefore = entry,
                    progressAfter = null,
                ),
            ),
        )

        assertEquals(listOf(entry), snapshot.progressRestore)
        assertEquals(listOf(episodeItem()), snapshot.watchedRemove)
    }

    @Test
    fun progressCreatedByTheActionIsClearedOnUndo() {
        val snapshot = buildWatchLogUndoSnapshot(
            listOf(
                bundle(
                    watchedBefore = null,
                    watchedAfter = null,
                    progressBefore = null,
                    progressAfter = episodeProgress(),
                ),
            ),
        )

        assertEquals(
            listOf(WatchLogProgressRef(videoId = VIDEO_ID, parentMetaId = "tt1")),
            snapshot.progressClear,
        )
        assertFalse(snapshot.isEmpty)
    }

    @Test
    fun unchangedStateProducesNothingToUndo() {
        val snapshot = buildWatchLogUndoSnapshot(
            listOf(
                bundle(
                    watchedBefore = episodeItem(),
                    watchedAfter = episodeItem(),
                    progressBefore = episodeProgress(),
                    progressAfter = episodeProgress(),
                ),
            ),
        )

        assertTrue(snapshot.isEmpty)
    }

    @Test
    fun untouchedNeighbourEpisodeStaysOutOfTheSnapshot() {
        val other = WatchedItem(
            id = "tt1",
            type = "series",
            name = "Second",
            season = 1,
            episode = 2,
            markedAtEpochMs = MARKED_AT_MS,
        )
        val snapshot = buildWatchLogUndoSnapshot(
            listOf(
                bundle(
                    watchedBefore = episodeItem(),
                    watchedAfter = null,
                ),
                bundle(
                    item = other,
                    watchedBefore = other,
                    watchedAfter = other,
                ),
            ),
        )

        assertEquals(listOf(episodeItem()), snapshot.watchedRestore)
        assertTrue(snapshot.watchedRemove.isEmpty())
    }

    private fun bundle(
        item: WatchedItem = episodeItem(),
        watchedBefore: WatchedItem?,
        watchedAfter: WatchedItem?,
        progressBefore: WatchProgressEntry? = null,
        progressAfter: WatchProgressEntry? = null,
    ): WatchLogUndoBundle = WatchLogUndoBundle(
        target = WatchLogUndoTarget(
            watchedItem = item,
            videoId = VIDEO_ID,
            parentMetaId = item.id,
        ),
        watchedBefore = watchedBefore,
        watchedAfter = watchedAfter,
        progressBefore = progressBefore,
        progressAfter = progressAfter,
    )

    private fun episodeItem(markedAtEpochMs: Long = MARKED_AT_MS): WatchedItem = WatchedItem(
        id = "tt1",
        type = "series",
        name = "Pilot",
        season = 1,
        episode = 1,
        markedAtEpochMs = markedAtEpochMs,
    )

    private fun episodeProgress(
        positionMs: Long = 600_000L,
        isCompleted: Boolean = false,
    ): WatchProgressEntry = WatchProgressEntry(
        contentType = "series",
        parentMetaId = "tt1",
        parentMetaType = "series",
        videoId = VIDEO_ID,
        title = "Show",
        seasonNumber = 1,
        episodeNumber = 1,
        lastPositionMs = positionMs,
        durationMs = 1_200_000L,
        lastUpdatedEpochMs = MARKED_AT_MS,
        isCompleted = isCompleted,
    )

    private companion object {
        private const val MARKED_AT_MS = 1_700_000_000_000L
        private const val VIDEO_ID = "tt1:1:1"
    }
}
