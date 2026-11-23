package com.nuvio.app.features.player

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.AddonStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Remembers the subtitle list every add-on returned for an episode, so the subtitle sheet can open
 * on it instead of on a spinner.
 *
 * The list itself is cheap for one add-on but not for five: each request is a separate host, and the
 * sheet stays busy until the slowest one answers. A viewer who opens the sheet twice, or who returns
 * to an episode they were watching, already knows which tracks exist, so the answer is kept for a few
 * hours and refreshed in the background by the live requests that follow it.
 */
internal object SubtitleListCache {
    /** Subtitle lists do not change often; half a day is short enough to pick up a new release. */
    const val FreshForMs = 6 * 60 * 60 * 1000L

    /** How many episodes are remembered at once; the oldest is dropped when a new one appears. */
    private const val MaxEntries = 12

    private const val cacheName = "subtitle_lists"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoredSubtitle(
        val id: String = "",
        val url: String = "",
        val language: String = "",
        val display: String = "",
        val addonName: String? = null,
        val label: String? = null,
    )

    @Serializable
    private data class Entry(
        val cachedAtMs: Long = 0L,
        val subtitles: List<StoredSubtitle> = emptyList(),
    )

    @Serializable
    private data class Snapshot(
        val entries: Map<String, Entry> = emptyMap(),
    )

    /**
     * The subtitle list depends on the video hash and size as well as the episode, because those are
     * what the subtitle add-ons match a file by: a different rip of the same episode is a different
     * list, and reusing the old one would offer tracks that do not line up.
     */
    fun contentKey(type: String, videoId: String, extras: SubtitleRequestExtras?): String =
        listOf(
            type.lowercase(),
            videoId.trim(),
            extras?.videoHash.orEmpty(),
            extras?.videoSize?.toString().orEmpty(),
        ).joinToString(separator = "|")

    fun isFresh(cachedAtMs: Long, nowMs: Long): Boolean {
        if (cachedAtMs <= 0L) return false
        val age = nowMs - cachedAtMs
        return age in 0..FreshForMs
    }

    fun load(
        profileId: Int,
        contentKey: String,
        nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
    ): List<AddonSubtitle>? {
        if (contentKey.isBlank()) return null
        val entry = readEntries(profileId)[contentKey] ?: return null
        if (!isFresh(entry.cachedAtMs, nowMs)) return null
        return entry.subtitles.mapNotNull { stored ->
            if (stored.url.isBlank()) return@mapNotNull null
            AddonSubtitle(
                id = stored.id,
                url = stored.url,
                language = stored.language,
                display = stored.display,
                addonName = stored.addonName,
                label = stored.label,
            )
        }.takeIf { it.isNotEmpty() }
    }

    fun save(
        profileId: Int,
        contentKey: String,
        subtitles: List<AddonSubtitle>,
        nowMs: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
    ) {
        if (contentKey.isBlank()) return
        val entries = readEntries(profileId).toMutableMap()
        // An empty answer replaces nothing: the next open should ask the add-ons again rather than
        // read a stored "nothing here" for six hours.
        if (subtitles.isNotEmpty()) {
            entries[contentKey] = Entry(
                cachedAtMs = nowMs,
                subtitles = subtitles.map { subtitle ->
                    StoredSubtitle(
                        id = subtitle.id,
                        url = subtitle.url,
                        language = subtitle.language,
                        display = subtitle.display,
                        addonName = subtitle.addonName,
                        label = subtitle.label,
                    )
                },
            )
        }
        writeEntries(profileId, trimToNewest(entries, nowMs))
    }

    fun clear(profileId: Int) {
        AddonStorage.saveStartupCache(profileId = profileId, name = cacheName, payload = "")
    }

    private fun trimToNewest(entries: Map<String, Entry>, nowMs: Long): Map<String, Entry> {
        val live = entries.filterValues { isFresh(it.cachedAtMs, nowMs) }
        if (live.size <= MaxEntries) return live
        return live.entries
            .sortedByDescending { (_, entry) -> entry.cachedAtMs }
            .take(MaxEntries)
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

    /** Exposed for tests: the payload format is the only part that can drift between releases. */
    internal fun encodeForTest(entries: Map<String, List<AddonSubtitle>>, nowMs: Long): String =
        json.encodeToString(
            Snapshot.serializer(),
            Snapshot(
                entries = entries.mapValues { (_, subtitles) ->
                    Entry(
                        cachedAtMs = nowMs,
                        subtitles = subtitles.map { subtitle ->
                            StoredSubtitle(
                                id = subtitle.id,
                                url = subtitle.url,
                                language = subtitle.language,
                                display = subtitle.display,
                                addonName = subtitle.addonName,
                                label = subtitle.label,
                            )
                        },
                    )
                },
            ),
        )

    /** Exposed for tests: reads a payload back, ignoring anything it does not recognise. */
    internal fun decodeForTest(raw: String): Map<String, List<AddonSubtitle>> =
        runCatching { json.decodeFromString(Snapshot.serializer(), raw) }
            .getOrNull()
            ?.entries
            .orEmpty()
            .mapValues { (_, entry) ->
                entry.subtitles.mapNotNull { stored ->
                    if (stored.url.isBlank()) return@mapNotNull null
                    AddonSubtitle(
                        id = stored.id,
                        url = stored.url,
                        language = stored.language,
                        display = stored.display,
                        addonName = stored.addonName,
                        label = stored.label,
                    )
                }
            }
}
