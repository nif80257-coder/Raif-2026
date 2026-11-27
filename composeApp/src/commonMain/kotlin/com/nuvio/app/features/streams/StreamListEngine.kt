package com.nuvio.app.features.streams

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.AddonFailureMemory
import com.nuvio.app.features.addons.AddonSilentSourceMemory
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginScraperHealth
import com.nuvio.app.features.plugins.pluginContentId
import com.nuvio.app.features.servers.ServerStreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlin.time.TimeSource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.addon_source_unreachable
import nuvio.composeapp.generated.resources.streams_failed_to_load_scraper
import org.jetbrains.compose.resources.getString

/**
 * Shared source-loading pipeline used by the source sheet and the player's source panels.
 * State publication, autoplay, cache hydration and presentation remain owned by each caller.
 */
internal object StreamListEngine {
    private val log = Logger.withTag("StreamListEngine")

    /**
     * How long one provider may take to answer a stream request.
     *
     * The runtime's own ceiling is a minute, which is right for the settings test but wrong here:
     * sixty providers share ten JS slots, so one that is still thinking after ten seconds keeps
     * nine others waiting for an answer the viewer has already given up on.
     */
    internal const val STREAM_PLUGIN_TIMEOUT_MS = 10_000L

    /**
     * How long an add-on's answer may stand in for a new request.
     *
     * Long enough to cover the sheet → player handoff and a return to the same episode, short
     * enough that a signed link inside the payload is still the one the source just minted.
     */
    internal const val STREAM_PAYLOAD_REUSE_WINDOW_MS = 2 * 60 * 1000L

    internal data class LoadBatch(
        val completions: Channel<StreamLoadCompletion>,
        val taskCount: Int,
    )

    fun startLoading(
        scope: CoroutineScope,
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
        forceRefresh: Boolean,
        /**
         * Whether an answer from the last couple of minutes may stand in for the request.
         *
         * The callers own this decision: a manual refresh and an auto-play run both refuse it, so a
         * link chosen without the viewer looking at the list is never one that may have expired.
         */
        reuseFreshPayloads: Boolean,
        streamAddons: List<InstalledStreamAddonTarget>,
        pluginProviderGroups: List<PluginProviderGroup>,
        serverSources: List<ServerStreamSource>,
        measuredSpeeds: Map<String, Long>,
        preferredRanksByAddonId: Map<String, Int?>,
        profileId: Int,
        contentKey: String,
    ): LoadBatch {
        val completions = Channel<StreamLoadCompletion>(capacity = Channel.BUFFERED)
        val taskCount = streamAddons.size + pluginProviderGroups.sumOf { it.scrapers.size } + serverSources.size

        fun publish(completion: StreamLoadCompletion) {
            if (completions.trySend(completion).isFailure) {
                log.d { "Ignoring late stream source completion after channel close" }
            }
        }

        /*
         * An answer that arrived seconds ago stands in for a new request: opening the player's
         * source panel after the sheet, or coming back to the same episode, used to ask every
         * add-on again and wait for the slowest one a second time.
         */
        val reusablePayloads = if (!reuseFreshPayloads || forceRefresh) {
            emptyMap()
        } else {
            StreamResponseCache.freshPayloads(
                profileId = profileId,
                contentKey = contentKey,
                maxAgeMs = STREAM_PAYLOAD_REUSE_WINDOW_MS,
            )
        }

        streamAddons.forEach { addon ->
            scope.launch {
                val group = runSourceTaskOrFallback(
                    block = {
                val reusedStreams = reusablePayloads[addon.addonId]?.let { payload ->
                    // A payload the parser cannot read is not an error to show: the live request
                    // below still has a chance to answer with something playable.
                    runCatching {
                        StreamParser.parse(
                            payload = payload,
                            addonName = addon.addonName,
                            addonId = addon.addonId,
                            addonLogo = addon.manifest.logoUrl,
                        )
                    }.getOrNull()
                }
                if (reusedStreams != null) {
                    return@runSourceTaskOrFallback AddonStreamGroup(
                        addonName = addon.addonName,
                        addonId = addon.addonId,
                        streams = reusedStreams.expandingPlaylistLinks()
                            .sortedForGroupedDisplay(preferredRanksByAddonId[addon.addonId]),
                        isLoading = false,
                    )
                }

                val url = buildAddonResourceUrl(
                    manifestUrl = addon.manifest.transportUrl,
                    resource = "stream",
                    type = type,
                    id = videoId,
                )
                log.d { "Fetching streams from ${url.substringAfter("://").substringBefore('/')}" }

                var elapsedMs = 0L
                val outcome = streamSourceFetchSemaphore.withPermit {
                    val requestStartedAt = TimeSource.Monotonic.markNow()
                    try {
                        runCatchingUnlessCancelled {
                            val payload = fetchStreamSourcePayload(
                                url = url,
                                forceRefresh = forceRefresh,
                                timeoutMs = streamSourceTimeoutMs(measuredSpeeds[addon.manifestUrl]),
                            )
                            StreamResponseCache.save(
                                profileId = profileId,
                                contentKey = contentKey,
                                addonId = addon.addonId,
                                payload = payload,
                            )
                            StreamParser.parse(
                                payload = payload,
                                addonName = addon.addonName,
                                addonId = addon.addonId,
                                addonLogo = addon.manifest.logoUrl,
                            )
                        }
                    } finally {
                        elapsedMs = requestStartedAt.elapsedNow().inWholeMilliseconds
                    }
                }

                if (outcome.isSuccess) {
                    val streams = outcome.getOrNull().orEmpty()
                    AddonSpeedMemory.recordSuccess(
                        profileId = profileId,
                        manifestUrl = addon.manifestUrl,
                        elapsedMs = elapsedMs,
                        resultCount = streams.size,
                    )
                    AddonFailureMemory.recordSuccess(addon.manifestUrl)
                    if (streams.isEmpty()) {
                        AddonSilentSourceMemory.recordEmptyAnswer(profileId, addon.manifestUrl)
                    } else {
                        AddonSilentSourceMemory.recordAnswerWithLinks(profileId, addon.manifestUrl)
                    }
                } else {
                    AddonSpeedMemory.recordFailure(profileId, addon.manifestUrl)
                    AddonFailureMemory.recordFailure(addon.manifestUrl, outcome.exceptionOrNull())
                }

                outcome.fold(
                    onSuccess = { streams ->
                        val expanded = streams.expandingPlaylistLinks()
                        AddonStreamGroup(
                            addonName = addon.addonName,
                            addonId = addon.addonId,
                            streams = expanded.sortedForGroupedDisplay(preferredRanksByAddonId[addon.addonId]),
                            isLoading = false,
                        )
                    },
                    onFailure = {
                        log.w { "Failed to fetch streams from ${addon.addonName}" }
                        AddonStreamGroup(
                            addonName = addon.addonName,
                            addonId = addon.addonId,
                            streams = emptyList(),
                            isLoading = false,
                            error = getString(Res.string.addon_source_unreachable),
                        )
                    },
                )
                    },
                    fallback = {
                        log.w { "Failed to process streams from ${addon.addonName}" }
                        AddonStreamGroup(
                            addonName = addon.addonName,
                            addonId = addon.addonId,
                            streams = emptyList(),
                            isLoading = false,
                            error = getString(Res.string.addon_source_unreachable),
                        )
                    },
                )
                publish(StreamLoadCompletion.Addon(group))
            }
        }

        serverSources.forEach { source ->
            scope.launch {
                streamSourceFetchSemaphore.withPermit {
                    val group = source.load()
                    publish(
                        StreamLoadCompletion.Addon(
                            if (group.error.isNullOrBlank()) group
                            else group.copy(error = getString(Res.string.addon_source_unreachable)),
                        ),
                    )
                }
            }
        }

        pluginProviderGroups.forEach { providerGroup ->
            providerGroup.scrapers.forEach { scraper ->
                scope.launch {
                    if (PluginScraperHealth.isRested(profileId, scraper.id)) {
                        // Three searches in a row with nothing to show, and this provider sits the
                        // next ten minutes out: the panel still counts it as answered, with nothing.
                        publish(
                            StreamLoadCompletion.PluginScraper(
                                addonId = providerGroup.addonId,
                                streams = emptyList(),
                                error = null,
                            ),
                        )
                        return@launch
                    }
                    val completion = runSourceTaskOrFallback(
                        block = {
                            streamSourceFetchSemaphore.withPermit {
                        val outcome = runCatchingUnlessCancelled {
                            PluginRepository.executeScraper(
                                scraper = scraper,
                                tmdbId = pluginContentId(
                                    videoId = videoId,
                                    season = season,
                                    episode = episode,
                                ),
                                mediaType = type,
                                season = season,
                                episode = episode,
                                // The sheet's budget, not the runtime's one-minute default.
                                timeoutMs = STREAM_PLUGIN_TIMEOUT_MS,
                            ).getOrThrow().map { result ->
                                result.toStreamItem(
                                    scraper = scraper,
                                    addonName = providerGroup.addonName,
                                    addonId = providerGroup.addonId,
                                )
                            }
                        }
                        if (outcome.isSuccess) {
                            PluginScraperHealth.recordSuccess(profileId, scraper.id)
                        } else {
                            PluginScraperHealth.recordFailure(profileId, scraper.id)
                        }
                        outcome.fold(
                            onSuccess = { streams ->
                                StreamLoadCompletion.PluginScraper(
                                    addonId = providerGroup.addonId,
                                    streams = streams,
                                    error = null,
                                )
                            },
                            onFailure = {
                                log.w { "Plugin scraper failed: ${scraper.name}" }
                                StreamLoadCompletion.PluginScraper(
                                    addonId = providerGroup.addonId,
                                    streams = emptyList(),
                                    error = getString(Res.string.streams_failed_to_load_scraper, scraper.name),
                                )
                            },
                        )
                            }
                        },
                        fallback = {
                            log.w { "Plugin scraper failed: ${scraper.name}" }
                            StreamLoadCompletion.PluginScraper(
                                addonId = providerGroup.addonId,
                                streams = emptyList(),
                                error = getString(Res.string.streams_failed_to_load_scraper, scraper.name),
                            )
                        },
                    )
                    publish(completion)
                }
            }
        }

        return LoadBatch(completions = completions, taskCount = taskCount)
    }
}
