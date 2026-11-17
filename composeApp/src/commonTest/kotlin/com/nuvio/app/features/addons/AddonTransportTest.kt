package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals

class AddonTransportTest {
    private fun manifest(vararg resourceNames: String): AddonManifest =
        AddonManifest(
            id = "test",
            name = "Test",
            description = "",
            version = "1.0.0",
            resources = resourceNames.map { AddonResource(name = it, types = listOf("movie")) },
            types = listOf("movie"),
            transportUrl = "",
        )

    @Test
    fun `a known torrent host lands in the torrent group`() {
        val addon = ManagedAddon(
            manifestUrl = "https://torrentio.strem.fun/language=arabic/manifest.json",
            manifest = manifest("stream", "catalog"),
        )

        assertEquals(AddonTransport.Torrent, addon.transport())
    }

    @Test
    fun `another stream host lands in the direct group`() {
        val addon = ManagedAddon(
            manifestUrl = "https://example.com/manifest.json",
            manifest = manifest("stream"),
        )

        assertEquals(AddonTransport.Direct, addon.transport())
    }

    @Test
    fun `a manifest without a stream resource is a catalog`() {
        val addon = ManagedAddon(
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            manifest = manifest("catalog", "meta"),
        )

        assertEquals(AddonTransport.Catalog, addon.transport())
    }

    @Test
    fun `an add-on that never loaded keeps the torrent host hint`() {
        val addon = ManagedAddon(manifestUrl = "https://addon.peerflix.mov/enc_x/manifest.json")

        assertEquals(AddonTransport.Torrent, addon.transport())
    }

    @Test
    fun `an add-on that never loaded and is not a torrent host is treated as direct`() {
        val addon = ManagedAddon(manifestUrl = "https://unknown.example/manifest.json")

        assertEquals(AddonTransport.Direct, addon.transport())
    }
}
