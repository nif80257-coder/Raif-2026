package com.nuvio.app.features.details

/**
 * MetaHub serves poster, backdrop and logo images for an IMDb id without a key. It is used only
 * where the source left one of them empty, so a title that arrives with no artwork still draws.
 */
private const val METAHUB_IMAGE_BASE = "https://images.metahub.space"

/**
 * Fills the poster, backdrop and logo of this title from MetaHub when the source left them empty
 * and an IMDb id is known. A title that already has artwork is returned untouched.
 */
internal fun MetaDetails.withMetahubArtwork(): MetaDetails {
    val id = imdbId?.trim()?.takeIf(String::isNotBlank) ?: return this
    return copy(
        poster = poster?.takeIf(String::isNotBlank)
            ?: "$METAHUB_IMAGE_BASE/poster/medium/$id/img",
        background = background?.takeIf(String::isNotBlank)
            ?: "$METAHUB_IMAGE_BASE/background/medium/$id/img",
        logo = logo?.takeIf(String::isNotBlank)
            ?: "$METAHUB_IMAGE_BASE/logo/medium/$id/img",
    )
}
