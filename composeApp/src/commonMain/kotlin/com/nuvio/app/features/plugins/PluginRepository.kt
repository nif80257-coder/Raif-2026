package com.nuvio.app.features.plugins

import kotlinx.coroutines.flow.StateFlow

/**
 * How long one provider may run before the app stops waiting for it.
 *
 * A settings test can afford a minute. A stream search passes its own shorter budget: a provider
 * still working after ten seconds holds a fetch slot the next one is waiting for, and the list it
 * would fill is already on screen.
 */
internal const val DefaultPluginTimeoutMs = 60_000L

expect object PluginRepository {
    val uiState: StateFlow<PluginsUiState>

    fun initialize()

    fun onProfileChanged(profileId: Int)

    fun clearLocalState()

    suspend fun pullFromServer(profileId: Int)

    suspend fun addRepository(rawUrl: String): AddPluginRepositoryResult

    fun removeRepository(manifestUrl: String)

    fun refreshAll()

    fun refreshRepository(manifestUrl: String, pushAfterRefresh: Boolean = false)

    fun toggleScraper(scraperId: String, enabled: Boolean)

    fun setPluginsEnabled(enabled: Boolean)

    fun setGroupStreamsByRepository(enabled: Boolean)

    fun setLocalPluginSearchPaused(paused: Boolean)

    fun getEnabledScrapersForType(type: String): List<PluginScraper>

    suspend fun testScraper(scraperId: String): Result<List<PluginRuntimeResult>>

    suspend fun executeScraper(
        scraper: PluginScraper,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        timeoutMs: Long = DefaultPluginTimeoutMs,
    ): Result<List<PluginRuntimeResult>>
}