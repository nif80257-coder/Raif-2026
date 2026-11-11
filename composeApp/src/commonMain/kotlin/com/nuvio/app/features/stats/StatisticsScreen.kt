package com.nuvio.app.features.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.share.PlatformShare
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.stats_completed_episodes
import nuvio.composeapp.generated.resources.stats_completed_movies
import nuvio.composeapp.generated.resources.stats_csv_column_completed
import nuvio.composeapp.generated.resources.stats_csv_column_duration
import nuvio.composeapp.generated.resources.stats_csv_column_episode
import nuvio.composeapp.generated.resources.stats_csv_column_last_watched
import nuvio.composeapp.generated.resources.stats_csv_column_position
import nuvio.composeapp.generated.resources.stats_csv_column_season
import nuvio.composeapp.generated.resources.stats_csv_column_title
import nuvio.composeapp.generated.resources.stats_csv_column_type
import nuvio.composeapp.generated.resources.stats_csv_completed_no
import nuvio.composeapp.generated.resources.stats_csv_completed_yes
import nuvio.composeapp.generated.resources.stats_csv_type_episode
import nuvio.composeapp.generated.resources.stats_csv_type_movie
import nuvio.composeapp.generated.resources.stats_empty
import nuvio.composeapp.generated.resources.stats_export_csv
import nuvio.composeapp.generated.resources.stats_export_empty
import nuvio.composeapp.generated.resources.stats_export_error
import nuvio.composeapp.generated.resources.stats_export_subject
import nuvio.composeapp.generated.resources.stats_export_success
import nuvio.composeapp.generated.resources.stats_favorites
import nuvio.composeapp.generated.resources.stats_hours_label
import nuvio.composeapp.generated.resources.stats_in_progress
import nuvio.composeapp.generated.resources.stats_most_watched
import nuvio.composeapp.generated.resources.stats_month
import nuvio.composeapp.generated.resources.stats_times_count
import nuvio.composeapp.generated.resources.stats_week
import nuvio.composeapp.generated.resources.stats_watch_time
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StatisticsScreenContent() {
    LaunchedEffect(Unit) {
        WatchProgressRepository.ensureLoaded()
        WatchedRepository.ensureLoaded()
        LibraryRepository.ensureLoaded()
    }
    val progress by WatchProgressRepository.uiState.collectAsStateWithLifecycle()
    val watched by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val library by LibraryRepository.uiState.collectAsStateWithLifecycle()

    val statistics = remember(progress.entries, watched.items, library.items) {
        buildWatchStatistics(
            progressEntries = progress.entries,
            watchedItems = watched.items,
            favoritesCount = library.items.size,
            nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs(),
        )
    }
    val tokens = MaterialTheme.nuvio
    var exportFeedback by remember { mutableStateOf<ExportFeedback?>(null) }
    val csvLabels = WatchHistoryCsvLabels(
        titleColumn = stringResource(Res.string.stats_csv_column_title),
        typeColumn = stringResource(Res.string.stats_csv_column_type),
        seasonColumn = stringResource(Res.string.stats_csv_column_season),
        episodeColumn = stringResource(Res.string.stats_csv_column_episode),
        lastWatchedColumn = stringResource(Res.string.stats_csv_column_last_watched),
        positionColumn = stringResource(Res.string.stats_csv_column_position),
        durationColumn = stringResource(Res.string.stats_csv_column_duration),
        completedColumn = stringResource(Res.string.stats_csv_column_completed),
        movieType = stringResource(Res.string.stats_csv_type_movie),
        episodeType = stringResource(Res.string.stats_csv_type_episode),
        completedYes = stringResource(Res.string.stats_csv_completed_yes),
        completedNo = stringResource(Res.string.stats_csv_completed_no),
    )
    val exportSuccessText = stringResource(Res.string.stats_export_success)
    val exportErrorText = stringResource(Res.string.stats_export_error)
    val exportEmptyText = stringResource(Res.string.stats_export_empty)
    val exportSubject = stringResource(Res.string.stats_export_subject)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!statistics.hasData) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = tokens.colors.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
            ) {
                Text(
                    text = stringResource(Res.string.stats_empty),
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textSecondary,
                )
            }
            return@Column
        }

        val (hours, minutes) = formatWatchTimeParts(statistics.totalWatchTimeMs)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = stringResource(Res.string.stats_watch_time, hours, minutes),
                label = stringResource(Res.string.stats_hours_label),
                isPrimary = true,
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = statistics.completedMovies.toString(),
                label = stringResource(Res.string.stats_completed_movies),
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = statistics.completedEpisodes.toString(),
                label = stringResource(Res.string.stats_completed_episodes),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = statistics.inProgressCount.toString(),
                label = stringResource(Res.string.stats_in_progress),
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = statistics.favoritesCount.toString(),
                label = stringResource(Res.string.stats_favorites),
            )
        }

        ExportCsvCard(
            onExport = {
                val rows = buildWatchHistoryRows(
                    progressEntries = progress.entries,
                    watchedItems = watched.items,
                )
                if (rows.isEmpty()) {
                    exportFeedback = ExportFeedback(text = exportEmptyText, isError = true)
                } else {
                    runCatching {
                        PlatformShare.shareText(
                            text = buildWatchHistoryCsv(
                                rows = rows,
                                labels = csvLabels,
                                formatTimestamp = ::formatLocalDateTime,
                            ),
                            fileName = watchHistoryCsvFileName(EpisodeReleaseDatePlatform.nowEpochMs()),
                            subject = exportSubject,
                        )
                    }.fold(
                        onSuccess = { exportFeedback = ExportFeedback(text = exportSuccessText) },
                        onFailure = { exportFeedback = ExportFeedback(text = exportErrorText, isError = true) },
                    )
                }
            },
        )

        exportFeedback?.let { feedback ->
            Text(
                text = feedback.text,
                style = MaterialTheme.typography.bodySmall,
                color = if (feedback.isError) tokens.colors.danger else tokens.colors.success,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NuvioSectionLabel(text = stringResource(Res.string.stats_week))
            ActivityBars(buckets = statistics.last7Days)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NuvioSectionLabel(text = stringResource(Res.string.stats_month))
            ActivityBars(buckets = statistics.last30Days, compact = true)
        }

        if (statistics.mostWatched.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NuvioSectionLabel(text = stringResource(Res.string.stats_most_watched))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = tokens.colors.surface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
                ) {
                    Column {
                        statistics.mostWatched.forEachIndexed { index, item ->
                            MostWatchedRow(
                                rank = index + 1,
                                item = item,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportCsvCard(
    onExport: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = tokens.colors.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExport)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.TableChart,
                contentDescription = null,
                tint = tokens.colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(Res.string.stats_export_csv),
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
    isPrimary: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = modifier,
        color = tokens.colors.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isPrimary) tokens.colors.accent else tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActivityBars(
    buckets: List<StatisticsDayBucket>,
    compact: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    val maxCount = buckets.maxOfOrNull { it.itemsCount }?.coerceAtLeast(1) ?: 1
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = tokens.colors.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 64.dp else 96.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                val fraction = if (bucket.itemsCount == 0) {
                    0.06f
                } else {
                    (bucket.itemsCount.toFloat() / maxCount.toFloat()).coerceIn(0.12f, 1f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((if (compact) 48.dp else 76.dp) * fraction)
                        .clip(RoundedCornerShape(if (compact) 3.dp else 5.dp))
                        .background(
                            if (bucket.itemsCount == 0) {
                                tokens.colors.borderSubtle
                            } else {
                                tokens.colors.accent.copy(alpha = if (compact) 0.75f else 0.9f)
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun MostWatchedRow(
    rank: Int,
    item: MostWatchedContent,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = tokens.colors.textMuted,
            modifier = Modifier.width(16.dp),
        )
        val poster = item.poster
        if (poster.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tokens.colors.borderSubtle),
            )
        } else {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 36.dp, height = 52.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.stats_times_count, item.count),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textSecondary,
            )
        }
    }
}


private data class ExportFeedback(
    val text: String,
    val isError: Boolean = false,
)
