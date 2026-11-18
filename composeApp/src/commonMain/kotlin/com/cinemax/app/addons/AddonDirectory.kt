package com.cinemax.app.addons

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One addon from Stremio's public collection: where to install it and what it is. */
@Serializable
data class AddonDirectoryEntry(
    val transportUrl: String = "",
    /** Some exports use this name for the same field. */
    val manifestUrl: String? = null,
    val manifest: AddonDirectoryManifest? = null
) {
    val installUrl: String get() = manifestUrl?.takeIf { it.isNotBlank() } ?: transportUrl
}

@Serializable
data class AddonDirectoryManifest(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val version: String? = null,
    val logo: String? = null,
    val types: List<String> = emptyList()
)

/**
 * Stremio publishes a JSON list of community addons with their manifest previews.
 * It is read on demand (never at launch), cached for a day, and only HTTPS entries
 * are kept, which is the same rule the installer enforces.
 */
class AddonDirectory(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    private val cache = TtlCache<List<AddonDirectoryEntry>>(CACHE_TTL_MS)

    suspend fun load(): List<AddonDirectoryEntry> {
        cache.get(CACHE_KEY)?.let { return it }
        val body = http.get(COLLECTION_URL).bodyAsText()
        val entries = runCatching {
            json.decodeFromString<List<AddonDirectoryEntry>>(body)
        }.getOrDefault(emptyList())
        val clean = entries
            .filter { it.installUrl.startsWith("https://") }
            .distinctBy { it.installUrl }
            .sortedBy { (it.manifest?.name ?: "").lowercase() }
        if (clean.isNotEmpty()) cache.put(CACHE_KEY, clean)
        return clean
    }

    private companion object {
        const val COLLECTION_URL = "https://api.strem.io/addonscollection.json"
        const val CACHE_KEY = "collection"
        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
