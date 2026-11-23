package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubtitleListCacheTest {

    private fun subtitle(id: String, url: String = "https://subs.example/$id.srt") = AddonSubtitle(
        id = id,
        url = url,
        language = "ar",
        display = "العربية",
        addonName = "Subs Addon",
        label = "Arabic",
    )

    @Test
    fun contentKeySeparatesEpisodesByHashAndSize() {
        val base = SubtitleListCache.contentKey("series", "tt1:1:1", SubtitleRequestExtras(videoHash = "abc"))

        assertEquals(
            base,
            SubtitleListCache.contentKey("series", "tt1:1:1", SubtitleRequestExtras(videoHash = "abc")),
        )
        assertTrue(base != SubtitleListCache.contentKey("series", "tt1:1:1", SubtitleRequestExtras(videoHash = "def")))
        assertTrue(
            base != SubtitleListCache.contentKey(
                "series",
                "tt1:1:1",
                SubtitleRequestExtras(videoHash = "abc", videoSize = 42L),
            ),
        )
        assertTrue(base != SubtitleListCache.contentKey("series", "tt1:1:2", SubtitleRequestExtras(videoHash = "abc")))
    }

    @Test
    fun payloadRoundTripsEveryField() {
        val entries = mapOf("series|tt1|abc|0" to listOf(subtitle("a"), subtitle("b")))

        val decoded = SubtitleListCache.decodeForTest(SubtitleListCache.encodeForTest(entries, nowMs = 1_000L))

        assertEquals(entries, decoded)
    }

    @Test
    fun unknownFieldsFromANewerBuildAreIgnored() {
        val raw = """
            {"entries":{"k":{"cachedAtMs":5,"subtitles":[{"url":"https://x/y.srt","display":"A","newField":7}]}}}
        """.trimIndent()

        val decoded = SubtitleListCache.decodeForTest(raw)

        assertEquals("https://x/y.srt", decoded.getValue("k").single().url)
    }

    @Test
    fun corruptPayloadDecodesToNothing() {
        assertTrue(SubtitleListCache.decodeForTest("not json at all").isEmpty())
        assertTrue(SubtitleListCache.decodeForTest("").isEmpty())
    }

    @Test
    fun entriesWithoutUrlAreDropped() {
        val decoded = SubtitleListCache.decodeForTest(
            SubtitleListCache.encodeForTest(
                entries = mapOf("k" to listOf(subtitle("good"), subtitle("bad", url = ""))),
                nowMs = 1_000L,
            ),
        )

        assertEquals(listOf("good"), decoded.getValue("k").map { it.id })
    }

    @Test
    fun freshnessEndsAfterSixHours() {
        val cachedAt = 1_700_000_000_000L

        assertTrue(SubtitleListCache.isFresh(cachedAt, cachedAt))
        assertTrue(SubtitleListCache.isFresh(cachedAt, cachedAt + SubtitleListCache.FreshForMs))
        assertFalse(SubtitleListCache.isFresh(cachedAt, cachedAt + SubtitleListCache.FreshForMs + 1L))
    }

    @Test
    fun entryWithoutTimestampIsNotFresh() {
        assertFalse(SubtitleListCache.isFresh(cachedAtMs = 0L, nowMs = 1_700_000_000_000L))
    }
}
