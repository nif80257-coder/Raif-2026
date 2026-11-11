package com.nuvio.app.features.watching.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupabaseWatchedContentTypeTest {
    @Test
    fun `movie history uses movie type and omits episode coordinates`() {
        assertEquals(
            SupabaseWatchedContent("movie"),
            toSupabaseWatchedContent("movie", seasonNumber = 2, episodeNumber = 3),
        )
    }

    @Test
    fun `episode history uses database episode type and maps back to series`() {
        assertEquals(
            SupabaseWatchedContent("episode", seasonNumber = 2, episodeNumber = 3),
            toSupabaseWatchedContent("series", seasonNumber = 2, episodeNumber = 3),
        )
        assertEquals("series", fromSupabaseWatchedContentType("episode"))
        assertEquals("movie", fromSupabaseWatchedContentType("movie"))
    }

    @Test
    fun `series-level history without episode coordinates is not written`() {
        assertNull(toSupabaseWatchedContent("series", seasonNumber = null, episodeNumber = null))
        assertNull(toSupabaseWatchedContent("series", seasonNumber = 2, episodeNumber = null))
    }
}
