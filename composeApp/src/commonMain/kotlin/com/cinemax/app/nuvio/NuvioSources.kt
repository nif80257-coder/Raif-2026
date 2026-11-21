package com.cinemax.app.nuvio

import com.cinemax.app.addons.StreamOption
import com.cinemax.app.addons.StremioBehaviorHints
import com.cinemax.app.addons.StremioProxyHeaders
import com.cinemax.app.addons.StremioStream
import com.cinemax.app.plugins.PluginSecurityPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

/** What running every enabled provider produced, and which ones failed. */
data class NuvioSourceReport(
    val streams: List<StreamOption> = emptyList(),
    /** "<provider>: <reason>" for each provider that threw or timed out. */
    val failures: List<String> = emptyList()
)

/**
 * Runs every enabled Nuvio provider that supports the media type and converts its
 * streams into the same shape Stremio addons produce, labelled with the provider.
 * A provider that fails or times out never hides the others, and its reason is
 * reported so the interface can say why sources are missing.
 */
suspend fun nuvioSourcesFrom(
    client: NuvioProviderClient,
    runtime: NuvioScriptRuntime,
    providers: List<NuvioProviderEntry>,
    request: NuvioMediaRequest
): NuvioSourceReport = coroutineScope {
    val reports = providers
        .filter { it.supportsType(request.mediaType) }
        .map { provider ->
            async {
                try {
                    val source = client.loadScript(provider.repository, provider.descriptor.filename)
                    val streams = withTimeout(PluginSecurityPolicy.NETWORK_TIMEOUT_MILLIS) {
                        runtime.streams(source, request)
                    }
                    val options = streams.mapNotNull { stream ->
                        val url = stream.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        StreamOption(
                            addonId = provider.providerKey,
                            addonName = provider.displayName,
                            stream = StremioStream(
                                name = stream.name ?: stream.quality ?: provider.descriptor.name,
                                title = listOfNotNull(stream.title, stream.quality)
                                    .joinToString(" • ")
                                    .ifBlank { null },
                                url = url,
                                behaviorHints = StremioBehaviorHints(
                                    proxyHeaders = if (stream.headers.isEmpty()) {
                                        null
                                    } else {
                                        StremioProxyHeaders(request = stream.headers)
                                    }
                                )
                            )
                        )
                    }
                    NuvioSourceReport(streams = options)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    val reason = error.message?.takeIf { it.isNotBlank() } ?: "خطأ غير معروف"
                    NuvioSourceReport(failures = listOf("${provider.displayName}: ${reason.take(120)}"))
                }
            }
        }
        .awaitAll()
    NuvioSourceReport(
        streams = reports.flatMap { it.streams },
        failures = reports.flatMap { it.failures }
    )
}

/** The streams only, for callers that do not surface provider failures. */
suspend fun nuvioStreamsFrom(
    client: NuvioProviderClient,
    runtime: NuvioScriptRuntime,
    providers: List<NuvioProviderEntry>,
    request: NuvioMediaRequest
): List<StreamOption> = nuvioSourcesFrom(client, runtime, providers, request).streams
