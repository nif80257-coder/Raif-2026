package com.nuvio.app.features.streams

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.AddonFailureMemory
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddonSilentSourceMemory
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.withoutPausedAddons
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit

/**
 * Asks the sources about a title while the viewer is still reading its page.
 *
 * Opening the source sheet is the moment a title stops being a page and starts being a video, and
 * the sheet can only be as fast as the slowest add-on it asks. Nothing about that changes if the
 * question is asked a few seconds earlier — except that the answers are already waiting in
 * [StreamResponseCache] when the sheet opens.
 *
 * Deliberately narrow:
 * - **Films only.** A series has one source list per episode, and guessing which episode the viewer
 *   will pick would spend a whole fetch on a guess. A film has exactly one list.
 * - **Online only.** A phone with no connection would spend thirty timeouts learning nothing.
 * - **Once per title.** A fresh cache for the same film means the warm-up already happened, so it
 *   is not repeated within the cache's life; a failed attempt is not repeated for ten minutes.
 * - **Nothing of its own.** No UI state, no auto-play, no navigation: it only writes payloads, and
 *   [cancel] hands the fetch slots back the moment the sheet asks for them itself.
 */
internal object StreamPrefetcher {
    private val log = Logger.withTag("StreamPrefetcher")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** How long a failed or fruitless warm-up is remembered before it may be tried again. */
    private const val CooldownMs = 10 * 60 * 1000L

    /**
     * A title where every source answered with nothing is remembered for a day, not forever: the
     * sources may have had a bad evening, and asking them again tomorrow is worth one visit.
     */
    private const val FruitlessWarmAgeMs = 24 * 60 * 60 * 1000L

    /** How many warm-up attempts are remembered at once, so browsing does not grow a map. */
    private const val MaxRememberedAttempts = 32

    /** Content keys warmed in this session, with when they were last tried. */
    private val lastAttemptAtMs = mutableMapOf<String, Long>()

    private var job: Job? = null

    /**
     * Starts warming the source list for a film, if it is worth starting.
     *
     * Called from the details screen, which runs on the main thread, so [lastAttemptAtMs] is only
     * ever touched there and the coroutine below reads its own copy of the key.
     */
    fun warmMovie(type: String, titleId: String) {
        if (!type.equals("movie", ignoreCase = true) || titleId.isBlank()) return
        // A connection that is known to be down would only spend thirty timeouts learning nothing.
        // "Unknown" is let through: the first title opened after launch would otherwise never be
        // warmed, and an unanswered probe is not the same as a dead network.
        NetworkStatusRepository.ensureStarted()
        if (NetworkStatusRepository.uiState.value.isOfflineLike) return

        val profileId = ProfileRepository.activeProfileId
        val contentKey = StreamResponseCache.contentKey(type = type, videoId = titleId)
        val nowMs = EpisodeReleaseDatePlatform.nowEpochMs()
        val lastAttempt = lastAttemptAtMs[contentKey]
        if (lastAttempt != null && nowMs - lastAttempt < CooldownMs) return
        // A visit remembered for this film is already the answer, whether the sources returned
        // links or answered with nothing: asking them again would repeat it for thirty-three of
        // them, and a film where everything was silent used to ask again at every launch.
        if (StreamResponseCache.wasCheckedWithin(profileId, contentKey, FruitlessWarmAgeMs)) return

        lastAttemptAtMs[contentKey] = nowMs
        if (lastAttemptAtMs.size > MaxRememberedAttempts) {
            lastAttemptAtMs.entries
                .sortedByDescending { (_, attemptedAt) -> attemptedAt }
                .drop(MaxRememberedAttempts)
                .forEach { (key, _) -> lastAttemptAtMs.remove(key) }
        }
        job?.cancel()
        job = scope.launch {
            val warmed = runWarm(
                profileId = profileId,
                type = type,
                videoId = titleId,
                contentKey = contentKey,
            )
            // "Nothing here" is remembered too, so the next launch does not ask all of them again.
            if (warmed == 0) StreamResponseCache.markChecked(profileId, contentKey)
        }
    }

    /**
     * Stops a warm-up in progress. The sheet calls this before its own load so the two never ask
     * the same add-on twice at the same time.
     */
    fun cancel() {
        job?.cancel()
        job = null
    }

    private suspend fun runWarm(
        profileId: Int,
        type: String,
        videoId: String,
        contentKey: String,
    ): Int {
        val failureSnapshot = AddonFailureMemory.snapshot()
        val silentManifestUrls = AddonSilentSourceMemory.silentManifestUrls(profileId)
        val targets = AddonRepository.uiState.value.addons.enabledAddons()
            .withoutPausedAddons(
                includeAll = false,
                pausedManifestUrls = failureSnapshot.pausedManifestUrls,
                silentManifestUrls = silentManifestUrls,
            )
            .mapNotNull { addon ->
                val manifest = addon.manifest ?: return@mapNotNull null
                if (!manifest.supportsStream(type, videoId)) return@mapNotNull null
                InstalledStreamAddonTarget(
                    addonName = addon.displayTitle.ifBlank { manifest.name },
                    addonId = addon.streamAddonInstanceId(manifest.id),
                    manifestUrl = addon.manifestUrl,
                    manifest = manifest,
                )
            }
        if (targets.isEmpty()) return 0

        log.d { "Warming ${targets.size} sources for $contentKey" }
        val warmed = coroutineScope {
            targets.map { addon ->
                async {
                    val url = buildAddonResourceUrl(
                        manifestUrl = addon.manifest.transportUrl,
                        resource = "stream",
                        type = type,
                        id = videoId,
                    )
                    streamSourceFetchSemaphore.withPermit {
                        runCatchingUnlessCancelled {
                            fetchStreamSourcePayload(url = url, forceRefresh = false)
                        }.fold(
                            onSuccess = { payload ->
                                StreamResponseCache.save(
                                    profileId = profileId,
                                    contentKey = contentKey,
                                    addonId = addon.addonId,
                                    payload = payload,
                                )
                                true
                            },
                            onFailure = { failure ->
                                // Not recorded in the failure memory: this is an extra question,
                                // and counting it would let a warm-up pause a source the viewer
                                // never actually asked for.
                                log.d { "Warm fetch failed for ${addon.addonName}: ${failure.message}" }
                                false
                            },
                        )
                    }
                }
            }.awaitAll().count { it }
        }
        log.d { "Warmed $warmed of ${targets.size} sources for $contentKey" }
        return warmed
    }
}
