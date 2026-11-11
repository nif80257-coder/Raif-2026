package com.nuvio.app.features.settings

internal expect object SourcesTransferStorage {
    fun loadBackupPayload(): String?
    fun saveBackupPayload(payload: String)
    fun clearBackupPayload()
}
