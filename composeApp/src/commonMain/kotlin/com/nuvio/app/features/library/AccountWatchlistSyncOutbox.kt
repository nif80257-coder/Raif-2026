package com.nuvio.app.features.library

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
internal enum class AccountWatchlistMutationKind {
    CREATE_LIST,
    RENAME_LIST,
    DELETE_LIST,
    UPSERT_ITEM,
    DELETE_ITEM,
}

@Serializable
internal data class PendingAccountWatchlistMutation(
    val operationId: String,
    val accountId: String,
    val kind: AccountWatchlistMutationKind,
    val watchlistId: String,
    val name: String = "",
    val isDefault: Boolean = false,
    val createdAt: String = "",
    val contentId: String = "",
    val contentType: String = "",
    val metadata: JsonObject = JsonObject(emptyMap()),
)

@Serializable
private data class StoredAccountWatchlistMutations(
    val accountId: String,
    val mutations: List<PendingAccountWatchlistMutation>,
)

internal class AccountWatchlistSyncOutbox(
    private val loadPayload: (accountId: String) -> String?,
    private val savePayload: (accountId: String, payload: String) -> Unit,
    private val clearPayload: (accountId: String) -> Unit,
) {
    private val lock = SynchronizedObject()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun record(mutation: PendingAccountWatchlistMutation) = synchronized(lock) {
        val pending = readLocked(mutation.accountId)
        if (pending.none { it.operationId == mutation.operationId }) {
            pending += mutation
            writeLocked(mutation.accountId, pending)
        }
    }

    fun recordOrReuseListCreate(mutation: PendingAccountWatchlistMutation): PendingAccountWatchlistMutation =
        synchronized(lock) {
            require(mutation.kind == AccountWatchlistMutationKind.CREATE_LIST)
            val pending = readLocked(mutation.accountId)
            val existing = pending.firstOrNull { queued ->
                queued.kind == AccountWatchlistMutationKind.CREATE_LIST && queued.name == mutation.name
            }
            if (existing != null) return@synchronized existing
            pending += mutation
            writeLocked(mutation.accountId, pending)
            mutation
        }

    fun pendingFor(accountId: String): List<PendingAccountWatchlistMutation> = synchronized(lock) {
        readLocked(accountId).toList()
    }

    fun clearIfMatches(mutation: PendingAccountWatchlistMutation): Boolean = synchronized(lock) {
        val pending = readLocked(mutation.accountId)
        val index = pending.indexOfFirst { queued ->
            queued.operationId == mutation.operationId && queued == mutation
        }
        if (index < 0) return@synchronized false
        pending.removeAt(index)
        writeLocked(mutation.accountId, pending)
        true
    }

    private fun readLocked(accountId: String): MutableList<PendingAccountWatchlistMutation> {
        val payload = loadPayload(accountId)?.trim().orEmpty()
        if (payload.isEmpty()) return mutableListOf()
        val stored = runCatching { json.decodeFromString<StoredAccountWatchlistMutations>(payload) }.getOrNull()
        if (stored == null || stored.accountId != accountId || stored.mutations.any { it.accountId != accountId }) {
            clearPayload(accountId)
            return mutableListOf()
        }
        return stored.mutations.toMutableList()
    }

    private fun writeLocked(accountId: String, mutations: List<PendingAccountWatchlistMutation>) {
        if (mutations.isEmpty()) {
            clearPayload(accountId)
        } else {
            savePayload(
                accountId,
                json.encodeToString(StoredAccountWatchlistMutations(accountId, mutations)),
            )
        }
    }
}

internal object AccountWatchlistMutationOutbox {
    private val delegate = AccountWatchlistSyncOutbox(
        loadPayload = LibraryStorage::loadPendingAccountWatchlistPayload,
        savePayload = LibraryStorage::savePendingAccountWatchlistPayload,
        clearPayload = LibraryStorage::clearPendingAccountWatchlistPayload,
    )

    fun record(mutation: PendingAccountWatchlistMutation) = delegate.record(mutation)

    fun recordOrReuseListCreate(mutation: PendingAccountWatchlistMutation): PendingAccountWatchlistMutation =
        delegate.recordOrReuseListCreate(mutation)

    fun pendingFor(accountId: String): List<PendingAccountWatchlistMutation> = delegate.pendingFor(accountId)

    fun clearIfMatches(mutation: PendingAccountWatchlistMutation): Boolean = delegate.clearIfMatches(mutation)
}
