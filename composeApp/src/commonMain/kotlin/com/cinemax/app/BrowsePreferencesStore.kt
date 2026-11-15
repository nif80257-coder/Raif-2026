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

    /** The library order, by enum name; null means the default (الأحدث مشاهدة). */
    fun loadLibrarySort(): String? = settings.getStringOrNull(LIBRARY_SORT)?.takeIf { it.isNotBlank() }

    fun saveLibrarySort(sort: String) {
        settings.putString(LIBRARY_SORT, sort)
    }

    /** The search tab's last type filter ("movie", "series" or "all"). */
    fun loadSearchType(): String? = settings.getStringOrNull(SEARCH_TYPE)?.takeIf { it.isNotBlank() }

    fun saveSearchType(type: String) {
        settings.putString(SEARCH_TYPE, type)
    }

    fun saveGenre(genre: String?) {
        if (genre.isNullOrBlank()) settings.remove(GENRE) else settings.putString(GENRE, genre)
    }

    fun saveSort(sort: String?) {
        if (sort.isNullOrBlank()) settings.remove(SORT) else settings.putString(SORT, sort)
    }

    fun clear() {
        settings.remove(GENRE)
        settings.remove(SORT)
        settings.remove(LIBRARY_SORT)
        settings.remove(SEARCH_TYPE)
    }

    private companion object {
        const val GENRE = "cinemax.browse.genre"
        const val SORT = "cinemax.browse.sort"
        const val LIBRARY_SORT = "cinemax.library.sort"
        const val SEARCH_TYPE = "cinemax.search.type"
    }
}
