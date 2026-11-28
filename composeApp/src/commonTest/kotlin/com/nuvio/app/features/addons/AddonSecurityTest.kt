package com.nuvio.app.features.addons

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonSecurityTest {
    @Test
    fun `a host that answers public once and private the next time is refused`() = runBlocking<Unit> {
        var lookups = 0
        assertFailsWith<AddonUnsafeDestinationException> {
            verifyAddonDestinationIsPublic("https://addons.example.org/manifest.json") { _ ->
                lookups += 1
                if (lookups == 1) listOf("93.184.216.34") else listOf("192.168.1.20")
            }
        }
    }

    @Test
    fun `a host that keeps answering public addresses is accepted`() = runBlocking<Unit> {
        var lookups = 0
        verifyAddonDestinationIsPublic("https://addons.example.org/manifest.json") { _ ->
            lookups += 1
            listOf("93.184.216.34")
        }
        assertEquals(2, lookups)
    }
    @Test
    fun `public web URLs are accepted while local and malformed endpoints are rejected`() {
        assertTrue(isSafePublicWebUrl("https://addons.example.org/manifest.json"))
        assertFalse(isSafePublicWebUrl("http://127.0.0.1/manifest.json"))
        assertFalse(isSafePublicWebUrl("https://192.168.1.20/manifest.json"))
        assertFalse(isSafePublicWebUrl("https://addons.example.org:99999/manifest.json"))
        assertFalse(isSafePublicWebUrl("https://user:pass@addons.example.org/manifest.json"))
        assertFalse(isSafePublicWebUrl("file:///etc/passwd"))
    }

    @Test
    fun `normalization appends manifest endpoint and preserves a non-secret query`() {
        assertEquals(
            "https://addons.example.org/catalog/manifest.json?language=ar",
            normalizeAddonManifestUrl("addons.example.org/catalog?language=ar"),
        )
    }

    @Test
    fun `generated protected path marker is not mistaken for an exposed secret path`() {
        val split = splitSecretAddonConfigUrl(
            manifestUrl = "https://addons.example.org/users/private-profile/manifest.json?api_key=private&language=ar",
            fields = listOf(AddonConfigField(key = "api_key", type = "password")),
        )

        assertTrue(split.safeManifestUrl.contains("/__raif_secret_path_"))
        assertEquals(listOf("api_key=private"), split.secretQueryPairs)
        assertEquals("/users/private-profile", split.secretPath)
        assertFalse(hasSensitiveAddonPath(split.safeManifestUrl))

        val remoteUrl = AddonConfigSecretStore.safeRemoteManifestUrl(
            split.safeManifestUrl,
            listOf(
                AddonConfigField(key = "api_key", type = "password"),
                AddonConfigField(key = "language"),
            ),
        )
        assertEquals(split.safeManifestUrl, remoteUrl)
        assertEquals(split.safeManifestUrl, AddonConfigSecretStore.safeExportManifestUrl(
            split.safeManifestUrl,
            listOf(
                AddonConfigField(key = "api_key", type = "password"),
                AddonConfigField(key = "language"),
            ),
        ))
    }

    @Test
    fun `secret query parameters are removed from remote and export URLs`() {
        val fields = listOf(
            AddonConfigField(key = "api_key", type = "password"),
            AddonConfigField(key = "language"),
        )
        val safeUrl = AddonConfigSecretStore.safeRemoteManifestUrl(
            "https://addons.example.org/manifest.json?api_key=private&language=ar",
            fields,
        )

        assertEquals("https://addons.example.org/manifest.json?language=ar", safeUrl)
        assertEquals(safeUrl, AddonConfigSecretStore.safeExportManifestUrl(
            "https://addons.example.org/manifest.json?api_key=private&language=ar",
            fields,
        ))
    }
}
