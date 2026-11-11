package com.nuvio.app.features.watching.application

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.episodePlaybackId
import com.nuvio.app.features.watched.releasedMainSeasonEpisodes
import com.nuvio.app.features.watched.toEpisodeWatchedItem
import com.nuvio.app.features.watched.toSeriesWatchedItem
import com.nuvio.app.features.watched.toWatchedItem
import com.nuvio.app.features.cloud.CloudLibraryContentType
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watching.domain.isReleasedBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WatchingActions {
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun togglePosterWatched(preview: MetaPreview) {
        if (!preview.type.isSeriesLikeType()) {
            val item = preview.toWatchedItem(markedAtEpochMs = 0L)
            val undo = WatchLogUndo.begin(targets = listOf(WatchLogUndoTarget(watchedItem = item)))
            WatchedRepository.toggleWatched(item)
            undo.commit()
            return
        }

        val isCurrentlyWatched = WatchedRepository.isWatched(
            id = preview.id,
            type = preview.type,
        ) || WatchedRepository.isFullyWatchedSeries(id = preview.id, type = preview.type)
        val fullyWatchedBefore = WatchedRepository.isFullyWatchedSeries(id = preview.id, type = preview.type)
        val meta = MetaDetailsRepository.fetch(type = preview.type, id = preview.id)
        if (meta == null) {
            if (isCurrentlyWatched) {
                val undo = WatchLogUndo.begin(
                    targets = listOf(WatchLogUndoTarget(preview.toWatchedItem(markedAtEpochMs = 0L))),
                    seriesType = preview.type,
                    seriesId = preview.id,
                    fullyWatchedBefore = fullyWatchedBefore,
                )
                WatchedRepository.unmarkWatched(preview.toWatchedItem(markedAtEpochMs = 0L))
                WatchedRepository.updateFullyWatchedSeries(
                    id = preview.id,
                    type = preview.type,
                    isFullyWatched = false,
                )
                undo.commit()
            }
            return
        }

        val todayIsoDate = CurrentDateProvider.todayIsoDate()
        val releasedMainEpisodes = meta.releasedMainSeasonEpisodes(todayIsoDate)
        if (releasedMainEpisodes.isEmpty()) {
            if (isCurrentlyWatched) {
                val undo = WatchLogUndo.begin(
                    targets = listOf(WatchLogUndoTarget(meta.toSeriesWatchedItem())),
                    seriesType = meta.type,
                    seriesId = meta.id,
                    fullyWatchedBefore = fullyWatchedBefore,
                )
                WatchedRepository.unmarkWatched(meta.toSeriesWatchedItem())
                WatchedRepository.updateFullyWatchedSeries(
                    id = meta.id,
                    type = meta.type,
                    isFullyWatched = false,
                )
                undo.commit()
            }
            return
        }
        val seriesItems = buildList {
            add(meta.toSeriesWatchedItem())
            addAll(releasedMainEpisodes.map(meta::toEpisodeWatchedItem))
        }
        val undo = WatchLogUndo.begin(
            targets = buildList {
                add(WatchLogUndoTarget(meta.toSeriesWatchedItem()))
                releasedMainEpisodes.forEach { episode -> add(meta.watchLogUndoTarget(episode)) }
            },
            seriesType = meta.type,
            seriesId = meta.id,
            fullyWatchedBefore = fullyWatchedBefore,
        )

        if (isCurrentlyWatched) {
            WatchedRepository.unmarkWatched(seriesItems)
            WatchProgressRepository.clearProgress(
                videoIds = releasedMainEpisodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
            WatchedRepository.updateFullyWatchedSeries(
                id = meta.id,
                type = meta.type,
                isFullyWatched = false,
            )
        } else {
            WatchedRepository.markWatched(seriesItems)
            WatchedRepository.updateFullyWatchedSeries(
                id = meta.id,
                type = meta.type,
                isFullyWatched = true,
            )
            WatchProgressRepository.clearProgress(
                videoIds = releasedMainEpisodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
        }
        undo.commit()
    }

    fun toggleEpisodeWatched(
        meta: MetaDetails,
        episode: MetaVideo,
        isCurrentlyWatched: Boolean,
    ) {
        val watchedItem = meta.toEpisodeWatchedItem(episode)
        val undo = WatchLogUndo.begin(
            targets = listOf(meta.watchLogUndoTarget(episode)),
            seriesType = meta.type,
            seriesId = meta.id,
            fullyWatchedBefore = WatchedRepository.isFullyWatchedSeries(id = meta.id, type = meta.type),
        )
        if (isCurrentlyWatched) {
            WatchedRepository.unmarkWatched(watchedItem)
            WatchProgressRepository.clearProgress(
                videoId = meta.episodePlaybackId(episode),
                parentMetaId = meta.id,
            )
        } else {
            WatchedRepository.markWatched(watchedItem)
            WatchProgressRepository.clearProgress(
                videoId = meta.episodePlaybackId(episode),
                parentMetaId = meta.id,
            )
        }
        reconcileSeriesWatchedState(meta)
        undo.commit()
    }

    fun togglePreviousEpisodesWatched(
        meta: MetaDetails,
        episodes: Collection<MetaVideo>,
        areCurrentlyWatched: Boolean,
    ) {
        toggleEpisodesWatched(
            meta = meta,
            episodes = episodes,
            areCurrentlyWatched = areCurrentlyWatched,
        )
    }

    fun toggleSeasonWatched(
        meta: MetaDetails,
        episodes: Collection<MetaVideo>,
        areCurrentlyWatched: Boolean,
    ) {
        toggleEpisodesWatched(
            meta = meta,
            episodes = episodes,
            areCurrentlyWatched = areCurrentlyWatched,
        )
    }

    fun reconcileSeriesWatchedState(
        meta: MetaDetails,
        todayIsoDate: String = CurrentDateProvider.todayIsoDate(),
    ) {
        if (!meta.type.isSeriesLikeType()) return

        WatchedRepository.reconcileSeriesWatchedState(
            meta = meta,
            todayIsoDate = todayIsoDate,
            isEpisodeCompleted = { episode ->
                WatchProgressRepository.progressForVideo(
                    videoId = meta.episodePlaybackId(episode),
                    parentMetaId = meta.id,
                    seasonNumber = episode.season,
                    episodeNumber = episode.episode,
                )?.isCompleted == true
            },
        )
    }

    fun onProgressEntryUpdated(entry: WatchProgressEntry, syncRemote: Boolean = true) {
        if (!entry.isCompleted) return

        val watchedItem = WatchedItem(
            id = entry.parentMetaId,
            type = entry.parentMetaType,
            name = entry.title,
            poster = entry.poster,
            season = entry.seasonNumber,
            episode = entry.episodeNumber,
            markedAtEpochMs = entry.lastUpdatedEpochMs,
        )
        WatchedRepository.markWatchedFromPlaybackCompletion(watchedItem, syncRemote = syncRemote)

        if (!syncRemote || !entry.isEpisode) return
        actionScope.launch {
            val meta = runCatching {
                MetaDetailsRepository.fetch(
                    type = entry.parentMetaType,
                    id = entry.parentMetaId,
                )
            }.getOrNull() ?: return@launch

            reconcileSeriesWatchedState(meta = meta)
        }
    }

    fun markContinueWatchingWatched(item: ContinueWatchingItem) {
        if (!item.canMarkWatched()) return
        val undo = WatchLogUndo.begin(
            targets = listOf(
                WatchLogUndoTarget(
                    watchedItem = item.toWatchedItem(),
                    videoId = item.videoId,
                    parentMetaId = item.parentMetaId,
                ),
            ),
            seriesType = item.parentMetaType,
            seriesId = item.parentMetaId,
            fullyWatchedBefore = WatchedRepository.isFullyWatchedSeries(
                id = item.parentMetaId,
                type = item.parentMetaType,
            ),
        )
        WatchedRepository.markWatched(item.toWatchedItem())
        WatchProgressRepository.clearProgress(
            videoId = item.videoId,
            parentMetaId = item.parentMetaId,
        )
        undo.commit()
        if (!item.parentMetaType.isSeriesLikeType()) return
        actionScope.launch {
            MetaDetailsRepository.fetch(type = item.parentMetaType, id = item.parentMetaId)
                ?.let { meta -> reconcileSeriesWatchedState(meta) }
        }
    }

    private fun toggleEpisodesWatched(
        meta: MetaDetails,
        episodes: Collection<MetaVideo>,
        areCurrentlyWatched: Boolean,
    ) {
        if (episodes.isEmpty()) return
        val watchedItems = episodes.map(meta::toEpisodeWatchedItem)
        val undo = WatchLogUndo.begin(
            targets = episodes.map(meta::watchLogUndoTarget),
            seriesType = meta.type,
            seriesId = meta.id,
            fullyWatchedBefore = WatchedRepository.isFullyWatchedSeries(id = meta.id, type = meta.type),
        )
        if (areCurrentlyWatched) {
            WatchedRepository.unmarkWatched(watchedItems)
            WatchProgressRepository.clearProgress(
                videoIds = episodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
        } else {
            WatchedRepository.markWatched(watchedItems)
            WatchProgressRepository.clearProgress(
                videoIds = episodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
        }
        reconcileSeriesWatchedState(meta)
        undo.commit()
    }
}

/** Cloud files have no catalog entry to track, and unreleased episodes cannot be watched yet. */
internal fun ContinueWatchingItem.canMarkWatched(
    todayIsoDate: String = CurrentDateProvider.todayIsoDate(),
): Boolean =
    !parentMetaType.equals(CloudLibraryContentType, ignoreCase = true) &&
        (!isNextUp || isReleasedBy(todayIsoDate, released))

/** Same shape as the details screen builds, so tracker sync takes the same path. */
internal fun ContinueWatchingItem.toWatchedItem(markedAtEpochMs: Long = 0L): WatchedItem =
    WatchedItem(
        id = parentMetaId,
        type = parentMetaType,
        name = episodeTitle?.takeIf { it.isNotBlank() } ?: title,
        poster = episodeThumbnail ?: background ?: poster,
        season = seasonNumber,
        episode = episodeNumber,
        videoId = videoId,
        markedAtEpochMs = markedAtEpochMs,
    )

private fun String.isSeriesLikeType(): Boolean =
    trim().lowercase() in setOf("series", "show", "tv", "tvshow")
