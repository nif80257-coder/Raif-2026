package com.cinemax.app.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TtlCacheTest {

    @Test
    fun servesTheValueUntilItExpires() {
        var now = 1_000L
        val cache = TtlCache<String>(ttlMs = 5_000L, now = { now })
        cache.put("k", "v")

        assertEquals("v", cache.get("k"))
        now += 5_001L
        assertNull(cache.get("k"))
    }

    @Test
    fun missingKeysAreNullAndClearEmptiesEverything() {
        var now = 0L
        val cache = TtlCache<String>(ttlMs = 1_000L, now = { now })
        assertNull(cache.get("absent"))

        cache.put("a", "1")
        cache.put("b", "2")
        cache.clear()

        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun theMapStaysBounded() {
        var now = 0L
        val cache = TtlCache<Int>(ttlMs = 60_000L, now = { now })
        repeat(100) { cache.put("k$it", it) }

        assertEquals(99, cache.get("k99"))
        assertNull(cache.get("k0"))
    }
}
