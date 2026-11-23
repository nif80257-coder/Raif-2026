package com.nuvio.app.features.addons

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Remembers sources that answer a request with nothing at all.
 *
 * A source that fails is already handled by [AddonFailureMemory]: three failures and it waits out
 * a pause. But a source that answers quickly and returns an empty list is never paused — it is
 * simply useless for the titles the user is opening, and it still takes one of the fetch slots on
 * every request, so every search waits for it and the list stays empty.
 *
 * After [EmptyAnswersBeforeSkip] empty answers in a row the source is left out of automatic loads
 * for [SkipDurationMs]. One answer with a link clears it, a manual refresh asks it again, and
 * nothing is deleted: the user can always resume it from the source list.
 */
internal object AddonSilentSourceMemory {
    private const val CacheName = "addon-silent-sources"

    /** How many empty answers in a row before the source is left out for a while. */
    const val EmptyAnswersBeforeSkip = 3

    /** How long a quiet source stays out of automatic loads. */
    const val SkipDurationMs = 12 * 60 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }

    /** Serialises the read-modify-write cycles of concurrent fetches. */
    private val writeMutex = Mutex()

    /** The sources that answered empty too often and are inside their skip window. */
    fun silentManifestUrls(
        profileId: Int,
        nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
    ): Set<String> = read(profileId)
        .filterValues { encoded ->
            val record = decodeSilentRecord(encoded)
            shouldSkipSilentSource(
                emptyAnswers = record?.emptyAnswers ?: 0,
                lastEmptyAtMs = record?.lastEmptyAtMs ?: 0L,
                nowMs = nowMs,
            )
        }
        .keys

    suspend fun recordEmptyAnswer(
        profileId: Int,
        manifestUrl: String,
        nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
    ) {
        if (manifestUrl.isBlank()) return
        writeMutex.withLock {
            val stored = read(profileId).toMutableMap()
            val previous = decodeSilentRecord(stored[manifestUrl])?.emptyAnswers ?: 0
            stored[manifestUrl] = encodeSilentRecord(emptyAnswers = previous + 1, lastEmptyAtMs = nowMs)
            write(profileId, stored)
        }
    }

    /** A source that returned a link is not quiet any more, so its count starts over. */
    suspend fun recordAnswerWithLinks(profileId: Int, manifestUrl: String) {
        if (manifestUrl.isBlank()) return
        writeMutex.withLock {
            val stored = read(profileId).toMutableMap()
            if (stored.remove(manifestUrl) != null) write(profileId, stored)
        }
    }

    /** Forgets everything remembered for a profile, for when its local data is cleared. */
    fun clear(profileId: Int) {
        AddonStorage.saveStartupCache(profileId = profileId, name = CacheName, payload = "")
    }

    private fun read(profileId: Int): Map<String, String> {
        val raw = AddonStorage.loadStartupCache(profileId, CacheName)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    private fun write(profileId: Int, records: Map<String, String>) {
        AddonStorage.saveStartupCache(
            profileId = profileId,
            name = CacheName,
            payload = json.encodeToString(records),
        )
    }
}

/** One stored silent record, decoded from the `emptyAnswers|lastEmptyEpochMs` payload. */
internal data class SilentSourceRecord(
    val emptyAnswers: Int,
    val lastEmptyAtMs: Long,
)

internal fun decodeSilentRecord(encoded: String?): SilentSourceRecord? {
    if (encoded.isNullOrBlank()) return null
    val parts = encoded.split('|')
    val emptyAnswers = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val lastEmptyAtMs = parts.getOrNull(1)?.toLongOrNull() ?: return null
    return SilentSourceRecord(emptyAnswers = emptyAnswers, lastEmptyAtMs = lastEmptyAtMs)
}

internal fun encodeSilentRecord(emptyAnswers: Int, lastEmptyAtMs: Long): String =
    "${emptyAnswers.coerceAtLeast(0)}|$lastEmptyAtMs"

/**
 * Whether a source has been quiet often enough, and recently enough, to be left out for a while.
 *
 * Kept apart from storage so the rule can be tested without a device: a count below the threshold,
 * a record without a time, or a window that already passed all mean "ask it again".
 */
internal fun shouldSkipSilentSource(
    emptyAnswers: Int,
    lastEmptyAtMs: Long,
    nowMs: Long,
    emptyAnswersBeforeSkip: Int = AddonSilentSourceMemory.EmptyAnswersBeforeSkip,
    skipDurationMs: Long = AddonSilentSourceMemory.SkipDurationMs,
): Boolean =
    emptyAnswers >= emptyAnswersBeforeSkip &&
        lastEmptyAtMs > 0L &&
        nowMs - lastEmptyAtMs < skipDurationMs
