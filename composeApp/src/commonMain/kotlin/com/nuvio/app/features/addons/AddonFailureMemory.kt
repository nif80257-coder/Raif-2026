package com.nuvio.app.features.addons

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.profiles.ProfileRepository
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
    fun pausedManifestUrls(nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs()): Set<String> =
        snapshot(nowMs).pausedManifestUrls

    /**
     * The failure streaks of every add-on that still has one inside the strike lifetime, keyed
     * by manifest URL. One storage read serves the whole installed list.
     */
    fun failureStreaks(nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs()): Map<String, Int> =
        snapshot(nowMs).failureStreaks

    /**
     * The paused set and the failure streaks from a single storage read.
     *
     * The add-ons screen needs both, and reading the stored records twice doubled the work for
     * every installed add-on on each visit. Sources the user marked as never pause stay out of
     * [AddonFailureSnapshot.pausedManifestUrls] while still reporting their streak.
     */
    fun snapshot(
        nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
    ): AddonFailureSnapshot {
        val stored = AddonHealthCheckStorage.loadStreamFailures()
        if (stored.isEmpty()) return AddonFailureSnapshot()
        val neverPaused = AddonPauseExceptions.neverPausedManifestUrls(ProfileRepository.activeProfileId)
        val paused = mutableSetOf<String>()
        val streaks = mutableMapOf<String, Int>()
        val reasons = mutableMapOf<String, AddonFailureReason>()
        val lastFailureAt = mutableMapOf<String, Long>()
        stored.forEach { (manifestUrl, encoded) ->
            val record = decodeFailureRecord(
                encoded = encoded,
                nowMs = nowMs,
                strikeLifetimeMs = StrikeLifetimeMs,
            ) ?: return@forEach
            streaks[manifestUrl] = record.failures
            reasons[manifestUrl] = record.reason
            lastFailureAt[manifestUrl] = record.lastFailureAtMs
            if (
                manifestUrl !in neverPaused &&
                record.isPaused(
                    nowMs = nowMs,
                    failuresBeforePause = FailuresBeforePause,
                    pauseDurationMs = PauseDurationMs,
                )
            ) {
                paused += manifestUrl
            }
        }
        return AddonFailureSnapshot(
            pausedManifestUrls = paused,
            failureStreaks = streaks,
            failureReasons = reasons,
            lastFailureAtMs = lastFailureAt,
        )
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

    suspend fun recordFailure(manifestUrl: String, failure: Throwable? = null) {
        if (manifestUrl.isBlank()) return
        val nowMs = EpisodeReleaseDatePlatform.nowEpochMs()
        val reason = addonFailureReason(failure)
        writeMutex.withLock {
            val stored = AddonHealthCheckStorage.loadStreamFailures().toMutableMap()
            val previous = stored[manifestUrl]?.let { encoded ->
                decodeFailureRecord(
                    encoded = encoded,
                    nowMs = nowMs,
                    strikeLifetimeMs = StrikeLifetimeMs,
                )
            }
            val failures = (previous?.failures ?: 0) + 1
            stored[manifestUrl] = "$failures|$nowMs|${reason.token}"
            AddonHealthCheckStorage.saveStreamFailures(stored)
        }
    }

    /**
     * Forgets every failure strike, so add-ons paused for a bad streak are asked again on the
     * next load. Exposed to the add-ons screen so a source that recovered is not waiting out
     * the rest of its six-hour pause.
     */
    suspend fun clearAll() {
        writeMutex.withLock {
            if (AddonHealthCheckStorage.loadStreamFailures().isNotEmpty()) {
                AddonHealthCheckStorage.saveStreamFailures(emptyMap())
            }
        }
    }
}

/**
 * How old a failure is, in the roughest unit that still reads usefully.
 *
 * Kept apart from the label so the buckets can be tested without a screen or a locale: the card
 * turns the unit into its own words.
 */
internal enum class FailureAgeUnit { Now, Minutes, Hours, Days }

internal data class FailureAge(val amount: Long, val unit: FailureAgeUnit)

internal fun failureAge(elapsedMs: Long): FailureAge {
    val minutes = elapsedMs.coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1L -> FailureAge(amount = 0L, unit = FailureAgeUnit.Now)
        minutes < 60L -> FailureAge(amount = minutes, unit = FailureAgeUnit.Minutes)
        minutes < 24L * 60L -> FailureAge(amount = minutes / 60L, unit = FailureAgeUnit.Hours)
        else -> FailureAge(amount = minutes / (24L * 60L), unit = FailureAgeUnit.Days)
    }
}

/**
 * Why a stream request failed the last time, so the sources screen can say more than "failed".
 *
 * It is stored with the failure streak: the reason a host refused yesterday is worth keeping while
 * the source is paused today.
 */
internal enum class AddonFailureReason(val token: String) {
    /** The request waited out its timeout: the host is unreachable or too slow. */
    Timeout("timeout"),

    /** The host answered that the page does not exist any more. */
    NotFound("not-found"),

    /** The host answered with a server error (5xx), including Cloudflare's 52x codes. */
    ServerError("server"),

    /** DNS, TLS, a dropped connection or a payload the parser refused. */
    Other("other"),
    ;

    companion object {
        fun fromToken(token: String?): AddonFailureReason =
            entries.firstOrNull { reason -> reason.token == token } ?: Other
    }
}

/**
 * Maps a failed request to the reason worth remembering.
 *
 * A status code is the useful part when the host answered and refused; a timeout says the host is
 * slow or unreachable; everything else stays generic so the label never claims more than it knows.
 */
internal fun addonFailureReason(failure: Throwable?): AddonFailureReason = when {
    failure == null -> AddonFailureReason.Other
    failure is AddonHttpStatusException -> when (failure.statusCode) {
        404, 410 -> AddonFailureReason.NotFound
        in 500..599 -> AddonFailureReason.ServerError
        else -> AddonFailureReason.Other
    }

    isTimeoutFailure(failure) -> AddonFailureReason.Timeout
    else -> AddonFailureReason.Other
}

/** One read of the stored failure records: which add-ons are paused and how many times each failed. */
internal data class AddonFailureSnapshot(
    val pausedManifestUrls: Set<String> = emptySet(),
    val failureStreaks: Map<String, Int> = emptyMap(),
    val failureReasons: Map<String, AddonFailureReason> = emptyMap(),
    val lastFailureAtMs: Map<String, Long> = emptyMap(),
)

/** One add-on's failure strikes, decoded from the `failures|lastFailureEpochMs|reason` payload. */internal data class AddonFailureRecord(
    val failures: Int,
    val lastFailureAtMs: Long,
    val reason: AddonFailureReason = AddonFailureReason.Other,
) {
    fun isPaused(
        nowMs: Long,
        failuresBeforePause: Int,
        pauseDurationMs: Long,
    ): Boolean =
        failures >= failuresBeforePause && nowMs - lastFailureAtMs < pauseDurationMs
}

/**
 * Decodes one stored strike, returning null for a malformed payload or a strike older than
 * [strikeLifetimeMs] so an add-on that failed once last week starts from a clean count.
 *
 * The reason is the third field; records written before it existed have only two and decode as
 * [AddonFailureReason.Other].
 */
internal fun decodeFailureRecord(
    encoded: String,
    nowMs: Long,
    strikeLifetimeMs: Long,
): AddonFailureRecord? {
    val parts = encoded.split('|')
    val failures = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val lastFailureAtMs = parts.getOrNull(1)?.toLongOrNull() ?: return null
    if (nowMs - lastFailureAtMs >= strikeLifetimeMs) return null
    return AddonFailureRecord(
        failures = failures,
        lastFailureAtMs = lastFailureAtMs,
        reason = AddonFailureReason.fromToken(parts.getOrNull(2)),
    )
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
