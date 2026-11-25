package com.nuvio.app.features.library

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.auth.currentSupabaseAccountUserId
import com.nuvio.app.core.auth.isCurrentSupabaseAccount
import com.nuvio.app.core.auth.requireSupabaseAccountUserId
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.tracking.LibraryListPrivacy
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import com.nuvio.app.features.tracking.TrackingListManager
import com.nuvio.app.features.tracking.TrackingListManagementCapabilities
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val ACCOUNT_WATCHLIST_KEY_PREFIX = "account-watchlist:"
private const val WATCHLIST_PAGE_SIZE = 900

@Serializable
private data class WatchlistRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class WatchlistItemRow(
    val id: String,
    @SerialName("watchlist_id") val watchlistId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val metadata: JsonObject = JsonObject(emptyMap()),
    @SerialName("added_at") val addedAt: String,
)

@Serializable
private data class WatchlistItemWriteRow(
    @SerialName("watchlist_id") val watchlistId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val metadata: JsonObject,
)

internal data class AccountWatchlist(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val createdAt: String,
)

internal data class AccountWatchlistState(
    val userId: String? = null,
    val watchlists: List<AccountWatchlist> = emptyList(),
    val itemsByKey: Map<String, LibraryItem> = emptyMap(),
    val isLoaded: Boolean = false,
)

object AccountWatchlistRepository {
    private val log = Logger.withTag("AccountWatchlistRepository")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(AccountWatchlistState())
    internal val state: StateFlow<AccountWatchlistState> = _state.asStateFlow()

    private var loadedUserId: String? = null
    private var accountGeneration = 0L

    val listManager: TrackingListManager = object : TrackingListManager {
        override val capabilities = TrackingListManagementCapabilities(
            privacyOptions = listOf(LibraryListPrivacy.PRIVATE),
        )

        override suspend fun createList(name: String, description: String?, privacy: LibraryListPrivacy) {
            createWatchlist(name)
        }

        override suspend fun updateList(
            key: String,
            name: String,
            description: String?,
            privacy: LibraryListPrivacy,
        ) {
            renameWatchlist(key, name)
        }

        override suspend fun deleteList(key: String) {
            deleteWatchlist(key)
        }
    }

    init {
        scope.launch {
            AuthRepository.state.collectLatest { authState ->
                val userId = (authState as? AuthState.Authenticated)
                    ?.takeIf { authenticated -> !authenticated.isAnonymous }
                    ?.userId
                    ?.takeIf(String::isNotBlank)
                if (loadedUserId == userId && _state.value.userId == userId) return@collectLatest
                loadedUserId = null
                accountGeneration += 1L
                _state.value = AccountWatchlistState(userId = userId)
                if (userId != null) {
                    runCatching { pullFromServer() }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            log.w(error) { "Initial watchlist sync failed" }
                        }
                }
            }
        }
    }

    fun currentAccountGeneration(): Long = accountGeneration

    fun currentAccountUserId(): String? = currentSupabaseAccountUserId()

    fun isWatchlistKey(key: String): Boolean = key.startsWith(ACCOUNT_WATCHLIST_KEY_PREFIX)

    fun tabs(): List<TrackingLibraryTab> {
        ensurePullScheduled()
        return _state.value.watchlists.map { watchlist ->
            TrackingLibraryTab(
                key = watchlist.key,
                title = watchlist.name,
                providerId = null,
                kind = TrackingLibraryTabKind.PERSONAL,
            )
        }
    }

    suspend fun ensureLoaded() {
        val userId = currentSupabaseAccountUserId() ?: return
        if (loadedUserId != userId || !_state.value.isLoaded) pullFromServer()
    }

    suspend fun pullFromServer() = performAccountOperation("Watchlist pull") {
        val userId = currentSupabaseAccountUserId() ?: return@performAccountOperation
        syncMutex.withLock {
            ensureCurrentAccount(userId)
            val pendingMutations = AccountWatchlistMutationOutbox.pendingFor(userId)
            pendingMutations.forEach { mutation ->
                ensureCurrentAccount(userId)
                applyPendingMutation(mutation)
            }
            val watchlists = fetchAll<WatchlistRow>("watchlists", userId)
                .filter { row -> row.userId == userId }
                .map { row -> AccountWatchlist(row.id, row.name, row.isDefault, row.createdAt) }
            val remoteItems = fetchAll<WatchlistItemRow>("watchlist_items", userId)
            if (!isCurrentSupabaseAccount(userId)) return@withLock

            val watchlistIds = watchlists.mapTo(mutableSetOf(), AccountWatchlist::id)
            val itemsByKey = remoteItems
                .filter { row -> row.watchlistId in watchlistIds && row.userId == userId }
                .associate { row ->
                    val contentType = normalizeSupabaseContentType(row.contentType)
                    val metadata = runCatching {
                        decodeLibrarySyncMetadata(json, row.metadata)
                    }.getOrDefault(LibrarySyncMetadata())
                    watchlistItemKey(row.watchlistId, row.contentId, contentType) to metadata.toLibraryItem(
                        contentId = row.contentId,
                        contentType = contentType,
                        fallbackSavedAtEpochMs = parseTimestamp(row.addedAt),
                    )
                }
            if (!isCurrentSupabaseAccount(userId)) return@withLock
            loadedUserId = userId
            _state.value = AccountWatchlistState(
                userId = userId,
                watchlists = watchlists,
                itemsByKey = itemsByKey,
                isLoaded = true,
            )
            pendingMutations.forEach { mutation ->
                ensureCurrentAccount(userId)
                AccountWatchlistMutationOutbox.clearIfMatches(mutation)
            }
        }
    }

    suspend fun applyMembership(item: LibraryItem, desiredMembership: Map<String, Boolean>) =
        performAccountOperation("Watchlist item sync") {
            val userId = requireSupabaseAccountUserId()
            ensureLoaded()
            ensureCurrentAccount(userId)
            val validLists = _state.value.watchlists.associateBy { watchlist -> watchlist.key }
            val contentType = normalizeSupabaseContentType(item.type)
            require(contentType in setOf("movie", "series", "episode")) {
                "This content type cannot be added to a watchlist."
            }

            desiredMembership.forEach { (key, desired) ->
                ensureCurrentAccount(userId)
                val watchlist = validLists[key] ?: return@forEach
                val rowKey = watchlistItemKey(watchlist.id, item.id, contentType)
                val isMember = rowKey in _state.value.itemsByKey
                if (desired == isMember) return@forEach
                val mutation = PendingAccountWatchlistMutation(
                    operationId = newOperationId(),
                    accountId = userId,
                    kind = if (desired) {
                        AccountWatchlistMutationKind.UPSERT_ITEM
                    } else {
                        AccountWatchlistMutationKind.DELETE_ITEM
                    },
                    watchlistId = watchlist.id,
                    contentId = item.id,
                    contentType = contentType,
                    metadata = item.toSafeSyncMetadataJson(json),
                )
                AccountWatchlistMutationOutbox.record(mutation)
                ensureCurrentAccount(userId)
            }
            pullFromServer()
        }

    private suspend fun createWatchlist(rawName: String) =
        performAccountOperation("Watchlist create") {
            val userId = requireSupabaseAccountUserId()
            val name = rawName.trim().take(80)
            require(name.isNotBlank()) { "Enter a list name." }
            ensureCurrentAccount(userId)
            AccountWatchlistMutationOutbox.recordOrReuseListCreate(
                PendingAccountWatchlistMutation(
                    operationId = newOperationId(),
                    accountId = userId,
                    kind = AccountWatchlistMutationKind.CREATE_LIST,
                    watchlistId = newOperationId(),
                    name = name,
                    createdAt = kotlin.time.Clock.System.now().toString(),
                ),
            )
            ensureCurrentAccount(userId)
            pullFromServer()
        }

    private suspend fun renameWatchlist(key: String, rawName: String) =
        performAccountOperation("Watchlist update") {
            val userId = requireSupabaseAccountUserId()
            ensureLoaded()
            ensureCurrentAccount(userId)
            val watchlistId = watchlistIdFromKey(key) ?: throw IllegalArgumentException("Invalid watchlist.")
            val watchlist = _state.value.watchlists.firstOrNull { it.id == watchlistId }
                ?: throw IllegalStateException("This watchlist is no longer available.")
            val name = rawName.trim().take(80)
            require(name.isNotBlank()) { "Enter a list name." }
            AccountWatchlistMutationOutbox.record(
                PendingAccountWatchlistMutation(
                    operationId = newOperationId(),
                    accountId = userId,
                    kind = AccountWatchlistMutationKind.RENAME_LIST,
                    watchlistId = watchlist.id,
                    name = name,
                    isDefault = watchlist.isDefault,
                    createdAt = watchlist.createdAt,
                ),
            )
            ensureCurrentAccount(userId)
            pullFromServer()
        }

    private suspend fun deleteWatchlist(key: String) =
        performAccountOperation("Watchlist delete") {
            val userId = requireSupabaseAccountUserId()
            ensureLoaded()
            ensureCurrentAccount(userId)
            val watchlistId = watchlistIdFromKey(key) ?: throw IllegalArgumentException("Invalid watchlist.")
            AccountWatchlistMutationOutbox.record(
                PendingAccountWatchlistMutation(
                    operationId = newOperationId(),
                    accountId = userId,
                    kind = AccountWatchlistMutationKind.DELETE_LIST,
                    watchlistId = watchlistId,
                ),
            )
            ensureCurrentAccount(userId)
            pullFromServer()
        }

    private fun ensurePullScheduled() {
        val userId = currentSupabaseAccountUserId() ?: return
        if (loadedUserId == userId || _state.value.userId != userId) return
        scope.launch {
            runCatching { pullFromServer() }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    log.w(error) { "Watchlist refresh failed" }
                }
        }
    }

    private suspend fun applyPendingMutation(mutation: PendingAccountWatchlistMutation) {
        val userId = mutation.accountId
        ensureCurrentAccount(userId)
        when (mutation.kind) {
            AccountWatchlistMutationKind.CREATE_LIST,
            AccountWatchlistMutationKind.RENAME_LIST -> {
                require(mutation.name.isNotBlank() && mutation.createdAt.isNotBlank()) {
                    "A queued watchlist is missing required list details."
                }
                SupabaseProvider.client.postgrest.from("watchlists")
                    .upsert(
                        WatchlistRow(
                            id = mutation.watchlistId,
                            userId = userId,
                            name = mutation.name,
                            isDefault = mutation.isDefault,
                            createdAt = mutation.createdAt,
                        ),
                    ) { onConflict = "id" }
            }

            AccountWatchlistMutationKind.DELETE_LIST -> {
                SupabaseProvider.client.postgrest.from("watchlist_items").delete {
                    filter {
                        eq("user_id", userId)
                        eq("watchlist_id", mutation.watchlistId)
                    }
                }
                ensureCurrentAccount(userId)
                SupabaseProvider.client.postgrest.from("watchlists").delete {
                    filter {
                        eq("user_id", userId)
                        eq("id", mutation.watchlistId)
                    }
                }
            }

            AccountWatchlistMutationKind.UPSERT_ITEM -> {
                require(mutation.contentId.isNotBlank() && mutation.contentType.isNotBlank()) {
                    "A queued watchlist item is missing its content identity."
                }
                val write = WatchlistItemWriteRow(
                    watchlistId = mutation.watchlistId,
                    userId = userId,
                    contentId = mutation.contentId,
                    contentType = mutation.contentType,
                    metadata = mutation.metadata,
                )
                SupabaseProvider.client.postgrest.from("watchlist_items")
                    .upsert(write) { onConflict = "watchlist_id,content_id,content_type" }
            }

            AccountWatchlistMutationKind.DELETE_ITEM -> {
                require(mutation.contentId.isNotBlank() && mutation.contentType.isNotBlank()) {
                    "A queued watchlist item is missing its content identity."
                }
                SupabaseProvider.client.postgrest.from("watchlist_items").delete {
                    filter {
                        eq("user_id", userId)
                        eq("watchlist_id", mutation.watchlistId)
                        eq("content_id", mutation.contentId)
                        eq("content_type", mutation.contentType)
                    }
                }
            }
        }
        ensureCurrentAccount(userId)
    }

    private suspend inline fun <reified T> fetchAll(
        table: String,
        userId: String,
    ): List<T> {
        val rows = mutableListOf<T>()
        var offset = 0L
        while (true) {
            ensureCurrentAccount(userId)
            val page = SupabaseProvider.client.postgrest.from(table)
                .select {
                    filter { eq("user_id", userId) }
                    range(offset, offset + WATCHLIST_PAGE_SIZE.toLong() - 1L)
                }
                .decodeList<T>()
            ensureCurrentAccount(userId)
            rows += page
            if (page.size < WATCHLIST_PAGE_SIZE) break
            offset += page.size.toLong()
        }
        return rows
    }

    private suspend fun performAccountOperation(
        operation: String,
        action: suspend () -> Unit,
    ) {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AuthRepository.signOutIfSessionInvalid(error, operation)
            throw error
        }
    }

    private fun ensureCurrentAccount(userId: String) {
        if (!isCurrentSupabaseAccount(userId)) {
            throw CancellationException("Account changed during watchlist sync")
        }
    }

    fun membership(item: LibraryItem): Map<String, Boolean> {
        ensurePullScheduled()
        val contentType = normalizeSupabaseContentType(item.type)
        val snapshot = _state.value
        return snapshot.watchlists.associate { watchlist ->
            watchlist.key to (watchlistItemKey(watchlist.id, item.id, contentType) in snapshot.itemsByKey)
        }
    }

    fun itemForWatchlist(watchlistKey: String, contentId: String, contentType: String): LibraryItem? {
        val watchlistId = watchlistIdFromKey(watchlistKey) ?: return null
        return _state.value.itemsByKey[watchlistItemKey(watchlistId, contentId, normalizeSupabaseContentType(contentType))]
    }

    fun clearLocalState() {
        loadedUserId = null
        accountGeneration += 1L
        _state.value = AccountWatchlistState()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newOperationId(): String = Uuid.random().toString()

    private val AccountWatchlist.key: String
        get() = "$ACCOUNT_WATCHLIST_KEY_PREFIX$id"

    private fun watchlistIdFromKey(key: String): String? =
        key.takeIf { it.startsWith(ACCOUNT_WATCHLIST_KEY_PREFIX) }
            ?.removePrefix(ACCOUNT_WATCHLIST_KEY_PREFIX)
            ?.takeIf(String::isNotBlank)

    private fun watchlistItemKey(watchlistId: String, contentId: String, contentType: String): String =
        "$watchlistId|${libraryItemKey(contentId, contentType)}"

    private fun parseTimestamp(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrDefault(0L)
}
