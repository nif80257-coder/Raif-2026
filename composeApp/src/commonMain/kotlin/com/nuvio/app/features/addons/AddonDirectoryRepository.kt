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
                val url = buildAddonResourceUrl(
                    manifestUrl = manifestUrl,
                    resource = "catalog",
                    type = catalog.type,
                    id = catalog.id,
                )
                val payload = fetchAddonResponseText(url = url, forceRefresh = true)
                withContext(Dispatchers.Default) {
                    AddonDirectoryParser.parse(
                        payload = payload,
                        installedManifestUrls = installed,
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
