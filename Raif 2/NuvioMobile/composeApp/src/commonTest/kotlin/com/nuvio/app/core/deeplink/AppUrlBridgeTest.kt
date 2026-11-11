package com.nuvio.app.core.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUrlBridgeTest {

    @Test
    fun `parses party invites from the app and the website`() {
        assertEquals(AppDeepLink.Party("K7M2QX"), parseAppDeepLink("nuvio://party/k7m-2qx"))
        assertEquals(AppDeepLink.Party("K7M2QX"), parseAppDeepLink("raif://party/k7m-2qx"))
        assertEquals(AppDeepLink.Party("K7M2QX"), parseAppDeepLink("https://nuvio.tv/party/K7M2QX"))
        assertEquals(AppDeepLink.Party("K7M2QX"), parseAppDeepLink("https://NUVIO.tv/party/K7M2QX/"))
        assertNull(parseAppDeepLink("https://nuvio.tv/party/K7M2Q"))
        assertNull(parseAppDeepLink("https://nuvio.tv/link?code=K7M2QX"))
        assertNull(parseAppDeepLink("https://evil.example/party/K7M2QX"))
        assertNull(parseAppDeepLink("http://nuvio.tv/party/K7M2QX"))
    }

    @Test
    fun `new app links use the RAIF scheme`() {
        val downloadsUrl = buildDownloadsDeepLinkUrl()
        assertEquals("raif://downloads", downloadsUrl)
        assertEquals(AppDeepLink.Downloads, parseAppDeepLink(downloadsUrl))
        assertEquals(
            AppDeepLink.Meta(type = "movie", id = "tt123"),
            parseAppDeepLink(buildMetaDeepLinkUrl(type = "movie", id = "tt123")),
        )
    }

    @Test
    fun `parses existing notification meta deeplink`() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            parseAppDeepLink("nuvio://meta?type=series&id=tt0944947"),
        )
    }

    @Test
    fun `parses direct nuvio addon install deeplink`() {
        assertEquals(
            AppDeepLink.AddonInstall("https://free.nebulapro.xyz/sports/i/free/manifest.json"),
            parseAppDeepLink("nuvio://free.nebulapro.xyz/sports/i/free/manifest.json"),
        )
    }

    @Test
    fun `parses stremio addon install deeplink`() {
        assertEquals(
            AppDeepLink.AddonInstall("https://free.nebulapro.xyz/sports/i/free/manifest.json"),
            parseAppDeepLink("stremio://free.nebulapro.xyz/sports/i/free/manifest.json"),
        )
    }

    @Test
    fun `parses direct imdb detail deeplink`() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            parseAppDeepLink("nuvio://series/tt0944947"),
        )
    }

    @Test
    fun `parses provider imdb detail deeplink`() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            parseAppDeepLink("nuvio://imdb/series/tt0944947"),
        )
    }

    @Test
    fun `parses provider tmdb detail deeplink`() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tmdb:1399"),
            parseAppDeepLink("nuvio://tmdb/tv/1399"),
        )
    }

    @Test
    fun `does not treat reserved auth link as addon install`() {
        assertNull(parseAppDeepLink("nuvio://auth/trakt?code=abc"))
    }

    @Test
    fun `routes only the exact RAIF password recovery callback`() {
        assertTrue(isSupabaseAuthCallback("raif://auth/callback"))
        assertTrue(isSupabaseAuthCallback("raif://auth/callback?code=abc&state=xyz"))
        assertTrue(isSupabaseAuthCallback("raif://auth/callback#access_token=abc"))
        assertFalse(isSupabaseAuthCallback("raif://auth/callback/extra"))
        assertFalse(isSupabaseAuthCallback("raif://auth/callback/"))
        assertFalse(isSupabaseAuthCallback("raif://other/callback"))
        assertFalse(isSupabaseAuthCallback("nuvio://auth/callback"))
        assertFalse(isSupabaseAuthCallback("raif://auth/not-callback"))
    }

    @Test
    fun `does not treat non-host stremio link as addon install`() {
        assertNull(parseAppDeepLink("stremio://detail/series/tt0944947"))
    }
}
