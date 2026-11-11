package com.nuvio.app.features.library

import com.nuvio.app.features.home.PosterShape
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
internal data class LibrarySyncMetadata(
    val name: String = "",
    val poster: String? = null,
    val banner: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val posterShape: String = "POSTER",
    val savedAtEpochMs: Long = 0L,
)

internal fun LibraryItem.toSafeSyncMetadata(): LibrarySyncMetadata = LibrarySyncMetadata(
    name = name,
    poster = poster,
    banner = banner,
    logo = logo,
    description = description,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating,
    genres = genres,
    posterShape = when (posterShape) {
        PosterShape.Poster -> "POSTER"
        PosterShape.Square -> "SQUARE"
        PosterShape.Landscape -> "LANDSCAPE"
    },
    savedAtEpochMs = savedAtEpochMs,
)

internal fun LibrarySyncMetadata.toLibraryItem(
    contentId: String,
    contentType: String,
    fallbackSavedAtEpochMs: Long = 0L,
): LibraryItem = LibraryItem(
    id = contentId,
    type = contentType,
    name = name,
    poster = poster,
    banner = banner,
    logo = logo,
    description = description,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating,
    genres = genres,
    posterShape = when (posterShape.trim().uppercase()) {
        "SQUARE" -> PosterShape.Square
        "LANDSCAPE" -> PosterShape.Landscape
        else -> PosterShape.Poster
    },
    savedAtEpochMs = savedAtEpochMs.takeIf { it > 0L } ?: fallbackSavedAtEpochMs,
)

internal fun LibraryItem.toSafeSyncMetadataJson(json: Json): JsonObject =
    json.encodeToJsonElement(LibrarySyncMetadata.serializer(), toSafeSyncMetadata()).jsonObject

internal fun decodeLibrarySyncMetadata(json: Json, metadata: JsonObject): LibrarySyncMetadata =
    json.decodeFromJsonElement(LibrarySyncMetadata.serializer(), metadata)

internal fun normalizeSupabaseContentType(type: String): String =
    when (type.trim().lowercase()) {
        "film", "films", "movie", "movies" -> "movie"
        "series", "show", "shows", "tv", "tvshow", "tvshows", "anime" -> "series"
        "episode", "episodes" -> "episode"
        else -> type.trim().lowercase()
    }
