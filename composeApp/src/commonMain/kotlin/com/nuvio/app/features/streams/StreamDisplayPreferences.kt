package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.AddonStorage

/**
 * How the stream list is drawn, per profile.
 *
 * Two choices here, both about the sources themselves rather than about this app:
 * - **One link per source.** A source often returns several links for the same film and only
 *   one of them plays; its own ordering puts the likeliest first, so the list draws that one
 *   and leaves the ones that mostly fail out of the way. This is the default.
 * - **Duplicates between sources.** Several add-ons scrape the same file, and the list used to
 *   keep a single copy of it and drop it from every other source — which made each source look
 *   like it returned fewer links than it did. Showing every link is the default, and merging
 *   the duplicates back into one list is a switch for whoever prefers it.
 */
internal object StreamDisplayPreferences {
    private const val TopLinkOnlyKey = "stream-one-link-per-source"
    private const val MergeKey = "stream-merge-duplicate-links"
    private const val HideJunkKey = "stream-hide-junk-releases"

    private val topLinkOnlyCache = mutableMapOf<Int, Boolean>()
    private val cache = mutableMapOf<Int, Boolean>()
    private val hideJunkCache = mutableMapOf<Int, Boolean>()

    /** On by default: a source's second and third links are the ones that usually miss. */
    fun singleLinkPerSource(profileId: Int): Boolean =
        topLinkOnlyCache.getOrPut(profileId) {
            storedSwitchIsOff(AddonStorage.loadStartupCache(profileId, TopLinkOnlyKey))
        }

    fun setSingleLinkPerSource(profileId: Int, value: Boolean) {
        if (topLinkOnlyCache[profileId] == value) return
        topLinkOnlyCache[profileId] = value
        AddonStorage.saveStartupCache(profileId, TopLinkOnlyKey, if (value) "1" else "0")
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
        topLinkOnlyCache.remove(profileId)
        cache.remove(profileId)
        hideJunkCache.remove(profileId)
    }
}

/**
 * Keeps at most [maxPerSource] links of every source, out of the order the list already has.
 *
 * The rows arrive ordered per source — strongest first — so the links that survive are the ones
 * that source itself ranks best, and the ones the viewer never sees are the ones that usually
 * fail. A source is its own name: a provider repository carries several scrapers in one group,
 * and each of them keeps its own links.
 *
 * [maxPerSource] of zero or less keeps everything, which is what the switch turns off.
 */
internal fun List<AddonStreamGroup>.showingTopLinksPerSource(
    maxPerSource: Int,
): List<AddonStreamGroup> {
    if (maxPerSource <= 0 || all { group -> group.streams.size <= maxPerSource }) return this
    return map { group ->
        if (group.streams.size <= maxPerSource) {
            group
        } else {
            val keptBySource = mutableMapOf<String, Int>()
            val kept = group.streams.filter { stream ->
                val sourceKey = stream.sourceName?.takeIf { it.isNotBlank() } ?: stream.addonName
                val keptSoFar = keptBySource[sourceKey] ?: 0
                if (keptSoFar >= maxPerSource) {
                    false
                } else {
                    keptBySource[sourceKey] = keptSoFar + 1
                    true
                }
            }
            if (kept.size == group.streams.size) group else group.copy(streams = kept)
        }
    }
}

/** The stored flag read as a switch: only an explicit "1" turns merging on. */
internal fun mergeDuplicatesFromStoredValue(stored: String?): Boolean = stored == "1"

/** The same reading for every other display switch. */
internal fun storedSwitchIsOn(stored: String?): Boolean = stored == "1"

/** The reading for a switch that is on until it is turned off: only a stored "0" is off. */
internal fun storedSwitchIsOff(stored: String?): Boolean = stored != "0"
