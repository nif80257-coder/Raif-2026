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

/**
 * The MetaHub poster for a title known by its IMDb id, or null when the id is not one.
 *
 * Catalog rows use it for a title their source listed without artwork, so a shelf still draws
 * with no API key and no extra configuration.
 */
internal fun metahubPosterUrl(imdbId: String?): String? =
    imdbId
        ?.trim()
        ?.takeIf { id -> id.startsWith("tt") && id.length > 2 && id.drop(2).all(Char::isDigit) }
        ?.let { id -> "$METAHUB_IMAGE_BASE/poster/medium/$id/img" }
