package com.nuvio.app.features.addons

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class UserAddonSyncRecord(
    @SerialName("addon_id") val addonId: String,
    @SerialName("manifest_url") val manifestUrl: String,
    val name: String? = null,
    val enabled: Boolean = true,
    val config: JsonObject = JsonObject(emptyMap()),
)

@Serializable
internal data class PendingUserAddonMutation(
    @SerialName("addon_id") val addonId: String,
    /** Null manifest URL represents a durable delete tombstone. */
    @SerialName("manifest_url") val manifestUrl: String? = null,
    val name: String? = null,
    val enabled: Boolean = true,
    val config: JsonObject = JsonObject(emptyMap()),
)

@Serializable
internal data class PendingUserAddonChange(
    val accountId: String,
    val profileId: Int,
    val mutations: List<PendingUserAddonMutation>,
)

internal class UserAddonSyncOutbox(
    private val loadPayload: (accountId: String, profileId: Int) -> String?,
    private val savePayload: (accountId: String, profileId: Int, payload: String) -> Unit,
    private val clearPayload: (accountId: String, profileId: Int) -> Unit,
) {
    private val lock = SynchronizedObject()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun record(change: PendingUserAddonChange) = synchronized(lock) {
        val existing = read(change.accountId, change.profileId)
        val mutations = linkedMapOf<String, PendingUserAddonMutation>()
        existing?.mutations.orEmpty().forEach { mutations[it.addonId] = it }
        change.mutations.forEach { mutations[it.addonId] = it }
        val merged = change.copy(mutations = mutations.values.toList())
        savePayload(change.accountId, change.profileId, json.encodeToString(merged))
    }

    fun pendingFor(accountId: String, profileId: Int): PendingUserAddonChange? = synchronized(lock) {
        read(accountId, profileId)
    }

    fun clearIfMatches(change: PendingUserAddonChange): Boolean = synchronized(lock) {
        if (read(change.accountId, change.profileId) != change) return@synchronized false
        clearPayload(change.accountId, change.profileId)
        true
    }

    private fun read(accountId: String, profileId: Int): PendingUserAddonChange? {
        val payload = loadPayload(accountId, profileId)?.trim().orEmpty()
        if (payload.isEmpty()) return null
        val change = runCatching { json.decodeFromString<PendingUserAddonChange>(payload) }.getOrNull()
        if (change == null || change.accountId != accountId || change.profileId != profileId) {
            clearPayload(accountId, profileId)
            return null
        }
        return change
    }
}

internal object AccountUserAddonSyncOutbox {
    private val delegate = UserAddonSyncOutbox(
        loadPayload = AddonStorage::loadPendingSyncPayload,
        savePayload = AddonStorage::savePendingSyncPayload,
        clearPayload = AddonStorage::clearPendingSyncPayload,
    )

    fun record(change: PendingUserAddonChange) = delegate.record(change)

    fun pendingFor(accountId: String, profileId: Int): PendingUserAddonChange? =
        delegate.pendingFor(accountId, profileId)

    fun clearIfMatches(change: PendingUserAddonChange): Boolean = delegate.clearIfMatches(change)
}

internal fun applyPendingUserAddonMutations(
    remoteRows: List<UserAddonSyncRecord>,
    mutations: List<PendingUserAddonMutation>,
): List<UserAddonSyncRecord> {
    val merged = linkedMapOf<String, UserAddonSyncRecord>()
    remoteRows.forEach { merged[it.addonId] = it }
    mutations.forEach { mutation ->
        val url = mutation.manifestUrl
        if (url == null) {
            merged.remove(mutation.addonId)
        } else {
            merged[mutation.addonId] = UserAddonSyncRecord(
                addonId = mutation.addonId,
                manifestUrl = url,
                name = mutation.name,
                enabled = mutation.enabled,
                config = mutation.config,
            )
        }
    }
    return merged.values.toList()
}
