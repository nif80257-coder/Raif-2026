package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.storage.SecretStore
import com.nuvio.app.features.mdblist.MdbListSettingsStorage
import com.nuvio.app.features.player.PlayerSettingsStorage
import com.nuvio.app.features.tmdb.TmdbSettingsStorage
import kotlinx.serialization.json.buildJsonObject
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiCredentialPlatformPersistenceTest {
    @Test
    fun tmdbApiKeyMigratesUpdatesAndDeletesInKeychain() {
        assertCredentialMigration(
            baseKey = "tmdb_api_key",
            load = TmdbSettingsStorage::loadApiKey,
            save = TmdbSettingsStorage::saveApiKey,
        )
    }

    @Test
    fun mdbListApiKeyMigratesUpdatesAndDeletesInKeychain() {
        assertCredentialMigration(
            baseKey = "mdblist_api_key",
            load = MdbListSettingsStorage::loadApiKey,
            save = MdbListSettingsStorage::saveApiKey,
        )
    }

    @Test
    fun introDbApiKeyMigratesUpdatesAndDeletesInKeychain() {
        assertCredentialMigration(
            baseKey = "introdb_api_key",
            load = PlayerSettingsStorage::loadIntroDbApiKey,
            save = PlayerSettingsStorage::saveIntroDbApiKey,
        )
    }

    @Test
    fun replacingMdbListSyncPayloadPreservesKeychainCredentialWhenMissing() {
        val name = ProfileScopedKey.of("mdblist_api_key")
        val defaults = NSUserDefaults.standardUserDefaults
        val originalLegacyValue = defaults.stringForKey(name)
        val originalSecret = SecretStore.read(name)

        try {
            SecretStore.write(name, "pre-sync credential fixture")
            defaults.setObject("stale legacy pre-sync credential fixture", forKey = name)

            MdbListSettingsStorage.replaceFromSyncPayload(buildJsonObject {})

            assertEquals("pre-sync credential fixture", SecretStore.read(name))
            assertNull(defaults.stringForKey(name))
        } finally {
            SecretStore.write(name, originalSecret)
            if (originalLegacyValue == null) {
                defaults.removeObjectForKey(name)
            } else {
                defaults.setObject(originalLegacyValue, forKey = name)
            }
        }
    }

    private fun assertCredentialMigration(
        baseKey: String,
        load: () -> String?,
        save: (String) -> Unit,
    ) {
        val name = ProfileScopedKey.of(baseKey)
        val defaults = NSUserDefaults.standardUserDefaults
        val originalLegacyValue = defaults.stringForKey(name)
        val originalSecret = SecretStore.read(name)

        try {
            SecretStore.write(name, null)
            defaults.removeObjectForKey(name)
            defaults.setObject("legacy credential fixture", forKey = name)

            assertEquals("legacy credential fixture", load())
            assertEquals("legacy credential fixture", SecretStore.read(name))
            assertNull(defaults.stringForKey(name))

            save("rotated credential fixture")
            assertEquals("rotated credential fixture", SecretStore.read(name))
            assertEquals("rotated credential fixture", load())

            save("")
            assertNull(SecretStore.read(name))
            assertNull(load())
            assertNull(defaults.stringForKey(name))
        } finally {
            SecretStore.write(name, originalSecret)
            if (originalLegacyValue == null) {
                defaults.removeObjectForKey(name)
            } else {
                defaults.setObject(originalLegacyValue, forKey = name)
            }
        }
    }
}
