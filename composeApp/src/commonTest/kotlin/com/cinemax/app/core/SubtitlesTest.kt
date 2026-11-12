package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitlesTest {
    @Test
    fun parsesSrtCues() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            مرحبًا <i>بالعالم</i>

            2
            00:00:05,500 --> 00:00:07,000
            سطر أول
            سطر ثاني
        """.trimIndent()

        val cues = parseSubtitles(srt)
        assertEquals(2, cues.size)
        assertEquals(1_000L, cues[0].startMillis)
        assertEquals(4_000L, cues[0].endMillis)
        assertEquals("مرحبًا بالعالم", cues[0].text)
        assertEquals("سطر أول\nسطر ثاني", cues[1].text)
        assertEquals(5_500L, cues[1].startMillis)
    }

    @Test
    fun parsesWebVttWithHeaderAndSettings() {
        val vtt = """
            WEBVTT

            cue-1
            00:00:00.500 --> 00:00:02.000 align:middle line:90%
            Hello &amp; welcome

            00:00:03.000 --> 00:00:04.000
            الثانية
        """.trimIndent()

        val cues = parseSubtitles(vtt)
        assertEquals(2, cues.size)
        assertEquals(500L, cues[0].startMillis)
        assertEquals("Hello & welcome", cues[0].text)
        assertEquals("الثانية", cues[1].text)
    }

    @Test
    fun picksTheCueAtTheCurrentPosition() {
        val cues = listOf(
            SubtitleCue(1_000, 2_000, "one"),
            SubtitleCue(3_000, 4_000, "two")
        )

        assertEquals("one", activeCue(cues, 1_500))
        assertNull(activeCue(cues, 2_500))
        assertEquals("two", activeCue(cues, 4_000))
        assertTrue(parseSubtitles("not a subtitle").isEmpty())
    }
}
