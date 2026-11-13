package com.cinemax.app.addons

/**
 * One catalog page appended to what is already on screen.
 *
 * `exhausted` is true when the page brought nothing new: either the addon
 * returned an empty list, or it repeated items the user already has (which is
 * what a `skip` a server does not honour looks like). Both mean "stop asking".
 */
data class CatalogPage(
    val items: List<StremioMetaPreview>,
    val exhausted: Boolean
)

/**
 * Appends a page to a paginated catalog list, keeping the existing order and
 * dropping ids that are already present so a repeated page cannot duplicate
 * posters or break the list's stable keys.
 */
fun appendCatalogPage(
    existing: List<StremioMetaPreview>,
    page: List<StremioMetaPreview>
): CatalogPage {
    val known = existing.mapTo(mutableSetOf()) { it.id }
    val fresh = page.filter { known.add(it.id) }
    return CatalogPage(items = existing + fresh, exhausted = fresh.isEmpty())
}

/**
 * The extra arguments one catalog page request carries: the genre the user
 * picked, when the catalog declares a `genre` extra, and the skip of the page
 * being asked for. Both live in the single extra path segment the protocol
 * defines, so they are built together.
 */
fun catalogPageExtra(genre: String?, skip: Int): Map<String, String> = buildMap {
    if (!genre.isNullOrBlank()) put("genre", genre)
    if (skip > 0) put("skip", skip.toString())
}
