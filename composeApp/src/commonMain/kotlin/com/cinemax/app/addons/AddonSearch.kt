package com.cinemax.app.addons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A catalog item together with the addon that returned it. */
data class CatalogEntry(
    val addonId: String,
    val addonName: String,
    val item: StremioMetaPreview
)

/**
 * Searches every installed addon that declares a search-capable catalog for the
 * type, in parallel. Addons without such a catalog are skipped, and one failing
 * addon never hides the others' results.
 */
suspend fun searchAllAddons(
    repository: AddonRepository,
    addons: List<InstalledAddon>,
    type: String,
    query: String
): List<CatalogEntry> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    return coroutineScope {
        addons
            .filter { AddonCapabilities.supports(it, "catalog", type) }
            .map { addon ->
                async {
                    val results = mutableListOf<CatalogEntry>()
                    val catalogs = addon.catalogs.filter { catalog ->
                        catalog.type == type && catalog.extra.any { it.name == "search" }
                    }
                    for (catalog in catalogs) {
                        val items = try {
                            repository.searchCatalog(addon.manifestUrl, catalog.type, catalog.id, trimmed)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            emptyList()
                        }
                        items.forEach { item ->
                            results.add(CatalogEntry(addon.id, addon.name, item))
                        }
                    }
                    results
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { it.item.id }
    }
}
