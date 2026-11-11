package com.nuvio.app.core.sync

import com.nuvio.app.features.trakt.TraktSettingsStorage
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PendingProfileSettingsChange(
    val accountId: String,
    val profileId: Int,
    val payload: String,
)

internal class ProfileSettingsSyncOutbox(
    private val loadPayload: (accountId: String, profileId: Int) -> String?,
    private val savePayload: (accountId: String, profileId: Int, payload: String) -> Unit,
    private val clearPayload: (accountId: String, profileId: Int) -> Unit,
) {
    private val lock = SynchronizedObject()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun record(change: PendingProfileSettingsChange) = synchronized(lock) {
        savePayload(change.accountId, change.profileId, json.encodeToString(change))
    }

    fun pendingFor(accountId: String, profileId: Int): PendingProfileSettingsChange? = synchronized(lock) {
        val payload = loadPayload(accountId, profileId)?.trim().orEmpty()
        if (payload.isEmpty()) return@synchronized null
        val change = runCatching { json.decodeFromString<PendingProfileSettingsChange>(payload) }.getOrNull()
        if (change == null || change.accountId != accountId || change.profileId != profileId) {
            clearPayload(accountId, profileId)
            return@synchronized null
        }
        change
    }

    fun clearIfMatches(change: PendingProfileSettingsChange): Boolean = synchronized(lock) {
        if (pendingFor(change.accountId, change.profileId) != change) return@synchronized false
        clearPayload(change.accountId, change.profileId)
        true
    }
}

internal object AccountProfileSettingsSyncOutbox {
    private val delegate = ProfileSettingsSyncOutbox(
        loadPayload = TraktSettingsStorage::loadPendingAccountSettingsPayload,
        savePayload = TraktSettingsStorage::savePendingAccountSettingsPayload,
        clearPayload = TraktSettingsStorage::clearPendingAccountSettingsPayload,
    )

    fun record(change: PendingProfileSettingsChange) = delegate.record(change)

    fun pendingFor(accountId: String, profileId: Int): PendingProfileSettingsChange? =
        delegate.pendingFor(accountId, profileId)

    fun clearIfMatches(change: PendingProfileSettingsChange): Boolean = delegate.clearIfMatches(change)
}
