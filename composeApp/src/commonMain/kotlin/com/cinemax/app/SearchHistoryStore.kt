package com.cinemax.app

import com.russhwolf.settings.Settings

/** The last few queries, offered under the search field like Nuvio does. */
class SearchHistoryStore(
    private val settings: Settings = Settings()
) {
    fun load(): List<String> = runCatching {
        settings.getString(KEY, "")
            .split('\u001f')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val next = (listOf(trimmed) + load().filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(MAX_ITEMS)
        settings.putString(KEY, next.joinToString("\u001f"))
    }

    fun clear() = settings.remove(KEY)

    private companion object {
        const val KEY = "cinemax.search.history.v1"
        const val MAX_ITEMS = 8
    }
}
