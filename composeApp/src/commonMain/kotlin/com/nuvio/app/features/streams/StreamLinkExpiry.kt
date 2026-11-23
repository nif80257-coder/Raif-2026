package com.nuvio.app.features.streams

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * How long before a signed playback link dies this app stops offering it again.
 *
 * A link that expires in two minutes is worse than no cached link: playback starts, the connection
 * dies mid-stream, and the user sees a stall rather than the source list. Five minutes leaves room
 * for the player to open and for a resume inside one session.
 */
internal const val StreamLinkRefreshWindowMs = 5L * 60L * 1000L

/**
 * Reads the expiration of a resolved playback link out of its query parameters.
 *
 * Signed links carry their death in the URL itself (`exp`, `expires`, `expires_in`, a CloudFront
 * `Policy`), which is the only reliable way to know whether a cached link is still usable. Until now
 * the cache answered that question with a coarse test — any credential-looking parameter meant "never
 * reuse" — so a link valid for six hours was thrown away on every app start, and the same source had
 * to be resolved again before playback.
 */
internal object StreamLinkExpiry {
    /** Parameter names whose value is the absolute epoch time the link dies. */
    private val absoluteKeys = setOf(
        "exp",
        "expire",
        "expires",
        "expiresat",
        "expiration",
        "expirydate",
        "expiry",
        "expirytime",
        "tokenexpires",
        "tokenexpiresat",
        "validuntil",
    )

    /** Parameter names whose value is a number of seconds from the moment the link was issued. */
    private val relativeKeys = setOf(
        "expiresin",
        "ttl",
        "validfor",
    )

    /** Anything at or beyond this is already milliseconds; below it, the value is in seconds. */
    private const val MillisecondMagnitude = 100_000_000_000L

    fun expiresAtMs(url: String, nowMs: Long): Long? {
        val parameters = queryParameters(url)
        if (parameters.isEmpty()) return null

        parameters.forEach { (key, value) ->
            val compactKey = key.replace("-", "").replace("_", "").replace(".", "")
            if (key in absoluteKeys || compactKey in absoluteKeys) {
                parseEpochMs(value)?.let { return it }
            }
            if (key in relativeKeys || compactKey in relativeKeys) {
                parseForwardMs(value, nowMs)?.let { return it }
            }
            if (compactKey == "policy") {
                cloudFrontPolicyExpiryMs(value)?.let { return it }
            }
        }
        return null
    }

    fun isReusable(url: String, nowMs: Long): Boolean =
        isReusable(expiresAtMs = expiresAtMs(url, nowMs), url = url, nowMs = nowMs)

    /**
     * A link with a known expiration is reusable while it lasts past [StreamLinkRefreshWindowMs].
     * A link with no expiration but with credentials is not reusable at all: there is no telling when
     * it dies, and a dead link stalls playback instead of failing fast.
     */
    fun isReusable(expiresAtMs: Long?, url: String, nowMs: Long): Boolean {
        if (expiresAtMs != null) return expiresAtMs - nowMs > StreamLinkRefreshWindowMs
        return !url.hasLikelyExpiringPlaybackCredentials()
    }

    private fun queryParameters(url: String): Map<String, String> {
        val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        if (query.isBlank()) return emptyMap()
        return query
            .split('&', ';')
            .mapNotNull { pair ->
                val key = pair.substringBefore('=', missingDelimiterValue = "").trim().lowercase()
                if (key.isBlank()) return@mapNotNull null
                val value = pair
                    .substringAfter('=', missingDelimiterValue = "")
                    .trim()
                    .trim('"', '\'')
                key to value
            }
            .toMap()
    }

    private fun parseEpochMs(value: String): Long? {
        val number = value.toLongOrNull() ?: return null
        if (number <= 0L) return null
        return if (number >= MillisecondMagnitude) number else number * 1_000L
    }

    private fun parseForwardMs(value: String, nowMs: Long): Long? {
        val number = value.toLongOrNull() ?: return null
        if (number <= 0L) return null
        return if (number >= MillisecondMagnitude) nowMs + number else nowMs + number * 1_000L
    }

    /**
     * A CloudFront signed URL hides the expiration inside a base64 policy document:
     * `{"Statement":[{"Condition":{"DateLessThan":{"AWS:EpochTime":1700000000}}}]}`.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun cloudFrontPolicyExpiryMs(rawValue: String): Long? {
        if (rawValue.isBlank()) return null
        val normalized = rawValue.replace(' ', '+')
        val decoded = runCatching { Base64.Default.decode(normalized) }
            .recoverCatching { Base64.UrlSafe.decode(normalized.trimEnd('=')) }
            .getOrNull()
            ?: return null
        val document = runCatching { decoded.decodeToString() }.getOrNull() ?: return null

        val match = cloudFrontEpochRegex.find(document) ?: return null
        return parseEpochMs(match.groupValues[1])
    }

    private val cloudFrontEpochRegex = Regex(""""exp"\s*:\s*(\d+)""")
}
