package com.nuvio.app.features.settings

import co.touchlab.kermit.Logger
import com.nuvio.app.core.concurrent.runCatchingCancellable
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonManifestParser
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.fetchAddonResponseText
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal object AddonHealthCheckRepository {
    private val log = Logger.withTag("AddonHealthCheck")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _results = MutableStateFlow<Map<String, AddonCheckResult>>(emptyMap())
    val results: StateFlow<Map<String, AddonCheckResult>> = _results.asStateFlow()

    private val _checking = MutableStateFlow<Set<String>>(emptySet())
    val checking: StateFlow<Set<String>> = _checking.asStateFlow()

    private val _isCheckingAll = MutableStateFlow(false)
    val isCheckingAll: StateFlow<Boolean> = _isCheckingAll.asStateFlow()

    private val _focusedAddonUrl = MutableStateFlow<String?>(null)
    val focusedAddonUrl: StateFlow<String?> = _focusedAddonUrl.asStateFlow()

    private var hasLoadedStoredResults = false

    /**
     * Brings back the outcome of the last check.
     *
     * A check costs a manifest request and a known-title probe per source, so it is not repeated on
     * every visit. Without this the screen would say "not tested" about every source as soon as the
     * app is relaunched, forgetting answers that were paid for a minute earlier.
     */
    fun ensureLoaded() {
        if (hasLoadedStoredResults) return
        hasLoadedStoredResults = true
        val stored = decodeAddonCheckResults(AddonHealthCheckStorage.loadCheckResults())
        if (stored.isEmpty()) return
        _results.update { current -> stored + current }
    }

    /**
     * Writes the results back, keeping only sources that are still installed.
     *
     * A removed source would otherwise leave its result behind for good, and the stored map would
     * grow with every source the user tried once.
     */
    private fun persistResults() {
        val installed = AddonRepository.uiState.value.addons.mapTo(mutableSetOf()) { addon ->
            addon.manifestUrl
        }
        val results = if (installed.isEmpty()) {
            _results.value
        } else {
            _results.value.filterKeys(installed::contains)
        }
        AddonHealthCheckStorage.saveCheckResults(encodeAddonCheckResults(results))
    }

    /** Asks the health check screen to open on a specific add-on. */
    fun requestFocus(manifestUrl: String) {
        _focusedAddonUrl.value = manifestUrl
    }

    fun consumeFocusedAddonUrl(): String? {
        val url = _focusedAddonUrl.value
        if (url != null) _focusedAddonUrl.value = null
        return url
    }

    fun checkAll() {
        AddonRepository.initialize()
        ensureLoaded()
        val addons = AddonRepository.uiState.value.addons
        if (addons.isEmpty() || _isCheckingAll.value) return
        val urls = addons.mapTo(mutableSetOf()) { addon -> addon.manifestUrl }
        scope.launch {
            _isCheckingAll.value = true
            _checking.update { it + urls }
            try {
                coroutineScope {
                    addons.map { addon ->
                        async {
                            val result = runCheck(addon)
                            _results.update { current -> current + (addon.manifestUrl to result) }
                        }
                    }.awaitAll()
                }
            } catch (error: Throwable) {
                log.w(error) { "Add-on health check run failed" }
            } finally {
                _checking.update { it - urls }
                _isCheckingAll.value = false
                // One write for the whole run: a run answers every source in a couple of seconds,
                // and a write per answer would rewrite the map thirty times.
                persistResults()
            }
        }
    }

    fun checkAddon(manifestUrl: String) {
        AddonRepository.initialize()
        ensureLoaded()
        val addon = AddonRepository.uiState.value.addons
            .firstOrNull { it.manifestUrl == manifestUrl }
            ?: return
        if (manifestUrl in _checking.value) return
        scope.launch {
            _checking.update { it + manifestUrl }
            try {
                val result = runCheck(addon)
                _results.update { current -> current + (manifestUrl to result) }
                persistResults()
            } catch (error: Throwable) {
                log.w(error) { "Add-on health check failed for a single add-on" }
            } finally {
                _checking.update { it - manifestUrl }
            }
        }
    }

    fun forget(manifestUrl: String) {
        _results.update { current -> current - manifestUrl }
        persistResults()
        val addonId = AddonRepository.uiState.value.addons
            .firstOrNull { it.manifestUrl == manifestUrl }
            ?.manifest?.id
        if (addonId != null) {
            val stored = AddonHealthCheckStorage.loadFingerprints().toMutableMap()
            if (stored.remove(addonId) != null) {
                AddonHealthCheckStorage.saveFingerprints(stored)
            }
        }
    }

    private suspend fun runCheck(addon: ManagedAddon): AddonCheckResult {
        val startedAt = EpisodeReleaseDatePlatform.nowEpochMs()
        val manifestOutcome = withTimeoutOrNull(AddonHealthCheckTimeoutMs) {
            runCatchingCancellable {
                val payload = fetchAddonResponseText(url = addon.manifestUrl, forceRefresh = true)
                AddonManifestParser.parse(manifestUrl = addon.manifestUrl, payload = payload)
            }
        }
        val elapsed = EpisodeReleaseDatePlatform.nowEpochMs() - startedAt
        val manifest = manifestOutcome?.getOrNull()
        val manifestError = when {
            manifestOutcome == null -> "timeout"
            else -> manifestOutcome.exceptionOrNull()?.message
        }

        val knownContent = if (manifest == null) {
            AddonKnownContentTest(videoId = AddonHealthCheckKnownVideoId)
        } else {
            runKnownContentTest(manifest)
        }

        val fingerprint = manifest?.addonFingerprint()
        val previousFingerprint = manifest?.let { AddonHealthCheckStorage.loadFingerprints()[it.id] }
        if (manifest != null && fingerprint != null && fingerprint != previousFingerprint) {
            val stored = AddonHealthCheckStorage.loadFingerprints().toMutableMap()
            stored[manifest.id] = fingerprint
            AddonHealthCheckStorage.saveFingerprints(stored)
        }

        val slow = elapsed >= AddonHealthCheckSlowThresholdMs ||
            knownContent.streamsTimedOut ||
            knownContent.subtitlesTimedOut

        val status = when {
            !addon.enabled -> AddonHealthStatus.DISABLED
            manifest == null -> AddonHealthStatus.DOWN
            slow -> AddonHealthStatus.SLOW
            else -> AddonHealthStatus.OK
        }

        return AddonCheckResult(
            manifestUrl = addon.manifestUrl,
            addonId = manifest?.id ?: addon.manifest?.id.orEmpty(),
            name = addon.displayTitle,
            enabled = addon.enabled,
            status = status,
            responseTimeMs = elapsed,
            manifestParsed = manifest != null,
            manifestError = manifestError,
            manifestVersion = manifest?.version,
            supportedTypes = manifest?.addonSupportedTypes() ?: emptySet(),
            catalogsCount = manifest?.catalogs?.size ?: 0,
            knownTest = knownContent,
            changedSinceLastCheck = if (manifest != null && previousFingerprint != null) {
                previousFingerprint != fingerprint
            } else {
                null
            },
            checkedAtEpochMs = startedAt,
        )
    }

    private suspend fun runKnownContentTest(manifest: AddonManifest): AddonKnownContentTest {
        val videoId = "tt0111601"
        val supportsStreams = manifest.resources.any { resource ->
            resource.name.equals("stream", true) || resource.name.equals("streams", true)
        }
        val supportsSubtitles = manifest.resources.any { resource ->
            resource.name.equals("subtitles", true) || resource.name.equals("subtitle", true)
        }

        val streamOutcome = if (supportsStreams) {
            withTimeoutOrNull(AddonHealthCheckTimeoutMs) {
                runCatchingCancellable {
                    val url = buildAddonResourceUrl(manifest.transportUrl, "stream", "movie", videoId)
                    val body = fetchAddonResponseText(url, forceRefresh = true)
                    parseArraySize(body, "streams")
                }
            }
        } else {
            null
        }

        val subtitleOutcome = if (supportsSubtitles) {
            withTimeoutOrNull(AddonHealthCheckTimeoutMs) {
                runCatchingCancellable {
                    val url = buildAddonResourceUrl(manifest.transportUrl, "subtitles", "movie", videoId)
                    val body = fetchAddonResponseText(url, forceRefresh = true)
                    parseArraySize(body, "subtitles")
                }
            }
        } else {
            null
        }

        return AddonKnownContentTest(
            videoId = videoId,
            streamCount = streamOutcome?.getOrNull(),
            subtitleCount = subtitleOutcome?.getOrNull(),
            streamsTimedOut = supportsStreams && streamOutcome == null,
            subtitlesTimedOut = supportsSubtitles && subtitleOutcome == null,
            streamsError = streamOutcome?.exceptionOrNull()?.message,
            subtitlesError = subtitleOutcome?.exceptionOrNull()?.message,
        )
    }

    private fun parseArraySize(body: String, field: String): Int {
        val root = runCatchingCancellable { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return 0
        val array: JsonArray = runCatchingCancellable { root[field]?.jsonArray }.getOrNull() ?: return 0
        return array.size
    }
}

/** Add-ons that should be probed with a known movie id. */
internal fun AddonHealthCheckRepository.checkableAddons(): List<ManagedAddon> =
    AddonRepository.uiState.value.addons.enabledAddons()
