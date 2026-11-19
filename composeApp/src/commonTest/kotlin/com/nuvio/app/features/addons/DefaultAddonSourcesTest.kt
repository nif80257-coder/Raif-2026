package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultAddonSourcesTest {
    @Test
    fun `the shipped list is the checked forty-one`() {
        assertEquals(41, DefaultAddonSources.size)
        assertEquals(41, DefaultAddonSources.map { source -> source.manifestUrl }.toSet().size)
    }

    @Test
    fun `no retired source is shipped`() {
        assertTrue(DefaultAddonSources.none { source -> isRetiredDefaultAddon(source.manifestUrl) })
    }

    @Test
    fun `the orion source with its embedded key is retired`() {
        assertTrue(isRetiredDefaultAddon("https://5a0d1888fa64-orion.baby-beamup.club/eyJhcGkiOiJMRUo4/manifest.json"))
    }

    @Test
    fun `the three catalogs stay`() {
        val urls = DefaultAddonSources.map { source -> source.manifestUrl }

        assertEquals(1, urls.count { url -> url == "https://v3-cinemeta.strem.io/manifest.json" })
        assertEquals(1, urls.count { url -> url.contains("arabcinemeta.osamayousry.com") })
        assertEquals(1, urls.count { url -> url.contains("collections.stremiolabar.com") })
    }

    @Test
    fun `the five subtitle providers stay`() {
        val urls = DefaultAddonSources.map { source -> source.manifestUrl }

        listOf(
            "subtito.com",
            "subsource.strem.top",
            "subdl.strem.top",
            "submaker.elfhosted.com",
            "opensubtitles-v3.strem.io",
        ).forEach { host ->
            assertEquals(1, urls.count { url -> url.contains(host) }, "subtitle provider $host")
        }
    }

    @Test
    fun `an add-on the user hosts on raw github is not retired`() {
        assertFalse(isRetiredDefaultAddon("https://raw.githubusercontent.com/someone/addons/main/manifest.json"))
        assertTrue(isRetiredDefaultAddon("https://raw.githubusercontent.com/yowmamasita/usa-tv-next/main/manifest.json"))
    }

    @Test
    fun `a dead host and a debrid key host are retired`() {
        assertTrue(isRetiredDefaultAddon("https://comet.elfhosted.com/eyJkZWJyaWQifQ==/manifest.json"))
        assertTrue(isRetiredDefaultAddon("https://froststream.cloutteam.com/usr_0488/manifest.json"))
        assertFalse(isRetiredDefaultAddon("https://torrentio.strem.fun/language=arabic/manifest.json"))
    }
}
