package com.nuvio.app.features.addons

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

/**
 * Sources the user never wants paused automatically.
 *
 * The failure memory pauses a source for six hours after three failed fetches. That is the right
 * default for a dead host, but a source that is merely slow, rate limited or geo blocked can hit
 * the same streak, and losing it silently for hours is worse than waiting for it. This store keeps
 * the exceptions per profile, so the pause logic skips them.
 */
object AddonPauseExceptions {
    private const val CACHE_NAME = "addon-pause-exceptions"

    private val flowsByProfile = mutableMapOf<Int, MutableStateFlow<Set<String>>>()

    private fun flowFor(profileId: Int): MutableStateFlow<Set<String>> =
        flowsByProfile.getOrPut(profileId) {
            MutableStateFlow(loadPersisted(profileId))
        }

    /** Every manifest URL that must never be paused for [profileId]. */
    fun neverPausedManifestUrls(profileId: Int): Set<String> = flowFor(profileId).value

    fun isNeverPaused(profileId: Int, manifestUrl: String): Boolean =
        manifestUrl in neverPausedManifestUrls(profileId)

    fun setNeverPaused(profileId: Int, manifestUrl: String, neverPaused: Boolean) {
        if (manifestUrl.isBlank()) return
        val flow = flowFor(profileId)
        val next = if (neverPaused) flow.value + manifestUrl else flow.value - manifestUrl
        if (next == flow.value) return
        flow.value = next
        AddonStorage.saveStartupCache(profileId, CACHE_NAME, encode(next))
    }

    /** Forgets every exception of [profileId], used when the profile's local data is cleared. */
    fun clear(profileId: Int) {
        flowsByProfile.remove(profileId)
        AddonStorage.saveStartupCache(profileId, CACHE_NAME, encode(emptySet()))
    }

    private fun loadPersisted(profileId: Int): Set<String> =
        AddonStorage.loadStartupCache(profileId, CACHE_NAME)
            ?.let(::decode)
            ?: emptySet()

    private fun decode(payload: String): Set<String> =
        runCatching {
            (Json.parseToJsonElement(payload) as JsonArray)
                .mapNotNull { element -> (element as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
                .toSet()
        }.getOrDefault(emptySet())

    private fun encode(urls: Set<String>): String =
        buildJsonArray {
            urls.forEach { url -> add(url) }
        }.toString()
}
