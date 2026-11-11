package com.nuvio.app.features.trakt

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object TraktSettingsStorage {
    private const val payloadKey = "trakt_settings_payload"
    private const val pendingWatchProgressSourceKey = "pending_watch_progress_source"

    actual fun loadPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(payloadKey))
    }

    actual fun loadPendingWatchProgressSourcePayload(profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(
            ProfileScopedKey.of(pendingWatchProgressSourceKey, profileId),
        )

    actual fun savePendingWatchProgressSourcePayload(profileId: Int, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(
            payload,
            forKey = ProfileScopedKey.of(pendingWatchProgressSourceKey, profileId),
        )
    }

    actual fun clearPendingWatchProgressSourcePayload(profileId: Int) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(
            ProfileScopedKey.of(pendingWatchProgressSourceKey, profileId),
        )
    }

    actual fun loadPendingAccountSettingsPayload(accountId: String, profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(pendingAccountSettingsKey(accountId, profileId))

    actual fun savePendingAccountSettingsPayload(accountId: String, profileId: Int, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = pendingAccountSettingsKey(accountId, profileId))
    }

    actual fun clearPendingAccountSettingsPayload(accountId: String, profileId: Int) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(pendingAccountSettingsKey(accountId, profileId))
    }

    private fun pendingAccountSettingsKey(accountId: String, profileId: Int): String =
        "pending_account_settings_${profileId}_$accountId"
}
