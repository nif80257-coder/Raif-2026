package com.nuvio.app.features.party

import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.userId
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressSourceLocal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Keeps the one pending "continue from the party position" offer for the
 * signed-in account. The offer is only created from content that was actually
 * played inside a watch party, is removed once used or dismissed, and is never
 * uploaded anywhere.
 */
internal object PartyResumeRepository {
    /** Below this a party stop is not worth offering to continue from. */
    private const val MinimumPositionMs = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _suggestion = MutableStateFlow<PartyResumeSuggestion?>(null)
    val suggestion: StateFlow<PartyResumeSuggestion?> = _suggestion.asStateFlow()

    private var loadedUserId: String? = null

    init {
        scope.launch {
            AuthRepository.state.collect { state -> refresh(state.userId) }
        }
    }

    fun refresh() {
        refresh(AuthRepository.state.value.userId)
    }

    private fun refresh(userId: String?) {
        if (userId == loadedUserId) return
        loadedUserId = userId
        _suggestion.value = userId?.let(PartyResumeStorage::load)
    }

    fun record(media: PartyMedia, positionMs: Long) {
        if (media.metaId.isBlank()) return
        if (positionMs < MinimumPositionMs) return
        val userId = AuthRepository.state.value.userId ?: return
        if (loadedUserId != userId) refresh(userId)

        val suggestion = PartyResumeSuggestion(
            metaType = media.metaType,
            metaId = media.metaId,
            videoId = media.videoId?.takeIf { it.isNotBlank() } ?: media.metaId,
            title = media.title,
            poster = media.poster,
            background = media.background,
            season = media.season,
            episode = media.episode,
            episodeTitle = media.episodeTitle,
            positionMs = positionMs,
            savedAtEpochMs = WatchProgressClock.nowEpochMs(),
        )
        _suggestion.value = suggestion
        PartyResumeStorage.save(userId, suggestion)
        saveWatchProgress(suggestion)
    }

    fun dismiss() {
        val userId = loadedUserId ?: return
        _suggestion.value = null
        PartyResumeStorage.save(userId, null)
    }

    /**
     * Writes the party stop into the account's watch progress so the rest of the
     * app knows where the party left off. A personal record that already sits
     * further into the video is never moved backwards.
     */
    private fun saveWatchProgress(suggestion: PartyResumeSuggestion) {
        val existing = WatchProgressRepository.progressForVideo(
            videoId = suggestion.videoId,
            parentMetaId = suggestion.metaId,
            seasonNumber = suggestion.season,
            episodeNumber = suggestion.episode,
        )
        if (existing != null && existing.lastPositionMs >= suggestion.positionMs) return

        WatchProgressRepository.applyExternalProgress(
            listOf(
                WatchProgressEntry(
                    contentType = suggestion.metaType,
                    parentMetaId = suggestion.metaId,
                    parentMetaType = suggestion.metaType,
                    videoId = suggestion.videoId,
                    title = suggestion.title.ifBlank { suggestion.metaId },
                    poster = suggestion.poster,
                    background = suggestion.background,
                    seasonNumber = suggestion.season,
                    episodeNumber = suggestion.episode,
                    episodeTitle = suggestion.episodeTitle,
                    lastPositionMs = suggestion.positionMs,
                    durationMs = existing?.durationMs ?: 0L,
                    lastUpdatedEpochMs = suggestion.savedAtEpochMs,
                    source = WatchProgressSourceLocal,
                ),
            ),
        )
    }
}
