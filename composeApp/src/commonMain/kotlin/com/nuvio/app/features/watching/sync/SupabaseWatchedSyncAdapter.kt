package com.nuvio.app.features.watching.sync

import com.nuvio.app.core.auth.isCurrentSupabaseAccount
import com.nuvio.app.core.auth.requireSupabaseAccountUserId
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.isEpisode
import com.nuvio.app.features.watched.normalizeWatchedMarkedAtEpochMs
import io.github.jan.supabase.postgrest.postgrest
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val WATCH_HISTORY_PAGE_SIZE = 900

object SupabaseWatchedSyncAdapter : WatchedSyncAdapter {
    override suspend fun getDeltaCursor(profileId: Int): Long? = null

    override suspend fun pullDelta(
        profileId: Int,
        sinceEventId: Long,
        limit: Int,
    ): List<WatchedDeltaEvent> = emptyList()

    override suspend fun pull(
        profileId: Int,
        pageSize: Int,
    ): List<WatchedItem> {
        val userId = requireSupabaseAccountUserId()
        val rows = mutableListOf<WatchHistoryRow>()
        val requestedPageSize = pageSize.coerceAtLeast(1).coerceAtMost(WATCH_HISTORY_PAGE_SIZE)
        var offset = 0L
        while (true) {
            val page = SupabaseProvider.client.postgrest.from("watch_history")
                .select {
                    filter { eq("user_id", userId) }
                    range(offset, offset + requestedPageSize.toLong() - 1L)
                }
                .decodeList<WatchHistoryRow>()
            ensureCurrentAccount(userId)
            rows += page
            if (page.size < requestedPageSize) break
            offset += page.size
        }
        return rows
            .map { row ->
                WatchedItem(
                    id = row.contentId,
                    type = fromSupabaseWatchedContentType(row.contentType),
                    name = row.title.orEmpty(),
                    poster = row.posterUrl,
                    season = row.seasonNumber,
                    episode = row.episodeNumber,
                    markedAtEpochMs = parseTimestamp(row.watchedAt),
                )
            }
            .groupBy(::stableWatchedKey)
            .values
            .mapNotNull { entries -> entries.maxByOrNull(WatchedItem::markedAtEpochMs) }
    }

    override suspend fun push(
        profileId: Int,
        items: Collection<WatchedItem>,
    ) {
        val userId = requireSupabaseAccountUserId()
        items.distinctBy(::stableWatchedKey).forEach { item ->
            ensureCurrentAccount(userId)
            val identity = toSupabaseWatchedContent(item.type, item.season, item.episode) ?: return@forEach
            val existing = findRows(userId, item.id, identity.contentType)
                .filter { row -> row.seasonNumber == identity.seasonNumber && row.episodeNumber == identity.episodeNumber }
                .maxByOrNull { row -> parseTimestamp(row.watchedAt) }
            val watchedAt = Instant.fromEpochMilliseconds(
                normalizeWatchedMarkedAtEpochMs(item.markedAtEpochMs),
            ).toString()
            if (existing == null) {
                SupabaseProvider.client.postgrest.from("watch_history")
                    .insert(
                        WatchHistoryInsertRow(
                            userId = userId,
                            contentId = item.id,
                            contentType = identity.contentType,
                            title = item.name,
                            seasonNumber = identity.seasonNumber,
                            episodeNumber = identity.episodeNumber,
                            posterUrl = item.poster,
                            addonId = null,
                            positionSeconds = 0.0,
                            durationSeconds = null,
                            watchedAt = watchedAt,
                        ),
                    )
            } else {
                SupabaseProvider.client.postgrest.from("watch_history")
                    .upsert(
                        WatchHistoryUpdateRow(
                            id = existing.id,
                            userId = userId,
                            contentId = item.id,
                            contentType = identity.contentType,
                            title = item.name,
                            seasonNumber = identity.seasonNumber,
                            episodeNumber = identity.episodeNumber,
                            posterUrl = item.poster,
                            addonId = null,
                            positionSeconds = 0.0,
                            durationSeconds = null,
                            watchedAt = watchedAt,
                        ),
                    ) { onConflict = "id" }
            }
            ensureCurrentAccount(userId)
        }
    }

    override suspend fun delete(
        profileId: Int,
        items: Collection<WatchedItem>,
    ) {
        val userId = requireSupabaseAccountUserId()
        items.distinctBy(::stableWatchedKey).forEach { item ->
            ensureCurrentAccount(userId)
            val identity = toSupabaseWatchedContent(item.type, item.season, item.episode) ?: return@forEach
            findRows(userId, item.id, identity.contentType)
                .filter { row -> row.seasonNumber == identity.seasonNumber && row.episodeNumber == identity.episodeNumber }
                .forEach { row -> deleteRow(userId, row.id) }
            ensureCurrentAccount(userId)
        }
    }

    private suspend fun findRows(userId: String, contentId: String, contentType: String): List<WatchHistoryRow> {
        ensureCurrentAccount(userId)
        val rows = SupabaseProvider.client.postgrest.from("watch_history")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("content_id", contentId)
                    eq("content_type", contentType)
                }
            }
            .decodeList<WatchHistoryRow>()
        ensureCurrentAccount(userId)
        return rows
    }

    private suspend fun deleteRow(userId: String, rowId: String) {
        ensureCurrentAccount(userId)
        SupabaseProvider.client.postgrest.from("watch_history").delete {
            filter {
                eq("user_id", userId)
                eq("id", rowId)
            }
        }
    }

    private fun WatchedItem.supabaseHistoryContentType(): String =
        when {
            isEpisodeRow() -> "episode"
            type.trim().lowercase() in setOf("movie", "film", "films") -> "movie"
            else -> "series"
        }

    private fun WatchedItem.isEpisodeRow(): Boolean =
        isEpisode || type.equals("episode", ignoreCase = true)

    private fun String.toWatchedItemType(): String =
        if (trim().equals("movie", ignoreCase = true) || trim().equals("film", ignoreCase = true)) {
            "movie"
        } else {
            "series"
        }

    private fun stableWatchedKey(item: WatchedItem): String {
        val type = if (item.type.trim().lowercase() in setOf("movie", "film", "films")) "movie" else "series"
        return "$type|${item.id}|${item.season ?: -1}|${item.episode ?: -1}"
    }

    private fun ensureCurrentAccount(userId: String) {
        if (!isCurrentSupabaseAccount(userId)) {
            throw kotlinx.coroutines.CancellationException("Account changed during watch history sync")
        }
    }

    private fun parseTimestamp(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrDefault(0L)
}

@Serializable
private data class WatchHistoryRow(
    val id: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("watched_at") val watchedAt: String = "",
)

@Serializable
private data class WatchHistoryInsertRow(
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("addon_id") val addonId: String? = null,
    @SerialName("position_seconds") val positionSeconds: Double,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("watched_at") val watchedAt: String,
)

@Serializable
private data class WatchHistoryUpdateRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("addon_id") val addonId: String? = null,
    @SerialName("position_seconds") val positionSeconds: Double,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("watched_at") val watchedAt: String,
)
