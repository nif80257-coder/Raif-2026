package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamPayloadReuseTest {
    @Test
    fun `a payload is reusable only inside the window`() {
        val window = 2 * 60 * 1000L
        val now = 1_800_000_000_000L

        assertTrue(StreamResponseCache.isFreshPayload(now, now, window))
        assertTrue(StreamResponseCache.isFreshPayload(now - 30_000L, now, window))
        assertTrue(StreamResponseCache.isFreshPayload(now - window, now, window))

        assertFalse(StreamResponseCache.isFreshPayload(now - window - 1L, now, window))
        assertFalse(StreamResponseCache.isFreshPayload(now - 10 * 60 * 1000L, now, window))
        // A clock that moved backwards must not make an answer look fresh.
        assertFalse(StreamResponseCache.isFreshPayload(now + 5_000L, now, window))
    }

    @Test
    fun `a missing or disabled timestamp is never reused`() {
        val now = 1_800_000_000_000L
        val window = 2 * 60 * 1000L

        assertFalse(StreamResponseCache.isFreshPayload(null, now, window))
        assertFalse(StreamResponseCache.isFreshPayload(0L, now, window))
        assertFalse(StreamResponseCache.isFreshPayload(now, now, 0L))
        assertFalse(StreamResponseCache.isFreshPayload(now, now, -1L))
    }
}
