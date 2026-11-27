package com.nuvio.app.features.plugins

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.AddonStorage
import kotlinx.serialization.json.Json

/** Consecutive failures after which one provider is left out of a search. */
internal const val ScraperRestAfterFailures = 3

/** How long a provider is left out before it is given another chance. */
internal const val ScraperRestMs = 10L * 60L * 1000L

/**
 * Whether a provider that has been failing is still resting.
 *
 * Pure so the decision can be tested without storage, and so the fetch path and the tests cannot
 * drift: three failures in a row rest a provider for ten minutes, one success ends the rest, and a
 * clock that moved backwards does not extend it.
 */
internal fun isScraperRested(
    consecutiveFailures: Int,
    lastFailureAtMs: Long,
    nowMs: Long,
): Boolean {
    if (consecutiveFailures < ScraperRestAfterFailures) return false
    if (lastFailureAtMs <= 0L) return false
    return nowMs - lastFailureAtMs in 0..ScraperRestMs
}

/**
 * How each provider has been doing, per profile.
 *
 * Add-ons have had this for a while (`AddonFailureMemory`); providers did not, so a scraper whose
 * site had gone away was executed again on every title, ten seconds at a time, in front of the ones
 * that work. The key is the provider id, not the repository, so three bad providers do not rest the
 * good ones that share their list.
 */
internal object PluginScraperHealth {
    private const val cacheName = "plugin-scraper-health"
    private val json = Json { ignoreUnknownKeys = true }
    private val entriesByProfile = mutableMapOf<Int, MutableMap<String, String>>()

    fun isRested(profileId: Int, scraperId: String): Boolean {
        val encoded = entries(profileId)[scraperId] ?: return false
        val failures = encoded.substringBefore('|').toIntOrNull() ?: 0
        val lastFailureAtMs = encoded.substringAfter('|', "0").toLongOrNull() ?: 0L
        return isScraperRested(
            consecutiveFailures = failures,
            lastFailureAtMs = lastFailureAtMs,
            nowMs = EpisodeReleaseDatePlatform.nowEpochMs(),
        )
    }

    fun recordSuccess(profileId: Int, scraperId: String) {
        if (entries(profileId).remove(scraperId) != null) persist(profileId)
    }

    fun recordFailure(profileId: Int, scraperId: String) {
        if (scraperId.isBlank()) return
        val entries = entries(profileId)
        val previousFailures = entries[scraperId]?.substringBefore('|')?.toIntOrNull() ?: 0
        entries[scraperId] = "${previousFailures + 1}|${EpisodeReleaseDatePlatform.nowEpochMs()}"
        persist(profileId)
    }

    /** Consecutive failures per provider, for the health report. */
    fun failureCounts(profileId: Int): Map<String, Int> =
        entries(profileId).mapNotNull { (scraperId, encoded) ->
            encoded.substringBefore('|').toIntOrNull()?.let { failures -> scraperId to failures }
        }.toMap()

    private fun entries(profileId: Int): MutableMap<String, String> =
        entriesByProfile.getOrPut(profileId) {
            val raw = AddonStorage.loadStartupCache(profileId, cacheName).orEmpty()
            runCatching { json.decodeFromString<Map<String, String>>(raw) }
                .getOrElse { emptyMap() }
                .toMutableMap()
        }

    private fun persist(profileId: Int) {
        AddonStorage.saveStartupCache(
            profileId = profileId,
            name = cacheName,
            payload = json.encodeToString(entries(profileId)),
        )
    }
}
