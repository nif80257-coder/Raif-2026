package com.cinemax.app

import com.cinemax.app.addons.StremioMetaPreview
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchProgressStoreTest {
    @Test
    fun progressPersistsAndIsSortedByMostRecent() {
        val settings = MapSettings()
        val store = WatchProgressStore(settings)
        val first = WatchProgress(
            StremioMetaPreview("tt001", "movie", "First"), 20_000L, 100_000L, 10L
        )
        val second = WatchProgress(
            StremioMetaPreview("tt002", "series", "Second"), 30_000L, 200_000L, 20L
        )

        store.save(first)
        store.save(second)

        val restored = WatchProgressStore(settings).load()
        assertEquals(listOf("tt002", "tt001"), restored.map { it.item.id })
        assertEquals(0.15f, restored.first().progressFraction)
    }

    @Test
    fun completedItemsAreRemovedFromContinueWatching() {
        val store = WatchProgressStore(MapSettings())
        val item = StremioMetaPreview("tt003", "movie", "Finished")
        store.save(WatchProgress(item, 90_000L, 100_000L, 10L))

        assertTrue(store.load().isEmpty())
    }

    @Test
    fun invalidStoredDataFallsBackToEmptyProgress() {
        val settings = MapSettings()
        settings.putString("cinemax.watch.progress.v1", "not-json")

        assertTrue(WatchProgressStore(settings).load().isEmpty())
    }

    @Test
    fun episodesOfTheSameSeriesAreTrackedSeparately() {
        val settings = MapSettings()
        val store = WatchProgressStore(settings)
        val series = StremioMetaPreview("tt100", "series", "Example Series")

        store.save(
            WatchProgress(
                series, 10_000L, 100_000L, 1L,
                videoId = "tt100:1:1", videoLabel = "الموسم 1 • الحلقة 1"
            )
        )
        store.save(
            WatchProgress(
                series, 20_000L, 100_000L, 2L,
                videoId = "tt100:1:2", videoLabel = "الموسم 1 • الحلقة 2"
            )
        )

        val restored = WatchProgressStore(settings).load()
        assertEquals(listOf("tt100:1:2", "tt100:1:1"), restored.map { it.key })

        store.remove("tt100:1:2")
        assertEquals(listOf("tt100:1:1"), WatchProgressStore(settings).load().map { it.key })
    }
}
