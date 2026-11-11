package com.nuvio.app.features.watching.sync

internal data class SupabaseProgressContent(
    val contentType: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

internal fun toSupabaseProgressContent(
    value: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
): SupabaseProgressContent? {
    val normalized = value.trim().lowercase()
    return when (normalized) {
        "film", "films", "movie", "movies" -> SupabaseProgressContent(contentType = "movie")
        "episode", "series", "show", "shows", "tv", "tvshow", "tvshows", "anime" -> {
            if (seasonNumber == null || episodeNumber == null) return null
            SupabaseProgressContent(
                contentType = "episode",
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
            )
        }
        else -> null
    }
}

internal fun fromSupabaseProgressContentType(value: String): String =
    when (value.trim().lowercase()) {
        "movie", "film", "films", "movies" -> "movie"
        "episode", "series", "show", "shows", "tv", "tvshow", "tvshows", "anime" -> "series"
        else -> value.trim().lowercase()
    }
