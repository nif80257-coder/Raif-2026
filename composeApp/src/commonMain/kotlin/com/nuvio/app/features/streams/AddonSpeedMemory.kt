package com.nuvio.app.features.streams

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.AddonStorage
import kotlinx.serialization.json.Json

/**
 * How long each installed source usually takes to answer a stream request.
 *
 * Opening a title asks every source, but only eight at a time, so the order the sources are
 * queued in decides when the first real result appears. Remembering the measured time lets the
 * fast sources take the first slots: the list fills with playable streams in a second or two
 * while the slow scrapers work through the rest.
 *
 * The stored value is a smoothed average of the whole request (fetch, cache write and parse). A
 * failure is recorded near the cap, so a source that just timed out goes to the back until it
 * answers again.
 */
internal object AddonSpeedMemory {
    private const val cacheName = "addon-stream-speed"

    /** Sources remembered at once; the ones measured longest ago are dropped first. */
    private const val MaxEntries = 80

    /** Weight of the newest sample: enough to follow a host that got better or worse. */
    private const val Smoothing = 0.4

    /**
     * How often the measurements reach storage. Opening one title answers thirty-four sources in
     * a couple of seconds, and a read-modify-write of the whole cache per answer would cost more
     * than the measurement is worth; the map in memory is always current, storage follows.
     */
    private const val SaveIntervalMs = 2_000L

    private const val FailureMs = 15_000L

    private const val UnknownMs = 20_000L

    private val json = Json { ignoreUnknownKeys = true }

    /** Entries per profile, kept in memory so a title open does not read the cache per source. */
    private val entriesByProfile = mutableMapOf<Int, MutableMap<String, String>>()
    private val lastSaveAtByProfile = mutableMapOf<Int, Long>()

    /** The smoothed milliseconds per manifest URL, for the sources measured so far. */
    fun speedByManifestUrl(profileId: Int): Map<String, Long> =
        entries(profileId).mapValues { (_, encoded) ->
            encoded.substringBefore('|').toLongOrNull() ?: UnknownMs
        }

    /**
     * How many streams each source returned the last time it answered.
     *
     * A source can be fast and still return nothing, and a slow one can return everything; this is
     * what tells the two apart on the add-ons screen. Sources measured before the count was stored
     * simply have no entry.
     */
    fun resultsByManifestUrl(profileId: Int): Map<String, Int> =
        entries(profileId).mapNotNull { (manifestUrl, encoded) ->
            parseResultCount(encoded)?.let { count -> manifestUrl to count }
        }.toMap()

    /**
     * When each source last answered, from the timestamp stored beside its speed.
     *
     * A source that stopped answering is the one the user notices, and the age of its last answer
     * says whether it went quiet just now or a week ago. Records without a readable time are left
     * out instead of being reported as the epoch.
     */
    fun lastSuccessByManifestUrl(profileId: Int): Map<String, Long> =
        entries(profileId).mapNotNull { (manifestUrl, encoded) ->
            val answeredAt = encoded.substringAfter('|')
                .substringBefore('|')
                .toLongOrNull()
                ?.takeIf { at -> at > 0L }
                ?: return@mapNotNull null
            manifestUrl to answeredAt
        }.toMap()

    fun recordSuccess(
        profileId: Int,
        manifestUrl: String,
        elapsedMs: Long,
        resultCount: Int? = null,
    ) {
        if (manifestUrl.isBlank()) return
        val entries = entries(profileId)
        val previousEncoded = entries[manifestUrl]
        val previous = previousEncoded?.substringBefore('|')?.toLongOrNull()
        val next = if (previous == null) {
            elapsedMs
        } else {
            (previous + (elapsedMs - previous) * Smoothing).toLong()
        }
        val count = resultCount ?: parseResultCount(previousEncoded)
        val suffix = count?.let { value -> "|$value" }.orEmpty()
        entries[manifestUrl] = "$next|${EpisodeReleaseDatePlatform.nowEpochMs()}$suffix"
        persistIfDue(profileId)
    }

    /** A source that failed or timed out should not be queued first next time. */
    fun recordFailure(profileId: Int, manifestUrl: String) {
        if (manifestUrl.isBlank()) return
        val entries = entries(profileId)
        entries[manifestUrl] = "$FailureMs|${EpisodeReleaseDatePlatform.nowEpochMs()}"
        persistIfDue(profileId)
    }

    /**
     * Drops the measurement and the failure penalty of one source, so it is queued by its own speed
     * again the next time it is asked.
     *
     * Resuming a paused source clears its failure streak, but the 15-second penalty written by
     * [recordFailure] would keep it behind every other source until it answered; this makes the
     * resume mean what it says.
     */
    fun forget(profileId: Int, manifestUrl: String) {
        if (manifestUrl.isBlank()) return
        val entries = entries(profileId)
        if (entries.remove(manifestUrl) == null) return
        save(profileId, entries)
    }

    /** Drops everything remembered for a profile, for when its local data is cleared. */
    fun clear(profileId: Int) {
        entriesByProfile.remove(profileId)
        lastSaveAtByProfile[profileId] = 0L
        AddonStorage.saveStartupCache(profileId = profileId, name = cacheName, payload = "")
    }

    private fun entries(profileId: Int): MutableMap<String, String> =
        entriesByProfile.getOrPut(profileId) { read(profileId).toMutableMap() }

    private fun persistIfDue(profileId: Int) {
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        val last = lastSaveAtByProfile[profileId] ?: 0L
        if (now - last < SaveIntervalMs) return
        lastSaveAtByProfile[profileId] = now
        save(profileId, entries(profileId))
    }

    private fun read(profileId: Int): Map<String, String> {
        val raw = AddonStorage.loadStartupCache(profileId, cacheName)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }
            .getOrDefault(emptyMap())
    }

    private fun save(profileId: Int, entries: Map<String, String>) {
        val payload = if (entries.size <= MaxEntries) {
            entries
        } else {
            entries.entries
                .sortedByDescending { (_, value) -> value.substringAfter('|').toLongOrNull() ?: 0L }
                .take(MaxEntries)
                .associate { (key, value) -> key to value }
        }
        AddonStorage.saveStartupCache(
            profileId = profileId,
            name = cacheName,
            payload = json.encodeToString(payload),
        )
    }
}

/**
 * Queues the measured-fast sources first and keeps the user's own order for everything else.
 *
 * A source with no measurement yet sorts after the measured ones, so a new install keeps its
 * configured order until the first responses arrive.
 *
 * A source that failed its last request still carries a failure streak, and it is queued after
 * every source that is answering — even a slower one. The first fetch slots are worth more to a
 * source that returns links than to one that is about to time out again.
 */
internal fun List<InstalledStreamAddonTarget>.orderedByRecordedSpeed(
    speeds: Map<String, Long>,
    failureStreaks: Map<String, Int> = emptyMap(),
): List<InstalledStreamAddonTarget> {
    if (size < 2) return this
    if (speeds.isEmpty() && failureStreaks.isEmpty()) return this
    return sortedWith(
        compareBy<InstalledStreamAddonTarget> { addon ->
            if ((failureStreaks[addon.manifestUrl] ?: 0) > 0) 1 else 0
        }.thenBy { addon -> speeds[addon.manifestUrl] ?: Long.MAX_VALUE / 2 },
    )
}

/**
 * The measured time written the way it is shown to the user: seconds with one decimal, rounded.
 *
 * The formatter stays in common code because Kotlin has no `String.format` here.
 */
internal fun formatSourceSpeedSeconds(ms: Long): String {
    val clamped = ms.coerceIn(0L, 999_999L)
    val tenths = (clamped + 50) / 100
    return "${tenths / 10}.${tenths % 10}"
}

/** The result count stored as the third field of a speed record, or null for older records. */
internal fun parseResultCount(encoded: String?): Int? =
    encoded?.split('|')?.getOrNull(2)?.toIntOrNull()?.takeIf { count -> count >= 0 }
