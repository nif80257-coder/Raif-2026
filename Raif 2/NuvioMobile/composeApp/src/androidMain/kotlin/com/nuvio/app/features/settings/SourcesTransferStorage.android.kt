package com.nuvio.app.features.settings

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

actual object SourcesTransferStorage {
    private const val preferencesName = "nuvio_sources_transfer"
    private const val backupKey = "sources_transfer_backup"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadBackupPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(backupKey), null)

    actual fun saveBackupPayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(backupKey), payload)
            ?.apply()
    }

    actual fun clearBackupPayload() {
        preferences
            ?.edit()
            ?.remove(ProfileScopedKey.of(backupKey))
            ?.apply()
    }
}
