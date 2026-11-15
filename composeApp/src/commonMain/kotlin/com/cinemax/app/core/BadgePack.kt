package com.cinemax.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One badge rule from a Fusion badge pack: a regex over the stream's name/title
 * and the image Nuvio-style clients draw when it matches.
 */
@Serializable
data class BadgeFilter(
    val id: String = "",
    val name: String = "",
    val pattern: String = "",
    val imageURL: String? = null,
    val isEnabled: Boolean = true,
    val type: String = "filter"
)

@Serializable
private data class BadgePackFile(val filters: List<BadgeFilter> = emptyList())

private val badgeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** Parses a badge pack manifest, keeping only enabled rules that can be drawn. */
fun parseBadgePack(raw: String): List<BadgeFilter> = runCatching {
    badgeJson.decodeFromString(BadgePackFile.serializer(), raw)
        .filters
        .filter { it.isEnabled && !it.imageURL.isNullOrBlank() }
}.getOrDefault(emptyList())

/**
 * The badges whose pattern matches the stream text, at most [limit]. A rule whose
 * regex is invalid is skipped instead of breaking the whole list.
 */
fun matchingBadges(filters: List<BadgeFilter>, text: String, limit: Int = 6): List<BadgeFilter> {
    if (text.isBlank()) return emptyList()
    val found = mutableListOf<BadgeFilter>()
    for (filter in filters) {
        if (found.size >= limit) break
        val matches = runCatching { Regex(filter.pattern).containsMatchIn(text) }.getOrDefault(false)
        if (matches) found.add(filter)
    }
    return found
}

/** Packs offered in the settings, Arabic label to raw manifest URL. */
val BADGE_PACKS: List<Pair<String, String>> = listOf(
    "زجاجي (الافتراضي)" to "https://raw.githubusercontent.com/farizmohd70/FR-Badges/main/badges.json",
    "نيون" to "https://raw.githubusercontent.com/farizmohd70/FR-Badges/main/badges-neon.json",
    "صلب" to "https://raw.githubusercontent.com/farizmohd70/FR-Badges/main/badges-solid.json",
    "خفيف" to "https://raw.githubusercontent.com/farizmohd70/FR-Badges/main/badges-light.json"
)
