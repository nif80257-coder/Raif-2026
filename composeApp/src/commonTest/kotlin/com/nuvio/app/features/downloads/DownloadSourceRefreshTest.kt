package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The download row keeps the `addonId` of the source it came from, and a retry asks that same
 * source again. Reading the kind back from that id is what decides how it is asked.
 */
class DownloadSourceRefreshTest {

    @Test
    fun `each source kind is read back from the id the list stored`() {
        assertEquals(DownloadSourceKind.MediaServer, downloadSourceKind("server:connection-a"))
        assertEquals(
            DownloadSourceKind.PluginProvider,
            downloadSourceKind("plugin:https://repo.example/manifest.json:scraper"),
        )
        assertEquals(
            DownloadSourceKind.PluginRepository,
            downloadSourceKind("plugin-repo:https://repo.example/manifest.json"),
        )
        assertEquals(
            DownloadSourceKind.StremioAddon,
            downloadSourceKind("addon:manifest-id:https://addon.example/manifest.json"),
        )
        assertEquals(DownloadSourceKind.StremioAddon, downloadSourceKind("https://addon.example/manifest.json"))
    }

    @Test
    fun `a grouped provider list is not read as a single provider`() {
        assertEquals(
            DownloadSourceKind.PluginProvider,
            downloadSourceKind("plugin:repo:one"),
        )
        assertEquals(
            DownloadSourceKind.PluginRepository,
            downloadSourceKind("plugin-repo:https://repo.example/manifest.json"),
        )
    }

    @Test
    fun `a row with no source id is left alone`() {
        assertEquals(DownloadSourceKind.Unknown, downloadSourceKind(null))
        assertEquals(DownloadSourceKind.Unknown, downloadSourceKind(""))
        assertEquals(DownloadSourceKind.Unknown, downloadSourceKind("   "))
    }
}
