package com.nuvio.app.features.settings

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

actual object AddonHealthCheckStorage {
    private const val preferencesName = "nuvio_addon_health_check"
    private const val payloadKey = "addon_health_fingerprints"
    private const val alertShownDaysKey = "addon_health_alert_shown_days"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val alertShownDaysSerializer = MapSerializer(String.serializer(), Long.serializer())

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadFingerprints(): Map<String, String> {
        val payload = preferences?.getString(ProfileScopedKey.of(payloadKey), null)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, payload) }.getOrDefault(emptyMap())
    }

    actual fun saveFingerprints(fingerprints: Map<String, String>) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(payloadKey), json.encodeToString(serializer, fingerprints))
            ?.apply()
    }

    actual fun loadAlertShownEpochDays(): Map<String, Long> {
        val payload = preferences?.getString(ProfileScopedKey.of(alertShownDaysKey), null)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching {
            json.decodeFromString(alertShownDaysSerializer, payload)
        }.getOrDefault(emptyMap())
    }

    actual fun saveAlertShownEpochDays(days: Map<String, Long>) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(alertShownDaysKey), json.encodeToString(alertShownDaysSerializer, days))
            ?.apply()
    }
}
