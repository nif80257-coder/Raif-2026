package com.nuvio.app.features.settings

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.network.ServerConfigurationRepository
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonManifestParser
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.updater.VersionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val RaifUpdatesBucket = "raif"
private const val RaifUpdatesObject = "version.json"
private const val AddonManifestTimeoutMs = 10_000L

internal object RaifUpdatesRepository {
    private val log = Logger.withTag("RaifUpdates")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _addonResults = MutableStateFlow<Map<String, AddonUpdateResult>>(emptyMap())
    val addonResults: StateFlow<Map<String, AddonUpdateResult>> = _addonResults.asStateFlow()

    private val _isCheckingAddons = MutableStateFlow(false)
    val isCheckingAddons: StateFlow<Boolean> = _isCheckingAddons.asStateFlow()

    private val _lastAddonCheckEpochMs = MutableStateFlow(RaifUpdatesStorage.loadLastCheckEpochMs())
    val lastAddonCheckEpochMs: StateFlow<Long?> = _lastAddonCheckEpochMs.asStateFlow()

    private val _appState = MutableStateFlow(RaifAppUpdateState(installedVersion = AppVersionConfig.VERSION_NAME))
    val appState: StateFlow<RaifAppUpdateState> = _appState.asStateFlow()

    fun checkAddons(force: Boolean) {
        if (_isCheckingAddons.value) return
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        if (!force) {
            val last = RaifUpdatesStorage.loadLastCheckEpochMs()
            if (last != null && now - last < RaifUpdatesDailyIntervalMs) {
                _lastAddonCheckEpochMs.value = last
                seedResultsFromSnapshots()
                return
            }
        }
        scope.launch {
            _isCheckingAddons.value = true
            try {
                AddonRepository.initialize()
                val addons = AddonRepository.uiState.value.addons
                if (addons.isEmpty()) return@launch
                val stored = RaifUpdatesStorage.loadSnapshots()
                val outcomes = coroutineScope {
                    addons.map { addon -> async { runAddonCheck(addon, stored) } }.awaitAll()
                }
                val snapshots = stored.toMutableMap()
                val results = mutableMapOf<String, AddonUpdateResult>()
                outcomes.forEach { outcome ->
                    results[outcome.result.manifestUrl] = outcome.result
                    outcome.addonId?.let { addonId ->
                        outcome.snapshot?.let { snapshot -> snapshots[addonId] = snapshot }
                    }
                }
                if (snapshots != stored) {
                    RaifUpdatesStorage.saveSnapshots(snapshots)
                }
                val completedAt = EpisodeReleaseDatePlatform.nowEpochMs()
                RaifUpdatesStorage.saveLastCheckEpochMs(completedAt)
                _addonResults.value = results
                _lastAddonCheckEpochMs.value = completedAt
            } catch (error: Throwable) {
                log.w(error) { "Add-on refresh failed" }
            } finally {
                _isCheckingAddons.value = false
            }
        }
    }

    /** Runs at most once every 24 hours; called when the app starts and when the screen opens. */
    fun runAutomaticDailyCheck() {
        if (_isCheckingAddons.value) return
        val last = RaifUpdatesStorage.loadLastCheckEpochMs()
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        if (last != null && now - last < RaifUpdatesDailyIntervalMs) {
            _lastAddonCheckEpochMs.value = last
            seedResultsFromSnapshots()
            return
        }
        checkAddons(force = false)
    }

    fun refreshAppRelease() {
        if (_appState.value.isLoading) return
        scope.launch {
            _appState.update { it.copy(isLoading = true, error = null) }
            val outcome = runCatching { fetchAppRelease() }
            _appState.update { current ->
                outcome.fold(
                    onSuccess = { release ->
                        current.copy(
                            installedVersion = AppVersionConfig.VERSION_NAME,
                            latest = release,
                            updateAvailable = VersionUtils.isRemoteNewer(
                                remote = release.version,
                                local = AppVersionConfig.VERSION_NAME,
                            ),
                            isLoading = false,
                            error = null,
                        )
                    },
                    onFailure = { error ->
                        log.w(error) { "App release information could not be read" }
                        current.copy(
                            isLoading = false,
                            error = error.message ?: "unavailable",
                        )
                    },
                )
            }
        }
    }

    private fun seedResultsFromSnapshots() {
        if (_addonResults.value.isNotEmpty()) return
        AddonRepository.initialize()
        val stored = RaifUpdatesStorage.loadSnapshots()
        if (stored.isEmpty()) return
        val seeded = AddonRepository.uiState.value.addons.mapNotNull { addon ->
            val addonId = addon.manifest?.id ?: return@mapNotNull null
            val snapshot = stored[addonId] ?: return@mapNotNull null
            addon.manifestUrl to AddonUpdateResult(
                manifestUrl = addon.manifestUrl,
                name = addon.displayTitle,
                state = AddonUpdateState.UNCHANGED,
                previousVersion = snapshot.version,
                currentVersion = snapshot.version,
            )
        }.toMap()
        if (seeded.isNotEmpty()) {
            _addonResults.value = seeded
        }
    }

    private suspend fun fetchAppRelease(): RaifAppReleaseDocument {
        val baseUrl = ServerConfigurationRepository.active.value.backendUrl.trimEnd('/')
        require(baseUrl.isNotBlank()) { "No backend configured" }
        val url = "$baseUrl/storage/v1/object/public/$RaifUpdatesBucket/$RaifUpdatesObject"
        val body = httpGetText(url)
        val root = json.parseToJsonElement(body).jsonObject
        val version = root.stringValue("version")
            ?: root.stringValue("latest_version")
            ?: error("version.json has no version")
        return RaifAppReleaseDocument(
            version = version,
            releaseDate = root.stringValue("release_date") ?: root.stringValue("date"),
            notes = root.stringValue("notes") ?: root.stringArray("changes").joinToString("\n"),
            downloadUrl = root.stringValue("download_url") ?: root.stringValue("url"),
        )
    }

    private suspend fun runAddonCheck(
        addon: ManagedAddon,
        stored: Map<String, AddonManifestSnapshot>,
    ): AddonCheckOutcome {
        val outcome = withTimeoutOrNull(AddonManifestTimeoutMs) {
            runCatching {
                val payload = fetchAddonResponseText(url = addon.manifestUrl, forceRefresh = true)
                AddonManifestParser.parse(manifestUrl = addon.manifestUrl, payload = payload)
            }
        }
        val manifest = outcome?.getOrNull()
            ?: return AddonCheckOutcome(
                result = AddonUpdateResult(
                    manifestUrl = addon.manifestUrl,
                    name = addon.displayTitle,
                    state = AddonUpdateState.ERROR,
                    error = if (outcome == null) {
                        "timeout"
                    } else {
                        outcome.exceptionOrNull()?.message
                    },
                ),
            )
        val snapshot = manifest.toManifestSnapshot()
        val previous = stored[manifest.id]
        val diff = diffManifestSnapshots(previous, snapshot)
        val versionChanged = previous != null && previous.version != snapshot.version
        val state = when {
            previous == null -> AddonUpdateState.NOT_CHECKED
            versionChanged || diff.hasChanges -> AddonUpdateState.CHANGED
            else -> AddonUpdateState.UNCHANGED
        }
        return AddonCheckOutcome(
            result = AddonUpdateResult(
                manifestUrl = addon.manifestUrl,
                name = addon.displayTitle,
                state = state,
                previousVersion = previous?.version,
                currentVersion = snapshot.version,
                diff = diff,
            ),
            addonId = manifest.id,
            snapshot = snapshot,
        )
    }

    private data class AddonCheckOutcome(
        val result: AddonUpdateResult,
        val addonId: String? = null,
        val snapshot: AddonManifestSnapshot? = null,
    )
}

private fun JsonObject.stringValue(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

private fun JsonObject.stringArray(name: String): List<String> =
    (this[name] as? JsonArray)
        ?.mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } }
        .orEmpty()
