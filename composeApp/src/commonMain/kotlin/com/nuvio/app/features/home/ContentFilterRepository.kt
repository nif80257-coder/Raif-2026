package com.nuvio.app.features.home

import com.nuvio.app.features.addons.AddonStorage
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.enabledAddons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

/**
 * What a profile does not want to see: titles it hid one by one, and the sources that declare
 * themselves adult through `behaviorHints.adult` once that switch is on.
 *
 * A hidden title stays hidden on the home shelves, in the catalog lists and in search, so hiding it
 * once is enough. Both the list and the switch live per profile, beside the add-on preferences they
 * filter.
 */
object ContentFilterRepository {
    private const val HIDDEN_TITLES_CACHE = "hidden-titles"
    private const val HIDE_ADULT_CACHE = "hidden-titles-hide-adult"

    private val hiddenByProfile = mutableMapOf<Int, MutableStateFlow<Set<String>>>()
    private val hideAdultByProfile = mutableMapOf<Int, MutableStateFlow<Boolean>>()

    /** Bumped on every change so a screen can recompute once instead of polling the store. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private fun hiddenFlow(profileId: Int): MutableStateFlow<Set<String>> =
        hiddenByProfile.getOrPut(profileId) { MutableStateFlow(loadHidden(profileId)) }

    private fun hideAdultFlow(profileId: Int): MutableStateFlow<Boolean> =
        hideAdultByProfile.getOrPut(profileId) { MutableStateFlow(loadHideAdult(profileId)) }

    /** Every `type:id` key this profile hid. */
    fun hiddenTitleKeys(profileId: Int): Set<String> = hiddenFlow(profileId).value

    fun hiddenTitleCount(profileId: Int): Int = hiddenFlow(profileId).value.size

    fun setHidden(profileId: Int, type: String, id: String, hidden: Boolean) {
        if (type.isBlank() || id.isBlank()) return
        val key = hiddenKey(type, id)
        val flow = hiddenFlow(profileId)
        val next = if (hidden) flow.value + key else flow.value - key
        if (next == flow.value) return
        flow.value = next
        AddonStorage.saveStartupCache(profileId, HIDDEN_TITLES_CACHE, encodeHidden(next))
    }

    fun isHidden(profileId: Int, type: String, id: String): Boolean =
        hiddenKey(type, id) in hiddenTitleKeys(profileId)

    fun clearHidden(profileId: Int) {
        val flow = hiddenFlow(profileId)
        if (flow.value.isEmpty()) return
        flow.value = emptySet()
        AddonStorage.saveStartupCache(profileId, HIDDEN_TITLES_CACHE, encodeHidden(emptySet()))
    }

    /**
     * Adds the titles an exclusion list names, such as the `exclude/meta.json` the Stremio
     * ecosystem ships, and returns how many keys were new.
     */
    fun importHiddenTitles(profileId: Int, payload: String): Int {
        val keys = HiddenTitleImportParser.parse(payload)
        if (keys.isEmpty()) return 0
        val flow = hiddenFlow(profileId)
        val next = flow.value + keys
        val added = next.size - flow.value.size
        if (added == 0) return 0
        flow.value = next
        AddonStorage.saveStartupCache(profileId, HIDDEN_TITLES_CACHE, encodeHidden(next))
        notifyChanged()
        return added
    }

    /** True when the profile asked for adult-flagged sources to stay out of every list. */
    fun hidesAdultAddons(profileId: Int): Boolean = hideAdultFlow(profileId).value

    fun setHideAdultAddons(profileId: Int, hide: Boolean) {
        val flow = hideAdultFlow(profileId)
        if (flow.value == hide) return
        flow.value = hide
        AddonStorage.saveStartupCache(profileId, HIDE_ADULT_CACHE, if (hide) "1" else "0")
    }

    /** Bumps [revision] so the screens that read these settings recompute. */
    fun notifyChanged() {
        _revision.value = _revision.value + 1
    }

    /** Forgets both settings of [profileId], used when the profile's local data is cleared. */
    fun clear(profileId: Int) {
        hiddenByProfile.remove(profileId)
        hideAdultByProfile.remove(profileId)
        AddonStorage.saveStartupCache(profileId, HIDDEN_TITLES_CACHE, "[]")
        AddonStorage.saveStartupCache(profileId, HIDE_ADULT_CACHE, "0")
        notifyChanged()
    }

    fun hiddenKey(type: String, id: String): String = "${type.trim().lowercase()}:${id.trim()}"

    private fun loadHidden(profileId: Int): Set<String> =
        AddonStorage.loadStartupCache(profileId, HIDDEN_TITLES_CACHE)
            ?.let(::decodeHidden)
            ?: emptySet()

    private fun loadHideAdult(profileId: Int): Boolean =
        AddonStorage.loadStartupCache(profileId, HIDE_ADULT_CACHE) == "1"

    private fun decodeHidden(payload: String): Set<String> =
        runCatching {
            (Json.parseToJsonElement(payload) as JsonArray)
                .mapNotNull { element -> (element as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
                .toSet()
        }.getOrDefault(emptySet())

    private fun encodeHidden(keys: Set<String>): String =
        buildJsonArray {
            keys.sorted().forEach { key -> add(key) }
        }.toString()
}

/** Drops the titles this profile hid, keeping the order of the rest. */
internal fun List<MetaPreview>.excludingHidden(hiddenKeys: Set<String>): List<MetaPreview> {
    if (hiddenKeys.isEmpty()) return this
    return filterNot { item -> ContentFilterRepository.hiddenKey(item.type, item.id) in hiddenKeys }
}

/**
 * The enabled add-ons, minus the ones whose manifest declares itself adult, when [hideAdult] is on.
 *
 * The flag is the source's own declaration, so honouring it costs nothing: a source that marks
 * itself adult is left out of the home shelves and of search entirely.
 */
internal fun List<ManagedAddon>.excludingAdultFlagged(hideAdult: Boolean): List<ManagedAddon> {
    val enabled = enabledAddons()
    if (!hideAdult) return enabled
    return enabled.filterNot { addon -> addon.manifest?.behaviorHints?.adult == true }
}
