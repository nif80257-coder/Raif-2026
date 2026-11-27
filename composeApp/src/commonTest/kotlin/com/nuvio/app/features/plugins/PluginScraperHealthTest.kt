package com.nuvio.app.features.plugins

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginScraperHealthTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `one or two failures do not rest a provider`() {
        assertFalse(isScraperRested(consecutiveFailures = 0, lastFailureAtMs = now, nowMs = now))
        assertFalse(isScraperRested(consecutiveFailures = 1, lastFailureAtMs = now, nowMs = now))
        assertFalse(isScraperRested(consecutiveFailures = 2, lastFailureAtMs = now, nowMs = now))
    }

    @Test
    fun `three failures rest it for the window`() {
        assertTrue(isScraperRested(3, lastFailureAtMs = now - 60_000L, nowMs = now))
        assertTrue(isScraperRested(5, lastFailureAtMs = now, nowMs = now))
    }

    @Test
    fun `the rest ends when the window passes`() {
        assertTrue(isScraperRested(3, lastFailureAtMs = now - ScraperRestMs, nowMs = now))
        assertFalse(isScraperRested(3, lastFailureAtMs = now - ScraperRestMs - 1L, nowMs = now))
    }

    @Test
    fun `a missing timestamp or a clock that moved backwards never rests a provider`() {
        assertFalse(isScraperRested(3, lastFailureAtMs = 0L, nowMs = now))
        assertFalse(isScraperRested(3, lastFailureAtMs = now + 5_000L, nowMs = now))
    }
}
