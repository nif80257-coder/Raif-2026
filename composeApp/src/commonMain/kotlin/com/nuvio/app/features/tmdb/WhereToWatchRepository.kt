package com.nuvio.app.features.tmdb

import com.nuvio.app.features.addons.httpGetText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One service a title is available on, with the logo TMDB serves for it. */
data class WatchProvider(
    val name: String,
    val logoUrl: String?,
)

/** How a provider offers the title: subscription, free, with ads, rent or buy. */
enum class WatchProviderKind {
    Stream,
    Free,
    Ads,
    Rent,
    Buy,
}

data class WatchProviderGroup(
    val kind: WatchProviderKind,
    val providers: List<WatchProvider>,
)

/**
 * Where a title can be watched in the profile's region, read from TMDB's watch providers. The
 * link is TMDB's own "just watch" page, which lists the regional options in full.
 */
data class WhereToWatch(
    val link: String?,
    val groups: List<WatchProviderGroup>,
)

/**
 * TMDB's watch providers for one title and region, memoized per title. It asks TMDB only when the
 * integration is on, and resolves the IMDb id of the title to a TMDB id through [TmdbService].
 */
object WhereToWatchRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, WhereToWatch?>()
    private val mutex = Mutex()

    suspend fun load(
        videoId: String,
        type: String,
        imdbId: String?,
        language: String,
    ): WhereToWatch? {
        val settings = TmdbSettingsRepository.snapshot()
        if (!settings.enabled) return null

        val mediaType = TmdbService.normalizeMediaType(type)
        if (mediaType != "movie" && mediaType != "tv") return null

        val region = watchRegion(language)
        val cacheKey = "$videoId|$type|$region"
        mutex.withLock {
            cache[cacheKey]?.let { cached -> return cached }
        }

        val tmdbId = TmdbService.ensureTmdbId(videoId, mediaType, imdbId) ?: return null
        val url = buildTmdbUrl(
            endpoint = "$mediaType/$tmdbId/watch/providers",
            apiKey = TmdbSettingsRepository.effectiveApiKey(),
            query = mapOf("watch_region" to region),
        )
        val result = runCatching {
            val body = json.decodeFromString<WatchProvidersResponse>(httpGetText(url))
            (body.results[region] ?: body.results.values.firstOrNull())?.toWhereToWatch()
        }.getOrNull()

        mutex.withLock { cache[cacheKey] = result }
        return result
    }

    /** Forgets the memoized answers, for when the profile or its TMDB settings change. */
    fun clear() {
        cache.clear()
    }
}

/** The region TMDB is asked about: the language's own region when it names one, else a default. */
internal fun watchRegion(language: String?): String {
    val normalized = normalizeTmdbLanguage(language)
    val country = normalized
        .substringAfter("-", "")
        .uppercase()
        .takeIf { it.length == 2 }
    if (country != null) return country
    return defaultWatchRegions[normalized.substringBefore("-")] ?: "US"
}

private val defaultWatchRegions = mapOf(
    "ar" to "SA",
    "pt" to "PT",
    "es" to "ES",
    "fr" to "FR",
    "de" to "DE",
    "it" to "IT",
    "tr" to "TR",
    "ja" to "JP",
    "ko" to "KR",
    "hi" to "IN",
    "ru" to "RU",
    "nl" to "NL",
    "pl" to "PL",
    "sv" to "SE",
    "nb" to "NO",
    "no" to "NO",
    "da" to "DK",
    "fi" to "FI",
    "el" to "GR",
    "he" to "IL",
    "id" to "ID",
    "th" to "TH",
    "vi" to "VN",
    "uk" to "UA",
    "cs" to "CZ",
    "ro" to "RO",
    "hu" to "HU",
)

private fun WatchProviderRegion.toWhereToWatch(): WhereToWatch? {
    val groups = listOf(
        WatchProviderKind.Stream to flatrate,
        WatchProviderKind.Free to free,
        WatchProviderKind.Ads to ads,
        WatchProviderKind.Rent to rent,
        WatchProviderKind.Buy to buy,
    ).mapNotNull { (kind, items) ->
        val providers = items
            .filter { provider -> provider.name.isNotBlank() }
            .distinctBy { provider -> provider.name }
            .map { provider ->
                WatchProvider(
                    name = provider.name,
                    logoUrl = provider.logoPath?.let { path -> "$TMDB_LOGO_BASE$path" },
                )
            }
        if (providers.isEmpty()) null else WatchProviderGroup(kind = kind, providers = providers)
    }
    if (groups.isEmpty()) return null
    return WhereToWatch(link = link, groups = groups)
}

private const val TMDB_LOGO_BASE = "https://image.tmdb.org/t/p/w92"

@Serializable
private data class WatchProvidersResponse(
    val results: Map<String, WatchProviderRegion> = emptyMap(),
)

@Serializable
private data class WatchProviderRegion(
    val link: String? = null,
    val flatrate: List<WatchProviderDto> = emptyList(),
    val free: List<WatchProviderDto> = emptyList(),
    val ads: List<WatchProviderDto> = emptyList(),
    val rent: List<WatchProviderDto> = emptyList(),
    val buy: List<WatchProviderDto> = emptyList(),
)

@Serializable
private data class WatchProviderDto(
    @SerialName("provider_name") val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
)
