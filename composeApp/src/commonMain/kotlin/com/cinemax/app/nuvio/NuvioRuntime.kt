package com.cinemax.app.nuvio

import io.ktor.client.HttpClient

/**
 * Runs an installed Nuvio provider script.
 *
 * Nuvio providers are bundled CommonJS modules exporting
 * `getStreams(tmdbId, mediaType, season, episode)`. They are executable code, so
 * the implementation is platform specific and isolated from the shared UI.
 */
interface NuvioScriptRuntime {
    suspend fun streams(source: String, request: NuvioMediaRequest): List<NuvioStream>
}

expect fun createNuvioScriptRuntime(http: HttpClient): NuvioScriptRuntime

/** A provider the user enabled, ready to be executed. */
data class NuvioProviderEntry(
    val providerKey: String,
    val repositoryName: String,
    val repository: InstalledNuvioRepository,
    val descriptor: NuvioScraperDescriptor
) {
    val displayName: String
        get() = "Nuvio • ${descriptor.name}"

    fun supportsType(mediaType: String): Boolean =
        descriptor.supportedTypes.isEmpty() || descriptor.supportedTypes.any { type ->
            type.equals(mediaType, ignoreCase = true) ||
                (mediaType == "tv" && type.equals("series", ignoreCase = true))
        }
}
