package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddedAudioTest {

    private val json = """
        [
          {"id":1,"index":0,"language":"ar","title":"","codec":"aac","channels":"2","selected":true},
          {"id":2,"index":1,"language":"en","title":"English 5.1","codec":"eac3","channels":"6","selected":false}
        ]
    """.trimIndent()

    @Test
    fun parsesTracksAndDropsEntriesWithoutId() {
        val tracks = parseEmbeddedAudioTracks(json)
        assertEquals(2, tracks.size)
        assertEquals("ar", tracks.first().language)
        assertTrue(tracks.first().selected)
        assertEquals(
            1,
            parseEmbeddedAudioTracks("""[{"id":-1,"language":"en"}]""").size
        )
    }

    @Test
    fun brokenJsonYieldsNothing() {
        assertEquals(emptyList(), parseEmbeddedAudioTracks("not json"))
        assertEquals(emptyList(), parseEmbeddedAudioTracks(""))
    }

    @Test
    fun labelPrefersTitleThenLanguageThenNumber() {
        val tracks = parseEmbeddedAudioTracks(json)
        assertEquals("العربية", embeddedAudioLabel(tracks[0]))
        assertEquals("English 5.1", embeddedAudioLabel(tracks[1]))
        assertEquals("المسار 3", embeddedAudioLabel(EmbeddedAudioTrack(id = 5, index = 2)))
    }

    @Test
    fun detailNamesTheContainerAndFormat() {
        val detail = embeddedAudioDetail(EmbeddedAudioTrack(id = 1, index = 1, codec = "eac3", channels = "6"))
        assertTrue(detail.contains("مدمج في الفيديو"))
        assertTrue(detail.contains("EAC3"))
        assertTrue(detail.contains("6 قنوات"))
        assertTrue(detail.contains("المسار 2"))
    }
}
