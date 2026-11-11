package com.nuvio.app.features.watching.application

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.watched.WatchedClock
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.episodePlaybackId
import com.nuvio.app.features.watched.toEpisodeWatchedItem
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How long the undo offer stays on screen, in milliseconds. */
internal const val WatchLogUndoWindowMs: Long = 5_000L

/**
 * One watch-log subject touched by a user action: the watched marker that was toggled plus the
 * playback-progress identity whose entry the action may also have cleared.
 */
data class WatchLogUndoTarget(
    val watchedItem: WatchedItem,
    val videoId: String? = null,
    val parentMetaId: String? = null,
)

/** A progress row that must disappear again when the action created it. */
data class WatchLogProgressRef(
    val videoId: String,
    val parentMetaId: String?,
)

/** The series whose fully-watched marker the action may have flipped. */
data class WatchLogUndoSeries(
    val type: String,
    val id: String,
    val fullyWatchedBefore: Boolean,
)

/** Watched and progress state of one target at a given moment. */
internal data class WatchLogUndoState(
    val watched: WatchedItem?,
    val progress: WatchProgressEntry?,
)

/** Before/after pair for one target; the pure input for [buildWatchLogUndoSnapshot]. */
data class WatchLogUndoBundle(
    val target: WatchLogUndoTarget,
    val watchedBefore: WatchedItem?,
    val watchedAfter: WatchedItem?,
    val progressBefore: WatchProgressEntry?,
    val progressAfter: WatchProgressEntry?,
)

/**
 * Everything an undo has to put back so the watch log looks exactly as it did before the action.
 */
data class WatchLogUndoSnapshot(
    val watchedRestore: List<WatchedItem> = emptyList(),
    val watchedRemove: List<WatchedItem> = emptyList(),
    val progressRestore: List<WatchProgressEntry> = emptyList(),
    val progressClear: List<WatchLogProgressRef> = emptyList(),
    val series: WatchLogUndoSeries? = null,
) {
    val isEmpty: Boolean
        get() = watchedRestore.isEmpty() &&
            watchedRemove.isEmpty() &&
            progressRestore.isEmpty() &&
            progressClear.isEmpty()
}

/**
 * Turns before/after states into the minimal set of changes that undoes them. Only the touched
 * titles are part of the snapshot, so the rest of the log and the other episodes are left alone.
 */
internal fun buildWatchLogUndoSnapshot(
    bundles: List<WatchLogUndoBundle>,
    series: WatchLogUndoSeries? = null,
): WatchLogUndoSnapshot {
    val watchedRestore = mutableListOf<WatchedItem>()
    val watchedRemove = mutableListOf<WatchedItem>()
    val progressRestore = mutableListOf<WatchProgressEntry>()
    val progressClear = mutableListOf<WatchLogProgressRef>()

    bundles.forEach { bundle ->
        when {
            bundle.watchedBefore == null && bundle.watchedAfter != null ->
                watchedRemove += bundle.target.watchedItem

            bundle.watchedBefore != null && bundle.watchedAfter == null ->
                watchedRestore += bundle.watchedBefore
        }

        if (bundle.progressBefore != bundle.progressAfter) {
            if (bundle.progressBefore != null) {
                progressRestore += bundle.progressBefore
            } else {
                val videoId = bundle.target.videoId
                if (videoId != null && bundle.progressAfter != null) {
                    progressClear += WatchLogProgressRef(
                        videoId = videoId,
                        parentMetaId = bundle.target.parentMetaId,
                    )
                }
            }
        }
    }

    return WatchLogUndoSnapshot(
        watchedRestore = watchedRestore,
        watchedRemove = watchedRemove,
        progressRestore = progressRestore,
        progressClear = progressClear,
        series = series,
    )
}

/**
 * Offers a short-lived undo after the watch log changed. The offer is published as soon as an
 * action commits and is withdrawn automatically once [WatchLogUndoWindowMs] has elapsed, so a late
 * tap can never rewrite history.
 */
object WatchLogUndo {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<WatchLogUndoSnapshot?>(null)
    val state: StateFlow<WatchLogUndoSnapshot?> = _state.asStateFlow()

    /** Captures the current state of [targets] so the following mutation can be undone. */
    fun begin(
        targets: List<WatchLogUndoTarget>,
        seriesType: String? = null,
        seriesId: String? = null,
        fullyWatchedBefore: Boolean = false,
    ): WatchLogUndoTransaction {
        if (targets.isEmpty()) return WatchLogUndoTransaction(emptyList(), emptyList(), null)
        val series = if (seriesType != null && seriesId != null && seriesType.isSeriesWatchedType()) {
            WatchLogUndoSeries(
                type = seriesType,
                id = seriesId,
                fullyWatchedBefore = fullyWatchedBefore,
            )
        } else {
            null
        }
        return WatchLogUndoTransaction(
            targets = targets,
            before = targets.map { it.currentState() },
            series = series,
        )
    }

    fun publish(snapshot: WatchLogUndoSnapshot) {
        if (snapshot.isEmpty) return
        _state.value = snapshot
        scope.launch {
            delay(WatchLogUndoWindowMs)
            if (_state.value === snapshot) _state.value = null
        }
    }

    fun dismiss() {
        _state.value = null
    }

    fun undo() {
        val snapshot = _state.value ?: return
        _state.value = null
        scope.launch { apply(snapshot) }
    }

    /**
     * Runs [remove] and, when it dropped watch-progress rows, offers to put them back.
     */
    fun progressRemoval(
        parentMetaId: String,
        remove: () -> Unit,
    ) {
        WatchProgressRepository.ensureLoaded()
        val before = WatchProgressRepository.uiState.value.entries
            .filter { it.parentMetaId == parentMetaId }
        remove()
        if (before.isEmpty()) return
        publish(WatchLogUndoSnapshot(progressRestore = before))
    }

    internal suspend fun apply(snapshot: WatchLogUndoSnapshot) {
        if (snapshot.watchedRestore.isNotEmpty()) {
            WatchedRepository.restoreWatched(snapshot.watchedRestore)
        }
        if (snapshot.watchedRemove.isNotEmpty()) {
            WatchedRepository.unmarkWatched(snapshot.watchedRemove)
        }
        if (snapshot.progressRestore.isNotEmpty()) {
            WatchProgressRepository.restoreProgress(snapshot.progressRestore)
        }
        snapshot.progressClear.forEach { ref ->
            WatchProgressRepository.clearProgress(
                videoId = ref.videoId,
                parentMetaId = ref.parentMetaId,
            )
        }

        val series = snapshot.series ?: return
        MetaDetailsRepository.fetch(type = series.type, id = series.id)?.let { meta ->
            WatchingActions.reconcileSeriesWatchedState(meta = meta)
        }
        WatchedRepository.updateFullyWatchedSeries(
            id = series.id,
            type = series.type,
            isFullyWatched = series.fullyWatchedBefore,
        )
    }
}

/** Captures the before-state of the targets and publishes the undo offer when it commits. */
class WatchLogUndoTransaction internal constructor(
    private val targets: List<WatchLogUndoTarget>,
    private val before: List<WatchLogUndoState>,
    private val series: WatchLogUndoSeries?,
) {
    fun commit() {
        if (targets.isEmpty()) return
        val bundles = targets.mapIndexed { index, target ->
            val state = before[index]
            val current = target.currentState()
            WatchLogUndoBundle(
                target = target,
                watchedBefore = state.watched,
                watchedAfter = current.watched,
                progressBefore = state.progress,
                progressAfter = current.progress,
            )
        }
        WatchLogUndo.publish(buildWatchLogUndoSnapshot(bundles = bundles, series = series))
    }
}

/** Builds the undo target for one episode of [this] title. */
fun MetaDetails.watchLogUndoTarget(episode: MetaVideo): WatchLogUndoTarget =
    WatchLogUndoTarget(
        watchedItem = toEpisodeWatchedItem(episode),
        videoId = episodePlaybackId(episode),
        parentMetaId = id,
    )

internal fun WatchLogUndoTarget.currentState(): WatchLogUndoState =
    WatchLogUndoState(watched = currentWatchedItem(), progress = currentProgressEntry())

private fun WatchLogUndoTarget.currentWatchedItem(): WatchedItem? {
    val stored = WatchedRepository.storedWatchedItem(
        id = watchedItem.id,
        type = watchedItem.type,
        season = watchedItem.season,
        episode = watchedItem.episode,
    )
    if (stored != null) return stored
    val isWatched = WatchedRepository.isWatched(
        id = watchedItem.id,
        type = watchedItem.type,
        season = watchedItem.season,
        episode = watchedItem.episode,
    )
    return if (isWatched) watchedItem.copy(markedAtEpochMs = WatchedClock.nowEpochMs()) else null
}

private fun WatchLogUndoTarget.currentProgressEntry(): WatchProgressEntry? {
    val videoId = videoId ?: return null
    return WatchProgressRepository.progressForVideo(
        videoId = videoId,
        parentMetaId = parentMetaId,
        seasonNumber = watchedItem.season,
        episodeNumber = watchedItem.episode,
    )
}

private fun String.isSeriesWatchedType(): Boolean =
    trim().lowercase() in setOf("series", "show", "tv", "tvshow")
