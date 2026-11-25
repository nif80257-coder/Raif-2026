package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.AddonStorage

/**
 * How the stream list is drawn, per profile.
 *
 * Two choices here, both about the sources themselves rather than about this app:
 * - **Duplicates between sources.** Several add-ons scrape the same file, and the list used to
 *   keep a single copy of it and drop it from every other source — which made each source look
 *   like it returned fewer links than it did. Showing every link is the default, and merging
 *   the duplicates back into one list is a switch for whoever prefers it.
 * - **One link per quality and audio.** The list shows every link a source returns: two copies at
 *   the same quality are not the same file to the viewer, and neither are two copies of the same
 *   quality in different audio — an English original, a Spanish dub, an Arabic one. The viewer picks
 *   the row, not the app. Shortening the list to one row per quality and audio is a switch, and it
 *   is off.
 */
internal object StreamDisplayPreferences {
    private const val OneLinkPerQualityKey = "stream-one-link-per-quality"
    private const val MergeKey = "stream-merge-duplicate-links"
    private const val HideJunkKey = "stream-hide-junk-releases"

    private val oneLinkPerQualityCache = mutableMapOf<Int, Boolean>()
    private val cache = mutableMapOf<Int, Boolean>()
    private val hideJunkCache = mutableMapOf<Int, Boolean>()

    /** Off by default: the sheet lists every link a source answers with. */
    fun oneLinkPerQualityPerSource(profileId: Int): Boolean =
        oneLinkPerQualityCache.getOrPut(profileId) {
            storedSwitchIsOn(AddonStorage.loadStartupCache(profileId, OneLinkPerQualityKey))
        }

    fun setOneLinkPerQualityPerSource(profileId: Int, value: Boolean) {
        if (oneLinkPerQualityCache[profileId] == value) return
        oneLinkPerQualityCache[profileId] = value
        AddonStorage.saveStartupCache(profileId, OneLinkPerQualityKey, if (value) "1" else "0")
    }

    fun mergeDuplicatesAcrossSources(profileId: Int): Boolean =
        cache.getOrPut(profileId) {
            mergeDuplicatesFromStoredValue(AddonStorage.loadStartupCache(profileId, MergeKey))
        }

    fun setMergeDuplicatesAcrossSources(profileId: Int, value: Boolean) {
        if (cache[profileId] == value) return
        cache[profileId] = value
        AddonStorage.saveStartupCache(profileId, MergeKey, if (value) "1" else "0")
    }

    /** Off by default: a source's camera copies stay listed until the user asks to hide them. */
    fun hideJunkReleases(profileId: Int): Boolean =
        hideJunkCache.getOrPut(profileId) {
            storedSwitchIsOn(AddonStorage.loadStartupCache(profileId, HideJunkKey))
        }

    fun setHideJunkReleases(profileId: Int, value: Boolean) {
        if (hideJunkCache[profileId] == value) return
        hideJunkCache[profileId] = value
        AddonStorage.saveStartupCache(profileId, HideJunkKey, if (value) "1" else "0")
    }

    /** Drops the remembered choice, for when a profile's local data is cleared. */
    fun clear(profileId: Int) {
        oneLinkPerQualityCache.remove(profileId)
        cache.remove(profileId)
        hideJunkCache.remove(profileId)
    }
}

/**
 * Keeps one link for every quality and audio a source offers, out of the order the list already has.
 *
 * The switch that turns this on asks for a shorter sheet, and even then it only collapses rows the
 * viewer could not tell apart: the same quality *and* the same audio tracks. Two rows that differ by
 * their dub — an English original and a Spanish one — are two choices, and both stay.
 *
 * A source is its own name, not its group: a provider repository carries several scrapers in one
 * group, and each of them keeps its own links.
 */
internal fun List<AddonStreamGroup>.showingOneLinkPerQualityPerSource(): List<AddonStreamGroup> =
    map { group ->
        if (group.streams.size < 2) return@map group
        val seen = mutableSetOf<String>()
        val kept = group.streams.filter { stream ->
            val sourceKey = stream.sourceName?.takeIf { it.isNotBlank() } ?: stream.addonName
            seen.add("$sourceKey|${stream.qualityRank()}|${stream.audioTrackKey()}")
        }
        if (kept.size == group.streams.size) group else group.copy(streams = kept)
    }

/** The stored flag read as a switch: only an explicit "1" turns merging on. */
internal fun mergeDuplicatesFromStoredValue(stored: String?): Boolean = stored == "1"

/** The same reading for every other display switch. */
internal fun storedSwitchIsOn(stored: String?): Boolean = stored == "1"
