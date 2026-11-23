package com.nuvio.app.features.streams

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamLinkExpiryTest {

    private val nowMs = 1_700_000_000_000L

    @Test
    fun expirationInSecondsIsRead() {
        val url = "https://cdn.example.com/movie.mkv?token=abc&exp=1700003600"

        assertEquals(1_700_003_600_000L, StreamLinkExpiry.expiresAtMs(url, nowMs))
    }

    @Test
    fun expirationInMillisecondsIsKeptAsIs() {
        val url = "https://cdn.example.com/movie.mkv?expires=1700003600000"

        assertEquals(1_700_003_600_000L, StreamLinkExpiry.expiresAtMs(url, nowMs))
    }

    @Test
    fun relativeExpirationCountsFromNow() {
        val url = "https://cdn.example.com/movie.mkv?expires_in=3600"

        assertEquals(nowMs + 3_600_000L, StreamLinkExpiry.expiresAtMs(url, nowMs))
    }

    @Test
    fun linkWithoutQueryHasNoExpirationAndStaysReusable() {
        val url = "https://cdn.example.com/movie.mkv"

        assertNull(StreamLinkExpiry.expiresAtMs(url, nowMs))
        assertTrue(StreamLinkExpiry.isReusable(url, nowMs))
    }

    @Test
    fun linkWithUnknownCredentialsIsNotReusable() {
        val url = "https://cdn.example.com/movie.mkv?sig=abcdef"

        assertNull(StreamLinkExpiry.expiresAtMs(url, nowMs))
        assertFalse(StreamLinkExpiry.isReusable(url, nowMs))
    }

    @Test
    fun unreadableExpirationFallsBackToCredentialTest() {
        val url = "https://cdn.example.com/movie.mkv?exp=not-a-number"

        assertNull(StreamLinkExpiry.expiresAtMs(url, nowMs))
        assertFalse(StreamLinkExpiry.isReusable(url, nowMs))
    }

    @Test
    fun linkExpiringInsideRefreshWindowIsNotReusable() {
        // Two minutes left, inside the five minute window.
        val url = "https://cdn.example.com/movie.mkv?exp=1700000120"

        assertFalse(StreamLinkExpiry.isReusable(url, nowMs))
    }

    @Test
    fun linkWithHoursLeftIsReusable() {
        val url = "https://cdn.example.com/movie.mkv?exp=1700018000&token=secret"

        assertTrue(StreamLinkExpiry.isReusable(url, nowMs))
    }

    @Test
    fun expiredLinkIsNotReusable() {
        val url = "https://cdn.example.com/movie.mkv?exp=1699999000"

        assertFalse(StreamLinkExpiry.isReusable(url, nowMs))
    }

    @Test
    fun storedExpirationDecidesWithoutReparsingTheUrl() {
        val url = "https://cdn.example.com/movie.mkv?exp=1700018000"

        assertTrue(StreamLinkExpiry.isReusable(expiresAtMs = 1_700_018_000_000L, url = url, nowMs = nowMs))
        assertFalse(StreamLinkExpiry.isReusable(expiresAtMs = nowMs + 1_000L, url = url, nowMs = nowMs))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun cloudFrontPolicyExpirationIsRead() {
        val policy = Base64.Default.encode("""{"exp":1700018000}""".encodeToByteArray())
        val url = "https://d111111abcdef8.cloudfront.net/movie.mp4?Policy=$policy&Signature=abc&Key-Pair-Id=K1"

        assertEquals(1_700_018_000_000L, StreamLinkExpiry.expiresAtMs(url, nowMs))
    }

    @Test
    fun unreadablePolicyIsIgnoredRatherThanThrowing() {
        val url = "https://cdn.example.com/movie.mkv?Policy=%%%not-base64%%%"

        assertNull(StreamLinkExpiry.expiresAtMs(url, nowMs))
    }
}
