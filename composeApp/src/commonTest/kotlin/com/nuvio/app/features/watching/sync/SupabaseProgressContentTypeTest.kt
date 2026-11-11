package com.nuvio.app.features.watching.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupabaseProgressContentTypeTest {
    @Test
    fun `movies are stored without episode coordinates`() {
        assertEquals(
            SupabaseProgressContent("movie"),
            toSupabaseProgressContent("movie", seasonNumber = 2, episodeNumber = 4),
        )
    }

    @Test
    fun `series episodes are stored with database episode type and coordinates`() {
        assertEquals(
            SupabaseProgressContent("episode", seasonNumber = 2, episodeNumber = 4),
            toSupabaseProgressContent("series", seasonNumber = 2, episodeNumber = 4),
        )
        assertEquals("series", fromSupabaseProgressContentType("episode"))
        assertEquals("movie", fromSupabaseProgressContentType("movie"))
    }

    @Test
    fun `series without full episode coordinates are not sent to constrained table`() {
        assertNull(toSupabaseProgressContent("series", seasonNumber = 2, episodeNumber = null))
        assertNull(toSupabaseProgressContent("series", seasonNumber = null, episodeNumber = 4))
        assertNull(toSupabaseProgressContent("cloud", seasonNumber = null, episodeNumber = null))
    }
}
