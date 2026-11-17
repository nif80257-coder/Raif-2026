package com.cinemax.app.addons

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Small, testable HTTP repository for remote Stremio-compatible addons.
 * It validates the manifest URL before making requests and keeps addon content as data.
 *
 * Manifests, first catalog pages, metadata and stream lists are cached briefly, so
 * opening the next episode, switching tabs or reopening a title answers instantly
 * instead of repeating a network round trip.
 */
class AddonRepository(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = false },
    clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val manifestCache = TtlCache<StremioManifest>(MANIFEST_TTL_MS, clock)
    private val catalogCache = TtlCache<List<StremioMetaPreview>>(CATALOG_TTL_MS, clock)
    private val metaCache = TtlCache<StremioMeta>(META_TTL_MS, clock)
    private val streamCache = TtlCache<List<StremioStream>>(STREAM_TTL_MS, clock)

    suspend fun loadManifest(input: String): StremioManifest {
        val url = StremioAddonClient.manifestUrl(input)
            ?: throw IllegalArgumentException("رابط الإضافة يجب أن يبدأ بـ https://")
        manifestCache.get(url)?.let { return it }
        val response = http.get(url)
        val manifest = json.decodeFromString<StremioManifest>(response.bodyAsText())
        manifestCache.put(url, manifest)
        return manifest
    }

    suspend fun loadCatalog(
        manifestUrl: String,
        type: String,
        catalogId: String,
        extra: Map<String, String> = emptyMap()
    ): List<StremioMetaPreview> {
        val base = validatedBase(manifestUrl)
        val url = StremioAddonClient.resourceUrl(base, "catalog", type, catalogId, extra)
        // Only the first, unfiltered page is cached: search and paging must stay live.
        val cacheable = extra.isEmpty()
        if (cacheable) catalogCache.get(url)?.let { return it }
        val response = http.get(url)
        val metas = json.decodeFromString<StremioCatalogResponse>(response.bodyAsText()).metas
        if (cacheable && metas.isNotEmpty()) catalogCache.put(url, metas)
        return metas
    }

    suspend fun searchCatalog(
        manifestUrl: String,
        type: String,
        catalogId: String,
        query: String,
        skip: Int = 0
    ): List<StremioMetaPreview> {
        require(query.isNotBlank()) { "Search query cannot be blank" }
        require(skip >= 0) { "Skip cannot be negative" }
        return loadCatalog(
            manifestUrl, type, catalogId,
            mapOf("search" to query.trim(), "skip" to skip.toString())
                .filterValues { it != "0" }
        )
    }

    suspend fun loadMeta(manifestUrl: String, type: String, mediaId: String): StremioMeta? {
        val url = StremioAddonClient.resourceUrl(validatedBase(manifestUrl), "meta", type, mediaId)
        metaCache.get(url)?.let { return it }
        val response = http.get(url)
        val meta = json.decodeFromString<StremioMetaResponse>(response.bodyAsText()).meta
        meta?.let { metaCache.put(url, it) }
        return meta
    }

    suspend fun loadStreams(manifestUrl: String, type: String, mediaId: String): List<StremioStream> {
        val url = StremioAddonClient.resourceUrl(validatedBase(manifestUrl), "stream", type, mediaId)
        streamCache.get(url)?.let { return it }
        val response = http.get(url)
        val streams = json.decodeFromString<StremioStreamResponse>(response.bodyAsText()).streams
        // An empty answer is not cached, so "أعد المحاولة" really asks again.
        if (streams.isNotEmpty()) streamCache.put(url, streams)
        return streams
    }

    /**
     * Subtitle addons are asked with the video id (the episode id for series) and,
     * when known, the video hash/filename that identify the exact file.
     */
    suspend fun loadSubtitles(
        manifestUrl: String,
        type: String,
        mediaId: String,
        extra: Map<String, String> = emptyMap()
    ): List<StremioSubtitle> {
        val url = StremioAddonClient.resourceUrl(validatedBase(manifestUrl), "subtitles", type, mediaId, extra)
        val response = http.get(url)
        return json.decodeFromString<StremioSubtitlesResponse>(response.bodyAsText()).subtitles
    }

    private fun validatedBase(manifestUrl: String): String =
        StremioAddonClient.manifestUrl(manifestUrl)
            ?: throw IllegalArgumentException("رابط الإضافة غير صالح")

    /** Drops every cached answer, used when the installed addons change. */
    fun clearCaches() {
        manifestCache.clear()
        catalogCache.clear()
        metaCache.clear()
        streamCache.clear()
    }

    private companion object {
        const val MANIFEST_TTL_MS = 10L * 60L * 1000L
        const val CATALOG_TTL_MS = 3L * 60L * 1000L
        const val META_TTL_MS = 5L * 60L * 1000L
        const val STREAM_TTL_MS = 60L * 1000L
    }
}
