package com.nuvio.app.features.stats

import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.isEpisode
import com.nuvio.app.features.watchprogress.WatchProgressEntry

internal enum class WatchHistoryContentType {
    MOVIE,
    EPISODE,
}

internal data class WatchHistoryCsvRow(
    val title: String,
    val contentType: WatchHistoryContentType,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val lastWatchedEpochMs: Long,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
)

internal data class WatchHistoryCsvLabels(
    val titleColumn: String,
    val typeColumn: String,
    val seasonColumn: String,
    val episodeColumn: String,
    val lastWatchedColumn: String,
    val positionColumn: String,
    val durationColumn: String,
    val completedColumn: String,
    val movieType: String,
    val episodeType: String,
    val completedYes: String,
    val completedNo: String,
)

/**
 * Collects one row per watched movie or episode from the local watch history.
 * Entries that only exist as progress are merged with the watched store, so a
 * title the user finished is never listed twice. Stream links, credentials and
 * account data are deliberately left out.
 */
internal fun buildWatchHistoryRows(
    progressEntries: List<WatchProgressEntry>,
    watchedItems: List<WatchedItem>,
): List<WatchHistoryCsvRow> {
    val progressMovieIds = progressEntries
        .filter { entry -> !entry.isEpisode }
        .mapTo(mutableSetOf(), WatchProgressEntry::parentMetaId)
    val progressEpisodeVideoIds = progressEntries
        .filter { entry -> entry.isEpisode }
        .mapTo(mutableSetOf(), WatchProgressEntry::videoId)

    val rows = mutableListOf<WatchHistoryCsvRow>()

    progressEntries.forEach { entry ->
        rows += WatchHistoryCsvRow(
            title = entry.title,
            contentType = if (entry.isEpisode) {
                WatchHistoryContentType.EPISODE
            } else {
                WatchHistoryContentType.MOVIE
            },
            seasonNumber = entry.seasonNumber,
            episodeNumber = entry.episodeNumber,
            lastWatchedEpochMs = entry.lastUpdatedEpochMs,
            positionMs = entry.lastPositionMs,
            durationMs = entry.durationMs,
            isCompleted = entry.isEffectivelyCompleted,
        )
    }

    watchedItems.forEach { item ->
        val itemIsEpisode = item.isEpisode
        val coveredByProgress = if (itemIsEpisode) {
            (item.videoId ?: item.id) in progressEpisodeVideoIds
        } else {
            item.id in progressMovieIds
        }
        if (coveredByProgress) return@forEach
        if (!itemIsEpisode && !isMovieType(item.type)) return@forEach

        rows += WatchHistoryCsvRow(
            title = item.name,
            contentType = if (itemIsEpisode) {
                WatchHistoryContentType.EPISODE
            } else {
                WatchHistoryContentType.MOVIE
            },
            seasonNumber = item.season,
            episodeNumber = item.episode,
            lastWatchedEpochMs = item.markedAtEpochMs,
            positionMs = 0L,
            durationMs = 0L,
            isCompleted = true,
        )
    }

    return rows.sortedByDescending { row -> row.lastWatchedEpochMs }
}

internal fun buildWatchHistoryCsv(
    rows: List<WatchHistoryCsvRow>,
    labels: WatchHistoryCsvLabels,
    formatTimestamp: (Long) -> String,
): String {
    val lines = mutableListOf<String>()
    lines += listOf(
        labels.titleColumn,
        labels.typeColumn,
        labels.seasonColumn,
        labels.episodeColumn,
        labels.lastWatchedColumn,
        labels.positionColumn,
        labels.durationColumn,
        labels.completedColumn,
    ).toCsvLine()

    rows.forEach { row ->
        lines += listOf(
            row.title,
            if (row.contentType == WatchHistoryContentType.MOVIE) labels.movieType else labels.episodeType,
            row.seasonNumber?.toString().orEmpty(),
            row.episodeNumber?.toString().orEmpty(),
            formatTimestamp(row.lastWatchedEpochMs),
            formatWatchHistoryDuration(row.positionMs),
            formatWatchHistoryDuration(row.durationMs),
            if (row.isCompleted) labels.completedYes else labels.completedNo,
        ).toCsvLine()
    }

    return lines.joinToString(separator = "\n")
}

internal fun watchHistoryCsvFileName(nowEpochMs: Long): String =
    "raif-watch-history-${isoDateFromEpochMs(nowEpochMs)}.csv"

internal fun formatWatchHistoryDuration(totalMs: Long): String {
    val totalSeconds = totalMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val paddedSeconds = seconds.toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:$paddedSeconds"
    } else {
        "$minutes:$paddedSeconds"
    }
}

/** UTC calendar date for a timestamp, used to stamp the exported file name. */
internal fun isoDateFromEpochMs(epochMs: Long): String {
    val epochDay = epochMs.floorDiv(86_400_000L)
    val shiftedDay = epochDay + 719_468L
    val era = (if (shiftedDay >= 0L) shiftedDay else shiftedDay - 146_096L) / 146_097L
    val dayOfEra = shiftedDay - era * 146_097L
    val yearOfEra = (dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
    val year = yearOfEra + era * 400L
    val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
    val monthPivot = (5L * dayOfYear + 2L) / 153L
    val day = dayOfYear - (153L * monthPivot + 2L) / 5L + 1L
    val month = if (monthPivot < 10L) monthPivot + 3L else monthPivot - 9L
    val calendarYear = if (month <= 2L) year + 1L else year

    return buildString {
        append(calendarYear.toString().padStart(4, '0'))
        append('-')
        append(month.toString().padStart(2, '0'))
        append('-')
        append(day.toString().padStart(2, '0'))
    }
}

private fun List<String>.toCsvLine(): String =
    joinToString(separator = ",") { value -> value.toCsvField() }

private fun String.toCsvField(): String {
    val needsQuotes = any { character -> character == ',' || character == '"' || character == '\n' || character == '\r' }
    if (!needsQuotes) return this
    return "\"" + replace("\"", "\"\"") + "\""
}
