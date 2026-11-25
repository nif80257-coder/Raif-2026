package com.nuvio.app.features.streams

import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.sanitizePlaybackHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** How much of the top link is read: enough for the host to start sending, small enough to be free. */
internal const val STREAM_WARM_BYTES = 64 * 1024

/**
 * The single link the viewer is most likely to tap: the first row of the list, when the player can
 * hand it to the engine as it stands.
 *
 * One candidate, never three. The rows below the first are what the viewer picks when the first is
 * wrong, so warming them spends a connection and a sliver of the viewer's data on a guess that is
 * usually not taken. The rows are already in the order the viewer sees them, so the walk below is
 * that order.
 */
internal fun List<AddonStreamGroup>.topWarmCandidate(): StreamItem? =
    asSequence()
        .flatMap { group -> group.streams.asSequence() }
        .firstOrNull { stream -> stream.isWarmableLink() }

/**
 * A link whose host can be contacted before the tap, which is only the case for a plain HTTP stream.
 *
 * Everything else is left alone on purpose:
 * - a torrent has no link yet, and announcing this device to its peers before the viewer accepted
 *   the consent dialog would leak the address the dialog is asking about;
 * - a row the app resolves itself (a debrid unlock, a YouTube id, a server preparation) has no
 *   address to reach until that resolve finishes, and warming it means paying for the resolve here;
 * - a row that only opens another app has nothing to play.
 */
internal fun StreamItem.isWarmableLink(): Boolean {
    val url = playableDirectUrl ?: return false
    if (!url.isWebUrl()) return false
    if (infoHash != null) return false
    if (clientResolve != null || serverTarget != null) return false
    if (youTubeIdToResolve != null || shouldOpenExternally) return false
    return true
}

/**
 * Asks the top link's host to start answering while the viewer is still reading the list.
 *
 * The app's own work is already off the critical path: the payloads are parsed off the main thread,
 * the cache is read once, each source is bounded by its own timeout. What is left before the first
 * frame is the host's own share — the name lookup, the connection, the redirects, the first byte —
 * and none of it can be done earlier than the list being known. This is that: one small request,
 * one link, started as soon as the list has a link the viewer can tap, so that by the time they do
 * tap, there is nothing left to wait for but the film itself.
 *
 * Deliberately narrow:
 * - **One candidate.** See [topWarmCandidate].
 * - **Cancelled by the tap.** [PlayerStreamsRepository] and [StreamsRepository] hand the fetch slots
 *   back when the sheet closes or a stream starts, and the warm-up is dropped with them.
 * - **Once per list.** The same link is not warmed twice in a session; a warm-up that asks the same
 *   host again after the viewer came back to the same title would only cost another connection.
 * - **Its own switch.** A viewer on a metered connection may not want the app to touch a stream host
 *   before they chose it, so the behaviour is theirs to turn off.
 */
internal object StreamLinkWarmer {
    private val log = Logger.withTag("StreamLinkWarmer")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The link last asked for, so publishing the same list again does not ask again. */
    private var requestedKey: String? = null
    private var job: Job? = null

    /** Starts warming the top link of [groups], if there is one worth warming. */
    fun warmTopLink(groups: List<AddonStreamGroup>) {
        if (!PlayerSettingsRepository.uiState.value.streamWarmTopLinkEnabled) return
        val candidate = groups.topWarmCandidate() ?: return
        val url = candidate.playableDirectUrl ?: return
        val headers = sanitizePlaybackHeaders(candidate.behaviorHints.proxyHeaders?.request)
        val key = warmKey(url, headers)
        if (key == requestedKey) return

        requestedKey = key
        job?.cancel()
        job = scope.launch {
            val startedAt = TimeSource.Monotonic.markNow()
            val reached = runCatchingUnlessCancelled {
                warmStreamLinkConnection(url = url, headers = headers, maxBytes = STREAM_WARM_BYTES)
            }.getOrDefault(false)
            if (reached) {
                log.d { "Top link answered in ${startedAt.elapsedNow().inWholeMilliseconds} ms" }
            } else {
                log.d { "Top link did not answer before the tap" }
            }
        }
    }

    /**
     * Drops a warm-up in progress: the sheet is closed or a stream started, so the connection has
     * no reason to stay open. The next list is warmed again from scratch.
     */
    fun cancel() {
        job?.cancel()
        job = null
        requestedKey = null
    }

    private fun warmKey(url: String, headers: Map<String, String>): String =
        buildString(url.length + 32) {
            append(url)
            headers.entries
                .map { (name, value) -> name.lowercase() to value }
                .sortedBy { (name, _) -> name }
                .forEach { (name, value) ->
                    append('\n')
                    append(name)
                    append('=')
                    append(value)
                }
        }
}
