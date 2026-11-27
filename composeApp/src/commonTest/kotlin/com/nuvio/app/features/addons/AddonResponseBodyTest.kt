package com.nuvio.app.features.addons

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonResponseBodyTest {
    @Test
    fun `response reader truncates without buffering past limit`() = runTest {
        val response = readLimitedAddonResponseBody(ByteReadChannel("abcdef"), maxBytes = 3)

        assertContentEquals("abc".encodeToByteArray(), response.bytes)
        assertTrue(response.truncated)
    }

    @Test
    fun `response exactly at limit is not truncated`() = runTest {
        val response = readLimitedAddonResponseBody(ByteReadChannel("abc"), maxBytes = 3)

        assertContentEquals("abc".encodeToByteArray(), response.bytes)
        assertFalse(response.truncated)
    }

    @Test
    fun `zero byte limit still detects a nonempty response`() = runTest {
        val response = readLimitedAddonResponseBody(ByteReadChannel("a"), maxBytes = 0)

        assertContentEquals(byteArrayOf(), response.bytes)
        assertTrue(response.truncated)
    }
}
