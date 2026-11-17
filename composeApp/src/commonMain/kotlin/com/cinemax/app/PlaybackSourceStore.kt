package com.cinemax.app

import com.russhwolf.settings.Settings

/**
 * Remembers the last link that actually played for a title, so the source sheet
 * opens with it on top ("reuse last link" in Nuvio's player).
 */
class PlaybackSourceStore(
    private val settings: Settings = Settings()
) {
    fun load(itemId: String): String? = settings.getStringOrNull(key(itemId))

    /**
     * The identity of the file that played last. A link's token can change between
     * sessions, so the fingerprint — not the URL — is what survives.
     */
    fun loadFingerprint(itemId: String): String? =
        settings.getStringOrNull(key(itemId) + FINGERPRINT_SUFFIX)?.takeIf { it.isNotBlank() }

    fun save(itemId: String, url: String, fingerprint: String? = null) {
        if (url.isBlank()) return
        settings.putString(key(itemId), url)
        if (!fingerprint.isNullOrBlank()) {
            settings.putString(key(itemId) + FINGERPRINT_SUFFIX, fingerprint)
        }
    }

    fun clear(itemId: String) {
        settings.remove(key(itemId))
        settings.remove(key(itemId) + FINGERPRINT_SUFFIX)
    }

    private fun key(itemId: String) = "cinemax.last.source.$itemId"

    private companion object {
        const val FINGERPRINT_SUFFIX = ".fingerprint"
    }
}
