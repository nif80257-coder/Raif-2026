package com.nuvio.app.features.addons

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * User-chosen values for an add-on catalog's declared `extra` properties.
 *
 * Stremio add-ons advertise optional catalog extras (genre, language, region, ...).
 * This store keeps one chosen value per (manifest, catalog, extra) and applies it
 * when that catalog is requested, so the user can pin a catalog to a subset
 * without re-selecting it on every visit.
 *
 * Values live in the profile-scoped startup cache, so they survive relaunches
 * and stay per profile.
 */
object AddonCatalogExtraValues {
    private const val CACHE_NAME = "catalog-extra-values"

    private val flowsByProfile = mutableMapOf<Int, MutableStateFlow<Map<String, String>>>()

    fun storageKey(
        manifestUrl: String,
        catalogId: String,
        type: String,
        extraName: String,
    ): String =
        "${manifestUrl.trim()}#${catalogId.trim()}#${type.trim()}#${extraName.trim()}"

    private fun flowFor(profileId: Int): MutableStateFlow<Map<String, String>> =
        flowsByProfile.getOrPut(profileId) {
            MutableStateFlow(loadPersisted(profileId))
        }

    fun values(profileId: Int): StateFlow<Map<String, String>> =
        flowFor(profileId).asStateFlow()

    /** The saved value for one extra property, or null when the add-on default applies. */
    fun value(
        profileId: Int,
        manifestUrl: String,
        catalogId: String,
        type: String,
        extraName: String,
    ): String? =
        values(profileId).value[storageKey(manifestUrl, catalogId, type, extraName)]

    /**
     * All saved values for one catalog, keyed by the extra's name. Entries whose
     * extra no longer exists on the current manifest are dropped so a changed
     * manifest never sends stale parameters.
     */
    fun valuesForCatalog(
        profileId: Int,
        manifestUrl: String,
        catalogId: String,
        type: String,
        allowedNames: Collection<String>,
    ): Map<String, String> {
        if (allowedNames.isEmpty()) return emptyMap()
        val allowed = allowedNames.mapTo(mutableSetOf()) { it.trim().lowercase() }
        val allValues = values(profileId).value
        val keyPrefix = storageKey(manifestUrl, catalogId, type, "")
        return buildMap {
            allValues.forEach { (key, value) ->
                val extraName = key.removePrefix(keyPrefix)
                if (
                    key.startsWith(keyPrefix) &&
                    extraName.isNotBlank() &&
                    extraName.trim().lowercase() in allowed
                ) {
                    put(extraName, value)
                }
            }
        }
    }

    fun setValue(
        profileId: Int,
        manifestUrl: String,
        catalogId: String,
        type: String,
        extraName: String,
        value: String?,
    ) {
        val flow = flowFor(profileId)
        val next = flow.value.toMutableMap()
        val key = storageKey(manifestUrl, catalogId, type, extraName)
        if (value.isNullOrBlank()) {
            next.remove(key)
        } else {
            next[key] = value
        }
        flow.value = next
        AddonStorage.saveStartupCache(profileId, CACHE_NAME, encode(next))
    }

    /**
     * Forgets every saved value of one catalog, so its requests go back to the add-on
     * defaults. Entries of other catalogs of the same manifest are left alone.
     */
    fun clearCatalogValues(
        profileId: Int,
        manifestUrl: String,
        catalogId: String,
        type: String,
    ) {
        val flow = flowFor(profileId)
        val keyPrefix = storageKey(manifestUrl, catalogId, type, "")
        val next = flow.value.filterKeys { key -> !key.startsWith(keyPrefix) }
        if (next.size == flow.value.size) return
        flow.value = next
        AddonStorage.saveStartupCache(profileId, CACHE_NAME, encode(next))
    }

    private fun loadPersisted(profileId: Int): Map<String, String> =
        AddonStorage.loadStartupCache(profileId, CACHE_NAME)
            ?.let(::decode)
            ?: emptyMap()

    private fun decode(payload: String): Map<String, String> =
        runCatching {
            Json.parseToJsonElement(payload).jsonObject.entries
                .mapNotNull { (key, element) ->
                    (element as? JsonPrimitive)?.contentOrNull?.let { key to it }
                }
                .toMap()
        }.getOrDefault(emptyMap())

    private fun encode(values: Map<String, String>): String =
        buildJsonObject {
            values.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        }.toString()
}
