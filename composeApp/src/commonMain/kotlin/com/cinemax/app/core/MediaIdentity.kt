package com.cinemax.app.core

import com.cinemax.app.addons.StremioMetaPreview

/**
 * One identity per title, so the same film opened from two catalogs shares its
 * favourite, its resume point and its last link even when the catalogs number it
 * differently ("tt1234567" vs "tmdb:550" vs a local id).
 *
 * The IMDb id wins when present, then a TMDB id, otherwise the normalised title
 * plus the release year, which is the fallback the plan asked for. A title with no
 * year anywhere is keyed by its name alone, which is the one case where a remake
 * could merge — the trade-off the plan accepts.
 */
fun canonicalMediaKey(item: StremioMetaPreview): String {
    imdbIdOf(item.id)?.let { return "imdb:$it" }
    tmdbIdOf(item.id)?.let { return "tmdb:$it" }
    val name = normalizeForSearch(item.name)
    val year = releaseYear(item.releaseInfo) ?: releaseYear(item.name)
    return if (year != null) "title:$name:$year" else "title:$name"
}

/** The IMDb id inside a Stremio id ("tt0944947:1:2" included), if any. */
fun imdbIdOf(id: String): String? {
    val base = id.substringBefore(':')
    if (!base.startsWith("tt") || base.length <= 2) return null
    if (!base.drop(2).all { it.isDigit() }) return null
    return base
}

/** The TMDB id inside "tmdb:550" or "tmdb:550:1:2". */
fun tmdbIdOf(id: String): String? {
    val parts = id.split(':')
    if (parts.size < 2) return null
    if (!parts[0].equals("tmdb", ignoreCase = true)) return null
    val digits = parts[1]
    if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
    return digits
}

/** The first four-digit year in free text ("2019", "2019–2021", "2019 • 1h 40m"). */
fun releaseYear(text: String?): String? {
    if (text.isNullOrBlank()) return null
    return yearRegex.find(text)?.value
}

private val yearRegex = Regex("""\b(19\d{2}|20\d{2})\b""")

/**
 * Whether two entries describe the same title: an identical id or identity, or the
 * same type and folded name with years within one year of each other.
 */
fun sameMedia(a: StremioMetaPreview, b: StremioMetaPreview): Boolean {
    if (a.id == b.id) return true
    if (canonicalMediaKey(a) == canonicalMediaKey(b)) return true
    if (a.type != b.type) return false
    val nameA = normalizeForSearch(a.name)
    val nameB = normalizeForSearch(b.name)
    if (nameA.isEmpty() || nameA != nameB) return false
    val yearA = releaseYear(a.releaseInfo)?.toIntOrNull()
    val yearB = releaseYear(b.releaseInfo)?.toIntOrNull()
    if (yearA != null && yearB != null) {
        val difference = yearA - yearB
        return difference in -1..1
    }
    return true
}

/** Collapses entries that describe the same title, keeping the first of each. */
fun distinctTitles(items: List<StremioMetaPreview>): List<StremioMetaPreview> {
    val kept = mutableListOf<StremioMetaPreview>()
    for (item in items) {
        if (kept.none { sameMedia(it, item) }) kept.add(item)
    }
    return kept
}
