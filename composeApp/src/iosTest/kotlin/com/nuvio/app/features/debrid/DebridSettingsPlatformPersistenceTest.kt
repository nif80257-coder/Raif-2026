package com.nuvio.app.features.debrid

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.storage.SecretStore
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import platform.Foundation.NSUserDefaults

class DebridSettingsPlatformPersistenceTest {
    @Test
    fun providerApiKeyMigratesUpdatesAndDeletesInKeychain() {
        val providerId = "ios_test_storage_fixture"
        val key = "debrid_${providerId}_api_key"
        val scopedKey = ProfileScopedKey.of(key)
        val defaults = NSUserDefaults.standardUserDefaults
        val originalLegacyValue = defaults.stringForKey(scopedKey)
        val originalSecret = SecretStore.read(scopedKey)

        try {
            SecretStore.write(scopedKey, null)
            defaults.removeObjectForKey(scopedKey)
            defaults.setObject("legacy debrid credential fixture", forKey = scopedKey)

            assertEquals(
                "legacy debrid credential fixture",
                DebridSettingsStorage.loadProviderApiKey(providerId),
            )
            assertEquals("legacy debrid credential fixture", SecretStore.read(scopedKey))
            assertNull(defaults.stringForKey(scopedKey))

            DebridSettingsStorage.saveProviderApiKey(providerId, "rotated debrid credential fixture")
            assertEquals("rotated debrid credential fixture", SecretStore.read(scopedKey))
            assertEquals(
                "rotated debrid credential fixture",
                DebridSettingsStorage.loadProviderApiKey(providerId),
            )

            DebridSettingsStorage.saveProviderApiKey(providerId, "")
            assertNull(SecretStore.read(scopedKey))
            assertNull(DebridSettingsStorage.loadProviderApiKey(providerId))
            assertNull(defaults.stringForKey(scopedKey))
        } finally {
            SecretStore.write(scopedKey, originalSecret)
            if (originalLegacyValue == null) {
                defaults.removeObjectForKey(scopedKey)
            } else {
                defaults.setObject(originalLegacyValue, forKey = scopedKey)
            }
        }
    }

    @Test
    fun replacingSyncPayloadPreservesProviderSecretsMissingFromPayload() {
        val defaults = NSUserDefaults.standardUserDefaults
        val providerKeys = DebridProviders.all().map { provider ->
            val scopedKey = ProfileScopedKey.of("debrid_${provider.id}_api_key")
            ProviderKeyState(
                scopedKey = scopedKey,
                originalLegacyValue = defaults.stringForKey(scopedKey),
                originalSecret = SecretStore.read(scopedKey),
            )
        }

        try {
            providerKeys.forEach { state ->
                SecretStore.write(state.scopedKey, "pre-sync debrid credential fixture")
                defaults.setObject("stale legacy pre-sync credential fixture", forKey = state.scopedKey)
            }

            DebridSettingsStorage.replaceFromSyncPayload(buildJsonObject {})

            providerKeys.forEach { state ->
                assertEquals("pre-sync debrid credential fixture", SecretStore.read(state.scopedKey))
                assertNull(defaults.stringForKey(state.scopedKey))
            }
        } finally {
            providerKeys.forEach { state ->
                SecretStore.write(state.scopedKey, state.originalSecret)
                if (state.originalLegacyValue == null) {
                    defaults.removeObjectForKey(state.scopedKey)
                } else {
                    defaults.setObject(state.originalLegacyValue, forKey = state.scopedKey)
                }
            }
        }
    }

    private data class ProviderKeyState(
        val scopedKey: String,
        val originalLegacyValue: String?,
        val originalSecret: String?,
    )
}
