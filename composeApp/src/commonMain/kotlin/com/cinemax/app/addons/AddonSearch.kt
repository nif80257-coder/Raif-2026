package com.cinemax.app.addons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

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
            .map { addon -> async { searchOneAddon(repository, addon, type, trimmed) } }
            .awaitAll()
            .flatten()
            .distinctBy { it.item.id }
    }
}

/**
 * The same search, but every addon's hits are handed to [onPartial] the moment
 * they arrive, so one slow addon no longer holds the whole list back. The
 * merged, de-duplicated list is returned once every addon has answered.
 */
suspend fun searchAllAddonsStreaming(
    repository: AddonRepository,
    addons: List<InstalledAddon>,
    type: String,
    query: String,
    onPartial: (List<CatalogEntry>) -> Unit
): List<CatalogEntry> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val targets = addons.filter { AddonCapabilities.supports(it, "catalog", type) }
    if (targets.isEmpty()) return emptyList()
    val merged = mutableListOf<CatalogEntry>()
    val seen = mutableSetOf<String>()
    coroutineScope {
        val channel = Channel<List<CatalogEntry>>(Channel.UNLIMITED)
        val jobs = targets.map { addon ->
            launch {
                val hits = try {
                    searchOneAddon(repository, addon, type, trimmed)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    emptyList()
                }
                channel.send(hits)
            }
        }
        repeat(jobs.size) {
            val fresh = channel.receive().filter { seen.add(it.item.id) }
            if (fresh.isNotEmpty()) {
                merged.addAll(fresh)
                onPartial(fresh)
            }
        }
        jobs.forEach { it.join() }
        channel.close()
    }
    return merged
}

/** One addon's hits for a type, across every searchable catalog it declares. */
private suspend fun searchOneAddon(
    repository: AddonRepository,
    addon: InstalledAddon,
    type: String,
    query: String
): List<CatalogEntry> {
    val results = mutableListOf<CatalogEntry>()
    val catalogs = addon.catalogs.filter { catalog ->
        catalog.type == type && catalog.extra.any { it.name == "search" }
    }
    for (catalog in catalogs) {
        val items = try {
            withTimeout(SEARCH_TIMEOUT_MS) {
                repository.searchCatalog(addon.manifestUrl, catalog.type, catalog.id, query)
            }
        } catch (_: TimeoutCancellationException) {
            emptyList()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            emptyList()
        }
        items.forEach { item -> results.add(CatalogEntry(addon.id, addon.name, item)) }
    }
    return results
}

/** One slow addon drops out of the search instead of holding the whole list. */
private const val SEARCH_TIMEOUT_MS = 12_000L
