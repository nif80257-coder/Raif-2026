package com.nuvio.app.features.search

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
        when (character) {
            in ARABIC_DIACRITICS, '\u0640' -> Unit
            '\u0622', '\u0623', '\u0625', '\u0627' -> builder.append('\u0627')
            '\u0649' -> builder.append('\u064A')
            '\u0629' -> builder.append('\u0647')
            else -> builder.append(character.lowercaseChar())
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
    if (words.any { word -> !normalizedName.contains(word) }) return null

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

/** Matches first, best first; everything else keeps the order the source returned. */
internal fun <T> rankByWordMatch(
    items: List<T>,
    query: String,
    title: (T) -> String,
): List<T> {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank() || items.size < 2) return items

    val scored = items.map { item -> item to searchWordScore(title(item), normalizedQuery) }
    if (scored.none { (_, score) -> score != null }) return items

    return scored
        .sortedByDescending { (_, score) -> score ?: -1 }
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
