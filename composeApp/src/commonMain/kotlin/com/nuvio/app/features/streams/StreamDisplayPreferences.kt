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
 * - **One link per quality.** A source that answers with a 4K file and a 1080p one is offering a
 *   choice, and the viewer picks the resolution their connection and screen can carry — so every
 *   quality stays. What a source returns twice for the same quality is the redundancy the viewer
 *   cannot use: the two rows differ by a URL and nothing else, and only one of them plays. Keeping
 *   one link for each quality and leaving that repeat out is the default, and listing the repeats
 *   too is a switch for whoever wants them.
 */
internal object StreamDisplayPreferences {
    private const val OneLinkPerQualityKey = "stream-one-link-per-quality"
    private const val MergeKey = "stream-merge-duplicate-links"
    private const val HideJunkKey = "stream-hide-junk-releases"

    private val oneLinkPerQualityCache = mutableMapOf<Int, Boolean>()
    private val cache = mutableMapOf<Int, Boolean>()
    private val hideJunkCache = mutableMapOf<Int, Boolean>()

    /**
     * On by default: every quality a source offers stays listed, and the same quality twice does not.
     * Absent from storage means on; only an explicit "0" is off.
     */
    fun oneLinkPerQualityPerSource(profileId: Int): Boolean =
        oneLinkPerQualityCache.getOrPut(profileId) {
            storedSwitchIsOff(AddonStorage.loadStartupCache(profileId, OneLinkPerQualityKey))
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
 * Keeps one link for every quality a source offers, out of the order the list already has.
 *
 * The rows arrive ordered per source — strongest first — so the link that survives for a quality is
 * the one that source ranks best for it, and what the viewer never sees is a second row of the same
 * quality that plays no differently. Two links at different qualities are two choices, and both stay:
 * a 4K file and a 1080p one are not duplicates of each other.
 *
 * A source is its own name, not its group: a provider repository carries several scrapers in one
 * group, and each of them keeps its own links. The quality is read by [qualityRank], which already
 * separates a remux from a web re-encode at the same resolution.
 */
internal fun List<AddonStreamGroup>.showingOneLinkPerQualityPerSource(): List<AddonStreamGroup> =
    map { group ->
        if (group.streams.size < 2) return@map group
        val seen = mutableSetOf<String>()
        val kept = group.streams.filter { stream ->
            val sourceKey = stream.sourceName?.takeIf { it.isNotBlank() } ?: stream.addonName
            seen.add("$sourceKey|${stream.qualityRank()}")
        }
        if (kept.size == group.streams.size) group else group.copy(streams = kept)
    }

/** The stored flag read as a switch: only an explicit "1" turns merging on. */
internal fun mergeDuplicatesFromStoredValue(stored: String?): Boolean = stored == "1"

/** The same reading for every other display switch. */
internal fun storedSwitchIsOn(stored: String?): Boolean = stored == "1"

/** The reading for a switch that is on until it is turned off: only a stored "0" is off. */
internal fun storedSwitchIsOff(stored: String?): Boolean = stored != "0"
