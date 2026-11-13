package com.cinemax.app.core

import com.cinemax.app.addons.StremioMetaPreview

/** How the library orders the titles it lists. */
enum class LibrarySort { Recent, Added, Title, Rating }

/** Arabic chip label for a sort option. */
fun librarySortLabel(sort: LibrarySort): String = when (sort) {
    LibrarySort.Recent -> "الأحدث مشاهدة"
    LibrarySort.Added -> "الأحدث إضافة"
    LibrarySort.Title -> "أبجديًا"
    LibrarySort.Rating -> "الأعلى تقييمًا"
}

/**
 * Orders saved titles for the library tab.
 *
 * `recentOrder` is the title ids of the watch history, most recently watched
 * first. Titles with no history keep their saved order after the watched ones,
 * so the list never reshuffles between launches for no reason. Every branch is
 * a stable sort, and a missing rating sorts last rather than as a zero.
 */
fun sortLibrary(
    items: List<StremioMetaPreview>,
    sort: LibrarySort,
    recentOrder: List<String>
): List<StremioMetaPreview> = when (sort) {
    LibrarySort.Recent -> {
        val rank = recentOrder.withIndex().associate { (index, id) -> id to index }
        items.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }
    // The store appends a newly saved title, so the newest is the last one.
    LibrarySort.Added -> items.reversed()
    LibrarySort.Title -> items.sortedBy { it.name }
    LibrarySort.Rating -> items.sortedByDescending { it.imdbRating?.toDoubleOrNull() ?: -1.0 }
}
