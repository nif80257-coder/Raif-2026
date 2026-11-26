package com.cinemax.app.nuvio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Registry served by a Nuvio provider repository:
 * `{ "name": ..., "version": ..., "scrapers": [ ... ] }`.
 */
@Serializable
data class NuvioProviderRepository(
    val name: String,
    val version: String = "",
    val description: String? = null,
    val author: String? = null,
    val scrapers: List<NuvioScraperDescriptor> = emptyList()
)

/** One executable provider inside a repository. */
@Serializable
data class NuvioScraperDescriptor(
    val id: String,
    val name: String,
    val version: String = "",
    val filename: String,
    val description: String? = null,
    val author: String? = null,
    val supportedTypes: List<String> = listOf("movie", "tv"),
    val enabled: Boolean = true,
    val logo: String? = null,
    @SerialName("contentLanguage") val contentLanguage: List<String> = emptyList(),
    val hasSettings: Boolean = false,
    /** Platforms the author excluded, e.g. ["ios"] for a provider built for Hermes. */
    val disabledPlatforms: List<String> = emptyList()
)

/**
 * True when this provider can run on iOS. A provider whose author excluded iOS,
 * or one that says it was built for Android TV only, answers with nothing here:
 * enabling it would cost a fetch and a script run for an empty answer, and it
 * would claim a source that never plays.
 */
val NuvioScraperDescriptor.runsOnIos: Boolean
    get() = disabledPlatforms.none { it.equals("ios", ignoreCase = true) } &&
        description?.contains("android tv", ignoreCase = true) != true

/** A repository installed on the device, with its providers. */
@Serializable
data class InstalledNuvioRepository(
    val manifestUrl: String,
    val name: String,
    val version: String = "",
    val description: String? = null,
    val scrapers: List<NuvioScraperDescriptor> = emptyList(),
    val installedAtEpochMillis: Long
)

/** What a Nuvio provider receives: `getStreams(tmdbId, mediaType, season, episode)`. */
data class NuvioMediaRequest(
    val tmdbId: String,
    val mediaType: String,
    val season: Int? = null,
    val episode: Int? = null
)

/** A stream returned by a provider script. */
@Serializable
data class NuvioStream(
    val name: String? = null,
    val title: String? = null,
    val url: String? = null,
    val quality: String? = null,
    val size: String? = null,
    val provider: String? = null,
    /** Referer / User-Agent a host demands before it serves the file. */
    val headers: Map<String, String> = emptyMap()
)
