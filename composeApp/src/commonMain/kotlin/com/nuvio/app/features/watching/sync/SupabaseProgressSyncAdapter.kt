package com.nuvio.app.features.watching.sync

import com.nuvio.app.core.auth.isCurrentSupabaseAccount
import com.nuvio.app.core.auth.requireSupabaseAccountUserId
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.buildWatchProgressKey
import com.nuvio.app.features.watchprogress.resolvedProgressKey
import io.github.jan.supabase.postgrest.postgrest
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val WATCH_PROGRESS_PAGE_SIZE = 900

object SupabaseProgressSyncAdapter : ProgressSyncAdapter {
    override suspend fun getDeltaCursor(profileId: Int): Long? = null

    override suspend fun pullDelta(
        profileId: Int,
        sinceEventId: Long,
        limit: Int,
    ): List<ProgressDeltaEvent> = emptyList()

    override suspend fun pull(
        profileId: Int,
        sinceLastWatched: Long?,
        limit: Int?,
    ): List<ProgressSyncRecord> {
        val userId = requireSupabaseAccountUserId()
        val rows = mutableListOf<WatchProgressRow>()
        var offset = 0L
        while (true) {
            val page = SupabaseProvider.client.postgrest.from("watch_progress")
                .select {
                    filter { eq("user_id", userId) }
                    range(offset, offset + WATCH_PROGRESS_PAGE_SIZE.toLong() - 1L)
                }
                .decodeList<WatchProgressRow>()
            ensureCurrentAccount(userId)
            rows += page
            if (page.size < WATCH_PROGRESS_PAGE_SIZE) break
            offset += page.size
        }
        return rows.map { row ->
            val contentType = fromSupabaseProgressContentType(row.contentType)
            val progressKey = buildWatchProgressKey(row.contentId, row.seasonNumber, row.episodeNumber)
            ProgressSyncRecord(
                progressKey = progressKey,
                contentId = row.contentId,
                contentType = contentType,
                videoId = progressKey,
                season = row.seasonNumber,
                episode = row.episodeNumber,
                position = (row.positionSeconds * 1000.0).toLong().coerceAtLeast(0L),
                duration = ((row.durationSeconds ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L),
                lastWatched = parseTimestamp(row.updatedAt),
                completed = row.completed,
            )
        }
    }

    override suspend fun push(
        profileId: Int,
        entries: Collection<WatchProgressEntry>,
    ) {
        val userId = requireSupabaseAccountUserId()
        entries.distinctBy { entry -> entry.resolvedProgressKey() }.forEach { entry ->
            ensureCurrentAccount(userId)
            val identity = toSupabaseProgressContent(
                value = entry.parentMetaType.ifBlank { entry.contentType },
                seasonNumber = entry.seasonNumber,
                episodeNumber = entry.episodeNumber,
            ) ?: return@forEach
            val contentType = identity.contentType
            val existing = findRows(userId, entry.parentMetaId, contentType)
                .firstOrNull { row ->
                    row.seasonNumber == identity.seasonNumber && row.episodeNumber == identity.episodeNumber
                }
            val positionSeconds = entry.lastPositionMs.coerceAtLeast(0L) / 1000.0
            val durationSeconds = entry.durationMs.takeIf { it > 0L }?.div(1000.0)
            if (existing == null) {
                try {
                    SupabaseProvider.client.postgrest.from("watch_progress")
                        .insert(
                            WatchProgressInsertRow(
                                userId = userId,
                                contentId = entry.parentMetaId,
                                contentType = contentType,
                                seasonNumber = identity.seasonNumber,
                                episodeNumber = identity.episodeNumber,
                                positionSeconds = positionSeconds,
                                durationSeconds = durationSeconds,
                                completed = entry.isEffectivelyCompleted,
                            ),
                        )
                } catch (insertError: Throwable) {
                    if (insertError is kotlinx.coroutines.CancellationException) throw insertError
                    ensureCurrentAccount(userId)
                    val concurrentRow = findRows(userId, entry.parentMetaId, contentType)
                        .firstOrNull { row ->
                            row.seasonNumber == identity.seasonNumber && row.episodeNumber == identity.episodeNumber
                        }
                        ?: throw insertError
                    updateProgressRow(
                        userId = userId,
                        row = concurrentRow,
                        entry = entry,
                        contentType = contentType,
                        seasonNumber = identity.seasonNumber,
                        episodeNumber = identity.episodeNumber,
                        positionSeconds = positionSeconds,
                        durationSeconds = durationSeconds,
                    )
                }
            } else {
                updateProgressRow(
                    userId = userId,
                    row = existing,
                    entry = entry,
                    contentType = contentType,
                    seasonNumber = identity.seasonNumber,
                    episodeNumber = identity.episodeNumber,
                    positionSeconds = positionSeconds,
                    durationSeconds = durationSeconds,
                )
            }
            ensureCurrentAccount(userId)
        }
    }

    override suspend fun delete(
        profileId: Int,
        entries: Collection<WatchProgressEntry>,
    ) {
        val userId = requireSupabaseAccountUserId()
        entries.distinctBy { entry -> entry.resolvedProgressKey() }.forEach { entry ->
            ensureCurrentAccount(userId)
            val identity = toSupabaseProgressContent(
                value = entry.parentMetaType.ifBlank { entry.contentType },
                seasonNumber = entry.seasonNumber,
                episodeNumber = entry.episodeNumber,
            ) ?: return@forEach
            val matchingRows = findRows(userId, entry.parentMetaId, identity.contentType)
                .filter { row ->
                    row.seasonNumber == identity.seasonNumber && row.episodeNumber == identity.episodeNumber
                }
            matchingRows.forEach { row ->
                ensureCurrentAccount(userId)
                SupabaseProvider.client.postgrest.from("watch_progress").delete {
                    filter {
                        eq("user_id", userId)
                        eq("id", row.id)
                    }
                }
            }
            ensureCurrentAccount(userId)
        }
    }

    private suspend fun updateProgressRow(
        userId: String,
        row: WatchProgressRow,
        entry: WatchProgressEntry,
        contentType: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
        positionSeconds: Double,
        durationSeconds: Double?,
    ) {
        ensureCurrentAccount(userId)
        SupabaseProvider.client.postgrest.from("watch_progress")
            .upsert(
                WatchProgressUpdateRow(
                    id = row.id,
                    userId = userId,
                    contentId = entry.parentMetaId,
                    contentType = contentType,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    positionSeconds = positionSeconds,
                    durationSeconds = durationSeconds,
                    completed = entry.isEffectivelyCompleted,
                    updatedAt = Instant.fromEpochMilliseconds(entry.lastUpdatedEpochMs).toString(),
                ),
            ) { onConflict = "id" }
    }

    private suspend fun findRows(userId: String, contentId: String, contentType: String): List<WatchProgressRow> {
        ensureCurrentAccount(userId)
        val rows = SupabaseProvider.client.postgrest.from("watch_progress")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("content_id", contentId)
                    eq("content_type", contentType)
                }
            }
            .decodeList<WatchProgressRow>()
        ensureCurrentAccount(userId)
        return rows
    }

    private fun ensureCurrentAccount(userId: String) {
        if (!isCurrentSupabaseAccount(userId)) {
            throw kotlinx.coroutines.CancellationException("Account changed during progress sync")
        }
    }

    private fun parseTimestamp(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrDefault(0L)
}

@Serializable
private data class WatchProgressRow(
    val id: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("position_seconds") val positionSeconds: Double = 0.0,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    val completed: Boolean = false,
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
private data class WatchProgressInsertRow(
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("position_seconds") val positionSeconds: Double,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    val completed: Boolean,
)

@Serializable
private data class WatchProgressUpdateRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("position_seconds") val positionSeconds: Double,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    val completed: Boolean,
    @SerialName("updated_at") val updatedAt: String,
)
