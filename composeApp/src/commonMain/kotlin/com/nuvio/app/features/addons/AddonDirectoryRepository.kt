package com.nuvio.app.features.addons

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fetches the add-ons an installed source lists through its `addon_catalog` resource and keeps
 * them for the directory screen. The request is the catalog's own link, so a configured source
 * carries its settings into the directory it serves.
 */
object AddonDirectoryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("AddonDirectoryRepository")
    private val _uiState = MutableStateFlow(AddonDirectoryUiState())
    val uiState: StateFlow<AddonDirectoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun open(
        manifestUrl: String,
        catalog: AddonCatalogRef,
    ) {
        loadJob?.cancel()
        _uiState.value = AddonDirectoryUiState(
            manifestUrl = manifestUrl,
            title = catalog.name,
            isLoading = true,
        )
        loadJob = scope.launch {
            val installed = AddonRepository.uiState.value.addons
                .mapTo(mutableSetOf()) { addon -> addon.manifestUrl }
            val result = runCatching {
                val payload = fetchDirectoryPayload(manifestUrl = manifestUrl, catalog = catalog)
                withContext(Dispatchers.Default) {
                    AddonDirectoryParser.parse(
                        payload = payload,
                        installedManifestUrls = installed,
                    )
                        // Alphabetical, with what is already installed at the end: the order a
                        // server happens to list its directory in should not read as random.
                        .sortedWith(
                            compareBy<AddonDirectoryEntry> { entry -> entry.isInstalled }
                                .thenBy { entry -> entry.name.lowercase() },
                        )
                }
            }
            if (result.isFailure) {
                val failure = result.exceptionOrNull()
                if (failure is CancellationException) throw failure
                log.w(failure) { "open() — the add-on directory did not load" }
            }
            val failureMessage = result.exceptionOrNull()?.message?.takeIf(String::isNotBlank)
            _uiState.update { current ->
                if (current.manifestUrl != manifestUrl) {
                    current
                } else {
                    current.copy(
                        isLoading = false,
                        entries = result.getOrDefault(emptyList()),
                        // A blank message is a failed load with nothing to say; the screen then
                        // shows its own localized line instead of an internal identifier.
                        errorMessage = if (result.isFailure) failureMessage.orEmpty() else null,
                    )
                }
            }
        }
    }

    /**
     * The directory a source serves is the `addon_catalog` resource, so the request has to go to
     * `{base}/addon_catalog/{type}/{id}.json`. It used to be sent to `/catalog/...`, which is the
     * ordinary catalog endpoint: a source that only serves a directory answered that with an
     * HTTP error, or listed something else. A source that does serve the list as a plain catalog
     * is still accepted through the fallback.
     */
    private suspend fun fetchDirectoryPayload(
        manifestUrl: String,
        catalog: AddonCatalogRef,
    ): String {
        var lastFailure: Throwable? = null
        for (resource in listOf("addon_catalog", "catalog")) {
            val url = buildAddonResourceUrl(
                manifestUrl = manifestUrl,
                resource = resource,
                type = catalog.type,
                id = catalog.id,
            )
            val outcome = runCatching { fetchAddonResponseText(url = url, forceRefresh = true) }
            outcome.getOrNull()?.let { return it }
            lastFailure = outcome.exceptionOrNull()
        }
        throw lastFailure ?: IllegalStateException("Add-on directory request failed")
    }

    /** Marks an entry installed as soon as the add-on list accepts it, without refetching. */
    fun markInstalled(manifestUrl: String) {
        val normalized = ensureAddonManifestUrl(manifestUrl)
        _uiState.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    if (entry.manifestUrl == normalized) entry.copy(isInstalled = true) else entry
                },
            )
        }
    }

    fun close() {
        loadJob?.cancel()
        loadJob = null
        _uiState.value = AddonDirectoryUiState()
    }
}
