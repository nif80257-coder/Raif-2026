package com.nuvio.app.features.addons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class HttpRequestTimeoutException : IllegalStateException("timed out")

class AddonHttpRetryTest {
    @Test
    fun `transient statuses are retried and client errors are final`() {
        assertTrue(isRetryableAddonHttpStatus(429))
        assertTrue(isRetryableAddonHttpStatus(503))
        assertTrue(isRetryableAddonHttpStatus(500))
        assertFalse(isRetryableAddonHttpStatus(404))
        assertFalse(isRetryableAddonHttpStatus(401))
    }

    @Test
    fun `timeouts and cancellation are not retried`() {
        assertFalse(isRetryableAddonFetchFailure(HttpRequestTimeoutException()))
        assertFalse(isRetryableAddonFetchFailure(CancellationException("cancelled")))
        assertTrue(isRetryableAddonFetchFailure(IllegalStateException("connection reset")))
    }

    @Test
    fun `a transient failure is retried until the request succeeds`() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        val result = retryAddonFetch(
            pause = { waits += it },
            fetch = {
                calls += 1
                if (calls < 3) {
                    throw AddonHttpStatusException(statusCode = 503, retryAfterHeader = null, message = "busy")
                }
                "manifest"
            },
        )

        assertEquals("manifest", result)
        assertEquals(3, calls)
        assertEquals(2, waits.size)
    }

    @Test
    fun `a client error is not retried`() = runBlocking {
        var calls = 0
        assertFailsWith<AddonHttpStatusException> {
            retryAddonFetch(
                pause = { },
                fetch = {
                    calls += 1
                    throw AddonHttpStatusException(statusCode = 404, retryAfterHeader = null, message = "missing")
                },
            )
        }
        assertEquals(1, calls)
    }

    @Test
    fun `gives up after the maximum number of attempts`() = runBlocking {
        var calls = 0
        assertFailsWith<AddonHttpStatusException> {
            retryAddonFetch(
                maxAttempts = 3,
                pause = { },
                fetch = {
                    calls += 1
                    throw AddonHttpStatusException(statusCode = 429, retryAfterHeader = null, message = "slow down")
                },
            )
        }
        assertEquals(3, calls)
    }

    @Test
    fun `retry after header is honoured and the wait stays bounded`() = runBlocking {
        val waits = mutableListOf<Long>()
        var calls = 0
        retryAddonFetch(
            pause = { waits += it },
            fetch = {
                calls += 1
                if (calls == 1) {
                    throw AddonHttpStatusException(statusCode = 429, retryAfterHeader = "2", message = "slow down")
                }
                "ok"
            },
        )

        // Retry-After of 2 seconds plus at most one second of jitter.
        assertTrue(waits.single() in 2_000L..3_000L)
    }
}
