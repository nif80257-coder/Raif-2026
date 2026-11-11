package com.nuvio.app.features.settings

internal expect object RaifUpdatesStorage {
    fun loadSnapshots(): Map<String, AddonManifestSnapshot>
    fun saveSnapshots(snapshots: Map<String, AddonManifestSnapshot>)
    fun loadLastCheckEpochMs(): Long?
    fun saveLastCheckEpochMs(epochMs: Long)
}
