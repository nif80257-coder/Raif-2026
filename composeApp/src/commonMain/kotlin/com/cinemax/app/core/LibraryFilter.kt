package com.cinemax.app.core

/**
 * Whether a title's name matches what the user typed in the library search
 * field. An empty query matches everything. It uses the same Arabic folding as
 * the network search, so «الاسد» finds «الأسد» in the saved list too.
 */
fun matchesLibraryQuery(name: String, query: String): Boolean {
    val needle = normalizeForSearch(query)
    return needle.isEmpty() || normalizeForSearch(name).contains(needle)
}
