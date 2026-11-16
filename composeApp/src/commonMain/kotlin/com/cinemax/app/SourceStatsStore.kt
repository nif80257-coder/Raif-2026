package com.cinemax.app

import com.cinemax.app.core.SourceStats
import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock

private const val STATS_PREFIX = "cinemax.stats.source."

/**
 * Keeps a small track record per playable link (its fingerprint), so the source
 * list can demote a link that just failed and prefer one that worked before.
 * Stored as one flat string per link; a broken entry is simply ignored.
 */
class SourceStatsStore(
    private val settings: Settings = Settings()
) {
    fun loadAll(): Map<String, SourceStats> = settings.keys
        .filter { it.startsWith(STATS_PREFIX) }
        .mapNotNull { key -> parse(settings.getStringOrNull(key))?.let { key.removePrefix(STATS_PREFIX) to it } }
        .toMap()

    fun load(fingerprint: String): SourceStats? =
        parse(settings.getStringOrNull(STATS_PREFIX + fingerprint))

    fun recordSuccess(fingerprint: String, timeToFirstFrameMs: Long?) {
        val current = load(fingerprint) ?: SourceStats()
        save(
            fingerprint,
            current.copy(
                lastSuccessAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                failures = 0,
                timeToFirstFrameMs = timeToFirstFrameMs?.takeIf { it >= 0L } ?: current.timeToFirstFrameMs
            )
        )
    }

    fun recordFailure(fingerprint: String) {
        val current = load(fingerprint) ?: SourceStats()
        save(
            fingerprint,
            current.copy(
                lastFailureAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                failures = current.failures + 1
            )
        )
    }

    fun clear() {
        settings.keys.filter { it.startsWith(STATS_PREFIX) }.forEach { settings.remove(it) }
    }

    private fun save(fingerprint: String, stats: SourceStats) {
        settings.putString(
            STATS_PREFIX + fingerprint,
            listOf(
                stats.lastSuccessAtEpochMillis,
                stats.lastFailureAtEpochMillis,
                stats.failures.toLong(),
                stats.timeToFirstFrameMs
            ).joinToString("|")
        )
    }

    private fun parse(raw: String?): SourceStats? {
        val parts = raw?.split('|') ?: return null
        if (parts.size < 4) return null
        return SourceStats(
            lastSuccessAtEpochMillis = parts[0].toLongOrNull() ?: return null,
            lastFailureAtEpochMillis = parts[1].toLongOrNull() ?: return null,
            failures = parts[2].toIntOrNull() ?: return null,
            timeToFirstFrameMs = parts[3].toLongOrNull() ?: return null
        )
    }
}
