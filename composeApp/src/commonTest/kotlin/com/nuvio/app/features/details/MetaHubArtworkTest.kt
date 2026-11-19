package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals

class MetaHubArtworkTest {
    private fun meta(
        imdbId: String?,
        poster: String? = null,
        background: String? = null,
        logo: String? = null,
    ) = MetaDetails(
        id = "tt1",
        type = "movie",
        name = "A title",
        imdbId = imdbId,
        poster = poster,
        background = background,
        logo = logo,
    )

    @Test
    fun fillsEmptyArtworkFromTheImdbId() {
        val filled = meta("tt0111161").withMetahubArtwork()

        assertEquals("https://images.metahub.space/poster/medium/tt0111161/img", filled.poster)
        assertEquals("https://images.metahub.space/background/medium/tt0111161/img", filled.background)
        assertEquals("https://images.metahub.space/logo/medium/tt0111161/img", filled.logo)
    }

    @Test
    fun keepsArtworkTheSourceAlreadyHad() {
        val filled = meta(
            imdbId = "tt0111161",
            poster = "https://images.example/poster.jpg",
            logo = "https://images.example/logo.png",
        ).withMetahubArtwork()

        assertEquals("https://images.example/poster.jpg", filled.poster)
        assertEquals("https://images.example/logo.png", filled.logo)
        assertEquals("https://images.metahub.space/background/medium/tt0111161/img", filled.background)
    }

    @Test
    fun fillsAnArtworklessCatalogCardOnlyForARealImdbId() {
        assertEquals(
            "https://images.metahub.space/poster/medium/tt0111161/img",
            metahubPosterUrl("tt0111161"),
        )
        assertEquals(null, metahubPosterUrl(null))
        assertEquals(null, metahubPosterUrl("  "))
        assertEquals(null, metahubPosterUrl("tmdb:123"))
        assertEquals(null, metahubPosterUrl("tt"))
    }
}
