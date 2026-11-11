package com.nuvio.app.core.sync

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.auth.isCurrentSupabaseAccount
import com.nuvio.app.core.network.ServerConfigurationRepository
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.collection.CollectionMobileSettingsRepository
import com.nuvio.app.features.collection.CollectionMobileSettingsStorage
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DebridSettingsStorage
import com.nuvio.app.features.details.MetaScreenSettingsStorage
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.mdblist.MdbListMetadataService
import com.nuvio.app.features.mdblist.MdbListSettingsStorage
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.player.PlayerSettingsStorage
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.core.ui.CardDepthStyleRepository
import com.nuvio.app.core.ui.CardDepthStyleStorage
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.core.poster.CustomPosterUrlRepository
import com.nuvio.app.core.poster.CustomPosterUrlStorage
import com.nuvio.app.core.ui.PosterCardStyleStorage
import com.nuvio.app.features.settings.ThemeSettingsStorage
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.streams.StreamBadgeSettingsRepository
import com.nuvio.app.features.streams.StreamBadgeSettingsStorage
import com.nuvio.app.features.tmdb.TmdbSettingsStorage
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.trakt.TraktCommentsStorage
import com.nuvio.app.features.trakt.TraktCommentsSettings
import com.nuvio.app.features.trakt.TraktSettingsStorage
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesStorage
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

private const val PUSH_DEBOUNCE_MS = 1500L

object ProfileSettingsSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("ProfileSettingsSync")
    private val syncMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var isApplyingRemoteBlob: Boolean = false

    @Volatile
    private var isServerSyncInFlight: Boolean = false

    @Volatile
    private var skipNextPushSignature: String? = null

    private var observeJob: Job? = null

    fun startObserving() {
        if (observeJob?.isActive == true) return
        ensureRepositoriesLoaded()
        if (ServerConfigurationRepository.active.value.isCustom) {
            ProviderCredentialSync.startObserving()
            MediaServerSync.startObserving()
        }
        observeLocalChangesAndPush()
    }

    fun clearAccountState() {
        observeJob?.cancel()
        observeJob = null
        skipNextPushSignature = null
        ProviderCredentialSync.clearAccountState()
        MediaServerSync.clearAccountState()
    }

    fun onProfileChanged() {
        if (observeJob?.isActive != true) return
        skipNextPushSignature = currentObservedStateSignature()
        ProviderCredentialSync.onProfileChanged()
    }

    suspend fun pull(profileId: Int): Boolean {
        ensureRepositoriesLoaded()
        val userId = currentSupabaseSettingsUserId() ?: return false
        return syncMutex.withLock {
            if (ProfileRepository.activeProfileId != profileId) {
                log.d { "pull(profileId=$profileId) — skipped because profile is no longer active" }
                return@withLock false
            }
            isServerSyncInFlight = true
            try {
                ensureAccountProfileRow(userId)
                if (!isCurrentSupabaseAccount(userId)) return@withLock false
                if (ProfileRepository.activeProfileId != profileId) return@withLock false
                val pending = AccountProfileSettingsSyncOutbox.pendingFor(userId, profileId)
                if (pending != null) {
                    log.i { "pull(profileId=$profileId) — retrying pending local settings before applying remote" }
                    return@withLock pushPendingSettingsLocked(userId, profileId, pending)
                }

                val localBlob = exportSettingsBlob()
                if (ProfileRepository.activeProfileId != profileId) return@withLock false
                val localSignature = buildSignature(localBlob)

                val response = SupabaseProvider.client.postgrest.from("user_settings")
                    .select {
                        filter { eq("user_id", userId) }
                        limit(1)
                    }
                    .decodeList<UserSettingsRow>()
                    .firstOrNull()
                if (!isCurrentSupabaseAccount(userId) || ProfileRepository.activeProfileId != profileId) {
                    return@withLock false
                }
                val remoteJson = response?.preferences
                if (remoteJson == null) {
                    pushSnapshotLocked(userId, profileId, localBlob)
                    log.i { "pull(profileId=$profileId) — seeded remote settings" }
                    return@withLock false
                }

                isApplyingRemoteBlob = true
                try {
                    val remoteBlob = runCatching {
                        json.decodeFromJsonElement(MobileProfileSettingsBlob.serializer(), remoteJson)
                    }.getOrElse { error ->
                        log.e(error) { "pull(profileId=$profileId) — failed to decode remote settings blob" }
                        return@withLock false
                    }
                    val remoteSignature = buildSignature(remoteBlob)
                    if (remoteSignature == localSignature) {
                        log.d { "pull(profileId=$profileId) — remote matches local" }
                        return@withLock false
                    }

                    if (!isCurrentSupabaseAccount(userId) || ProfileRepository.activeProfileId != profileId) {
                        return@withLock false
                    }
                    applyRemoteBlob(remoteBlob)
                    skipNextPushSignature = currentObservedStateSignature()
                } finally {
                    isApplyingRemoteBlob = false
                }

                log.i { "pull(profileId=$profileId) — applied remote settings blob" }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (AuthRepository.signOutIfSessionInvalid(error, "Profile settings pull")) return@withLock false
                log.e(error) { "pull(profileId=$profileId) — FAILED" }
                false
            } finally {
                isServerSyncInFlight = false
            }
        }
    }

    suspend fun pushCurrentProfileToRemote(): Boolean {
        ensureRepositoriesLoaded()
        val userId = currentSupabaseSettingsUserId() ?: return false
        return syncMutex.withLock {
            try {
                val profileId = ProfileRepository.activeProfileId
                ensureAccountProfileRow(userId)
                val blob = exportSettingsBlob()
                if (ProfileRepository.activeProfileId != profileId || !isCurrentSupabaseAccount(userId)) {
                    return@withLock false
                }
                pushSnapshotLocked(userId, profileId, blob)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!AuthRepository.signOutIfSessionInvalid(error, "Profile settings push")) {
                    log.e(error) { "pushCurrentProfileToRemote() — FAILED" }
                }
                false
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeLocalChangesAndPush() {
        val signatureFlows = listOf(
            ThemeSettingsRepository.selectedThemePreference.map { "theme" },
            ThemeSettingsRepository.customThemePreference.map { "custom_theme_colors" },
            ThemeSettingsRepository.amoledEnabled.map { "amoled" },
            ThemeSettingsRepository.liquidGlassNativeTabBarEnabled.map { "liquid_glass_tab_bar" },
            ThemeSettingsRepository.navBarGlowEnabled.map { "nav_bar_glow_enabled" },
            ThemeSettingsRepository.navBarStyle.map { "nav_bar_style" },
            PosterCardStyleRepository.uiState.map { "poster_card_style" },
            CustomPosterUrlRepository.pattern.map { "custom_poster_url" },
            CustomPosterUrlRepository.enabledScreens.map { "custom_poster_screens" },
            CardDepthStyleRepository.uiState.map { "card_depth_style" },
            PlayerSettingsRepository.uiState.map { "player" },
            StreamBadgeSettingsRepository.uiState.map { "stream_badges" },
            DebridSettingsRepository.uiState.map { "debrid" },
            TmdbSettingsRepository.uiState.map { "tmdb" },
            MdbListSettingsRepository.uiState.map { "mdblist" },
            MetaScreenSettingsRepository.uiState.map { "meta" },
            CollectionMobileSettingsRepository.uiState.map { "collection_mobile_settings" },
            ContinueWatchingPreferencesRepository.uiState.map { "continue_watching" },
            TrackingSettingsRepository.uiState.map { "trakt_settings" },
            TraktCommentsSettings.enabled.map { "trakt_comments" },
            EpisodeReleaseNotificationsRepository.uiState.map { "episode_release_alerts" },
        )

        observeJob = scope.launch {
            combine(signatureFlows) { currentObservedStateSignature() }
                .distinctUntilChanged()
                .drop(1)
                .onEach { signature ->
                    val authState = AuthRepository.state.value
                    if (authState !is AuthState.Authenticated || authState.isAnonymous) return@onEach
                    if (isApplyingRemoteBlob || signature == skipNextPushSignature) return@onEach
                    if (signature != currentObservedStateSignature()) return@onEach
                    AccountProfileSettingsSyncOutbox.record(
                        PendingProfileSettingsChange(
                            accountId = authState.userId,
                            profileId = ProfileRepository.activeProfileId,
                            payload = signature,
                        ),
                    )
                }
                .debounce(PUSH_DEBOUNCE_MS)
                .collect { signature ->
                    val authState = AuthRepository.state.value
                    if (authState !is AuthState.Authenticated || authState.isAnonymous) return@collect
                    if (isApplyingRemoteBlob || isServerSyncInFlight) return@collect
                    if (signature != currentObservedStateSignature()) return@collect
                    if (signature == skipNextPushSignature) {
                        skipNextPushSignature = null
                        return@collect
                    }
                    pushCurrentProfileToRemote()
                }
        }
    }

    private suspend fun pushSnapshotLocked(
        userId: String,
        profileId: Int,
        blob: MobileProfileSettingsBlob,
    ): Boolean {
        if (ProfileRepository.activeProfileId != profileId || !isCurrentSupabaseAccount(userId)) return false
        val pending = PendingProfileSettingsChange(
            accountId = userId,
            profileId = profileId,
            payload = buildSignature(blob),
        )
        AccountProfileSettingsSyncOutbox.record(pending)
        return pushPendingSettingsLocked(userId, profileId, pending)
    }

    private suspend fun pushPendingSettingsLocked(
        userId: String,
        profileId: Int,
        pending: PendingProfileSettingsChange,
    ): Boolean {
        if (pending.accountId != userId || pending.profileId != profileId) return false
        if (ProfileRepository.activeProfileId != profileId || !isCurrentSupabaseAccount(userId)) return false
        val blob = runCatching { json.decodeFromString<MobileProfileSettingsBlob>(pending.payload) }
            .getOrElse { error ->
                log.e(error) { "Pending profile settings payload is invalid; clearing unusable local snapshot" }
                AccountProfileSettingsSyncOutbox.clearIfMatches(pending)
                return false
            }
        pushToRemoteLocked(userId, blob)
        if (ProfileRepository.activeProfileId != profileId || !isCurrentSupabaseAccount(userId)) return false
        return AccountProfileSettingsSyncOutbox.clearIfMatches(pending)
    }

    private suspend fun pushToRemoteLocked(userId: String, blob: MobileProfileSettingsBlob) {
        if (!isCurrentSupabaseAccount(userId)) return
        SupabaseProvider.client.postgrest.from("user_settings")
            .upsert(
                UserSettingsWriteRow(
                    userId = userId,
                    preferences = json.encodeToJsonElement(MobileProfileSettingsBlob.serializer(), blob).jsonObject,
                ),
            ) { onConflict = "user_id" }
        check(isCurrentSupabaseAccount(userId)) { "Account changed during settings sync" }
        log.d { "pushToRemoteLocked() — success" }
    }

    private suspend fun ensureAccountProfileRow(userId: String) {
        if (!isCurrentSupabaseAccount(userId)) return
        val existing = SupabaseProvider.client.postgrest.from("profiles")
            .select {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<AccountProfileRow>()
            .firstOrNull()
        if (existing != null || !isCurrentSupabaseAccount(userId)) return
        try {
            SupabaseProvider.client.postgrest.from("profiles").insert(AccountProfileInsertRow(id = userId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val concurrentInsert = SupabaseProvider.client.postgrest.from("profiles")
                .select {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeList<AccountProfileRow>()
                .firstOrNull()
            if (concurrentInsert == null) throw error
        }
    }

    private fun exportSettingsBlob(): MobileProfileSettingsBlob {
        ensureRepositoriesLoaded()
        return MobileProfileSettingsBlob(
            features = MobileProfileSettingsFeatures(
                themeSettings = ThemeSettingsStorage.exportToSyncPayload(),
                posterCardStyleSettingsPayload = PosterCardStyleStorage.loadPayload().orEmpty().trim(),
                customPosterUrlPattern = CustomPosterUrlStorage.loadPattern().orEmpty().trim(),
                customPosterEnabledScreens = CustomPosterUrlStorage.loadEnabledScreens()
                    ?.joinToString(",").orEmpty(),
                cardDepthStyleSettingsPayload = CardDepthStyleStorage.loadPayload().orEmpty().trim(),
                playerSettings = withoutProfileCredentials(
                    PROFILE_PLAYER_SETTINGS_FEATURE,
                    PlayerSettingsStorage.exportToSyncPayload(),
                ),
                streamBadgeSettings = StreamBadgeSettingsStorage.exportToSyncPayload(),
                debridSettings = withoutProfileCredentials(
                    PROFILE_DEBRID_SETTINGS_FEATURE,
                    DebridSettingsStorage.exportToSyncPayload(),
                ),
                tmdbSettings = withoutProfileCredentials(
                    PROFILE_TMDB_SETTINGS_FEATURE,
                    TmdbSettingsStorage.exportToSyncPayload(),
                ),
                mdbListSettings = withoutProfileCredentials(
                    PROFILE_MDBLIST_SETTINGS_FEATURE,
                    MdbListSettingsStorage.exportToSyncPayload(),
                ),
                metaScreenSettingsPayload = MetaScreenSettingsStorage.loadPayload().orEmpty().trim(),
                collectionMobileSettingsPayload = CollectionMobileSettingsStorage.loadPayload().orEmpty().trim(),
                continueWatchingSettingsPayload = ContinueWatchingPreferencesStorage.loadPayload().orEmpty().trim(),
                traktSettingsPayload = TraktSettingsStorage.loadPayload().orEmpty().trim(),
                traktCommentsSettings = TraktCommentsStorage.exportToSyncPayload(),
                notificationsSettings = NotificationsSettingsPayload(
                    episodeReleaseAlertsEnabled = EpisodeReleaseNotificationsRepository.uiState.value.isEnabled,
                ),
            ),
        )
    }

    private fun applyRemoteBlob(blob: MobileProfileSettingsBlob) {
        ThemeSettingsStorage.replaceFromSyncPayload(blob.features.themeSettings)
        ThemeSettingsRepository.onProfileChanged()

        PosterCardStyleStorage.savePayload(blob.features.posterCardStyleSettingsPayload)
        PosterCardStyleRepository.onProfileChanged()

        CustomPosterUrlStorage.savePattern(blob.features.customPosterUrlPattern.ifBlank { null })
        val remoteScreenKeys = blob.features.customPosterEnabledScreens
            .takeIf { it.isNotBlank() }
            ?.split(",")
            ?.toSet()
        CustomPosterUrlStorage.saveEnabledScreens(remoteScreenKeys)
        CustomPosterUrlRepository.onProfileChanged()
        com.nuvio.app.features.home.HomeRepository.applyCurrentSettings()

        CardDepthStyleStorage.savePayload(blob.features.cardDepthStyleSettingsPayload)
        CardDepthStyleRepository.onProfileChanged()

        val localPlayerSettings = PlayerSettingsStorage.exportToSyncPayload()
        val localIntroDbApiKey = PlayerSettingsStorage.loadIntroDbApiKey()
        PlayerSettingsStorage.replaceFromSyncPayload(
            preservingLocalProfileCredentials(
                PROFILE_PLAYER_SETTINGS_FEATURE,
                blob.features.playerSettings,
                localPlayerSettings,
            ),
        )
        localIntroDbApiKey?.let(PlayerSettingsStorage::saveIntroDbApiKey)
        PlayerSettingsRepository.onProfileChanged()

        StreamBadgeSettingsStorage.replaceFromSyncPayload(blob.features.streamBadgeSettings)
        StreamBadgeSettingsRepository.onProfileChanged()

        DebridSettingsStorage.replaceFromSyncPayload(
            preservingLocalProfileCredentials(
                PROFILE_DEBRID_SETTINGS_FEATURE,
                blob.features.debridSettings,
                DebridSettingsStorage.exportToSyncPayload(),
            ),
        )
        DebridSettingsRepository.onProfileChanged()

        TmdbSettingsStorage.replaceFromSyncPayload(
            preservingLocalProfileCredentials(
                PROFILE_TMDB_SETTINGS_FEATURE,
                blob.features.tmdbSettings,
                TmdbSettingsStorage.exportToSyncPayload(),
            ),
        )
        TmdbSettingsRepository.onProfileChanged()

        MdbListSettingsStorage.replaceFromSyncPayload(
            preservingLocalProfileCredentials(
                PROFILE_MDBLIST_SETTINGS_FEATURE,
                blob.features.mdbListSettings,
                MdbListSettingsStorage.exportToSyncPayload(),
            ),
        )
        MdbListMetadataService.clearCache()
        MdbListSettingsRepository.onProfileChanged()

        MetaScreenSettingsStorage.savePayload(blob.features.metaScreenSettingsPayload)
        MetaScreenSettingsRepository.onProfileChanged()

        CollectionMobileSettingsStorage.savePayload(blob.features.collectionMobileSettingsPayload)
        CollectionMobileSettingsRepository.onProfileChanged()

        ContinueWatchingPreferencesStorage.savePayload(blob.features.continueWatchingSettingsPayload)
        ContinueWatchingPreferencesRepository.onProfileChanged()

        TraktSettingsStorage.savePayload(blob.features.traktSettingsPayload)
        TrackingSettingsRepository.onProfileChanged()

        TraktCommentsStorage.replaceFromSyncPayload(blob.features.traktCommentsSettings)
        TraktCommentsSettings.onProfileChanged()

        EpisodeReleaseNotificationsRepository.applyFromSyncEnabled(blob.features.notificationsSettings.episodeReleaseAlertsEnabled)
    }

    private fun ensureRepositoriesLoaded() {
        ThemeSettingsRepository.ensureLoaded()
        PosterCardStyleRepository.ensureLoaded()
        CustomPosterUrlRepository.ensureLoaded()
        CardDepthStyleRepository.ensureLoaded()
        PlayerSettingsRepository.ensureLoaded()
        StreamBadgeSettingsRepository.ensureLoaded()
        DebridSettingsRepository.ensureLoaded()
        TmdbSettingsRepository.ensureLoaded()
        MdbListSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.ensureLoaded()
        CollectionMobileSettingsRepository.ensureLoaded()
        ContinueWatchingPreferencesRepository.ensureLoaded()
        TrackingSettingsRepository.ensureLoaded()
        TraktCommentsSettings.ensureLoaded()
        EpisodeReleaseNotificationsRepository.ensureLoaded()
    }

    private fun buildSignature(blob: MobileProfileSettingsBlob): String =
        json.encodeToString(MobileProfileSettingsBlob.serializer(), blob)

    private fun currentObservedStateSignature(): String = buildSignature(exportSettingsBlob())

}

@Serializable
private data class MobileProfileSettingsBlob(
    val version: Int = 4,
    val features: MobileProfileSettingsFeatures = MobileProfileSettingsFeatures(),
)

@Serializable
private data class MobileProfileSettingsFeatures(
    @SerialName("theme_settings") val themeSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("poster_card_style_settings_payload") val posterCardStyleSettingsPayload: String = "",
    @SerialName("custom_poster_url_pattern") val customPosterUrlPattern: String = "",
    @SerialName("custom_poster_enabled_screens") val customPosterEnabledScreens: String = "",
    @SerialName("card_depth_style_settings_payload") val cardDepthStyleSettingsPayload: String = "",
    @SerialName("player_settings") val playerSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("stream_badge_settings") val streamBadgeSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("debrid_settings") val debridSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("tmdb_settings") val tmdbSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("mdblist_settings") val mdbListSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("meta_screen_settings_payload") val metaScreenSettingsPayload: String = "",
    @SerialName("collection_mobile_settings_payload") val collectionMobileSettingsPayload: String = "",
    @SerialName("continue_watching_settings_payload") val continueWatchingSettingsPayload: String = "",
    @SerialName("trakt_settings_payload") val traktSettingsPayload: String = "",
    @SerialName("trakt_comments_settings") val traktCommentsSettings: JsonObject = JsonObject(emptyMap()),
    @SerialName("notifications_settings") val notificationsSettings: NotificationsSettingsPayload = NotificationsSettingsPayload(),
)

@Serializable
private data class NotificationsSettingsPayload(
    @SerialName("episode_release_alerts_enabled") val episodeReleaseAlertsEnabled: Boolean = false,
)

private fun currentSupabaseSettingsUserId(): String? =
    (AuthRepository.state.value as? AuthState.Authenticated)
        ?.takeIf { authenticated -> !authenticated.isAnonymous }
        ?.userId
        ?.takeIf(String::isNotBlank)

@Serializable
private data class UserSettingsRow(
    @SerialName("user_id") val userId: String,
    val preferences: JsonObject = JsonObject(emptyMap()),
)

@Serializable
private data class UserSettingsWriteRow(
    @SerialName("user_id") val userId: String,
    val preferences: JsonObject,
)

@Serializable
private data class AccountProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
private data class AccountProfileInsertRow(
    val id: String,
)
