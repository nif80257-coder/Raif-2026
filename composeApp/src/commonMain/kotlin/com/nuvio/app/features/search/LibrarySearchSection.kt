package com.nuvio.app.features.search

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.toMetaPreview

internal const val LibrarySearchSectionKey = "search_library"

/**
 * The saved titles that match the query, so a search finds what the profile already has before it
 * looks for what is missing. Everything here is local: no source is asked, and the row appears as
 * soon as a letter is typed, while the sources are still being queried.
 *
 * The section carries no openable target: it is rendered by the same shelf component as the
 * source results, which only shows its "view all" affordance when a click handler is passed, and
 * the whole library already has its own screen.
 */
internal fun librarySearchSection(
    items: List<LibraryItem>,
    query: String,
    title: String,
    limit: Int = 20,
): HomeCatalogSection? {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank() || items.isEmpty()) return null
    val matches = items
        .mapNotNull { item ->
            val score = searchWordScore(item.name, normalizedQuery) ?: return@mapNotNull null
            score to item
        }
        .sortedWith(
            compareByDescending<Pair<Int, LibraryItem>> { (score, _) -> score }
                .thenBy { (_, item) -> item.name.lowercase() },
        )
        .take(limit)
        .map { (_, item) -> item.toMetaPreview() }
    if (matches.isEmpty()) return null
    return HomeCatalogSection(
        key = LibrarySearchSectionKey,
        title = title,
        subtitle = "",
        addonName = "",
        target = CatalogTarget.Addon(
            manifestUrl = "",
            contentType = matches.first().type,
            catalogId = "",
            supportsPagination = false,
        ),
        items = matches,
        availableItemCount = matches.size,
        hasMore = false,
    )
}
