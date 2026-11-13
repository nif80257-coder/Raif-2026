package com.cinemax.app.core

import com.cinemax.app.WatchProgress

/**
 * What a poster can say about a title the user already touched: how far it was
 * watched, and whether it is in the favourites list.
 */
data class PosterStatus(val progress: Float?, val saved: Boolean)

/**
 * One status per title id, built from the resume history and the favourites.
 * A series keeps progress per episode, so the most recently updated entry wins
 * and the poster reflects the episode that was watched last.
 */
fun posterStatusById(
    progress: List<WatchProgress>,
    favoriteIds: Set<String>
): Map<String, PosterStatus> {
    val latest = LinkedHashMap<String, Float>()
    for (entry in progress.sortedByDescending { it.updatedAtEpochMillis }) {
        if (!latest.containsKey(entry.item.id)) latest[entry.item.id] = entry.progressFraction
    }
    return (latest.keys + favoriteIds).associateWith { id ->
        PosterStatus(
            progress = latest[id]?.takeIf { it > 0.005f && it < 0.999f },
            saved = id in favoriteIds
        )
    }
}
