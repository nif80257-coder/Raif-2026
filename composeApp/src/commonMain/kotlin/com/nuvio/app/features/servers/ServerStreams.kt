package com.nuvio.app.features.servers

import com.nuvio.app.features.streams.AddonStreamGroup
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.getString

/**
 * How long one media server may take before its row in the sheet counts as unreachable.
 *
 * The fetch semaphore hands out a fixed number of slots, and a server that accepts the connection
 * and then says nothing holds its slot for as long as the socket lives: every other source queues
 * behind an answer that is not coming. A server on the same network answers in well under a second,
 * and one across a wide-area link still answers inside fifteen.
 */
internal const val SERVER_STREAM_LOAD_TIMEOUT_MS = 15_000L

/** The `addonId` a media server's rows carry; the download retry path strips it back to an id. */
internal const val SERVER_SOURCE_ADDON_PREFIX = "server:"

internal class ServerStreamSource(
    val addonId: String,
    val addonName: String,
    val preferred: Boolean,
    private val loader: suspend () -> List<StreamItem>,
) {
    fun loadingGroup(): AddonStreamGroup =
        AddonStreamGroup(addonName = addonName, addonId = addonId, streams = emptyList(), isLoading = true)

    suspend fun load(): AddonStreamGroup = try {
        AddonStreamGroup(
            addonName = addonName,
            addonId = addonId,
            streams = withTimeout(SERVER_STREAM_LOAD_TIMEOUT_MS) { loader() },
            isLoading = false,
        )
    } catch (error: Throwable) {
        if (error is CancellationException) currentCoroutineContext().ensureActive()
        AddonStreamGroup(
            addonName = addonName,
            addonId = addonId,
            streams = emptyList(),
            isLoading = false,
            error = getString(error.serverFailure().message()),
        )
    }
}

internal object ServerStreams {
    fun isNativeRequest(videoId: String): Boolean = ServerItemRef.isServerId(videoId)

    fun canServe(type: String, videoId: String): Boolean {
        ServerItemRef.parse(videoId)?.let { ref -> return ServerRepository.connection(ref.connectionId)?.enabled == true }
        val connections = ServerRepository.enabledConnections().ifEmpty { return false }
        val request = ServerMatcher.shared.request(type, videoId, season = null, episode = null) ?: return false
        return connections.any { ServerMatcher.shared.supports(it, request.kind) }
    }

    fun isServerSourceId(addonId: String?): Boolean =
        addonId?.startsWith(SERVER_SOURCE_ADDON_PREFIX) == true

    fun sources(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
        forceRefresh: Boolean = false,
    ): List<ServerStreamSource> {
        ServerItemRef.parse(videoId)?.let { ref ->
            val connection = ServerRepository.connection(ref.connectionId) ?: return emptyList()
            return listOf(source(connection, preferred = true) { candidates(ref) })
        }
        val connections = ServerRepository.enabledConnections().ifEmpty { return emptyList() }
        val request = ServerMatcher.shared.request(type, videoId, season, episode) ?: return emptyList()
        return connections
            .filter { ServerMatcher.shared.supports(it, request.kind) }
            .map { connection ->
                source(connection, preferred = connection.useCatalogMetadata) {
                    ServerMatcher.shared.match(connection, request, forceRefresh).flatMap { candidates(it) }
                }
            }
    }

    private fun source(
        connection: ServerConnection,
        preferred: Boolean,
        loader: suspend () -> List<StreamItem>,
    ): ServerStreamSource =
        ServerStreamSource(
            addonId = groupId(connection.id),
            addonName = ServerRepository.sourceLabel(connection),
            preferred = preferred,
            loader = loader,
        )

    suspend fun candidates(ref: ServerItemRef): List<StreamItem> {
        val connection = ServerRepository.connection(ref.connectionId) ?: throw ServerException(ServerFailure.NOT_FOUND)
        val label = ServerRepository.sourceLabel(connection)
        return ServerRepository.call(ref.connectionId) { provider, session -> provider.candidates(session, ref.itemId) }
            .map { candidate ->
                StreamItem(
                    name = candidate.title,
                    description = candidate.description,
                    sourceName = label,
                    addonName = label,
                    addonId = groupId(connection.id),
                    behaviorHints = StreamBehaviorHints(
                        filename = candidate.filename,
                        videoSize = candidate.sizeBytes,
                    ),
                    serverTarget = candidate.target,
                )
            }
    }

    private fun groupId(connectionId: String) = "$SERVER_SOURCE_ADDON_PREFIX$connectionId"
}
