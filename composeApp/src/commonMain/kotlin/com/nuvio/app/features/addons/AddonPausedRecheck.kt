package com.nuvio.app.features.addons

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform

/**
 * How long a paused source waits before the app checks it again on its own.
 *
 * A pause exists so a dead host stops costing launch time; it is not meant to be permanent. The
 * user can resume a source from the add-ons screen, and this daily check is the other way back:
 * a host that recovered is asked once a day instead of waiting for the six-hour pause to run out
 * on the next failure.
 */
internal const val PausedRecheckIntervalMs = 24L * 60L * 60L * 1000L

/** Storage key of the last automatic recheck, per profile. */
internal const val PAUSED_RECHECK_CACHE = "addon-pause-recheck"

/**
 * True when at least one source is paused and the last automatic recheck is a day old or older.
 *
 * A zero timestamp is the record of a check that never happened, which counts as due; so does a
 * clock that moved backwards, so a device whose time was corrected is not left waiting for a
 * check that looks like it happened in the future.
 */
internal fun shouldRecheckPausedSources(
    nowMs: Long,
    lastRecheckAtMs: Long,
    pausedCount: Int,
): Boolean {
    if (pausedCount <= 0) return false
    if (lastRecheckAtMs <= 0L) return true
    if (nowMs < lastRecheckAtMs) return true
    return nowMs - lastRecheckAtMs >= PausedRecheckIntervalMs
}

/**
 * Asks every paused source for its manifest once and clears the pause of the ones that answer.
 *
 * The manifest is the cheapest request a source serves, and it is enough to tell "the host is
 * back" from "the host is still down" without opening a stream request for a title.
 */
internal suspend fun recheckPausedSources(profileId: Int) {
    val paused = AddonFailureMemory.pausedManifestUrls()
    if (paused.isEmpty()) return
    val nowMs = EpisodeReleaseDatePlatform.nowEpochMs()
    val lastRecheckAtMs = AddonStorage.loadStartupCache(profileId, PAUSED_RECHECK_CACHE)
        ?.toLongOrNull()
        ?: 0L
    if (!shouldRecheckPausedSources(nowMs, lastRecheckAtMs, paused.size)) return
    AddonStorage.saveStartupCache(profileId, PAUSED_RECHECK_CACHE, nowMs.toString())
    paused.forEach { manifestUrl ->
        val answered = runCatching {
            fetchAddonResponseText(url = manifestUrl, forceRefresh = true)
        }.isSuccess
        if (answered) AddonFailureMemory.recordSuccess(manifestUrl)
    }
}
