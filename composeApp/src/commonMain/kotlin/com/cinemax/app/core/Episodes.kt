package com.cinemax.app.core

import com.cinemax.app.addons.StremioVideo

/** Season/episode from the fields the addon sent, else from an "S02E05" title. */
private fun tagFor(video: StremioVideo): EpisodeTag? =
    parseEpisodeTag(video.name) ?: parseEpisodeTag(video.title)

private fun effectiveSeason(video: StremioVideo): Int? = video.season ?: tagFor(video)?.season

private fun effectiveEpisode(video: StremioVideo): Int? = video.episode ?: tagFor(video)?.episode

/**
 * Orders episodes by season then episode number, reading an "S02E05" tag from the
 * title when the addon left the fields empty, and returns the one that follows
 * [current], or null when the current episode is the last (or unknown).
 */
fun nextEpisode(current: StremioVideo?, videos: List<StremioVideo>): StremioVideo? {
    if (videos.isEmpty()) return null
    val ordered = videos.sortedWith(
        compareBy({ effectiveSeason(it) ?: Int.MAX_VALUE }, { effectiveEpisode(it) ?: Int.MAX_VALUE })
    )
    val index = ordered.indexOfFirst { it.id == current?.id }
    return if (index >= 0) ordered.getOrNull(index + 1) else null
}

/** "الموسم 1 • الحلقة 3 — Episode title", built from whatever the addon sends. */
fun episodeLabel(video: StremioVideo): String {
    val season = effectiveSeason(video)
    val episode = effectiveEpisode(video)
    val position = when {
        season != null && episode != null -> "الموسم $season • الحلقة $episode"
        episode != null -> "الحلقة $episode"
        else -> null
    }
    val title = video.name?.takeIf { it.isNotBlank() } ?: video.title?.takeIf { it.isNotBlank() }
    return listOfNotNull(position, title).joinToString(" — ").ifEmpty { "حلقة" }
}
