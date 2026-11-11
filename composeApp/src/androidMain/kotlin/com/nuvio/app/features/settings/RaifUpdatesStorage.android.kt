package com.nuvio.app.features.settings

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal actual object RaifUpdatesStorage {
    private const val preferencesName = "nuvio_raif_updates"
    private const val snapshotsKey = "addon_snapshots"
    private const val lastCheckKey = "last_check_epoch_ms"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), AddonManifestSnapshot.serializer())

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadSnapshots(): Map<String, AddonManifestSnapshot> {
        val payload = preferences?.getString(ProfileScopedKey.of(snapshotsKey), null)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, payload) }.getOrDefault(emptyMap())
    }

    actual fun saveSnapshots(snapshots: Map<String, AddonManifestSnapshot>) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(snapshotsKey), json.encodeToString(serializer, snapshots))
            ?.apply()
    }

    actual fun loadLastCheckEpochMs(): Long? =
        preferences
            ?.getLong(ProfileScopedKey.of(lastCheckKey), 0L)
            ?.takeIf { it > 0L }

    actual fun saveLastCheckEpochMs(epochMs: Long) {
        preferences
            ?.edit()
            ?.putLong(ProfileScopedKey.of(lastCheckKey), epochMs)
            ?.apply()
    }
}
