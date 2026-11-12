package com.cinemax.app.nuvio

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Nuvio providers take TMDB ids while Stremio catalogs usually carry IMDb ids
 * ("tt1234567"). With a user-supplied TMDB key the id is resolved and cached;
 * without one the raw id is passed through so providers that accept it still work.
 */
class TmdbIdResolver(
    private val http: HttpClient,
    private val store: NuvioProviderStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun request(itemId: String, itemType: String, season: Int?, episode: Int?): NuvioMediaRequest {
        val mediaType = NuvioMediaIds.mediaType(itemType)
        val tmdbId = NuvioMediaIds.explicitTmdbId(itemId) ?: resolveFromImdb(itemId) ?: itemId
        return NuvioMediaRequest(
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = if (mediaType == "tv") season else null,
            episode = if (mediaType == "tv") episode else null
        )
    }

    private suspend fun resolveFromImdb(itemId: String): String? {
        if (!itemId.startsWith("tt")) return null
        val apiKey = store.loadTmdbApiKey()
        if (apiKey.isBlank()) return null
        store.loadTmdbId(itemId)?.let { return it }
        val bearerToken = apiKey.length > 40
        val query = if (bearerToken) "" else "&api_key=$apiKey"
        val url = "https://api.themoviedb.org/3/find/$itemId?external_source=imdb_id$query"
        val body = try {
            http.get(url) { if (bearerToken) header("Authorization", "Bearer $apiKey") }.bodyAsText()
        } catch (_: Throwable) {
            return null
        }
        val results = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val tmdbId = listOf("movie_results", "tv_results").firstNotNullOfOrNull { key ->
            (results[key] as? JsonArray)?.firstOrNull()?.let { entry ->
                ((entry as? JsonObject)?.get("id") as? JsonPrimitive)?.intOrNull?.toString()
            }
        } ?: return null
        store.saveTmdbId(itemId, tmdbId)
        return tmdbId
    }
}
