package com.nuvio.app.features.search

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.toMetaPreview
import com.nuvio.app.features.watchprogress.WatchProgressEntry

internal const val LibrarySearchSectionKey = "search_library"
internal const val HistorySearchSectionKey = "search_history"

/**
 * The rows a search can fill without asking anyone: what the profile saved, and what it has
 * already started watching. Both are built from local state, so they appear as soon as a letter
 * is typed, while the sources are still being queried.
 *
 * Neither section carries an openable target: they are rendered by the same shelf component as
 * the source results, which only shows its "view all" affordance when a click handler is passed,
 * and the library and the history already have screens of their own.
 */
internal fun librarySearchSection(
    items: List<LibraryItem>,
    query: String,
    title: String,
    limit: Int = 20,
): HomeCatalogSection? = localSearchSection(
    key = LibrarySearchSectionKey,
    title = title,
    items = items.matchingSearchOrder(query = query, limit = limit) { item -> item.name }
        .map { item -> item.toMetaPreview() },
)

/**
 * The titles already watched or in progress that match the query, one row per title: the history
 * keeps one entry per episode, so a series would otherwise repeat itself once per episode.
 */
internal fun historySearchSection(
    entries: List<WatchProgressEntry>,
    query: String,
    title: String,
    limit: Int = 20,
): HomeCatalogSection? = localSearchSection(
    key = HistorySearchSectionKey,
    title = title,
    items = entries
        .distinctBy { entry -> entry.parentMetaType to entry.parentMetaId }
        .matchingSearchOrder(query = query, limit = limit) { entry -> entry.title }
        .map { entry ->
            MetaPreview(
                id = entry.parentMetaId,
                type = entry.parentMetaType,
                name = entry.title,
                poster = entry.poster,
                banner = entry.background,
                logo = entry.logo,
            )
        },
)

/**
 * The items whose name matches [query], best match first and then by name, using the same word
 * matching the source results are ranked by.
 */
private fun <T> List<T>.matchingSearchOrder(
    query: String,
    limit: Int,
    name: (T) -> String,
): List<T> {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank() || isEmpty()) return emptyList()
    return mapNotNull { item ->
        val score = searchWordScore(name(item), normalizedQuery) ?: return@mapNotNull null
        score to item
    }.sortedWith(
        compareByDescending<Pair<Int, T>> { (score, _) -> score }
            .thenBy { (_, item) -> name(item).lowercase() },
    ).take(limit)
        .map { (_, item) -> item }
}

private fun localSearchSection(
    key: String,
    title: String,
    items: List<MetaPreview>,
): HomeCatalogSection? {
    if (items.isEmpty()) return null
    return HomeCatalogSection(
        key = key,
        title = title,
        subtitle = "",
        addonName = "",
        target = CatalogTarget.Addon(
            manifestUrl = "",
            contentType = items.first().type,
            catalogId = "",
            supportsPagination = false,
        ),
        items = items,
        availableItemCount = items.size,
        hasMore = false,
    )
}
