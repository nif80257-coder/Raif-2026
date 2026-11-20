package com.nuvio.app.features.addons

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.auth.isCurrentSupabaseAccount
import com.nuvio.app.core.auth.requireSupabaseAccountUserId
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.profiles.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

private typealias AddonRow = UserAddonSyncRecord

@Serializable
private data class AddonWriteRow(
    @SerialName("user_id") val userId: String,
    @SerialName("addon_id") val addonId: String,
    @SerialName("manifest_url") val manifestUrl: String,
    val name: String? = null,
    val enabled: Boolean = true,
    val config: JsonObject = JsonObject(emptyMap()),
)

private const val ADDON_PUSH_DEBOUNCE_MS = 500L

/** A cold start spreads its manifest refreshes out from the staggered queue. */
private const val MANIFEST_REFRESH_STAGGER_MS = 150L
private const val MAX_MANIFEST_STAGGER_SLOTS = 40

object AddonRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("AddonRepository")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _uiState = MutableStateFlow(AddonsUiState())
    val uiState: StateFlow<AddonsUiState> = _uiState.asStateFlow()

    private var initialized = false
    private var currentProfileId: Int = 1
    private val activeRefreshJobs = mutableMapOf<String, Job>()
    private var defaultAddonSeedJob: Job? = null
    private val pushJobsByProfile = mutableMapOf<Int, Job>()

    /**
     * A cold start resolves every installed add-on, and the shipped list has forty-two.
     * Four at a time keeps the launch from opening every connection at once; the rest queue
     * behind and fill in as they answer, and a paused source skips the queue entirely.
     */
    private val manifestRefreshSemaphore = Semaphore(4)

    /** Marks that the retired bundled sources were already removed from this profile. */
    private const val RETIRED_DEFAULTS_CACHE = "retired-default-addons-v2"

    fun initialize() {
        val effectiveProfileId = resolveEffectiveProfileId(ProfileRepository.activeProfileId)
        if (initialized) return
        initialized = true
        currentProfileId = effectiveProfileId
        log.d { "initialize() — loading local addons for profile $currentProfileId" }

        pruneRetiredDefaultAddons(currentProfileId)

        val storedUrls = dedupeManifestUrls(AddonStorage.loadInstalledAddonUrls(currentProfileId))
        val enabledByUrl = loadLocalEnabledStates()
        log.d { "initialize() — local addon count: ${storedUrls.size}" }
        if (storedUrls.isEmpty()) {
            seedDefaultAddonsIfNeeded(currentProfileId)
            return
        }
        // Top up anything the first attempt could not fetch (a launch made while offline, say).
        seedDefaultAddonsIfNeeded(currentProfileId)

        // Catalogs come from the manifests kept by the last launch, so the home screen has
        // something to draw before the first response arrives. Anything stale is re-fetched in
        // the background, staggered, instead of sixty sockets opening at once.
        val cachedByUrl = CachedManifestStore.load(currentProfileId)
        val existingByUrl = _uiState.value.addons.associateBy(ManagedAddon::manifestUrl)
        _uiState.value = AddonsUiState(
            addons = storedUrls.map { manifestUrl ->
                existingByUrl[manifestUrl].toRestoredAddon(
                    manifestUrl = manifestUrl,
                    cached = cachedByUrl[manifestUrl],
                    enabled = enabledByUrl[manifestUrl],
                )
            },
        )

        val addonsByUrl = _uiState.value.addons.associateBy(ManagedAddon::manifestUrl)
        // A paused source is not asked again at startup: its cached manifest still draws its
        // shelves, and the pause exists precisely so a slow host stops costing launch time.
        val pausedManifestUrls = AddonFailureMemory.pausedManifestUrls()
        storedUrls.forEachIndexed { index, manifestUrl ->
            val addon = addonsByUrl[manifestUrl]
            if (addon?.enabled != true) return@forEachIndexed
            if (manifestUrl in pausedManifestUrls) return@forEachIndexed
            if (cachedByUrl[manifestUrl]?.isFresh == true) return@forEachIndexed
            refreshAddon(
                manifestUrl = manifestUrl,
                staggerIndex = index,
                showProgress = addon.manifest == null,
            )
        }
        CachedManifestStore.retainOnly(currentProfileId, storedUrls.toSet())
    }

    /**
     * Removes the bundled sources that were dropped from the shipped list from a profile that
     * still has them, once. A profile seeded before the list was checked keeps its own added
     * sources; only the exact retired hosts go.
     */
    private fun pruneRetiredDefaultAddons(profileId: Int) {
        if (AddonStorage.loadStartupCache(profileId, RETIRED_DEFAULTS_CACHE) != null) return
        val storedUrls = AddonStorage.loadInstalledAddonUrls(profileId)
        if (storedUrls.isNotEmpty()) {
            val kept = storedUrls.filterNot(::isRetiredDefaultAddon)
            if (kept.size != storedUrls.size) {
                log.i { "Pruning ${storedUrls.size - kept.size} retired bundled add-ons" }
                AddonStorage.saveInstalledAddonUrls(profileId, kept)
            }
        }
        AddonStorage.saveStartupCache(profileId, RETIRED_DEFAULTS_CACHE, "1")
    }

    fun onProfileChanged(profileId: Int) {
        val effectiveProfileId = resolveEffectiveProfileId(profileId)
        if (effectiveProfileId == currentProfileId && initialized) return
        cancelActiveRefreshes()
        currentProfileId = effectiveProfileId
        initialized = false
        _uiState.value = AddonsUiState()
    }

    fun clearLocalState() {
        cancelActiveRefreshes()
        pushJobsByProfile.values.forEach(Job::cancel)
        pushJobsByProfile.clear()
        currentProfileId = 1
        initialized = false
        _uiState.value = AddonsUiState()
    }

    suspend fun pullFromServer(profileId: Int) {
        currentProfileId = resolveEffectiveProfileId(profileId)
        val syncProfileId = currentProfileId
        val authState = AuthRepository.state.value as? AuthState.Authenticated ?: return
        if (authState.isAnonymous) return
        val userId = authState.userId
        log.i { "pullFromServer() — profileId=$syncProfileId, initialized=$initialized" }
        try {
            val rows = SupabaseProvider.client.postgrest
                .from("user_addons")
                .select { filter { eq("user_id", userId) } }
                .decodeList<AddonRow>()
            if (!isCurrentSupabaseAccount(userId) || currentProfileId != syncProfileId) return

            val rowsByUrl = linkedMapOf<String, AddonRow>()
            rows.sortedBy { row -> row.config["sort_order"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE }
                .forEach { row ->
                    val manifestUrl = ensureManifestSuffix(row.manifestUrl)
                    val addonId = ensureManifestSuffix(row.addonId)
                    if (!rowsByUrl.containsKey(manifestUrl)) {
                        rowsByUrl[manifestUrl] = row.copy(addonId = addonId, manifestUrl = manifestUrl)
                    }
                }

            val queued = AccountUserAddonSyncOutbox.pendingFor(userId, syncProfileId)
            val pendingIds = queued?.mutations.orEmpty().mapTo(mutableSetOf()) { it.addonId }
            val storedUrls = dedupeManifestUrls(AddonStorage.loadInstalledAddonUrls(syncProfileId))
            val enabledByUrl = AddonStorage.loadAddonEnabledStates(syncProfileId)
                .mapKeys { (url, _) -> ensureManifestSuffix(url) }
            val currentByUrl = _uiState.value.addons.associateBy(ManagedAddon::manifestUrl)
            val localOnly = storedUrls.mapIndexedNotNull { index, url ->
                val addonId = ensureManifestSuffix(url)
                if (url in rowsByUrl || addonId in pendingIds) return@mapIndexedNotNull null
                val addon = currentByUrl[url]
                PendingUserAddonMutation(
                    addonId = addonId,
                    manifestUrl = url,
                    name = addon?.userSetName?.takeIf(String::isNotBlank)
                        ?: addon?.manifest?.name?.takeIf(String::isNotBlank),
                    enabled = enabledByUrl[url] ?: addon?.enabled ?: true,
                    config = JsonObject(mapOf("sort_order" to JsonPrimitive(rowsByUrl.size + index))),
                )
            }
            if (localOnly.isNotEmpty()) {
                AccountUserAddonSyncOutbox.record(
                    PendingUserAddonChange(userId, syncProfileId, localOnly),
                )
            }

            val pending = AccountUserAddonSyncOutbox.pendingFor(userId, syncProfileId)
            val mergedRows = applyPendingUserAddonMutations(rowsByUrl.values.toList(), pending?.mutations.orEmpty())
                .withIndex()
                .sortedWith(
                    compareBy<IndexedValue<UserAddonSyncRecord>> {
                        it.value.config["sort_order"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE
                    }.thenBy { it.index },
                )
                .map { it.value }
                // The server row list can still carry sources that were dropped from the shipped
                // list, because it was synced before the list was checked. Pruning on launch is
                // not enough: the next pull would restore them.
                .filterNot { row -> isRetiredDefaultAddon(row.manifestUrl) }
            if (mergedRows.isEmpty() && storedUrls.isNotEmpty()) {
                // A server copy that held nothing but retired sources must not leave the profile
                // with no sources at all: the device list already installed stays in place.
                log.w { "pullFromServer() — server list held only retired sources; keeping the local list" }
                return
            }
            val urls = mergedRows.map { row -> ensureManifestSuffix(row.manifestUrl) }
            log.i { "pullFromServer() — server returned ${rows.size} addons; merged ${urls.size}" }
            val cachedByUrl = CachedManifestStore.load(syncProfileId)
            _uiState.value = AddonsUiState(
                addons = mergedRows.map { row ->
                    val url = ensureManifestSuffix(row.manifestUrl)
                    currentByUrl[url].toRestoredAddon(
                        manifestUrl = url,
                        cached = cachedByUrl[url],
                        userSetName = row.name?.takeIf(String::isNotBlank),
                        enabled = row.enabled,
                    )
                },
            )
            persist()
            urls.forEach { url ->
                val existing = currentByUrl[url]
                val addon = _uiState.value.addons.firstOrNull { it.manifestUrl == url }
                if (addon?.enabled == true && (existing == null || (addon.manifest == null && !addon.isRefreshing))) {
                    refreshAddon(url)
                }
            }
            flushPendingToServer(userId, syncProfileId)
            if (currentProfileId != syncProfileId || !isCurrentSupabaseAccount(userId)) return
            initialized = true
            log.i { "pullFromServer() — applied ${urls.size} addons to state" }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (AuthRepository.signOutIfSessionInvalid(error, "Addon pull")) return
            log.e(error) { "pullFromServer() — FAILED" }
        }
    }

    suspend fun awaitManifestsLoaded() {
        if (_uiState.value.addons.isEmpty()) return
        uiState.first { state ->
            state.addons.isEmpty() ||
                state.addons.any { it.manifest != null } ||
                state.addons.none { it.isRefreshing }
        }
    }

    suspend fun addAddon(rawUrl: String): AddAddonResult {
        if (isUsingPrimaryAddonsFromSecondaryProfile()) {
            return AddAddonResult.Error(getString(Res.string.profile_primary_addons_required))
        }
        log.i { "addAddon() — validating manifest URL" }
        val manifestUrl = try {
            normalizeManifestUrl(rawUrl)
        } catch (error: IllegalArgumentException) {
            return AddAddonResult.Error(error.message ?: getString(Res.string.addon_invalid_url))
        }

        if (_uiState.value.addons.any { it.manifestUrl == manifestUrl }) {
            return AddAddonResult.Error(getString(Res.string.addon_already_installed))
        }

        val manifest = try {
            withContext(Dispatchers.Default) {
                val payload = fetchAddonResponseText(manifestUrl)
                val parsed = AddonManifestParser.parse(
                    manifestUrl = manifestUrl,
                    payload = payload,
                )
                CachedManifestStore.save(
                    profileId = currentProfileId,
                    manifestUrl = manifestUrl,
                    payload = payload,
                )
                parsed
            }
        } catch (error: Throwable) {
            return AddAddonResult.Error(error.message ?: getString(Res.string.addon_load_manifest_failed))
        }

        _uiState.update { current ->
            current.copy(
                addons = current.addons + ManagedAddon(
                    manifestUrl = manifestUrl,
                    manifest = manifest,
                    isRefreshing = false,
                    errorMessage = null,
                ),
            )
        }
        persist()
        queueAddonChanges(changedManifestUrls = setOf(manifestUrl))
        pushToServer()
        return AddAddonResult.Success(manifest)
    }

    /**
     * Saves new settings onto an installed source instead of adding a second copy.
     *
     * The configured manifest URL replaces the current one in the same position, keeping the
     * source's name and enabled state, so a token or a filter can be changed from the app without
     * the list growing a near-identical twin.
     */
    suspend fun replaceAddon(currentManifestUrl: String, rawNewUrl: String): AddAddonResult {
        if (isUsingPrimaryAddonsFromSecondaryProfile()) {
            return AddAddonResult.Error(getString(Res.string.profile_primary_addons_required))
        }
        val before = _uiState.value.addons
        val oldIndex = before.indexOfFirst { addon -> addon.manifestUrl == currentManifestUrl }
        val oldAddon = before.getOrNull(oldIndex) ?: return addAddon(rawNewUrl)

        val added = addAddon(rawNewUrl)
        if (added !is AddAddonResult.Success) return added
        val newUrl = added.manifest.transportUrl
        if (newUrl == currentManifestUrl) return added

        _uiState.update { current ->
            val withoutOld = current.addons.filterNot { addon ->
                addon.manifestUrl == currentManifestUrl
            }
            val editedAddon = withoutOld.firstOrNull { addon -> addon.manifestUrl == newUrl }
                ?: return@update current
            val edited = editedAddon.copy(
                userSetName = editedAddon.userSetName ?: oldAddon.userSetName,
                enabled = oldAddon.enabled,
            )
            val others = withoutOld.filterNot { addon -> addon.manifestUrl == newUrl }
            val insertAt = oldIndex.coerceIn(0, others.size)
            current.copy(addons = others.toMutableList().apply { add(insertAt, edited) })
        }
        CachedManifestStore.remove(currentProfileId, currentManifestUrl)
        persist()
        queueAddonChanges(
            changedManifestUrls = setOf(newUrl),
            deletedManifestUrls = setOf(currentManifestUrl),
        )
        pushToServer()
        return added
    }

    fun removeAddon(manifestUrl: String) {
        if (isUsingPrimaryAddonsFromSecondaryProfile()) return
        log.i { "removeAddon() — removing installed add-on" }
        var changed = false
        _uiState.update { current ->
            val updatedAddons = current.addons.filterNot { it.manifestUrl == manifestUrl }
            changed = updatedAddons.size != current.addons.size
            if (changed) current.copy(addons = updatedAddons) else current
        }
        if (!changed) return
        CachedManifestStore.remove(currentProfileId, manifestUrl)
        persist()
        queueAddonChanges(deletedManifestUrls = setOf(manifestUrl))
        pushToServer()
    }

    fun moveAddon(fromIndex: Int, toIndex: Int) {
        if (isUsingPrimaryAddonsFromSecondaryProfile()) return
        var changed = false
        _uiState.update { current ->
            val addons = current.addons
            if (
                fromIndex !in addons.indices ||
                toIndex !in addons.indices ||
                fromIndex == toIndex
            ) {
                return@update current
            }

            val reordered = addons.toMutableList()
            val movingAddon = reordered.removeAt(fromIndex)
            reordered.add(toIndex, movingAddon)
            changed = true
            current.copy(addons = reordered)
        }
        if (!changed) return
        persist()
        queueAddonChanges(changedManifestUrls = _uiState.value.addons.mapTo(mutableSetOf(), ManagedAddon::manifestUrl))
        pushToServer()
    }

    fun setAddonEnabled(manifestUrl: String, enabled: Boolean) {
        if (isUsingPrimaryAddonsFromSecondaryProfile()) return
        var shouldRefresh = false
        var changed = false
        _uiState.update { current ->
            current.copy(
                addons = current.addons.map { addon ->
                    if (addon.manifestUrl != manifestUrl || addon.enabled == enabled) {
                        addon
                    } else {
                        changed = true
                        shouldRefresh = enabled && addon.manifest == null && !addon.isRefreshing
                        addon.copy(enabled = enabled)
                    }
                },
            )
        }
        if (!changed) return
        persist()
        queueAddonChanges(changedManifestUrls = setOf(manifestUrl))
        pushToServer()
        if (shouldRefresh) {
            refreshAddon(manifestUrl)
        }
    }

    fun refreshAll() {
        _uiState.value.addons.filter { it.enabled }.distinctBy { it.manifestUrl }.forEach { addon ->
            refreshAddon(
                manifestUrl = addon.manifestUrl,
                forceRefresh = true,
            )
        }
    }

    fun refreshAddon(
        manifestUrl: String,
        forceRefresh: Boolean = false,
        staggerIndex: Int = 0,
        showProgress: Boolean = true,
    ) {
        val existingJob = activeRefreshJobs[manifestUrl]
        if (existingJob?.isActive == true) return

        if (showProgress) markRefreshing(manifestUrl)
        val profileId = currentProfileId
        var refreshJob: Job? = null
        refreshJob = scope.launch {
            try {
                if (staggerIndex > 0) {
                    delay(staggerIndex.coerceAtMost(MAX_MANIFEST_STAGGER_SLOTS) * MANIFEST_REFRESH_STAGGER_MS)
                }
                val result = runCatching {
                    val payload = fetchAddonResponseText(
                        url = manifestUrl,
                        forceRefresh = forceRefresh,
                        gate = manifestRefreshSemaphore,
                    )
                    val manifest = AddonManifestParser.parse(
                        manifestUrl = manifestUrl,
                        payload = payload,
                    )
                    CachedManifestStore.save(
                        profileId = profileId,
                        manifestUrl = manifestUrl,
                        payload = payload,
                    )
                    manifest
                }

                _uiState.update { current ->
                    current.copy(
                        addons = current.addons.map { addon ->
                            if (addon.manifestUrl != manifestUrl) {
                                addon
                            } else {
                                result.fold(
                                    onSuccess = { manifest ->
                                        addon.copy(
                                            manifest = manifest,
                                            isRefreshing = false,
                                            errorMessage = null,
                                        )
                                    },
                                    onFailure = { error ->
                                        addon.copy(
                                            isRefreshing = false,
                                            errorMessage = error.message ?: getString(Res.string.addon_load_manifest_failed),
                                        )
                                    },
                                )
                            }
                        },
                    )
                }
            } finally {
                if (activeRefreshJobs[manifestUrl] === refreshJob) {
                    activeRefreshJobs.remove(manifestUrl)
                }
            }
        }
        activeRefreshJobs[manifestUrl] = refreshJob
    }

    private fun queueAddonChanges(
        changedManifestUrls: Set<String> = emptySet(),
        deletedManifestUrls: Set<String> = emptySet(),
    ) {
        if (isUsingPrimaryAddonsFromSecondaryProfile()) return
        val authState = AuthRepository.state.value as? AuthState.Authenticated ?: return
        if (authState.isAnonymous) return
        val userId = authState.userId
        val profileId = currentProfileId
        val changedUrls = changedManifestUrls.mapTo(mutableSetOf(), ::ensureManifestSuffix)
        val deletedUrls = deletedManifestUrls.mapTo(mutableSetOf(), ::ensureManifestSuffix)
        val mutations = _uiState.value.addons
            .distinctBy { ensureManifestSuffix(it.manifestUrl) }
            .mapIndexedNotNull { index, addon ->
                val manifestUrl = ensureManifestSuffix(addon.manifestUrl)
                if (manifestUrl !in changedUrls) return@mapIndexedNotNull null
                PendingUserAddonMutation(
                    addonId = manifestUrl,
                    manifestUrl = manifestUrl,
                    name = addon.userSetName?.takeIf(String::isNotBlank) ?: addon.manifest?.name,
                    enabled = addon.enabled,
                    config = JsonObject(mapOf("sort_order" to JsonPrimitive(index))),
                )
            } + deletedUrls.map { addonId -> PendingUserAddonMutation(addonId = addonId) }
        if (mutations.isEmpty()) return
        AccountUserAddonSyncOutbox.record(
            PendingUserAddonChange(accountId = userId, profileId = profileId, mutations = mutations),
        )
    }

    private fun pushToServer() {
        if (isUsingPrimaryAddonsFromSecondaryProfile()) return
        val authState = AuthRepository.state.value as? AuthState.Authenticated ?: return
        if (authState.isAnonymous) return
        val userId = authState.userId
        val profileId = currentProfileId
        pushJobsByProfile[profileId]?.cancel()
        var pushJob: Job? = null
        pushJob = scope.launch {
            try {
                delay(ADDON_PUSH_DEBOUNCE_MS)
                if (currentProfileId != profileId || !isCurrentSupabaseAccount(userId)) return@launch
                flushPendingToServer(userId, profileId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (AuthRepository.signOutIfSessionInvalid(error, "Addon push")) return@launch
                log.e(error) { "pushToServer() — FAILED; pending edits are retained" }
            } finally {
                if (pushJobsByProfile[profileId] === pushJob) {
                    pushJobsByProfile.remove(profileId)
                }
            }
        }
        pushJobsByProfile[profileId] = pushJob
    }

    private suspend fun flushPendingToServer(userId: String, profileId: Int) {
        if (currentProfileId != profileId || !isCurrentSupabaseAccount(userId)) return
        val pending = AccountUserAddonSyncOutbox.pendingFor(userId, profileId) ?: return
        val upserts = pending.mutations.mapNotNull { mutation ->
            val manifestUrl = mutation.manifestUrl ?: return@mapNotNull null
            AddonWriteRow(
                userId = userId,
                addonId = mutation.addonId,
                manifestUrl = manifestUrl,
                name = mutation.name,
                enabled = mutation.enabled,
                config = mutation.config,
            )
        }
        if (upserts.isNotEmpty()) {
            SupabaseProvider.client.postgrest.from("user_addons")
                .upsert(upserts) { onConflict = "user_id,addon_id" }
        }
        pending.mutations.filter { it.manifestUrl == null }.forEach { mutation ->
            if (currentProfileId != profileId || !isCurrentSupabaseAccount(userId)) return
            SupabaseProvider.client.postgrest.from("user_addons").delete {
                filter {
                    eq("user_id", userId)
                    eq("addon_id", mutation.addonId)
                }
            }
        }
        if (currentProfileId != profileId || !isCurrentSupabaseAccount(userId)) return
        AccountUserAddonSyncOutbox.clearIfMatches(pending)
        log.d { "flushPendingToServer() — synced ${pending.mutations.size} add-on changes" }
    }

    private fun markRefreshing(manifestUrl: String) {
        _uiState.update { current ->
            current.copy(
                addons = current.addons.map { addon ->
                    if (addon.manifestUrl == manifestUrl) {
                        addon.copy(
                            isRefreshing = true,
                            errorMessage = null,
                        )
                    } else {
                        addon
                    }
                },
            )
        }
    }

    /**
     * Installs the bundled default add-ons the first time a profile has none, so the app opens on
     * working catalogs instead of an empty home screen. The marker is persisted, so removing them
     * afterwards is remembered and they never come back on their own.
     */
    private fun seedDefaultAddonsIfNeeded(profileId: Int) {
        if (isUsingPrimaryAddonsFromSecondaryProfile()) return
        if (AddonStorage.areDefaultAddonsSeeded(profileId)) return
        if (defaultAddonSeedJob?.isActive == true) return

        val defaultUrls = DefaultAddonSources.mapTo(mutableSetOf()) { it.manifestUrl }
        val existingUrls = _uiState.value.addons.map { it.manifestUrl }
        if (defaultUrls.all { it in existingUrls }) {
            // Every bundled add-on is already installed; remember it so this stops running.
            AddonStorage.markDefaultAddonsSeeded(profileId)
            return
        }

        var seedJob: Job? = null
        seedJob = scope.launch {
            val alreadyInstalled = _uiState.value.addons.mapTo(mutableSetOf()) { it.manifestUrl }
            val results = coroutineScope {
                DefaultAddonSources.map { source ->
                    async {
                        if (currentProfileId != profileId || source.manifestUrl in alreadyInstalled) {
                            return@async false
                        }
                        val manifest = runCatching {
                            val payload = fetchAddonResponseText(
                                url = source.manifestUrl,
                                gate = manifestRefreshSemaphore,
                            )
                            val parsed = AddonManifestParser.parse(
                                manifestUrl = source.manifestUrl,
                                payload = payload,
                            )
                            CachedManifestStore.save(
                                profileId = profileId,
                                manifestUrl = source.manifestUrl,
                                payload = payload,
                            )
                            parsed
                        }.getOrNull() ?: return@async false
                        if (currentProfileId != profileId) return@async false

                        _uiState.update { current ->
                            if (current.addons.any { it.manifestUrl == source.manifestUrl }) {
                                current
                            } else {
                                current.copy(
                                    addons = current.addons + ManagedAddon(
                                        manifestUrl = source.manifestUrl,
                                        manifest = manifest,
                                        isRefreshing = false,
                                        errorMessage = null,
                                    ),
                                )
                            }
                        }
                        true
                    }
                }.awaitAll()
            }
            if (currentProfileId != profileId) return@launch
            log.d { "seedDefaultAddonsIfNeeded() — fetched ${results.count { it }} bundled manifests concurrently" }

            val seededUrls = _uiState.value.addons.map { it.manifestUrl }
            if (seededUrls.isEmpty()) {
                log.w { "seedDefaultAddonsIfNeeded() — nothing installed; retrying on the next launch" }
                return@launch
            }
            if (defaultUrls.any { it !in seededUrls }) {
                log.w { "seedDefaultAddonsIfNeeded() — some sources are unreachable this time" }
            }

            persist()
            queueAddonChanges(changedManifestUrls = defaultUrls)
            pushToServer()
            // Home shelves are built from the catalog settings, which otherwise only the settings
            // screen refreshes; without this a first launch would still look empty. It publishes UI
            // state, so it runs on the main dispatcher rather than this background one.
            withContext(Dispatchers.Main) {
                HomeCatalogSettingsRepository.syncCatalogs(_uiState.value.addons.enabledAddons())
            }
            AddonStorage.markDefaultAddonsSeeded(profileId)
            log.i { "seedDefaultAddonsIfNeeded() — bundled add-ons installed" }
        }
        defaultAddonSeedJob = seedJob
    }

    /**
     * Removes the bundled sources from this profile and installs them again in the order the
     * bundle lists them, leaving the user's own sources untouched. Returns how many installed.
     */
    suspend fun resetBuiltInSources(): Int {
        if (isUsingPrimaryAddonsFromSecondaryProfile()) return 0
        val profileId = currentProfileId
        val builtInUrls = DefaultAddonSources.mapTo(mutableSetOf()) { source -> source.manifestUrl }

        _uiState.update { current ->
            current.copy(addons = current.addons.filterNot { addon -> addon.manifestUrl in builtInUrls })
        }
        builtInUrls.forEach { url -> CachedManifestStore.remove(profileId, url) }
        persist()

        val installed = coroutineScope {
            DefaultAddonSources.map { source ->
                async {
                    if (currentProfileId != profileId) return@async null
                    runCatching {
                        val payload = fetchAddonResponseText(
                            url = source.manifestUrl,
                            gate = manifestRefreshSemaphore,
                        )
                        val manifest = AddonManifestParser.parse(
                            manifestUrl = source.manifestUrl,
                            payload = payload,
                        )
                        CachedManifestStore.save(
                            profileId = profileId,
                            manifestUrl = source.manifestUrl,
                            payload = payload,
                        )
                        ManagedAddon(
                            manifestUrl = source.manifestUrl,
                            manifest = manifest,
                            isRefreshing = false,
                            errorMessage = null,
                        )
                    }.getOrNull()
                }
            }.awaitAll()
        }.filterNotNull()
        if (currentProfileId != profileId) return 0

        _uiState.update { current ->
            val userAddons = current.addons.filterNot { addon -> addon.manifestUrl in builtInUrls }
            current.copy(addons = installed + userAddons)
        }
        persist()
        queueAddonChanges(changedManifestUrls = builtInUrls)
        pushToServer()
        withContext(Dispatchers.Main) {
            HomeCatalogSettingsRepository.syncCatalogs(_uiState.value.addons.enabledAddons())
        }
        log.i { "resetBuiltInSources() — reinstalled ${installed.size} bundled sources" }
        return installed.size
    }

    private fun persist() {
        val addons = _uiState.value.addons
        AddonStorage.saveInstalledAddonUrls(
            currentProfileId,
            dedupeManifestUrls(addons.map { it.manifestUrl }),
        )
        AddonStorage.saveAddonEnabledStates(
            currentProfileId,
            addons.associate { it.manifestUrl to it.enabled },
        )
    }

    private fun loadLocalEnabledStates(): Map<String, Boolean> =
        AddonStorage.loadAddonEnabledStates(currentProfileId)
            .mapKeys { (url, _) -> ensureManifestSuffix(url) }

    private fun cancelActiveRefreshes() {
        activeRefreshJobs.values.forEach(Job::cancel)
        activeRefreshJobs.clear()
    }

    private fun resolveEffectiveProfileId(profileId: Int): Int {
        val active = ProfileRepository.state.value.activeProfile
        return if (active != null && active.profileIndex != 1 && active.usesPrimaryAddons) 1 else profileId
    }

    private fun isUsingPrimaryAddonsFromSecondaryProfile(): Boolean {
        val active = ProfileRepository.state.value.activeProfile
        return active != null && active.profileIndex != 1 && active.usesPrimaryAddons
    }
}

private fun ManagedAddon?.toPendingAddon(
    manifestUrl: String,
    userSetName: String? = null,
    enabled: Boolean? = null,
): ManagedAddon =
    when {
        this == null -> ManagedAddon(
            manifestUrl = manifestUrl,
            isRefreshing = enabled ?: true,
            userSetName = userSetName,
            enabled = enabled ?: true,
        )
        manifest != null -> copy(
            manifestUrl = manifestUrl,
            isRefreshing = false,
            userSetName = userSetName ?: this.userSetName,
            enabled = enabled ?: this.enabled,
        )
        isRefreshing -> copy(
            manifestUrl = manifestUrl,
            userSetName = userSetName ?: this.userSetName,
            enabled = enabled ?: this.enabled,
        )
        else -> copy(
            manifestUrl = manifestUrl,
            isRefreshing = enabled ?: this.enabled,
            errorMessage = null,
            userSetName = userSetName ?: this.userSetName,
            enabled = enabled ?: this.enabled,
        )
    }

/**
 * An add-on restored at launch: the manifest already in memory wins, then the copy kept
 * from the last launch, and only an add-on with neither is left waiting on the network.
 */
private fun ManagedAddon?.toRestoredAddon(
    manifestUrl: String,
    cached: CachedManifest?,
    userSetName: String? = null,
    enabled: Boolean? = null,
): ManagedAddon {
    val pending = toPendingAddon(
        manifestUrl = manifestUrl,
        userSetName = userSetName,
        enabled = enabled,
    )
    val manifest = this?.manifest ?: cached?.manifest ?: return pending
    return pending.copy(
        manifest = manifest,
        isRefreshing = false,
        errorMessage = null,
    )
}

private fun dedupeManifestUrls(urls: List<String>): List<String> =
    urls.map(::ensureManifestSuffix).distinct()

private fun ensureManifestSuffix(url: String): String {
    val path = url.substringBefore("?").trimEnd('/')
    val query = url.substringAfter("?", "")
    val withSuffix = if (path.endsWith("/manifest.json")) path else "$path/manifest.json"
    return if (query.isEmpty()) withSuffix else "$withSuffix?$query"
}

private fun normalizeManifestUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    require(trimmed.isNotEmpty()) { runBlocking { getString(Res.string.addons_error_enter_url) } }

    val normalizedScheme = when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("stremio://") -> "https://${trimmed.removePrefix("stremio://")}"
        else -> "https://$trimmed"
    }

    val withoutFragment = normalizedScheme.substringBefore("#")
    val query = withoutFragment.substringAfter("?", "")
    val path = withoutFragment.substringBefore("?").trimEnd('/')
    val manifestPath = if (path.endsWith("/manifest.json")) {
        path
    } else {
        "$path/manifest.json"
    }

    return if (query.isEmpty()) manifestPath else "$manifestPath?$query"
}
