package com.cinemax.app.nuvio

/** Maps Stremio item ids onto what Nuvio providers expect. */
object NuvioMediaIds {
    /**
     * Providers take a TMDB id. Item ids that already carry one
     * (`tmdb:1234` or a plain number) are used as-is.
     */
    fun explicitTmdbId(itemId: String): String? {
        val trimmed = itemId.trim()
        val candidate = if (trimmed.startsWith("tmdb:", ignoreCase = true)) trimmed.substring(5) else trimmed
        return candidate.takeIf { value -> value.isNotEmpty() && value.all { it.isDigit() } }
    }

    /** Nuvio providers use "tv" for series and "movie" for everything else they support. */
    fun mediaType(itemType: String): String = when (itemType.lowercase()) {
        "series", "tv", "show" -> "tv"
        else -> "movie"
    }
}
