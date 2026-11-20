package com.nuvio.app.features.search

import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.stableKey

/**
 * Word-based title matching for the search screen.
 *
 * Every source sifts its own catalogue, and they disagree about how literal to be:
 * a two-word query can come back with a whole page of titles that contain only one
 * of the words. Ordering the results locally by how well the words cover the name
 * keeps what the user actually typed on top, without discarding the rest.
 */

private val ARABIC_DIACRITICS = charArrayOf(
    '\u064B', '\u064C', '\u064D', '\u064E', '\u064F',
    '\u0650', '\u0651', '\u0652', '\u0653', '\u0654',
    '\u0655', '\u0670',
)

/** Lower-cased, without Arabic diacritics or tatweel, with the letter forms folded. */
internal fun normalizeSearchText(value: String): String {
    val builder = StringBuilder(value.length)
    for (character in value) {
        when {
            character in ARABIC_DIACRITICS || character == '\u0640' -> Unit
            character == '\u0622' || character == '\u0623' || character == '\u0625' || character == '\u0627' ->
                builder.append('\u0627')

            character == '\u0649' -> builder.append('\u064A')
            character == '\u0629' -> builder.append('\u0647')
            // Punctuation and separators are word breaks, so "Spider-Man" and "Spider Man" both
            // reduce to the same words instead of one title keeping a hyphen in the middle.
            character.isLetterOrDigit() -> builder.append(character.lowercaseChar())
            else -> builder.append(' ')
        }
    }
    return builder.toString()
        .split(' ', '\t', '\n', '\u00A0')
        .filter { word -> word.isNotBlank() }
        .joinToString(separator = " ")
}

/**
 * The score of [name] for [normalizedQuery], or null when one of the query words is
 * not in the name at all. A higher score means a closer match: the whole query, then
 * its start, then a first word, then the words in the order they were typed.
 */
internal fun searchWordScore(name: String, normalizedQuery: String): Int? {
    if (normalizedQuery.isBlank()) return 0
    val normalizedName = normalizeSearchText(name)
    if (normalizedName.isBlank()) return null

    val words = normalizedQuery.split(' ').filter { word -> word.isNotBlank() }
    if (words.isEmpty()) return 0
    // A title can be written with or without the space ("Spider-Man" / "Spiderman"), so the
    // compact forms are compared as well before the title is called unrelated.
    val compactName = normalizedName.replace(" ", "")
    val compactQuery = normalizedQuery.replace(" ", "")
    val allWordsPresent = words.all { word ->
        normalizedName.contains(word) || compactName.contains(word)
    }
    if (!allWordsPresent && !compactName.contains(compactQuery)) return null

    var score = words.size * 10
    score += when {
        normalizedName == normalizedQuery -> 100
        normalizedName.startsWith(normalizedQuery) -> 60
        normalizedName.split(' ').firstOrNull()?.startsWith(words.first()) == true -> 30
        else -> 0
    }
    if (wordsAppearInOrder(normalizedName, words)) score += 15
    return score
}

/**
 * Matching titles first, best first, with the unrelated ones dropped when at least one
 * result does match the query. A source that answers a search with a whole page of titles
 * containing none of the typed words used to fill the screen under the real matches; those
 * now stay out of the result once a real match exists. When nothing matches (the catalogue
 * names titles in another language, say), the server's list is kept as it is so the query
 * still shows what the source offered.
 */
internal fun <T> rankByWordMatch(
    items: List<T>,
    query: String,
    title: (T) -> String,
): List<T> {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank() || items.size < 2) return items

    val scored = items.map { item -> item to searchWordScore(title(item), normalizedQuery) }
    val matched = scored.filter { (_, score) -> score != null }
    if (matched.isEmpty()) return items

    return matched
        .sortedByDescending { (_, score) -> score ?: 0 }
        .map { (item, _) -> item }
}

private fun wordsAppearInOrder(normalizedName: String, words: List<String>): Boolean {
    var cursor = 0
    for (word in words) {
        val index = normalizedName.indexOf(word, startIndex = cursor)
        if (index < 0) return false
        cursor = index + word.length
    }
    return true
}

/**
 * Keeps one copy of each title across the sections a search returned.
 *
 * A query reaches every installed catalogue, and several of them describe the same film or series
 * — Cinemeta and ArabCinemeta list the same IMDb id — so the same poster was drawn once per
 * catalogue that answered. The first section to offer a title keeps it, and a section left with
 * nothing drops out instead of leaving an empty header.
 */
internal fun List<HomeCatalogSection>.deduplicatedAcrossSections(): List<HomeCatalogSection> {
    if (size < 2) return this
    val seenKeys = mutableSetOf<String>()
    return map { section ->
        section.copy(items = section.items.filter { item -> seenKeys.add(item.stableKey()) })
    }.filter { section -> section.items.isNotEmpty() }
}
