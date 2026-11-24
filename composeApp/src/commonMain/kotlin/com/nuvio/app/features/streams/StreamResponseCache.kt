package com.nuvio.app.features.streams

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.AddonStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Keeps the last stream list every add-on returned for a title.
 *
 * Opening the source sheet currently starts from an empty list and waits for the
 * slowest add-on. With this the sheet opens on the sources found last time, in the
 * same quality order, while the live responses arrive and replace them group by group.
 *
 * The cache is deliberately small: a handful of titles, a capped payload per add-on and
 * a capped number of add-ons per title. It is a head start, not a source of truth, so
 * anything over budget is simply not kept.
 */
internal object StreamResponseCache {
    /** Titles remembered at once; the oldest is dropped when a new one appears. */
    private const val MaxContentKeys = 6

    /** A single add-on response bigger than this is not stored. */
    private const val MaxAddonPayloadChars = 6_000

    /**
     * How long a remembered list may open the sheet.
     *
     * The links inside a payload are signed and short-lived, so an old answer is worse than no
     * answer: the sheet fills with rows whose first tap fails. Half a day keeps the head start for
     * a binge without carrying yesterday's dead links into today.
     */
    private const val MaxAgeMs = 12 * 60 * 60 * 1000L

    /** Total add-on payloads kept per title. */
    private const val MaxAddonsPerContent = 24

    /** Total characters kept per title. */
    private const val MaxContentChars = 40_000

    private const val cacheName = "stream_responses"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Entry(
        val cachedAtMs: Long = 0L,
        val payloads: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class Snapshot(
        val entries: Map<String, Entry> = emptyMap(),
    )

    /** The cache key for a title, shared with the playback-link cache. */
    fun contentKey(
        type: String,
        videoId: String,
        parentMetaId: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ): String = StreamLinkCacheRepository.contentKey(
        type = type,
        videoId = videoId,
        parentMetaId = parentMetaId,
        season = season,
        episode = episode,
    )

    fun load(profileId: Int, contentKey: String): Map<String, String> {
        if (contentKey.isBlank()) return emptyMap()
        val entry = readEntries(profileId)[contentKey] ?: return emptyMap()
        val age = EpisodeReleaseDatePlatform.nowEpochMs() - entry.cachedAtMs
        if (entry.cachedAtMs <= 0L || age > MaxAgeMs) return emptyMap()
        return entry.payloads
    }

    fun save(
        profileId: Int,
        contentKey: String,
        addonId: String,
        payload: String,
        nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
    ) {
        if (contentKey.isBlank() || addonId.isBlank() || payload.isBlank()) return
        if (payload.length > MaxAddonPayloadChars) return

        val entries = readEntries(profileId).toMutableMap()
        val existing = entries[contentKey]?.payloads.orEmpty()
        if (addonId !in existing && existing.size >= MaxAddonsPerContent) return
        val usedChars = existing.entries.sumOf { (id, value) -> if (id == addonId) 0 else value.length }
        if (addonId !in existing && usedChars + payload.length > MaxContentChars) return

        entries[contentKey] = Entry(
            cachedAtMs = nowMs,
            payloads = existing + (addonId to payload),
        )
        writeEntries(profileId, trimToNewest(entries))
    }

    fun clear(profileId: Int) {
        AddonStorage.saveStartupCache(profileId = profileId, name = cacheName, payload = "")
    }

    private fun trimToNewest(entries: Map<String, Entry>): Map<String, Entry> {
        if (entries.size <= MaxContentKeys) return entries
        return entries.entries
            .sortedByDescending { (_, entry) -> entry.cachedAtMs }
            .take(MaxContentKeys)
            .associate { (key, entry) -> key to entry }
    }

    private fun readEntries(profileId: Int): Map<String, Entry> {
        val raw = AddonStorage.loadStartupCache(profileId, cacheName)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching { json.decodeFromString(Snapshot.serializer(), raw) }
            .getOrNull()
            ?.entries
            .orEmpty()
    }

    private fun writeEntries(profileId: Int, entries: Map<String, Entry>) {
        AddonStorage.saveStartupCache(
            profileId = profileId,
            name = cacheName,
            payload = json.encodeToString(Snapshot.serializer(), Snapshot(entries)),
        )
    }
}
