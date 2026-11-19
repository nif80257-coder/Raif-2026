package com.nuvio.app.features.addons

import com.nuvio.app.features.streams.StreamSourceTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val StrikeLifetimeMs = 24 * 60 * 60 * 1000L
private const val PauseDurationMs = 6 * 60 * 60 * 1000L
private const val FailuresBeforePause = 3

class AddonFailureMemoryTest {
    @Test
    fun `malformed payloads are ignored`() {
        assertNull(decodeFailureRecord("", nowMs = 0L, strikeLifetimeMs = StrikeLifetimeMs))
        assertNull(decodeFailureRecord("abc|123", nowMs = 0L, strikeLifetimeMs = StrikeLifetimeMs))
        assertNull(decodeFailureRecord("3", nowMs = 0L, strikeLifetimeMs = StrikeLifetimeMs))
        assertNull(decodeFailureRecord("3|nope", nowMs = 0L, strikeLifetimeMs = StrikeLifetimeMs))
    }

    @Test
    fun `a strike is decoded while it is still within its lifetime`() {
        val record = decodeFailureRecord(
            encoded = "2|1000",
            nowMs = 2_000L,
            strikeLifetimeMs = StrikeLifetimeMs,
        )

        assertEquals(2, record?.failures)
        assertEquals(1_000L, record?.lastFailureAtMs)
    }

    @Test
    fun `a strike older than its lifetime is forgotten`() {
        assertNull(
            decodeFailureRecord(
                encoded = "5|0",
                nowMs = StrikeLifetimeMs,
                strikeLifetimeMs = StrikeLifetimeMs,
            ),
        )
    }

    @Test
    fun `an add-on is paused only after repeated failures inside the pause window`() {
        val record = AddonFailureRecord(failures = 3, lastFailureAtMs = 1_000L)

        assertTrue(
            record.isPaused(
                nowMs = 1_000L,
                failuresBeforePause = FailuresBeforePause,
                pauseDurationMs = PauseDurationMs,
            ),
        )
        assertFalse(
            record.isPaused(
                nowMs = 1_000L + PauseDurationMs,
                failuresBeforePause = FailuresBeforePause,
                pauseDurationMs = PauseDurationMs,
            ),
        )
    }

    @Test
    fun `two failures are not enough to pause an add-on`() {
        val record = AddonFailureRecord(failures = 2, lastFailureAtMs = 1_000L)

        assertFalse(
            record.isPaused(
                nowMs = 2_000L,
                failuresBeforePause = FailuresBeforePause,
                pauseDurationMs = PauseDurationMs,
            ),
        )
    }

    @Test
    fun `a stored reason is decoded with the streak`() {
        val record = decodeFailureRecord(
            encoded = "3|1000|server",
            nowMs = 2_000L,
            strikeLifetimeMs = StrikeLifetimeMs,
        )

        assertEquals(3, record?.failures)
        assertEquals(1_000L, record?.lastFailureAtMs)
        assertEquals(AddonFailureReason.ServerError, record?.reason)
    }

    @Test
    fun `a record written before reasons existed decodes as other`() {
        val record = decodeFailureRecord(
            encoded = "2|1000",
            nowMs = 2_000L,
            strikeLifetimeMs = StrikeLifetimeMs,
        )

        assertEquals(2, record?.failures)
        assertEquals(AddonFailureReason.Other, record?.reason)
    }

    @Test
    fun `a refused request says why it was refused`() {
        assertEquals(
            AddonFailureReason.NotFound,
            addonFailureReason(AddonHttpStatusException(404, null, "missing")),
        )
        assertEquals(
            AddonFailureReason.NotFound,
            addonFailureReason(AddonHttpStatusException(410, null, "gone")),
        )
        assertEquals(
            AddonFailureReason.ServerError,
            addonFailureReason(AddonHttpStatusException(503, null, "unavailable")),
        )
        assertEquals(
            AddonFailureReason.ServerError,
            addonFailureReason(AddonHttpStatusException(526, null, "tls")),
        )
        assertEquals(
            AddonFailureReason.Other,
            addonFailureReason(AddonHttpStatusException(403, null, "blocked")),
        )
    }

    @Test
    fun `a request that waited out its timeout is reported as a timeout`() {
        assertEquals(AddonFailureReason.Timeout, addonFailureReason(SocketTimeoutException()))
        // The stream fetch cap aborts a slow source with its own exception; it is a timeout too.
        assertEquals(AddonFailureReason.Timeout, addonFailureReason(StreamSourceTimeoutException()))
        assertEquals(AddonFailureReason.Other, addonFailureReason(IllegalStateException("bad json")))
        assertEquals(AddonFailureReason.Other, addonFailureReason(null))
    }
}

private class SocketTimeoutException : Exception("timed out")
