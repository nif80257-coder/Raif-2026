package com.nuvio.app.features.player

import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.StreamLinkCacheRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_no_subtitles_found
import org.jetbrains.compose.resources.getString

object SubtitleRepository {
    /** How long the file hash may delay the subtitle list; past it the list is fetched without one. */
    private const val VideoHashBudgetMs = 3_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _addonSubtitles = MutableStateFlow<List<AddonSubtitle>>(emptyList())
    val addonSubtitles: StateFlow<List<AddonSubtitle>> = _addonSubtitles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow<SubtitleLoadingProgress?>(null)
    internal val loadingProgress: StateFlow<SubtitleLoadingProgress?> = _loadingProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var activeFetchJob: Job? = null

    fun fetchAddonSubtitles(
        type: String,
        videoId: String,
        extras: SubtitleRequestExtras? = null,
    ) {
        activeFetchJob?.cancel()
        _loadingProgress.value = null
        activeFetchJob = scope.launch {
            executeSubtitleFetch(type, videoId, extras)
        }
    }

    /**
     * Like [fetchAddonSubtitles], but first computes the OpenSubtitles video hash
     * when the stream extras do not already contain one.
     *
     * [contentKey] is the key of this episode's cached playback link. Its stored hash is used
     * before any range request is made: the hash describes the file, so an episode fingerprinted
     * once never pays for it again, and the second visit to the subtitle sheet answers at once.
     */
    fun fetchAddonSubtitlesWithHashComputation(
        type: String,
        videoId: String,
        extras: SubtitleRequestExtras?,
        sourceUrl: String,
        sourceHeaders: Map<String, String>,
        contentKey: String? = null,
        onExtrasUpdated: (SubtitleRequestExtras) -> Unit,
    ) {
        activeFetchJob?.cancel()
        _loadingProgress.value = null
        activeFetchJob = scope.launch {
            _isLoading.value = true
            _error.value = null
            _addonSubtitles.value = cachedSubtitles(type, videoId, extras)

            val known = contentKey?.let { key -> StreamLinkCacheRepository.knownVideoHash(key) }
            val reusedExtras = extras.withKnownFileHash(
                knownHash = known?.first,
                knownSize = known?.second,
            )
            val reusedHash = reusedExtras?.videoHash
            val enrichedExtras: SubtitleRequestExtras? = if (!reusedHash.isNullOrBlank() && reusedExtras != null) {
                if (reusedExtras != extras) onExtrasUpdated(reusedExtras)
                reusedExtras
            } else {
                try {
                    // Two 64 KiB range requests, usually a few hundred milliseconds. Bounded
                    // anyway: a host that ignores ranges must not hold the subtitle list behind it.
                    val hashResult = withTimeoutOrNull(VideoHashBudgetMs) {
                        computeVideoHash(sourceUrl, sourceHeaders)
                    }
                    if (hashResult != null) {
                        val (hash, fileSize) = hashResult
                        val updated = (reusedExtras ?: SubtitleRequestExtras()).copy(
                            videoHash = hash,
                            videoSize = reusedExtras?.videoSize ?: fileSize,
                        )
                        onExtrasUpdated(updated)
                        updated
                    } else {
                        reusedExtras
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    reusedExtras
                }
            }

            executeSubtitleLoad(type, videoId, enrichedExtras)
        }
    }

    /** Adds a subtitle picked from a local SRT/VTT file and returns it for immediate selection. */
    fun addLocalSubtitle(displayName: String, content: String): AddonSubtitle? {
        val url = LocalSubtitleFileStore.write(displayName, content) ?: return null
        val subtitle = AddonSubtitle(
            id = "local:$url",
            url = url,
            language = localSubtitleLanguageCode(displayName),
            display = displayName,
        )
        _addonSubtitles.update { current -> listOf(subtitle) + current }
        return subtitle
    }

    fun clear() {
        activeFetchJob?.cancel()
        _loadingProgress.value = null
        _addonSubtitles.value = emptyList()
        _isLoading.value = false
        _error.value = null
    }

    /** The viewer's own languages first, then Arabic, whatever the add-ons answered in. */
    private fun preferredSubtitlePrefixes(): List<String> {
        PlayerSettingsRepository.ensureLoaded()
        val settings = PlayerSettingsRepository.uiState.value
        return preferredSubtitleLanguagePrefixes(
            preferredSubtitleLanguage = settings.preferredSubtitleLanguage,
            secondaryPreferredSubtitleLanguage = settings.secondaryPreferredSubtitleLanguage,
            deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
        )
    }

    /**
     * The tracks this episode had last time, so the subtitle sheet opens on a list instead of on a
     * spinner while five add-ons answer. The live requests replace it as they land.
     */
    private fun cachedSubtitles(
        type: String,
        videoId: String,
        extras: SubtitleRequestExtras?,
    ): List<AddonSubtitle> =
        SubtitleListCache.load(
            profileId = ProfileRepository.activeProfileId,
            contentKey = SubtitleListCache.contentKey(type, videoId, extras),
        )?.let { cached -> orderedAddonSubtitles(cached, preferredSubtitlePrefixes()) }.orEmpty()

    private suspend fun executeSubtitleFetch(
        type: String,
        videoId: String,
        extras: SubtitleRequestExtras?,
    ) {
        _isLoading.value = true
        _error.value = null
        _addonSubtitles.value = cachedSubtitles(type, videoId, extras)
        executeSubtitleLoad(type, videoId, extras)
    }

    private suspend fun executeSubtitleLoad(
        type: String,
        videoId: String,
        extras: SubtitleRequestExtras?,
    ) {
        val requests = addonSubtitleRequests(type, videoId, extras)
        if (requests.isEmpty()) {
            _isLoading.value = false
            return
        }

        _loadingProgress.value = SubtitleLoadingProgress(total = requests.size)
        val prefixes = preferredSubtitlePrefixes()
        loadAddonSubtitles(requests) { request, subtitles ->
            // Keep the viewer's own languages at the top as the answers stream in, instead of showing
            // whichever add-on replied first.
            _addonSubtitles.update { current -> orderedAddonSubtitles(current + subtitles, prefixes) }
            _loadingProgress.update { progress ->
                progress?.copy(completed = progress.completed + 1, addonName = request.addonName)
            }
        }
        _addonSubtitles.update { current -> orderedAddonSubtitles(current, prefixes) }

        val loaded = _addonSubtitles.value
        if (loaded.isNotEmpty()) {
            SubtitleListCache.save(
                profileId = ProfileRepository.activeProfileId,
                contentKey = SubtitleListCache.contentKey(type, videoId, extras),
                subtitles = loaded,
            )
        }

        if (loaded.isEmpty()) {
            _error.value = getString(Res.string.compose_player_no_subtitles_found)
        }
        _isLoading.value = false
    }
}

internal data class SubtitleLoadingProgress(
    val total: Int,
    val completed: Int = 0,
    val addonName: String? = null,
)

private fun localSubtitleLanguageCode(fileName: String): String {
    val tokens = fileName.lowercase().split('.', '_', '-', ' ')
    return when {
        tokens.any { it == "ar" || it == "ara" || it == "arabic" } -> "ar"
        tokens.any { it == "en" || it == "eng" || it == "english" } -> "en"
        else -> ""
    }
}
