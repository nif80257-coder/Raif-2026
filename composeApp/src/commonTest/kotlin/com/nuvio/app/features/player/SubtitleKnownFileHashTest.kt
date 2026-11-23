package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleKnownFileHashTest {

    @Test
    fun `a remembered hash is added to extras that had none`() {
        val extras = SubtitleRequestExtras(filename = "Show.S01E02.1080p.mkv")

        val enriched = extras.withKnownFileHash(knownHash = "0011223344556677", knownSize = 4_000_000L)

        assertEquals("0011223344556677", enriched?.videoHash)
        assertEquals(4_000_000L, enriched?.videoSize)
        assertEquals("Show.S01E02.1080p.mkv", enriched?.filename)
    }

    @Test
    fun `nothing remembered leaves the extras untouched`() {
        assertNull(((null as SubtitleRequestExtras?).withKnownFileHash(knownHash = null, knownSize = null)))
    }

    @Test
    fun `the hash a stream declared wins over the remembered one`() {
        val extras = SubtitleRequestExtras(videoHash = "aaaaaaaaaaaaaaaa", videoSize = 900L)

        val enriched = extras.withKnownFileHash(knownHash = "bbbbbbbbbbbbbbbb", knownSize = 8_000L)

        assertEquals("aaaaaaaaaaaaaaaa", enriched?.videoHash)
        assertEquals(900L, enriched?.videoSize)
    }

    @Test
    fun `a declared size survives while the remembered hash fills in`() {
        val extras = SubtitleRequestExtras(filename = "clip.mkv", videoSize = 5_000L)

        val enriched = extras.withKnownFileHash(knownHash = "cccccccccccccccc", knownSize = 9_000L)

        assertEquals("cccccccccccccccc", enriched?.videoHash)
        assertEquals(5_000L, enriched?.videoSize)
    }

    @Test
    fun `a blank remembered hash is not kept`() {
        val extras = SubtitleRequestExtras(filename = "clip.mkv")

        val enriched = extras.withKnownFileHash(knownHash = "   ", knownSize = null)

        assertEquals(extras, enriched)
    }

    @Test
    fun `a remembered size alone still reaches the request`() {
        val enriched = (null as SubtitleRequestExtras?)
            .withKnownFileHash(knownHash = "", knownSize = 1_234L)

        assertEquals(1_234L, enriched?.videoSize)
        assertNull(enriched?.videoHash)
        assertEquals("videoSize=1234", enriched?.toPathSegment())
    }
}
