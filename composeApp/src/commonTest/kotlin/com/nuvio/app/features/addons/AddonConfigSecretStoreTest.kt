package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddonConfigSecretStoreTest {
    @Test
    fun `restoring a protected path restores provider path and preserves resource query`() {
        val safeManifest = "https://addons.example.org/__raif_secret_path_-abc123/manifest.json"
        val resourceUrl = "https://addons.example.org/__raif_secret_path_-abc123/stream/movie/tt123.json?language=ar"
        val restored = restoreSecretPath(
            url = resourceUrl,
            manifestUrl = safeManifest,
            secretPath = "/users/profile-token",
        )

        assertEquals(
            "https://addons.example.org/users/profile-token/stream/movie/tt123.json?language=ar",
            restored,
        )
    }

    @Test
    fun `stored secret query pairs replace duplicate keys without changing unrelated parameters`() {
        assertEquals(
            "https://addons.example.org/manifest.json?language=ar&api_key=new",
            appendAddonSecretQueryPairs(
                url = "https://addons.example.org/manifest.json?language=ar&api_key=old",
                queryPairs = listOf("api_key=new"),
            ),
        )
    }

    @Test
    fun `restore leaves unrelated resource paths unchanged`() {
        val restored = restoreSecretPath(
            url = "https://addons.example.org/other/stream/movie/tt123.json",
            manifestUrl = "https://addons.example.org/__raif_secret_path_-abc123/manifest.json",
            secretPath = "/users/profile-token",
        )

        assertEquals("https://addons.example.org/other/stream/movie/tt123.json", restored)
        assertTrue(restored.startsWith("https://"))
    }
}
