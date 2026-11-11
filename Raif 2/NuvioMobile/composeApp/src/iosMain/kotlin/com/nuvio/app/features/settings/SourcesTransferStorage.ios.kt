package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

actual object SourcesTransferStorage {
    private const val backupKey = "sources_transfer_backup"

    actual fun loadBackupPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(backupKey))

    actual fun saveBackupPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(backupKey))
    }

    actual fun clearBackupPayload() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(backupKey))
    }
}
