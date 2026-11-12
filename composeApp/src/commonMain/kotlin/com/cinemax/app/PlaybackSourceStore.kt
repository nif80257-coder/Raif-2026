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

    fun save(itemId: String, url: String) {
        if (url.isBlank()) return
        settings.putString(key(itemId), url)
    }

    fun clear(itemId: String) = settings.remove(key(itemId))

    private fun key(itemId: String) = "cinemax.last.source.$itemId"
}
