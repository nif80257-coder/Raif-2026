package com.nuvio.app.features.addons

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A manifest restored from disk, with the age it was fetched at. */
internal data class CachedManifest(
    val manifest: AddonManifest,
    val cachedAtMs: Long,
    /** False when the entry is old enough that the next launch should re-fetch it. */
    val isFresh: Boolean,
)

/**
 * Keeps the last good manifest payload of every installed add-on.
 *
 * Without this, every cold start re-fetches sixty manifests before the home screen has
 * a single catalog to draw. The payload is stored rather than the parsed model, so the
 * cache stays valid when the manifest shape changes: it is simply parsed again at
 * launch, which costs microseconds and no network.
 *
 * A manifest older than [FreshForMs] is refreshed in the background with a stagger, so
 * the launch itself never waits for it.
 */
internal object CachedManifestStore {
    /** How long a stored manifest is trusted before the next launch refreshes it. */
    const val FreshForMs = 6 * 60 * 60 * 1000L

    /** A manifest payload bigger than this is not worth keeping between launches. */
    private const val MaxPayloadChars = 200_000

    private const val cacheName = "addon_manifests"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Entry(
        val cachedAtMs: Long = 0L,
        val payload: String = "",
    )

    @Serializable
    private data class Snapshot(
        val entries: Map<String, Entry> = emptyMap(),
    )

    fun load(
        profileId: Int,
        nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
    ): Map<String, CachedManifest> {
        val raw = AddonStorage.loadStartupCache(profileId, cacheName)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        val snapshot = runCatching { json.decodeFromString(Snapshot.serializer(), raw) }
            .getOrNull()
            ?: return emptyMap()
        return snapshot.entries.mapNotNull { (manifestUrl, entry) ->
            if (entry.payload.isBlank()) return@mapNotNull null
            val manifest = runCatching {
                AddonManifestParser.parse(manifestUrl = manifestUrl, payload = entry.payload)
            }.getOrNull() ?: return@mapNotNull null
            val age = nowMs - entry.cachedAtMs
            manifestUrl to CachedManifest(
                manifest = manifest,
                cachedAtMs = entry.cachedAtMs,
                isFresh = entry.cachedAtMs > 0L && age in 0..FreshForMs,
            )
        }.toMap()
    }

    fun save(
        profileId: Int,
        manifestUrl: String,
        payload: String,
        nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
    ) {
        if (manifestUrl.isBlank() || payload.isBlank()) return
        if (payload.length > MaxPayloadChars) return
        val entries = readEntries(profileId).toMutableMap()
        entries[manifestUrl] = Entry(cachedAtMs = nowMs, payload = payload)
        writeEntries(profileId, entries)
    }

    fun remove(profileId: Int, manifestUrl: String) {
        val entries = readEntries(profileId)
        if (manifestUrl !in entries) return
        writeEntries(profileId, entries - manifestUrl)
    }

    /** Drops entries for add-ons the profile no longer has installed. */
    fun retainOnly(profileId: Int, manifestUrls: Set<String>) {
        val entries = readEntries(profileId)
        if (entries.isEmpty()) return
        val retained = entries.filterKeys { it in manifestUrls }
        if (retained.size == entries.size) return
        writeEntries(profileId, retained)
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
