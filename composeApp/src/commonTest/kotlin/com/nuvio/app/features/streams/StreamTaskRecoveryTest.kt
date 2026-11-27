package com.nuvio.app.features.streams

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StreamTaskRecoveryTest {
    @Test
    fun `unexpected source failure resolves through fallback`() = runTest {
        val result = runSourceTaskOrFallback(
            block = { throw IllegalStateException("internal detail") },
            fallback = { "Source unavailable" },
        )

        assertEquals("Source unavailable", result)
    }

    @Test
    fun `source cancellation is not converted into a visible error`() = runTest {
        assertFailsWith<CancellationException> {
            runSourceTaskOrFallback(
                block = { throw CancellationException("cancelled") },
                fallback = { "Source unavailable" },
            )
        }
    }
}
