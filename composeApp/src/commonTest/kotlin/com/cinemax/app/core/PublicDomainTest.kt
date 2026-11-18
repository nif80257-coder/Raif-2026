package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicDomainTest {

    @Test
    fun everyFilmStreamsOverHttps() {
        assertTrue(PUBLIC_DOMAIN_FILMS.isNotEmpty())
        PUBLIC_DOMAIN_FILMS.forEach { film ->
            assertTrue(film.streamUrl.startsWith("https://"), "${film.imdbId} is not HTTPS")
            assertTrue(film.poster.startsWith("https://"), "${film.imdbId} has no HTTPS poster")
            assertTrue(film.name.isNotBlank())
            assertTrue(film.year.isNotBlank())
        }
    }

    @Test
    fun idsAreUniqueAndCarryThePrefix() {
        val ids = publicDomainPreviews().map { it.id }
        assertEquals(PUBLIC_DOMAIN_FILMS.size, ids.distinct().size)
        ids.forEach { id ->
            assertTrue(isPublicDomainId(id), id)
            assertTrue(id.startsWith("pd:tt"), id)
        }
    }

    @Test
    fun aKnownFilmAnswersMetaAndStreams() {
        val id = PUBLIC_DOMAIN_ID_PREFIX + "tt1254207"
        assertEquals("tt1254207", publicDomainImdbId(id))
        val meta = publicDomainMeta(id)
        assertNotNull(meta)
        assertEquals(id, meta.id)
        assertEquals("movie", meta.type)
        val streams = publicDomainStreams(id)
        assertEquals(1, streams.size)
        assertEquals(PUBLIC_DOMAIN_FILMS.first().streamUrl, streams.first().url)
    }

    @Test
    fun anUnknownIdAnswersNothing() {
        val id = PUBLIC_DOMAIN_ID_PREFIX + "tt0000000"
        assertTrue(isPublicDomainId(id))
        assertNull(publicDomainMeta(id))
        assertTrue(publicDomainStreams(id).isEmpty())
        assertNotEquals(null, publicDomainFilm(PUBLIC_DOMAIN_ID_PREFIX + "tt0013442"))
    }
}
