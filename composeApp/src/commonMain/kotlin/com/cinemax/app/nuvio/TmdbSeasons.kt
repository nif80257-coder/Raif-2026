package com.cinemax.app.nuvio

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Episode counts per season for a TMDB show, used to turn an absolute episode
 * number (as some addons number episodes) into a season/episode pair. Needs the
 * optional TMDB key the user already enters in settings; without it nothing is
 * fetched and the app keeps its tag/title heuristic.
 */
class TmdbSeasons(
    private val http: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seasonLengths(tmdbId: String, apiKey: String): Map<Int, Int>? {
        if (tmdbId.isBlank() || apiKey.isBlank()) return null
        val bearer = apiKey.length > 40
        val key = if (bearer) "" else "&api_key=$apiKey"
        val url = "https://api.themoviedb.org/3/tv/$tmdbId?language=ar$key"
        val body = try {
            http.get(url) { if (bearer) header("Authorization", "Bearer $apiKey") }.bodyAsText()
        } catch (_: Throwable) {
            return null
        }
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val seasons = root["seasons"] as? JsonArray ?: return null
        val counts = mutableMapOf<Int, Int>()
        for (entry in seasons) {
            val season = entry as? JsonObject ?: continue
            val number = season["season_number"]?.jsonPrimitive?.intOrNull ?: continue
            val count = season["episode_count"]?.jsonPrimitive?.intOrNull ?: continue
            // Season 0 is the specials, which are not part of an absolute count.
            if (number > 0 && count > 0) counts[number] = count
        }
        return counts.takeIf { it.isNotEmpty() }
    }
}
