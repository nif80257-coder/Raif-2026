package com.cinemax.app

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The per-link record the iOS sources screen now ranks and writes through
 * `AppleBridge`: a failure counts, a success heals it, a broken entry is ignored
 * and clearing forgets every link.
 */
class SourceStatsStoreTest {

    @Test
    fun aFailureIsCountedAndClearedByASuccess() {
        val store = SourceStatsStore(MapSettings())
        store.recordFailure("https://host/a.mkv")
        store.recordFailure("https://host/a.mkv")
        assertEquals(2, store.load("https://host/a.mkv")?.failures)

        store.recordSuccess("https://host/a.mkv", 1_200L)
        val healed = store.load("https://host/a.mkv")
        assertNotNull(healed)
        assertEquals(0, healed.failures)
        assertEquals(1_200L, healed.timeToFirstFrameMs)
        assertTrue(healed.hasPlayed)
    }

    @Test
    fun aSuccessWithoutAFrameKeepsTheOldOne() {
        val store = SourceStatsStore(MapSettings())
        store.recordSuccess("https://host/b.mkv", 900L)
        store.recordSuccess("https://host/b.mkv", null)
        assertEquals(900L, store.load("https://host/b.mkv")?.timeToFirstFrameMs)
    }

    @Test
    fun aBrokenEntryIsIgnoredInsteadOfCrashing() {
        val settings = MapSettings()
        settings.putString("cinemax.stats.source.broken", "not|a|record")
        assertEquals(emptyMap(), SourceStatsStore(settings).loadAll())
    }

    @Test
    fun clearingForgetsEveryLink() {
        val store = SourceStatsStore(MapSettings())
        store.recordFailure("https://host/a.mkv")
        store.recordSuccess("https://host/c.mkv", 400L)
        store.clear()
        assertEquals(emptyMap(), store.loadAll())
    }
}
