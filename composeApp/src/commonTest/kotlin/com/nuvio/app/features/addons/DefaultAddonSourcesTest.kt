package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultAddonSourcesTest {
    @Test
    fun `the shipped list is the checked twenty-four`() {
        assertEquals(24, DefaultAddonSources.size)
        assertEquals(24, DefaultAddonSources.map { source -> source.manifestUrl }.toSet().size)
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
    fun `no bundled stream source carries an account configuration`() {
        // A `profile=`, an `enc_` blob, a `/u/` path or an `/addon/<id>` path is one person's
        // configuration: the source stops answering the day that person changes or revokes it, and
        // it was the finding that four stream sources shipped someone else's setup.
        DefaultAddonSources
            .map { source -> source.manifestUrl }
            .filter { url ->
                KeptCatalogAddonHosts.none { host -> url.contains(host, ignoreCase = true) } &&
                    KeptSubtitleAddonHosts.none { host -> url.contains(host, ignoreCase = true) }
            }
            .forEach { url ->
                assertFalse(url.contains("|profile="), "profile id in $url")
                assertFalse(url.contains("/enc_"), "encrypted config in $url")
                assertFalse(url.contains("/u/"), "user path in $url")
                assertFalse(url.contains("/u_"), "user path in $url")
                assertFalse(url.contains("/addon/"), "config id in $url")
                // `api/stremio/manifest.json` is a plain public path: only a segment between
                // `stremio/` and the manifest file is the configuration of one person.
                assertFalse(SENSITIVE_STREMIO_SEGMENT.containsMatchIn(url), "config id in $url")
            }
    }

    @Test
    fun `the aiostreams configuration that was shipped is retired`() {
        assertTrue(
            isRetiredDefaultAddon(
                "https://aiostreamsfortheweebsstable.midnightignite.me/stremio/c1a28aae/eyJpIjoiQ0xiT1MifQ/manifest.json",
            ),
        )
        assertFalse(isRetiredDefaultAddon("https://torrentio.strem.fun/language=arabic/manifest.json"))
    }

    @Test
    fun `every stream source is http direct or p2p and never debrid only`() {
        val urls = DefaultAddonSources.map { source -> source.manifestUrl }

        // A debrid service fails the bundle's rule: it only plays once an account is linked.
        listOf(
            "addon.torrin.app",
            "mediafusion.elfhosted.com",
            "froststream.cloutteam.com",
            "jackettio.elfhosted.com",
            "comet.elfhosted.com",
        ).forEach { host ->
            assertTrue(urls.none { url -> url.contains(host) }, "debrid-only source $host")
        }

        // What stays either answers with a direct link or with an info hash.
        assertTrue(urls.any { url -> url.contains("torrentio.strem.fun") })
        assertTrue(urls.any { url -> url.contains("cinema.debrid.in") })
        assertTrue(urls.any { url -> url.contains("hd.plavio.site") })
    }

    @Test
    fun `the absolute cinema entry that ships is the public one without live tv`() {
        val url = DefaultAddonSources.map { source -> source.manifestUrl }
            .first { url -> url.contains("cinema.debrid.in") }

        assertTrue(url.contains("|livetv=0"))
        assertTrue(url.contains("mode=http"))
        // The shipped link used to carry a `profile=` id, which is one person's configuration.
        assertFalse(url.contains("|profile="))
    }

    private companion object {
        /** A configuration segment sitting between `stremio/` and the manifest file itself. */
        val SENSITIVE_STREMIO_SEGMENT = Regex("/stremio/[^/?]+/")
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

    @Test
    fun `a host matched by fragment keeps the variant that still works`() {
        // Both variants live on the same host; only the one with live TV was dropped.
        assertFalse(
            isRetiredDefaultAddon(
                "https://cinema.debrid.in/sort=qualityseeders|mode=http|livetv=0/manifest.json",
            ),
        )
        assertTrue(isRetiredDefaultAddon("https://cinema.debrid.in/sort=qualityseeders|livetv=1/manifest.json"))
        assertTrue(isRetiredDefaultAddon("https://cinema.debrid.in/sort=qualityseeders|profile=970d9cd3af6706d88733fe7ff57d3dbf|mode=mixed/manifest.json"))
    }

    @Test
    fun `every bundled source has a stable one-based order`() {
        assertEquals(1, builtInSourceOrder(DefaultAddonSources.first().manifestUrl))
        assertEquals(24, builtInSourceOrder(DefaultAddonSources.last().manifestUrl))
        assertNull(builtInSourceOrder("https://example.com/manifest.json"))
        assertTrue(isBuiltInSource(DefaultAddonSources[10].manifestUrl))
        assertFalse(isBuiltInSource("https://example.com/manifest.json"))
    }
}
