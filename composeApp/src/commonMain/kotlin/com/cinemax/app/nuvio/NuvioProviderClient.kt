package com.cinemax.app.nuvio

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/** Reads Nuvio provider repositories and the provider scripts they point at. */
class NuvioProviderClient(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val scriptCache = mutableMapOf<String, String>()

    suspend fun loadRepository(input: String): InstalledNuvioRepository {
        val manifestUrl = NuvioUrls.manifestUrl(input)
            ?: throw IllegalArgumentException("رابط مستودع Nuvio يجب أن يبدأ بـ https://")
        val response = http.get(manifestUrl)
        val parsed = json.decodeFromString(NuvioProviderRepository.serializer(), response.bodyAsText())
        return InstalledNuvioRepository(
            manifestUrl = manifestUrl,
            name = parsed.name,
            version = parsed.version,
            description = parsed.description,
            scrapers = parsed.scrapers,
            installedAtEpochMillis = Clock.System.now().toEpochMilliseconds()
        )
    }

    suspend fun loadScript(repository: InstalledNuvioRepository, filename: String): String {
        val url = NuvioUrls.resolve(repository.manifestUrl, filename)
            ?: throw IllegalArgumentException("مسار سكربت غير صالح")
        scriptCache[url]?.let { return it }
        val script = http.get(url).bodyAsText()
        scriptCache[url] = script
        return script
    }

    /** Forgets downloaded scripts, used when a repository is added or removed. */
    fun clearScriptCache() {
        scriptCache.clear()
    }
}
