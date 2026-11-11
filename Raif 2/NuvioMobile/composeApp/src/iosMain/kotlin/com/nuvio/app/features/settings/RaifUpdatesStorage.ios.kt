package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

internal actual object RaifUpdatesStorage {
    private const val snapshotsKey = "addon_snapshots"
    private const val lastCheckKey = "last_check_epoch_ms"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), AddonManifestSnapshot.serializer())

    actual fun loadSnapshots(): Map<String, AddonManifestSnapshot> {
        val payload = NSUserDefaults.standardUserDefaults
            .stringForKey(ProfileScopedKey.of(snapshotsKey))
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, payload) }.getOrDefault(emptyMap())
    }

    actual fun saveSnapshots(snapshots: Map<String, AddonManifestSnapshot>) {
        NSUserDefaults.standardUserDefaults.setObject(
            json.encodeToString(serializer, snapshots),
            forKey = ProfileScopedKey.of(snapshotsKey),
        )
    }

    actual fun loadLastCheckEpochMs(): Long? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(lastCheckKey)
        if (defaults.objectForKey(key) == null) return null
        return defaults.doubleForKey(key).toLong().takeIf { it > 0L }
    }

    actual fun saveLastCheckEpochMs(epochMs: Long) {
        NSUserDefaults.standardUserDefaults.setDouble(epochMs.toDouble(), forKey = ProfileScopedKey.of(lastCheckKey))
    }
}
