package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class M3uPlaylistTest {

    @Test
    fun `a plain m3u list is a playlist and an m3u8 manifest is not`() {
        assertTrue("https://example.com/list.m3u".looksLikeM3uListUrl())
        assertTrue("https://example.com/LIST.M3U?token=abc".looksLikeM3uListUrl())
        assertTrue("https://example.com/channels?format=m3u&x=1".looksLikeM3uListUrl())

        assertFalse("https://example.com/stream.m3u8".looksLikeM3uListUrl())
        assertFalse("https://example.com/video.mkv".looksLikeM3uListUrl())
        assertFalse("https://example.com/channels?format=m3u8".looksLikeM3uListUrl())
    }

    @Test
    fun `entries keep their name and their own player options`() {
        val payload = """
            #EXTM3U
            #EXTINF:-1 tvg-id="one",Channel One
            #EXTVLCOPT:http-user-agent=Mozilla/5.0
            #EXTVLCOPT:http-referrer=https://example.com/
            https://cdn.example.com/one/index.m3u8
            #EXTINF:-1,Channel Two
            https://cdn.example.com/two/index.m3u8
        """.trimIndent()

        val entries = parseM3uPlaylist(payload)

        assertEquals(2, entries.size)
        assertEquals("Channel One", entries[0].title)
        assertEquals("https://cdn.example.com/one/index.m3u8", entries[0].url)
        assertEquals(
            mapOf("User-Agent" to "Mozilla/5.0", "Referer" to "https://example.com/"),
            entries[0].headers,
        )
        assertEquals("Channel Two", entries[1].title)
        assertTrue(entries[1].headers.isEmpty())
    }

    @Test
    fun `the json header form is read too`() {
        val payload = """
            #EXTM3U
            #EXTINF:-1,Movie
            #EXTHTTP:{"User-Agent":"Raif/1.0","Referer":"https://example.com/watch"}
            https://example.com/movie.mp4
        """.trimIndent()

        val entry = parseM3uPlaylist(payload).single()

        assertEquals("Raif/1.0", entry.headers["User-Agent"])
        assertEquals("https://example.com/watch", entry.headers["Referer"])
    }

    @Test
    fun `comments and relative lines never become streams`() {
        val payload = """
            #EXTM3U
            # a note
            #EXTINF:-1,Relative
            /local/file.mp4

            #EXTINF:-1,Real
            https://example.com/real.mp4
        """.trimIndent()

        val entries = parseM3uPlaylist(payload)

        assertEquals(listOf("Real"), entries.map { entry -> entry.title })
    }

    @Test
    fun `a payload that is not a playlist yields nothing`() {
        assertTrue(parseM3uPlaylist("""{"streams":[]}""").isEmpty())
        assertTrue(parseM3uPlaylist("").isEmpty())
        assertTrue(parseM3uPlaylist("#EXTM3U").isEmpty())
    }

    @Test
    fun `a list without a name still yields its url`() {
        val entry = parseM3uPlaylist("#EXTM3U\nhttps://example.com/only.mp4").single()

        assertEquals("https://example.com/only.mp4", entry.url)
        assertEquals(null, entry.title)
    }
}
