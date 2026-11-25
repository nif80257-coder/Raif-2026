package com.nuvio.app.features.library.sync

import com.nuvio.app.core.auth.isCurrentSupabaseAccount
import com.nuvio.app.core.auth.requireSupabaseAccountUserId
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibrarySyncMetadata
import com.nuvio.app.features.library.decodeLibrarySyncMetadata
import com.nuvio.app.features.library.normalizeSupabaseContentType
import com.nuvio.app.features.library.toLibraryItem
import com.nuvio.app.features.library.toSafeSyncMetadataJson
import io.github.jan.supabase.postgrest.postgrest
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val FAVORITES_PAGE_SIZE = 900

object SupabaseLibrarySyncAdapter : LibrarySyncAdapter {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val supportsDelta: Boolean = false

    override suspend fun pullSnapshot(
        profileId: Int,
        pageSize: Int,
    ): List<LibraryItem> {
        val userId = requireSupabaseAccountUserId()
        val items = mutableListOf<LibraryItem>()
        var offset = 0L
        val requestedPageSize = pageSize.coerceAtLeast(1).coerceAtMost(FAVORITES_PAGE_SIZE)
        while (true) {
            val rows = SupabaseProvider.client.postgrest.from("favorites")
                .select {
                    filter { eq("user_id", userId) }
                    range(offset, offset + requestedPageSize.toLong() - 1L)
                }
                .decodeList<FavoriteRow>()
            if (!isCurrentSupabaseAccount(userId)) {
                throw kotlinx.coroutines.CancellationException("Account changed during favorites sync")
            }
            items += rows.map { row ->
                val contentType = normalizeSupabaseContentType(row.contentType)
                val metadata = runCatching {
                    decodeLibrarySyncMetadata(json, row.metadata)
                }.getOrDefault(LibrarySyncMetadata())
                metadata.toLibraryItem(
                    contentId = row.contentId,
                    contentType = contentType,
                    fallbackSavedAtEpochMs = parseTimestamp(row.createdAt),
                )
            }
            if (rows.size < requestedPageSize) break
            offset += rows.size
        }
        return items
    }

    override suspend fun getDeltaCursor(profileId: Int): Long = 0L

    override suspend fun pullDelta(
        profileId: Int,
        sinceEventId: Long,
        limit: Int,
    ): List<LibraryDeltaEvent> = emptyList()

    override suspend fun pushItems(
        profileId: Int,
        items: Collection<LibraryItem>,
    ) {
        val userId = requireSupabaseAccountUserId()
        val rows = items
            .distinctBy { item -> "${item.id}|${normalizeSupabaseContentType(item.type)}" }
            .map { item ->
                val contentType = normalizeSupabaseContentType(item.type)
                require(contentType in setOf("movie", "series", "episode")) {
                    "This content type cannot be saved to favorites."
                }
                FavoriteWriteRow(
                    userId = userId,
                    contentId = item.id,
                    contentType = contentType,
                    metadata = item.toSafeSyncMetadataJson(json),
                )
            }
        if (rows.isEmpty()) return
        SupabaseProvider.client.postgrest.from("favorites")
            .upsert(rows) { onConflict = "user_id,content_id,content_type" }
        check(isCurrentSupabaseAccount(userId)) { "Account changed during favorites sync" }
    }

    override suspend fun deleteItems(
        profileId: Int,
        keys: Collection<LibrarySyncKey>,
    ) {
        val userId = requireSupabaseAccountUserId()
        keys.distinctBy { key -> "${key.contentId}|${normalizeSupabaseContentType(key.contentType)}" }
            .forEach { key ->
                SupabaseProvider.client.postgrest.from("favorites").delete {
                    filter {
                        eq("user_id", userId)
                        eq("content_id", key.contentId)
                        eq("content_type", normalizeSupabaseContentType(key.contentType))
                    }
                }
                check(isCurrentSupabaseAccount(userId)) { "Account changed during favorites sync" }
            }
    }
}

@Serializable
private data class FavoriteRow(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val metadata: JsonObject = JsonObject(emptyMap()),
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
private data class FavoriteWriteRow(
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val metadata: JsonObject,
)

private fun parseTimestamp(value: String): Long =
    runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrDefault(0L)

@Serializable
internal data class LibrarySyncItem(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val name: String = "",
    val poster: String? = null,
    @SerialName("poster_shape") val posterShape: String = "POSTER",
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    @SerialName("release_info") val releaseInfo: String? = null,
    @SerialName("imdb_rating") val imdbRating: Float? = null,
    val genres: List<String> = emptyList(),
    @SerialName("addon_base_url") val addonBaseUrl: String? = null,
    @SerialName("added_at") val addedAt: Long = 0,
)

@Serializable
internal data class LibraryDeltaSyncItem(
    @SerialName("event_id") val eventId: Long,
    val operation: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val name: String = "",
    val poster: String? = null,
    @SerialName("poster_shape") val posterShape: String = "POSTER",
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    @SerialName("release_info") val releaseInfo: String? = null,
    @SerialName("imdb_rating") val imdbRating: Float? = null,
    val genres: List<String> = emptyList(),
    @SerialName("addon_base_url") val addonBaseUrl: String? = null,
    @SerialName("added_at") val addedAt: Long = 0,
)

internal fun LibrarySyncItem.toLibraryItem(): LibraryItem =
    LibraryItem(
        id = contentId,
        type = contentType,
        name = name,
        poster = poster,
        banner = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating?.toString(),
        genres = genres,
        posterShape = posterShape.toPosterShape(),
        addonBaseUrl = addonBaseUrl,
        savedAtEpochMs = addedAt,
    )

internal fun LibraryDeltaSyncItem.toLibraryItem(): LibraryItem =
    LibraryItem(
        id = contentId,
        type = contentType,
        name = name,
        poster = poster,
        banner = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating?.toString(),
        genres = genres,
        posterShape = posterShape.toPosterShape(),
        addonBaseUrl = addonBaseUrl,
        savedAtEpochMs = addedAt,
    )

internal fun LibraryItem.toSyncItem(): LibrarySyncItem =
    LibrarySyncItem(
        contentId = id,
        contentType = type,
        name = name,
        poster = poster,
        posterShape = posterShape.toSyncName(),
        background = banner,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating?.toFloatOrNull(),
        genres = genres,
        addonBaseUrl = addonBaseUrl,
        addedAt = savedAtEpochMs,
    )

private fun String.toPosterShape(): PosterShape =
    when (trim().uppercase()) {
        "LANDSCAPE" -> PosterShape.Landscape
        "SQUARE" -> PosterShape.Square
        else -> PosterShape.Poster
    }

private fun PosterShape.toSyncName(): String =
    when (this) {
        PosterShape.Poster -> "POSTER"
        PosterShape.Square -> "SQUARE"
        PosterShape.Landscape -> "LANDSCAPE"
    }
