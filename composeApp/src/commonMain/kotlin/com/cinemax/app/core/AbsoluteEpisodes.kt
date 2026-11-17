package com.cinemax.app.core

/** A season/episode pair derived from an absolute episode number. */
data class SeasonEpisode(val season: Int, val episode: Int)

/**
 * Maps an absolute episode number (1, 2, 3 … across the whole series) to a
 * season/episode pair using the episode count of each season, which is the shape
 * TMDB returns. Returns null when the number is outside the series.
 */
fun absoluteToSeasonEpisode(seasonLengths: List<Int>, absolute: Int): SeasonEpisode? {
    if (absolute <= 0) return null
    var remaining = absolute
    for ((index, length) in seasonLengths.withIndex()) {
        if (length <= 0) continue
        if (remaining <= length) return SeasonEpisode(season = index + 1, episode = remaining)
        remaining -= length
    }
    return null
}

/** The episode counts of seasons 1..n, as the mapping above needs them. */
fun seasonLengthsOf(episodeCountBySeason: Map<Int, Int>): List<Int> {
    val maxSeason = episodeCountBySeason.keys.maxOrNull() ?: return emptyList()
    return (1..maxSeason).map { episodeCountBySeason[it] ?: 0 }
}
