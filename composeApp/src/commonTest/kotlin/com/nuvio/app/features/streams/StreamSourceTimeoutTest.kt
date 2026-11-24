package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamSourceTimeoutTest {

    @Test
    fun `a source that was never measured keeps the whole budget`() {
        assertEquals(15_000L, streamSourceTimeoutMs(null))
    }

    @Test
    fun `a source measured under a second gets the shortest time`() {
        assertEquals(6_000L, streamSourceTimeoutMs(500L))
    }

    @Test
    fun `a source measured at a second gets nine seconds`() {
        assertEquals(9_000L, streamSourceTimeoutMs(1_000L))
    }

    @Test
    fun `a source measured at two seconds keeps the whole budget`() {
        assertEquals(15_000L, streamSourceTimeoutMs(2_000L))
    }

    @Test
    fun `a recorded timeout does not raise the budget above the cap`() {
        assertEquals(15_000L, streamSourceTimeoutMs(15_000L))
    }

    @Test
    fun `a missing or unusable measurement falls back to the whole budget`() {
        assertEquals(15_000L, streamSourceTimeoutMs(0L))
        assertEquals(15_000L, streamSourceTimeoutMs(-1L))
    }
}
