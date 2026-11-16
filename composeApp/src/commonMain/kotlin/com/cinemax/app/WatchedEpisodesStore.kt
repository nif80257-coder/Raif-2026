package com.cinemax.app

import com.russhwolf.settings.Settings

/**
 * Episodes the user marked as watched by hand. Kept apart from [WatchProgress],
 * which only holds real resume points and drops anything near the end.
 */
class WatchedEpisodesStore(
    private val settings: Settings = Settings()
) {
    fun load(): Set<String> = settings.getStringOrNull(KEY)
        ?.split('\n')
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet()

    fun save(ids: Set<String>) {
        if (ids.isEmpty()) settings.remove(KEY) else settings.putString(KEY, ids.joinToString("\n"))
    }

    private companion object {
        const val KEY = "cinemax.watched.episodes.v1"
    }
}
