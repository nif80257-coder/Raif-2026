package com.cinemax.app.core

import com.cinemax.app.addons.CatalogEntry

/**
 * Orders search results by how closely the title matches the query: an exact
 * name first, then names that start with it, then names that contain it, then
 * everything else. The sort is stable, so inside one group the addons' own
 * order is kept and results never jump around between identical queries.
 */
fun rankSearchResults(results: List<CatalogEntry>, query: String): List<CatalogEntry> {
    val needle = normalizeForSearch(query)
    if (needle.isEmpty()) return results
    return results.sortedBy { matchRank(it.item.name, needle) }
}

private fun matchRank(name: String, needle: String): Int {
    val haystack = normalizeForSearch(name)
    return when {
        haystack == needle -> 0
        haystack.startsWith(needle) -> 1
        haystack.contains(needle) -> 2
        else -> 3
    }
}

/**
 * Folds what people type inconsistently in Arabic: the harakat, the alef forms,
 * alef maqsura versus ya, ta marbuta versus ha, and the tatweel. Without this,
 * "الاسد" would not match "الأسد" and a search would look broken.
 */
fun normalizeForSearch(value: String): String = buildString {
    for (raw in value.trim().lowercase()) {
        val char = when (raw) {
            '\u0623', '\u0625', '\u0622' -> '\u0627'
            '\u0649' -> '\u064A'
            '\u0629' -> '\u0647'
            '\u0640' -> ' '
            else -> raw
        }
        if (char in '\u064B'..'\u0652') continue
        append(char)
    }
}.trim()
