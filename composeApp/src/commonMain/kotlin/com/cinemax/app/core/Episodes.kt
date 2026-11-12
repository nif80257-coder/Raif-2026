package com.cinemax.app.core

import com.cinemax.app.addons.StremioVideo

/**
 * Orders episodes by season then episode number and returns the one that follows
 * [current], or null when the current episode is the last (or unknown).
 */
fun nextEpisode(current: StremioVideo?, videos: List<StremioVideo>): StremioVideo? {
    if (videos.isEmpty()) return null
    val ordered = videos.sortedWith(
        compareBy({ it.season ?: Int.MAX_VALUE }, { it.episode ?: Int.MAX_VALUE })
    )
    val index = ordered.indexOfFirst { it.id == current?.id }
    return if (index >= 0) ordered.getOrNull(index + 1) else null
}

/** "الموسم 1 • الحلقة 3 — Episode title", built from whatever the addon sends. */
fun episodeLabel(video: StremioVideo): String {
    val position = when {
        video.season != null && video.episode != null -> "الموسم ${video.season} • الحلقة ${video.episode}"
        video.episode != null -> "الحلقة ${video.episode}"
        else -> null
    }
    val title = video.name?.takeIf { it.isNotBlank() } ?: video.title?.takeIf { it.isNotBlank() }
    return listOfNotNull(position, title).joinToString(" — ").ifEmpty { "حلقة" }
}
