package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeTagsTest {

    @Test
    fun readsTheSeasonAndEpisodeFromATag() {
        assertEquals(EpisodeTag(2, 5), parseEpisodeTag("S02E05"))
        assertEquals(EpisodeTag(2, 5), parseEpisodeTag("s2 e5"))
        assertEquals(EpisodeTag(10, 123), parseEpisodeTag("Show.S10.E123.1080p"))
    }

    @Test
    fun ignoresTextWithoutATag() {
        assertNull(parseEpisodeTag("الحلقة 13"))
        assertNull(parseEpisodeTag(""))
        assertNull(parseEpisodeTag(null))
    }
}
