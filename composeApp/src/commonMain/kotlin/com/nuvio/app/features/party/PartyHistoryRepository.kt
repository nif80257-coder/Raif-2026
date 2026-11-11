package com.nuvio.app.features.party

import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.userId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Local list of the watch parties the signed-in account hosted or joined.
 * Entries are keyed by account id, never shared between accounts and never
 * uploaded anywhere.
 */
internal object PartyHistoryRepository {
    private const val limit = 50

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _entries = MutableStateFlow<List<PartyHistoryEntry>>(emptyList())
    val entries: StateFlow<List<PartyHistoryEntry>> = _entries.asStateFlow()

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
        _entries.value = userId
            ?.let(PartyHistoryStorage::load)
            .orEmpty()
            .sortedByDescending { it.startedAtEpochMs }
    }

    fun record(party: ActiveParty, startedAtEpochMs: Long) {
        val userId = AuthRepository.state.value.userId ?: return
        if (loadedUserId != userId) refresh(userId)
        val entry = PartyHistoryEntry(
            id = party.id,
            code = party.code,
            title = party.media.title,
            poster = party.media.poster,
            metaType = party.media.metaType,
            metaId = party.media.metaId,
            videoId = party.media.videoId,
            season = party.media.season,
            episode = party.media.episode,
            episodeTitle = party.media.episodeTitle,
            startedAtEpochMs = startedAtEpochMs,
            members = party.members.size,
            maxMembers = party.maxMembers,
            host = party.isHost,
        )
        val updated = listOf(entry) + _entries.value.filterNot { it.id == entry.id }
        publish(userId, updated.sortedByDescending { it.startedAtEpochMs }.take(limit))
    }

    fun updateMembers(partyId: String, members: Int) {
        val userId = loadedUserId ?: return
        if (_entries.value.none { it.id == partyId }) return
        publish(
            userId,
            _entries.value.map { entry ->
                if (entry.id == partyId) entry.copy(members = members.coerceAtLeast(1)) else entry
            },
        )
    }

    fun markEnded(partyId: String) {
        val userId = loadedUserId ?: return
        if (_entries.value.none { it.id == partyId }) return
        publish(
            userId,
            _entries.value.map { entry ->
                if (entry.id == partyId) entry.copy(ended = true) else entry
            },
        )
    }

    fun remove(partyId: String) {
        val userId = loadedUserId ?: return
        publish(userId, _entries.value.filterNot { it.id == partyId })
    }

    private fun publish(userId: String, entries: List<PartyHistoryEntry>) {
        _entries.value = entries
        PartyHistoryStorage.save(userId, entries)
    }
}
