package com.cinemax.app.core

/** Season and episode numbers read from a title like "S02E05". */
data class EpisodeTag(val season: Int, val episode: Int)

private val episodeTagRegex = Regex("""[s]\s*(\d{1,2})\s*[.\-_]?\s*e\s*(\d{1,3})""", RegexOption.IGNORE_CASE)

/**
 * Reads a "S02E05" / "S2 E5" / "s02.e05" tag out of an episode title. Some addons
 * send only an absolute number in the title instead of season/episode fields, and
 * this recovers the pair the rest of the app needs.
 */
fun parseEpisodeTag(text: String?): EpisodeTag? {
    if (text.isNullOrBlank()) return null
    val match = episodeTagRegex.find(text) ?: return null
    val season = match.groupValues[1].toIntOrNull() ?: return null
    val episode = match.groupValues[2].toIntOrNull() ?: return null
    return EpisodeTag(season, episode)
}
