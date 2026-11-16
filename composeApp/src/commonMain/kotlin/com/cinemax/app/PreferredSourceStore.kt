package com.cinemax.app

import com.russhwolf.settings.Settings

/**
 * The source the user pinned for a title. The detail screen and the player put it
 * first every time, so a preferred link survives new sessions and re-ranking.
 */
class PreferredSourceStore(
    private val settings: Settings = Settings()
) {
    fun load(itemId: String): String? =
        settings.getStringOrNull(key(itemId))?.takeIf { it.isNotBlank() }

    fun save(itemId: String, url: String) {
        if (url.isBlank()) return
        settings.putString(key(itemId), url)
    }

    fun clear(itemId: String) = settings.remove(key(itemId))

    private fun key(itemId: String) = "cinemax.pinned.source.$itemId"
}
