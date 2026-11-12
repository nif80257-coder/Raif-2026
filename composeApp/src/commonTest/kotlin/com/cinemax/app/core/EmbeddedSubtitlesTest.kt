package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddedSubtitlesTest {
    @Test
    fun parsesReportedTracksAndIgnoresUnknownFields() {
        val raw = """
            [
              {"id":1,"index":0,"language":"en","title":"English","codec":"subrip","forced":false,"selected":true},
              {"id":4,"index":1,"language":"ar","title":"","codec":"ass","extra":"ignored"}
            ]
        """.trimIndent()
        val tracks = parseEmbeddedSubtitleTracks(raw)
        assertEquals(2, tracks.size)
        assertEquals(1L, tracks[0].id)
        assertEquals("English", tracks[0].title)
        assertTrue(tracks[0].selected)
        assertEquals("ar", tracks[1].language)
        assertEquals(4L, tracks[1].id)
    }

    @Test
    fun ignoresEmptyAndBrokenPayloads() {
        assertTrue(parseEmbeddedSubtitleTracks("").isEmpty())
        assertTrue(parseEmbeddedSubtitleTracks("[]").isEmpty())
        assertTrue(parseEmbeddedSubtitleTracks("not json").isEmpty())
        assertTrue(parseEmbeddedSubtitleTracks("""[{"id":-1}]""").isEmpty())
    }

    @Test
    fun labelsFallBackFromTitleToLanguageToNumber() {
        assertEquals("Arabic SDH", embeddedSubtitleLabel(EmbeddedSubtitleTrack(id = 2, title = "Arabic SDH")))
        assertEquals("العربية", embeddedSubtitleLabel(EmbeddedSubtitleTrack(id = 2, language = "ar")))
        assertEquals("الإنجليزية", embeddedSubtitleLabel(EmbeddedSubtitleTrack(id = 2, language = "en-GB")))
        assertEquals("الفرنسية", embeddedSubtitleLabel(EmbeddedSubtitleTrack(id = 2, language = "fr", index = 1)))
        assertEquals("XX", embeddedSubtitleLabel(EmbeddedSubtitleTrack(id = 2, language = "xx")))
        assertEquals("المسار 2", embeddedSubtitleLabel(EmbeddedSubtitleTrack(id = 2, index = 1)))
    }

    @Test
    fun detailNamesTheSourceAndFormat() {
        val detail = embeddedSubtitleDetail(
            EmbeddedSubtitleTrack(id = 3, index = 1, language = "ar", codec = "ass", forced = true)
        )
        assertTrue(detail.contains("مدمجة في الفيديو"))
        assertTrue(detail.contains("إجبارية"))
        assertTrue(detail.contains("ASS"))
        assertTrue(detail.contains("المسار 2"))
    }
}
