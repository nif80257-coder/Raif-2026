package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamLinkWarmerTest {
    private fun group(name: String, vararg streams: StreamItem): AddonStreamGroup =
        AddonStreamGroup(addonName = name, addonId = "addon:$name", streams = streams.toList())

    private fun stream(
        url: String? = null,
        infoHash: String? = null,
        externalUrl: String? = null,
        ytId: String? = null,
        clientResolve: StreamClientResolve? = null,
    ): StreamItem = StreamItem(
        url = url,
        infoHash = infoHash,
        externalUrl = externalUrl,
        ytId = ytId,
        addonName = "Source",
        addonId = "addon:source",
        clientResolve = clientResolve,
    )

    @Test
    fun `the first playable http link of the list is the candidate`() {
        val first = stream(url = "https://a.example/film.mkv")
        val second = stream(url = "https://b.example/film.mkv")

        assertEquals(
            first,
            listOf(group("A", first, second)).topWarmCandidate(),
        )
    }

    @Test
    fun `a torrent before it is skipped because peers must wait for the consent`() {
        val torrent = stream(infoHash = "abcdef0123456789abcdef0123456789abcdef01")
        val direct = stream(url = "https://a.example/film.mkv")

        assertEquals(
            direct,
            listOf(group("A", torrent, direct)).topWarmCandidate(),
        )
        assertFalse(torrent.isWarmableLink())
    }

    @Test
    fun `an external page and a youtube row are skipped`() {
        val external = stream(externalUrl = "https://service.example/title")
        val youTube = stream(ytId = "dQw4w9WgXcQ")

        assertNull(listOf(group("A", external, youTube)).topWarmCandidate())
        assertFalse(external.isWarmableLink())
        assertFalse(youTube.isWarmableLink())
    }

    @Test
    fun `a row the app resolves itself is not warmed`() {
        val debrid = stream(
            url = "https://a.example/film.mkv",
            clientResolve = StreamClientResolve(
                type = "debrid",
                infoHash = "abcdef0123456789abcdef0123456789abcdef01",
                service = "provider",
                isCached = true,
            ),
        )

        assertFalse(debrid.isWarmableLink())
        assertNull(listOf(group("A", debrid)).topWarmCandidate())
    }

    @Test
    fun `a link that is not http is not warmed`() {
        assertNull(listOf(group("A", stream(url = "ftp://a.example/film.mkv"))).topWarmCandidate())
        assertNull(listOf(group("A", stream(url = "magnet:?xt=urn:btih:abcdef0123456789abcdef0123456789abcdef01"))).topWarmCandidate())
    }

    @Test
    fun `an empty list has no candidate`() {
        assertNull(emptyList<AddonStreamGroup>().topWarmCandidate())
    }

    @Test
    fun `the order of the list decides which link is warmed`() {
        val torrent = stream(infoHash = "fffeeddccbbaa99887766554433221100ffeeddcc")
        val preferred = group("Email", stream(url = "https://z.example/film.mkv"))
        val first = group("Torrents", torrent)

        assertTrue(listOf(first, preferred).topWarmCandidate()?.playableDirectUrl == "https://z.example/film.mkv")
    }
}
