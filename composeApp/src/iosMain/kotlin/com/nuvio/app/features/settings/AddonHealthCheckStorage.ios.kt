package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

actual object AddonHealthCheckStorage {
    private const val payloadKey = "addon_health_fingerprints"
    private const val alertShownDaysKey = "addon_health_alert_shown_days"
    private const val streamFailuresKey = "addon_stream_failures"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val alertShownDaysSerializer = MapSerializer(String.serializer(), Long.serializer())

    actual fun loadFingerprints(): Map<String, String> {
        val payload = NSUserDefaults.standardUserDefaults
            .stringForKey(ProfileScopedKey.of(payloadKey))
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, payload) }.getOrDefault(emptyMap())
    }

    actual fun saveFingerprints(fingerprints: Map<String, String>) {
        NSUserDefaults.standardUserDefaults.setObject(
            json.encodeToString(serializer, fingerprints),
            forKey = ProfileScopedKey.of(payloadKey),
        )
    }

    actual fun loadAlertShownEpochDays(): Map<String, Long> {
        val payload = NSUserDefaults.standardUserDefaults
            .stringForKey(ProfileScopedKey.of(alertShownDaysKey))
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching {
            json.decodeFromString(alertShownDaysSerializer, payload)
        }.getOrDefault(emptyMap())
    }

    actual fun saveAlertShownEpochDays(days: Map<String, Long>) {
        NSUserDefaults.standardUserDefaults.setObject(
            json.encodeToString(alertShownDaysSerializer, days),
            forKey = ProfileScopedKey.of(alertShownDaysKey),
        )
    }

    actual fun loadStreamFailures(): Map<String, String> {
        val payload = NSUserDefaults.standardUserDefaults
            .stringForKey(ProfileScopedKey.of(streamFailuresKey))
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, payload) }.getOrDefault(emptyMap())
    }

    actual fun saveStreamFailures(failures: Map<String, String>) {
        NSUserDefaults.standardUserDefaults.setObject(
            json.encodeToString(serializer, failures),
            forKey = ProfileScopedKey.of(streamFailuresKey),
        )
    }
}
