package com.nuvio.app.features.stats

import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.isEpisode

private const val DayMillis = 86_400_000L

internal data class StatisticsDayBucket(
    val epochDay: Long,
    val itemsCount: Int,
    val watchTimeMs: Long,
)

internal data class MostWatchedContent(
    val id: String,
    val title: String,
    val poster: String?,
    val count: Int,
)

internal data class WatchStatistics(
    val totalWatchTimeMs: Long = 0L,
    val completedMovies: Int = 0,
    val completedEpisodes: Int = 0,
    val inProgressCount: Int = 0,
    val favoritesCount: Int = 0,
    val last7Days: List<StatisticsDayBucket> = emptyList(),
    val last30Days: List<StatisticsDayBucket> = emptyList(),
    val mostWatched: List<MostWatchedContent> = emptyList(),
) {
    val hasData: Boolean
        get() = totalWatchTimeMs > 0L ||
            completedMovies > 0 ||
            completedEpisodes > 0 ||
            inProgressCount > 0 ||
            last30Days.any { it.itemsCount > 0 }
}

internal fun buildWatchStatistics(
    progressEntries: List<WatchProgressEntry>,
    watchedItems: List<WatchedItem>,
    favoritesCount: Int,
    nowEpochMs: Long,
): WatchStatistics {
    if (progressEntries.isEmpty() && watchedItems.isEmpty() && favoritesCount == 0) {
        return WatchStatistics()
    }

    val progressByVideoId = progressEntries.associateBy { it.videoId }
    val progressMovieIds = progressEntries
        .filter { entry -> !entry.isEpisode }
        .mapTo(mutableSetOf(), WatchProgressEntry::parentMetaId)
    val progressEpisodeVideoIds = progressEntries
        .filter { entry -> entry.isEpisode }
        .mapTo(mutableSetOf(), WatchProgressEntry::videoId)

    val completedMovies = progressEntries.count { entry -> !entry.isEpisode && entry.isEffectivelyCompleted } +
        watchedItems.count { item ->
            !item.isEpisode && item.id !in progressMovieIds && isMovieType(item.type)
        }
    val completedEpisodes = progressEntries.count { entry -> entry.isEpisode && entry.isEffectivelyCompleted } +
        watchedItems.count { item ->
            item.isEpisode && (item.videoId ?: item.id) !in progressEpisodeVideoIds
        }
    val inProgressCount = progressEntries.count { entry -> entry.isResumable && entry.lastPositionMs > 0L }
    val totalWatchTimeMs = progressEntries.sumOf { entry -> watchedMillis(entry) }

    val todayEpochDay = nowEpochMs.floorDiv(DayMillis)
    val watchTimeByDay = mutableMapOf<Long, Long>()
    val itemsByDay = mutableMapOf<Long, Int>()

    progressEntries.forEach { entry ->
        val day = entry.lastUpdatedEpochMs.floorDiv(DayMillis)
        watchTimeByDay[day] = (watchTimeByDay[day] ?: 0L) + watchedMillis(entry)
        itemsByDay[day] = (itemsByDay[day] ?: 0) + 1
    }
    watchedItems.forEach { item ->
        val day = item.markedAtEpochMs.floorDiv(DayMillis)
        itemsByDay[day] = (itemsByDay[day] ?: 0) + 1
    }

    fun bucketsFor(days: Int): List<StatisticsDayBucket> =
        (days - 1 downTo 0).map { offset ->
            val epochDay = todayEpochDay - offset
            StatisticsDayBucket(
                epochDay = epochDay,
                itemsCount = itemsByDay[epochDay] ?: 0,
                watchTimeMs = watchTimeByDay[epochDay] ?: 0L,
            )
        }

    val mostWatched = buildMostWatched(progressEntries, watchedItems)

    return WatchStatistics(
        totalWatchTimeMs = totalWatchTimeMs,
        completedMovies = completedMovies,
        completedEpisodes = completedEpisodes,
        inProgressCount = inProgressCount,
        favoritesCount = favoritesCount,
        last7Days = bucketsFor(days = 7),
        last30Days = bucketsFor(days = 30),
        mostWatched = mostWatched,
    )
}

private data class MostWatchedAccumulator(
    val id: String,
    val title: String,
    val poster: String?,
    var count: Int,
)

private fun buildMostWatched(
    progressEntries: List<WatchProgressEntry>,
    watchedItems: List<WatchedItem>,
): List<MostWatchedContent> {
    val byId = linkedMapOf<String, MostWatchedAccumulator>()
    progressEntries.forEach { entry ->
        val existing = byId[entry.parentMetaId]
        if (existing == null) {
            byId[entry.parentMetaId] = MostWatchedAccumulator(
                id = entry.parentMetaId,
                title = entry.title,
                poster = entry.poster ?: entry.background ?: entry.logo,
                count = 1,
            )
        } else {
            existing.count += 1
            if (existing.poster == null) {
                // The accumulator is mutable by design; keep a poster once one is known.
                byId[entry.parentMetaId] = existing.copy(
                    poster = entry.poster ?: entry.background ?: entry.logo,
                )
            }
        }
    }
    watchedItems.forEach { item ->
        val existing = byId[item.id]
        if (existing == null) {
            byId[item.id] = MostWatchedAccumulator(
                id = item.id,
                title = item.name,
                poster = item.poster,
                count = 1,
            )
        } else {
            existing.count += 1
        }
    }
    return byId.values
        .sortedWith(compareByDescending<MostWatchedAccumulator> { it.count }.thenBy { it.title })
        .take(5)
        .map { MostWatchedContent(id = it.id, title = it.title, poster = it.poster, count = it.count) }
}

internal fun watchedMillis(entry: WatchProgressEntry): Long {
    if (entry.isEffectivelyCompleted && entry.durationMs > 0L) return entry.durationMs
    val position = entry.lastPositionMs
    val duration = entry.durationMs
    return if (duration > 0L) position.coerceIn(0L, duration) else position.coerceAtLeast(0L)
}

internal fun isMovieType(type: String): Boolean =
    type.trim().lowercase() in setOf("movie", "film")

internal fun formatWatchTimeParts(totalMillis: Long): Pair<Long, Long> {
    val totalMinutes = (totalMillis.coerceAtLeast(0L) / 60_000L)
    return (totalMinutes / 60L) to (totalMinutes % 60L)
}
