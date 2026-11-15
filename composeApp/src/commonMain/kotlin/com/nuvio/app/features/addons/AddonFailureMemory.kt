package com.nuvio.app.features.addons

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.settings.AddonHealthCheckStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Remembers add-ons that keep failing to answer a stream request.
 *
 * With dozens of installed manifests, one dead host can hold a fetch worker until the
 * request timeout on every single title. After [FailuresBeforePause] consecutive
 * failures the add-on is skipped for [PauseDurationMs]. A successful fetch clears the
 * record, a strike older than [StrikeLifetimeMs] is forgotten, and an explicit refresh
 * asks every add-on again, so nothing is lost permanently.
 *
 * The records live beside the add-on health fingerprints and are scoped to the profile.
 */
internal object AddonFailureMemory {
    private const val FailuresBeforePause = 3
    private const val PauseDurationMs = 6 * 60 * 60 * 1000L
    private const val StrikeLifetimeMs = 24 * 60 * 60 * 1000L

    /** Serialises the read-modify-write cycles of the concurrent fetches. */
    private val writeMutex = Mutex()

    /** Add-ons that failed three times in a row and are still inside their pause. */
    fun pausedManifestUrls(nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs()): Set<String> {
        val stored = AddonHealthCheckStorage.loadStreamFailures()
        if (stored.isEmpty()) return emptySet()
        return stored
            .mapNotNull { (manifestUrl, encoded) ->
                val record = decodeRecord(encoded, nowMs) ?: return@mapNotNull null
                manifestUrl.takeIf { record.isPaused(nowMs) }
            }
            .toSet()
    }

    suspend fun recordSuccess(manifestUrl: String) {
        if (manifestUrl.isBlank()) return
        writeMutex.withLock {
            val stored = AddonHealthCheckStorage.loadStreamFailures()
            if (manifestUrl in stored) {
                AddonHealthCheckStorage.saveStreamFailures(stored - manifestUrl)
            }
        }
    }

    suspend fun recordFailure(manifestUrl: String) {
        if (manifestUrl.isBlank()) return
        val nowMs = EpisodeReleaseDatePlatform.nowEpochMs()
        writeMutex.withLock {
            val stored = AddonHealthCheckStorage.loadStreamFailures().toMutableMap()
            val previous = stored[manifestUrl]?.let { decodeRecord(it, nowMs) }
            val failures = (previous?.failures ?: 0) + 1
            stored[manifestUrl] = "$failures|$nowMs"
            AddonHealthCheckStorage.saveStreamFailures(stored)
        }
    }

    private data class FailureRecord(
        val failures: Int,
        val lastFailureAtMs: Long,
    ) {
        fun isPaused(nowMs: Long): Boolean =
            failures >= FailuresBeforePause && nowMs - lastFailureAtMs < PauseDurationMs
    }

    private fun decodeRecord(encoded: String, nowMs: Long): FailureRecord? {
        val failures = encoded.substringBefore('|').toIntOrNull() ?: return null
        val lastFailureAtMs = encoded.substringAfter('|', missingDelimiterValue = "").toLongOrNull()
            ?: return null
        if (nowMs - lastFailureAtMs >= StrikeLifetimeMs) return null
        return FailureRecord(failures = failures, lastFailureAtMs = lastFailureAtMs)
    }
}

/**
 * Drops add-ons that are paused, unless [includeAll] is set (a manual refresh).
 *
 * The full list comes back if the pause list would have covered every add-on, so a bad
 * streak across a whole install can never leave the screen with no sources at all.
 */
internal fun List<ManagedAddon>.withoutPausedAddons(includeAll: Boolean): List<ManagedAddon> {
    if (includeAll) return this
    val paused = AddonFailureMemory.pausedManifestUrls()
    if (paused.isEmpty()) return this
    return filterNot { it.manifestUrl in paused }.ifEmpty { this }
}
