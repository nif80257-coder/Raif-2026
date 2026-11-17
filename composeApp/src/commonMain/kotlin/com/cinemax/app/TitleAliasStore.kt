package com.cinemax.app

import com.russhwolf.settings.Settings

/**
 * Every media id the app has seen for one title, keyed by its canonical identity.
 * When the same film is opened from a second catalog under a different id, the
 * new id is remembered, so the source sheet can ask every catalog's id at once
 * and one page still gathers the sources of both.
 */
class TitleAliasStore(
    private val settings: Settings = Settings()
) {
    fun ids(canonicalKey: String): List<String> {
        val raw = settings.getStringOrNull(key(canonicalKey)) ?: return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    /** Records [mediaId] for the identity and returns every known id, current first. */
    fun add(canonicalKey: String, mediaId: String): List<String> {
        if (canonicalKey.isBlank() || mediaId.isBlank()) return ids(canonicalKey)
        val known = ids(canonicalKey)
        if (mediaId in known) {
            return listOf(mediaId) + known.filterNot { it == mediaId }
        }
        val next = (listOf(mediaId) + known).take(MAX_IDS)
        settings.putString(key(canonicalKey), next.joinToString("\n"))
        return next
    }

    fun clear() {
        settings.keys.filter { it.startsWith(PREFIX) }.forEach { settings.remove(it) }
    }

    private fun key(canonicalKey: String) = PREFIX + canonicalKey

    private companion object {
        const val PREFIX = "cinemax.title.aliases."
        const val MAX_IDS = 4
    }
}
