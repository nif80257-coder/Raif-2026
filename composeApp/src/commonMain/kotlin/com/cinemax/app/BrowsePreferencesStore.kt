package com.cinemax.app

import com.russhwolf.settings.Settings

/**
 * Remembers how the built-in library grid was left (genre and sort), so opening
 * it again continues where the user stopped reading instead of resetting.
 */
class BrowsePreferencesStore(
    private val settings: Settings = Settings()
) {
    fun loadGenre(): String? = settings.getStringOrNull(GENRE)?.takeIf { it.isNotBlank() }

    fun loadSort(): String? = settings.getStringOrNull(SORT)?.takeIf { it.isNotBlank() }

    fun saveGenre(genre: String?) {
        if (genre.isNullOrBlank()) settings.remove(GENRE) else settings.putString(GENRE, genre)
    }

    fun saveSort(sort: String?) {
        if (sort.isNullOrBlank()) settings.remove(SORT) else settings.putString(SORT, sort)
    }

    fun clear() {
        settings.remove(GENRE)
        settings.remove(SORT)
    }

    private companion object {
        const val GENRE = "cinemax.browse.genre"
        const val SORT = "cinemax.browse.sort"
    }
}
