package com.cinemax.app.nuvio

import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Persists installed Nuvio repositories, enabled providers and the optional TMDB key. */
class NuvioProviderStore(
    private val settings: Settings = Settings(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun loadRepositories(): List<InstalledNuvioRepository> = runCatching {
        json.decodeFromString(
            ListSerializer(InstalledNuvioRepository.serializer()),
            settings.getString(REPOSITORIES, "[]")
        )
    }.getOrDefault(emptyList())

    fun saveRepositories(repositories: List<InstalledNuvioRepository>) {
        settings.putString(
            REPOSITORIES,
            json.encodeToString(ListSerializer(InstalledNuvioRepository.serializer()), repositories)
        )
    }

    fun loadEnabledProviders(): Set<String> =
        settings.getStringOrNull(ENABLED)?.split('\n')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun saveEnabledProviders(keys: Set<String>) {
        settings.putString(ENABLED, keys.sorted().joinToString("\n"))
    }

    fun loadTmdbApiKey(): String = settings.getString(TMDB_KEY, "")

    fun saveTmdbApiKey(value: String) {
        settings.putString(TMDB_KEY, value.trim())
    }

    fun loadTmdbId(itemId: String): String? = settings.getStringOrNull("$TMDB_CACHE_PREFIX$itemId")

    fun saveTmdbId(itemId: String, tmdbId: String) {
        settings.putString("$TMDB_CACHE_PREFIX$itemId", tmdbId)
    }

    private companion object {
        const val REPOSITORIES = "cinemax.nuvio.repositories.v1"
        const val ENABLED = "cinemax.nuvio.enabled.v1"
        const val TMDB_KEY = "cinemax.nuvio.tmdb.key"
        const val TMDB_CACHE_PREFIX = "cinemax.nuvio.tmdb.id."
    }
}
