package com.nuvio.app.features.watching.sync

internal data class SupabaseWatchedContent(
    val contentType: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

internal fun toSupabaseWatchedContent(
    value: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
): SupabaseWatchedContent? {
    val normalized = value.trim().lowercase()
    return when {
        normalized in setOf("movie", "movies", "film", "films") -> SupabaseWatchedContent("movie")
        normalized in setOf("episode", "series", "show", "shows", "tv", "tvshow", "tvshows", "anime") -> {
            if (seasonNumber == null || episodeNumber == null) return null
            SupabaseWatchedContent("episode", seasonNumber, episodeNumber)
        }
        seasonNumber != null && episodeNumber != null ->
            SupabaseWatchedContent("episode", seasonNumber, episodeNumber)
        else -> null
    }
}

internal fun fromSupabaseWatchedContentType(value: String): String =
    when (value.trim().lowercase()) {
        "movie", "movies", "film", "films" -> "movie"
        "episode", "series", "show", "shows", "tv", "tvshow", "tvshows", "anime" -> "series"
        else -> value.trim().lowercase()
    }
