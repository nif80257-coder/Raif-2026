package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.AddonStorage

/**
 * How the stream list is drawn, per profile.
 *
 * The one choice here is duplication between sources. Several add-ons scrape the same file, and
 * the list used to keep a single copy of it and drop it from every other source — which made each
 * source look like it returned fewer links than it did. Showing every link is the default now, and
 * merging the duplicates back into one list is a switch for whoever prefers it.
 */
internal object StreamDisplayPreferences {
    private const val MergeKey = "stream-merge-duplicate-links"
    private const val HideJunkKey = "stream-hide-junk-releases"

    private val cache = mutableMapOf<Int, Boolean>()
    private val hideJunkCache = mutableMapOf<Int, Boolean>()

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
        cache.remove(profileId)
        hideJunkCache.remove(profileId)
    }
}

/** The stored flag read as a switch: only an explicit "1" turns merging on. */
internal fun mergeDuplicatesFromStoredValue(stored: String?): Boolean = stored == "1"

/** The same reading for every other display switch. */
internal fun storedSwitchIsOn(stored: String?): Boolean = stored == "1"
