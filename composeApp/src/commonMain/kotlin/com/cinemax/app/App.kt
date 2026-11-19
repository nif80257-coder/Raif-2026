package com.cinemax.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cinemax.app.addons.AddonCapabilities
import com.cinemax.app.addons.AddonDirectory
import com.cinemax.app.addons.AddonDirectoryEntry
import com.cinemax.app.addons.AddonRepository
import com.cinemax.app.addons.AddonSettingsStore
import com.cinemax.app.addons.CatalogEntry
import com.cinemax.app.addons.InstalledAddon
import com.cinemax.app.addons.StreamOption
import com.cinemax.app.addons.StremioMeta
import com.cinemax.app.addons.StremioMetaPreview
import com.cinemax.app.addons.StremioManifest
import com.cinemax.app.addons.StremioVideo
import com.cinemax.app.addons.appendCatalogPage
import com.cinemax.app.addons.catalogPageExtra
import com.cinemax.app.addons.createCinemaxHttpClient
import com.cinemax.app.addons.searchAllAddonsStreaming
import com.cinemax.app.archive.ARCHIVE_GENRES
import com.cinemax.app.archive.ARCHIVE_PROVIDER_NAME
import com.cinemax.app.archive.ARCHIVE_SORTS
import com.cinemax.app.archive.archiveSortValue
import com.cinemax.app.core.BADGE_PACKS
import com.cinemax.app.core.BadgeFilter
import com.cinemax.app.core.matchesLibraryQuery
import com.cinemax.app.core.parseBadgePack
import com.cinemax.app.archive.ArchiveClient
import com.cinemax.app.archive.archiveIdentifier
import com.cinemax.app.archive.isArchiveItemId
import com.cinemax.app.core.CinemaxRoadmap
import com.cinemax.app.core.LibrarySort
import com.cinemax.app.core.EmbeddedAudioTrack
import com.cinemax.app.core.EmbeddedSubtitleTrack
import com.cinemax.app.core.SubtitleCue
import com.cinemax.app.core.SuggestedSource
import com.cinemax.app.core.activeCue
import com.cinemax.app.core.appVersionLabel
import com.cinemax.app.core.embeddedAudioDetail
import com.cinemax.app.core.embeddedAudioLabel
import com.cinemax.app.core.embeddedSubtitleDetail
import com.cinemax.app.core.embeddedSubtitleLabel
import com.cinemax.app.core.episodeLabel
import com.cinemax.app.core.languageIndex
import com.cinemax.app.core.nextEpisode
import com.cinemax.app.core.parseSubtitles
import com.cinemax.app.core.parseSuggestedSources
import com.cinemax.app.core.posterStatusById
import com.cinemax.app.core.nextUntriedSource
import com.cinemax.app.core.rankSearchResults
import com.cinemax.app.core.rankedSources
import com.cinemax.app.core.dedupeSources
import com.cinemax.app.core.SourceStats
import com.cinemax.app.core.bestSource
import com.cinemax.app.core.mergeSources
import com.cinemax.app.core.absoluteToSeasonEpisode
import com.cinemax.app.core.canonicalMediaKey
import com.cinemax.app.core.distinctTitles
import com.cinemax.app.core.episodeNumber
import com.cinemax.app.core.isFailedRecently
import com.cinemax.app.core.PUBLIC_DOMAIN_FILMS
import com.cinemax.app.core.PUBLIC_DOMAIN_PROVIDER
import com.cinemax.app.core.isPublicDomainId
import com.cinemax.app.core.publicDomainImdbId
import com.cinemax.app.core.publicDomainMeta
import com.cinemax.app.core.publicDomainPreviews
import com.cinemax.app.core.publicDomainStreams
import com.cinemax.app.core.seasonLengthsOf
import com.cinemax.app.core.sameMedia
import com.cinemax.app.core.streamBadges
import com.cinemax.app.core.streamFingerprint
import com.cinemax.app.core.sortLibrary
import com.cinemax.app.core.unsupportedVideoContainer
import com.cinemax.app.nuvio.InstalledNuvioRepository
import com.cinemax.app.nuvio.NuvioMediaIds
import com.cinemax.app.nuvio.NuvioProviderClient
import com.cinemax.app.nuvio.NuvioProviderEntry
import com.cinemax.app.nuvio.NuvioProviderStore
import com.cinemax.app.nuvio.TmdbIdResolver
import com.cinemax.app.nuvio.TmdbSeasons
import com.cinemax.app.nuvio.createNuvioHttpClient
import com.cinemax.app.nuvio.createNuvioScriptRuntime
import com.cinemax.app.nuvio.nuvioSourcesFrom
import com.cinemax.app.ui.CinemaxCatalogScreen
import com.cinemax.app.ui.cinemaxTypography
import com.cinemax.app.ui.InAppBrowser
import com.cinemax.app.ui.CinemaxFilterChip
import com.cinemax.app.ui.CinemaxDetailScreen
import com.cinemax.app.ui.CinemaxHomeScreen
import com.cinemax.app.ui.CinemaxLibraryScreen
import com.cinemax.app.ui.CinemaxPlayerScreen
import com.cinemax.app.ui.CinemaxRoadmapScreen
import com.cinemax.app.ui.CinemaxSearchScreen
import com.cinemax.app.ui.CinemaxSettingsScreen
import com.cinemax.app.ui.CinemaxShelf
import com.cinemax.app.ui.CinemaxTabBar
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

private val Ink = Color(0xFF090B12)
private val Panel = Color(0xFF171B28)
private val Accent = Color(0xFFC173E6)

/** How many of an addon's catalogs the home feed turns into shelves. */
private const val MAX_HOME_SHELVES = 5

/** A subtitle the user can enable in the player, from a stream or a subtitle addon. */
private data class SubtitleChoice(
    val lang: String,
    val label: String,
    val source: String,
    val url: String
)

@Composable
fun App() {
    val http = remember { createCinemaxHttpClient() }
    val repository = remember { AddonRepository(http) }
    val addonDirectory = remember { AddonDirectory(http) }
    val archive = remember { ArchiveClient(http) }
    val addonStore = remember { AddonSettingsStore() }
    val nuvioHttp = remember { createNuvioHttpClient() }
    val nuvioStore = remember { NuvioProviderStore() }
    val nuvioClient = remember { NuvioProviderClient(nuvioHttp) }
    val nuvioRuntime = remember { createNuvioScriptRuntime(nuvioHttp) }
    val tmdbResolver = remember { TmdbIdResolver(nuvioHttp, nuvioStore) }
    val nuvioRepositories = remember { mutableStateListOf<InstalledNuvioRepository>().apply { addAll(nuvioStore.loadRepositories()) } }
    val nuvioEnabled = remember { mutableStateListOf<String>().apply { addAll(nuvioStore.loadEnabledProviders().toList()) } }
    var showNuvioDialog by remember { mutableStateOf(false) }
    var nuvioRepoInput by remember { mutableStateOf("") }
    var nuvioBusy by remember { mutableStateOf(false) }
    var tmdbKey by remember { mutableStateOf(nuvioStore.loadTmdbApiKey()) }
    val nuvioProviders = nuvioRepositories.flatMap { repository ->
        repository.scrapers
            .filter { scraper ->
                scraper.enabled && "${repository.manifestUrl}#${scraper.id}" in nuvioEnabled
            }
            .map { scraper ->
                NuvioProviderEntry("${repository.manifestUrl}#${scraper.id}", repository.name, repository, scraper)
            }
    }
    val nuvioSignature = nuvioProviders.joinToString("|") { it.providerKey }
    val preferencesStore = remember { CinemaxPreferencesStore() }
    val favoritesStore = remember { FavoritesStore() }
    val favoriteItems = remember { mutableStateListOf<StremioMetaPreview>().apply { addAll(favoritesStore.load()) } }
    val favoriteIds = favoriteItems.map { it.id }.toSet()
    val watchProgressStore = remember { WatchProgressStore() }
    val lastSourceStore = remember { PlaybackSourceStore() }
    val searchHistoryStore = remember { SearchHistoryStore() }
    val browseStore = remember { BrowsePreferencesStore() }
    val badgeStore = remember { BadgePackStore() }
    var badgePackUrl by remember { mutableStateOf(badgeStore.loadPackUrl()) }
    var badgeFilters by remember { mutableStateOf(parseBadgePack(badgeStore.loadRawPack() ?: "")) }
    var searchHistory by remember { mutableStateOf(searchHistoryStore.load()) }
    var searchType by remember { mutableStateOf(browseStore.loadSearchType() ?: "movie") }
    var lastPlayedUrl by remember { mutableStateOf<String?>(null) }
    var providerIssues by remember { mutableStateOf<List<String>>(emptyList()) }
    var tonightPick by remember { mutableStateOf<StremioMetaPreview?>(null) }
    // The last opened title, kept so the detail screen can fade out after closing.
    var lastDetailItem by remember { mutableStateOf<StremioMetaPreview?>(null) }
    val homeShelves = remember { mutableStateListOf<CinemaxShelf>() }
    var archiveItems by remember { mutableStateOf<List<StremioMetaPreview>>(emptyList()) }
    var archiveError by remember { mutableStateOf<String?>(null) }
    // The shelf opened as a full grid ("المزيد"), and the pages appended to it.
    val browseItems = remember { mutableStateListOf<StremioMetaPreview>() }
    var browseShelf by remember { mutableStateOf<CinemaxShelf?>(null) }
    var browseLoadingMore by remember { mutableStateOf(false) }
    var browseExhausted by remember { mutableStateOf(false) }
    var browseArchivePage by remember { mutableStateOf(1) }
    var browseSort by remember { mutableStateOf<String?>(null) }
    var browseGenre by remember { mutableStateOf<String?>(null) }
    var libraryFilter by remember { mutableStateOf("all") }
    var librarySort by remember {
        mutableStateOf(
            runCatching { LibrarySort.valueOf(browseStore.loadLibrarySort() ?: "") }
                .getOrDefault(LibrarySort.Recent)
        )
    }
    var libraryQuery by remember { mutableStateOf("") }
    val watchProgressItems = remember { mutableStateListOf<WatchProgress>().apply { addAll(watchProgressStore.load()) } }
    val watchedStore = remember { WatchedEpisodesStore() }
    var watchedEpisodes by remember {
        mutableStateOf(runCatching { watchedStore.load() }.getOrDefault(emptySet()))
    }
    // The progress bar and the saved star every poster draws, keyed by title id.
    val posterStatuses = posterStatusById(watchProgressItems.toList(), favoriteIds)
    var preferences by remember {
        // A preference written by an older build must never stop the app from starting.
        mutableStateOf(runCatching { preferencesStore.load() }.getOrDefault(CinemaxPreferences()))
    }
    var showRoadmap by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var playerControls by remember { mutableStateOf<CinemaxPlayerControls?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var hud by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var subtitleDelayMs by remember { mutableStateOf(0L) }
    var playerDurationMs by remember { mutableStateOf(0L) }
    var playerStarted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val addons = remember { mutableStateListOf<InstalledAddon>().apply { addAll(addonStore.load()) } }
    val catalogItems = remember { mutableStateListOf<StremioMetaPreview>() }
    val searchResults = remember { mutableStateListOf<CatalogEntry>() }
    val availableCatalogs = remember { mutableStateListOf<com.cinemax.app.addons.StremioCatalog>() }
    val streamOptions = remember { mutableStateListOf<StreamOption>() }
    /** Bumped by "أعد المحاولة" in the source sheet, to fetch the streams again. */
    var streamRefresh by remember { mutableStateOf(0) }
    var activeAddon by remember { mutableStateOf(addons.lastOrNull()) }
    var selectedItem by remember { mutableStateOf<StremioMetaPreview?>(null) }
    var selectedMeta by remember { mutableStateOf<StremioMeta?>(null) }
    var selectedVideo by remember { mutableStateOf<StremioVideo?>(null) }
    var activeSeason by remember { mutableStateOf<Int?>(null) }
    var pendingVideoId by remember { mutableStateOf<String?>(null) }
    var lastOpenedItemId by remember { mutableStateOf<String?>(null) }
    var metaResolved by remember { mutableStateOf(false) }
    var activeCatalogType by remember { mutableStateOf<String?>(null) }
    var activeCatalogId by remember { mutableStateOf<String?>(null) }
    var activeCatalogSupportsSearch by remember { mutableStateOf(false) }
    var activeCatalogSupportsSkip by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    // The search in flight and the number of the current one, so a newer query
    // cancels the older search and that search cannot overwrite its results.
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchGeneration by remember { mutableStateOf(0) }
    var lastSearchQuery by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addonUrl by remember { mutableStateOf("") }
    // The exported source files ship in the app bundle; they are merged and
    // de-duplicated once at launch and offered for one-tap install.
    var suggestedSources by remember { mutableStateOf<List<SuggestedSource>>(emptyList()) }
    var suggestedQuery by remember { mutableStateOf("") }
    var installingSourceUrl by remember { mutableStateOf<String?>(null) }
    // The public addon collection, fetched only when the viewer asks for it.
    var directoryEntries by remember { mutableStateOf<List<AddonDirectoryEntry>>(emptyList()) }
    var directoryLoading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("أضف إضافة Stremio متوافقة للبدء") }
    var loading by remember { mutableStateOf(false) }
    var loadingMeta by remember { mutableStateOf(false) }
    var loadingStreams by remember { mutableStateOf(false) }
    var playbackUrl by remember { mutableStateOf<String?>(null) }
    var playingItem by remember { mutableStateOf<StremioMetaPreview?>(null) }
    var playingVideo by remember { mutableStateOf<StremioVideo?>(null) }
    var playingStream by remember { mutableStateOf<StreamOption?>(null) }
    var autoplayQueued by remember { mutableStateOf(false) }
    val subtitleOptions = remember { mutableStateListOf<SubtitleChoice>() }
    var selectedSubtitleUrl by remember { mutableStateOf<String?>(null) }
    var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleBusy by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var playerPositionMs by remember { mutableStateOf(0L) }
    var playerMessage by remember { mutableStateOf<String?>(null) }
    // Each link's track record, so a source that just failed drops below the
    // rest, and the addons that offer each file, so a duplicate is listed once.
    val statsStore = remember { SourceStatsStore() }
    var sourceStats by remember {
        mutableStateOf(runCatching { statsStore.loadAll() }.getOrDefault(emptyMap()))
    }
    var sourceProviders by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val preferredSourceStore = remember { PreferredSourceStore() }
    var pinnedSourceUrl by remember { mutableStateOf<String?>(null) }
    val sourcePreferenceStore = remember { SourcePreferenceStore() }
    var sourcePreferences by remember {
        mutableStateOf(runCatching { sourcePreferenceStore.loadAll() }.getOrDefault(emptyMap()))
    }
    val titleAliasStore = remember { TitleAliasStore() }
    /** Every id this title has been seen under, so sources gather from all of them. */
    var aliasIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val tmdbSeasons = remember { TmdbSeasons(nuvioHttp) }
    /** S02E05-style labels derived from TMDB season lists (needs a TMDB key). */
    var episodeLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var playbackFingerprint by remember { mutableStateOf<String?>(null) }
    var playbackStartedAtMs by remember { mutableStateOf(0L) }
    var successRecorded by remember { mutableStateOf(false) }
    /** True once MPV failed for this link and the system player is being tried. */
    var usingSystemFallback by remember { mutableStateOf(false) }
    // The source picker inside the player and the links already tried for the
    // title being watched, so a failing source moves on instead of looping.
    var showPlayerSources by remember { mutableStateOf(false) }
    // The addon configure page opened inside the app, and the address it is on.
    var configBrowserUrl by remember { mutableStateOf<String?>(null) }
    var configBrowserCurrent by remember { mutableStateOf<String?>(null) }
    // How many playable sources each episode has, filled on demand ("كم مصدرًا؟")
    // so the app never fires one request per episode just to fill the list.
    var episodeSourceCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var checkingEpisodeId by remember { mutableStateOf<String?>(null) }
    var attemptedSourceUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    // A failed link or a source switch gets a line of its own: the reset that runs
    // whenever the player reloads clears the player's own message, not this one.
    var sourceNotice by remember { mutableStateOf<String?>(null) }
    // Result of the manual source check, keyed by addon id: true means the
    // addon's manifest answered.
    var addonHealth by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var addonCheckRunning by remember { mutableStateOf(false) }
    /** Addons whose manifest advertises a newer version than the stored one. */
    var addonUpdates by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val embeddedSubtitleTracks = remember { mutableStateListOf<EmbeddedSubtitleTrack>() }
    var selectedEmbeddedTrackId by remember { mutableStateOf<Long?>(null) }
    // Set once a subtitle choice was made, so a late track list cannot override it.
    var userChoseSubtitle by remember { mutableStateOf(false) }
    // The audio tracks packed inside the file, and the one being played.
    val embeddedAudioTracks = remember { mutableStateListOf<EmbeddedAudioTrack>() }
    var selectedAudioTrackId by remember { mutableStateOf<Long?>(null) }
    var showAudioPicker by remember { mutableStateOf(false) }
    val addonSignature = addons.joinToString("|") { it.manifestUrl }

    // The built-in public-domain library, loaded once so the app has content even
    // with no addon installed. A failure is kept so home can offer a retry.
    fun loadArchive() {
        scope.launch {
            archiveError = null
            try {
                val found = archive.popular(rows = 60)
                if (found.isNotEmpty()) archiveItems = found
                else archiveError = "لم تُرجع المكتبة الحرة أي فيلم الآن"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                archiveError = error.message ?: "تعذر الاتصال بالمكتبة الحرة"
            }
        }
    }

    LaunchedEffect(Unit) { loadArchive() }

    // Both exported source lists are read once, merged into one list with a
    // single entry per manifest URL, and stripped of anything the app cannot
    // install (a plain http address, say).
    LaunchedEffect(Unit) {
        val exported = listOf("raif-sources", "raif2-sources")
            .mapNotNull { readBundledText(it, "json") }
            .map { parseSuggestedSources(it) }
        suggestedSources = dedupeSources(exported)
            .mapNotNull { source ->
                val normalized = com.cinemax.app.addons.StremioAddonClient.manifestUrl(source.url)
                    ?: return@mapNotNull null
                SuggestedSource(name = source.name, url = normalized)
            }
            .distinctBy { it.url.lowercase() }
    }

    /** Downloads a Fusion badge pack and caches it so badges survive a cold start. */
    fun loadBadges(url: String) {
        scope.launch {
            try {
                val raw = http.get(url).bodyAsText()
                val filters = parseBadgePack(raw)
                if (filters.isNotEmpty()) {
                    badgeFilters = filters
                    badgeStore.saveRawPack(raw)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر تحميل شارات المصادر: ${error.message ?: "خطأ غير معروف"}"
            }
        }
    }

    LaunchedEffect(badgePackUrl) { loadBadges(badgePackUrl) }

    // Metadata may come from any installed addon that declares the `meta`
    // resource, and a series episode is chosen before its sources are requested.
    LaunchedEffect(selectedItem?.id, pendingVideoId, addonSignature) {
        val item = selectedItem
        if (item == null) {
            selectedMeta = null
            metaResolved = false
            selectedVideo = null
            activeSeason = null
            aliasIds = emptyList()
            loadingMeta = false
            return@LaunchedEffect
        }
        if (lastOpenedItemId != item.id) {
            lastOpenedItemId = item.id
            selectedMeta = null
            selectedVideo = null
            activeSeason = null
        }
        metaResolved = false
        loadingMeta = true
        val meta = try {
            if (isArchiveItemId(item.id)) {
                archive.meta(archiveIdentifier(item.id))
            } else if (isPublicDomainId(item.id)) {
                publicDomainMeta(item.id)
            } else {
                metaFrom(repository, addons.toList(), item.type, item.id)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            status = "تعذر جلب تفاصيل العنصر: ${error.message ?: "خطأ غير معروف"}"
            null
        }
        selectedMeta = meta
        // Remember this id for the title, so a second catalog's id can be asked too.
        aliasIds = runCatching { titleAliasStore.add(canonicalMediaKey(item), item.id) }
            .getOrDefault(listOf(item.id))
        val videos = meta?.videos.orEmpty()
        // Absolute episode numbers become S02E05 when a TMDB key says how long each
        // season is; otherwise the tag/title fallback stays in charge.
        episodeLabels = emptyMap()
        val key = tmdbKey
        if (item.type == "series" && key.isNotBlank() && videos.isNotEmpty() &&
            videos.all { it.season == null }
        ) {
            try {
                val tmdbId = tmdbResolver.request(item.id, item.type, null, null).tmdbId
                val counts = tmdbSeasons.seasonLengths(tmdbId, key)
                if (counts != null) {
                    val lengths = seasonLengthsOf(counts)
                    val labels = videos.mapNotNull { video ->
                        val absolute = episodeNumber(video) ?: return@mapNotNull null
                        val pair = absoluteToSeasonEpisode(lengths, absolute) ?: return@mapNotNull null
                        video.id to "الموسم ${pair.season} • الحلقة ${pair.episode}"
                    }.toMap()
                    if (labels.isNotEmpty()) episodeLabels = labels
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                episodeLabels = emptyMap()
            }
        }
        val nextVideo = videos.firstOrNull { it.id == pendingVideoId }
            ?: videos.firstOrNull { it.id == selectedVideo?.id }
            ?: videos.firstOrNull()
        if (nextVideo?.id != selectedVideo?.id) selectedVideo = nextVideo
        if (nextVideo?.season != activeSeason) activeSeason = nextVideo?.season
        // The pinned source and the last link belong to the episode being looked
        // at, not to the series as a whole.
        val sourceKey = nextVideo?.id ?: item.id
        lastPlayedUrl = lastSourceStore.load(sourceKey)
        pinnedSourceUrl = preferredSourceStore.load(sourceKey)
        if (meta != null) {
            val index = favoriteItems.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                favoriteItems[index] = item.copy(
                    name = meta.name, poster = meta.poster ?: item.poster,
                    background = meta.background ?: item.background,
                    description = meta.description ?: item.description,
                    releaseInfo = meta.releaseInfo ?: item.releaseInfo,
                    imdbRating = meta.imdbRating ?: item.imdbRating
                )
                favoritesStore.save(favoriteItems.toList())
            }
        }
        loadingMeta = false
        metaResolved = true
    }

    // Sources are aggregated from every installed addon that serves streams for
    // the item's type, so a catalog addon and a stream addon work together.
    LaunchedEffect(selectedItem?.id, selectedVideo?.id, metaResolved, addonSignature, nuvioSignature, streamRefresh) {
        val item = selectedItem
        if (item == null || !metaResolved) {
            streamOptions.clear()
            sourceProviders = emptyMap()
            loadingStreams = false
            return@LaunchedEffect
        }
        val videos = selectedMeta?.videos.orEmpty()
        val episode = selectedVideo
        if (videos.isNotEmpty() && episode == null) {
            streamOptions.clear()
            loadingStreams = false
            status = "اختر حلقة لعرض مصادر المشاهدة"
            return@LaunchedEffect
        }
        val mediaId = episode?.id ?: item.id
        loadingStreams = true
        streamOptions.clear()
        try {
            val stremioSources = when {
                isArchiveItemId(item.id) -> archive.streams(archiveIdentifier(item.id))
                    .map { StreamOption(item.id, ARCHIVE_PROVIDER_NAME, it) }
                // A public-domain film opens with its own checked file, and the
                // addons are still asked by the film's IMDb id so a better copy or
                // a subtitle pack can join it.
                isPublicDomainId(item.id) -> publicDomainStreams(item.id)
                    .map { StreamOption(item.id, PUBLIC_DOMAIN_PROVIDER, it) } +
                    streamsFrom(repository, addons.toList(), item.type, publicDomainImdbId(item.id))
                // A film seen under more than one catalog id gathers the sources
                // of every id, which is the practical half of "one page per title".
                item.type == "movie" && preferences.mergeCatalogSources && aliasIds.size > 1 -> aliasIds
                    .distinct()
                    .take(3)
                    .flatMap { id -> streamsFrom(repository, addons.toList(), item.type, id) }
                else -> streamsFrom(repository, addons.toList(), item.type, mediaId)
            }
            var failures: List<String> = emptyList()
            val nuvioSources = if (nuvioProviders.isEmpty()) {
                emptyList()
            } else {
                val tmdbRequest = try {
                    tmdbResolver.request(item.id, item.type, episode?.season, episode?.episode)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    null
                }
                if (tmdbRequest == null) {
                    failures = listOf("TMDB: تعذر تحويل معرّف العنصر (أضف مفتاح TMDB من الإعدادات)")
                    emptyList()
                } else {
                    val report = nuvioSourcesFrom(nuvioClient, nuvioRuntime, nuvioProviders, tmdbRequest)
                    failures = report.failures
                    report.streams
                }
            }
            providerIssues = failures
            // One row per file: the addons that offer the same link are merged,
            // so a duplicate appears once with every provider named.
            val found = stremioSources + nuvioSources
            val merged = mergeSources(found)
            streamOptions.addAll(merged.map { it.option })
            sourceProviders = merged.associate { it.fingerprint to it.providers }
            // The last link is remembered by file identity, so a link whose token
            // changed still points at the source that actually played.
            val storedFingerprint = lastSourceStore.loadFingerprint(mediaId)
            val storedUrl = lastSourceStore.load(mediaId)
            lastPlayedUrl = when {
                storedUrl != null && merged.any { it.option.stream.url == storedUrl } -> storedUrl
                storedFingerprint != null ->
                    merged.firstOrNull { it.fingerprint == storedFingerprint }?.option?.stream?.url
                else -> storedUrl
            }
            // Warm the next episode's sources in the background so the next-episode
            // shortcut opens instantly; the repository cache holds the answer.
            val nextVideo = if (isArchiveItemId(item.id)) null else nextEpisode(episode, videos)
            if (nextVideo != null) {
                scope.launch {
                    runCatching {
                        streamsFrom(repository, addons.toList(), item.type, nextVideo.id)
                    }
                }
            }
            if (autoplayQueued) {
                autoplayQueued = false
                val auto = bestSource(
                    streamOptions.toList(),
                    preference = preferences.preferredQuality,
                    stats = sourceStats,
                    pinnedUrl = pinnedSourceUrl,
                    nowEpochMillis = Clock.System.now().toEpochMilliseconds()
                )?.takeIf { option ->
                    option.stream.url?.let { com.cinemax.app.addons.StremioAddonClient.isSafeMediaUrl(it) } == true
                }
                val autoUrl = auto?.stream?.url
                val autoItem = selectedItem
                if (autoUrl != null && autoItem != null) {
                    val meta = selectedMeta
                    playingItem = autoItem.copy(
                        name = meta?.name ?: autoItem.name,
                        poster = meta?.poster ?: autoItem.poster,
                        background = meta?.background ?: autoItem.background,
                        description = meta?.description ?: autoItem.description,
                        releaseInfo = meta?.releaseInfo ?: autoItem.releaseInfo,
                        imdbRating = meta?.imdbRating ?: autoItem.imdbRating
                    )
                    playingVideo = selectedVideo
                    playingStream = auto
                    playbackUrl = autoUrl
                }
            }
            status = if (found.isEmpty()) {
                val queriedAddons = addons.count { AddonCapabilities.supports(it, "stream", item.type) }
                val nuvioType = NuvioMediaIds.mediaType(item.type)
                val queriedProviders = nuvioProviders.count { it.supportsType(nuvioType) }
                "لا نتائج: $queriedAddons إضافة و$queriedProviders مصدر Nuvio لم تُرجع روابط"
            } else "المصادر: ${merged.size}"
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            status = "تعذر جلب المصادر: ${error.message ?: "خطأ غير معروف"}"
        } finally {
            loadingStreams = false
        }
    }

    fun applySubtitle(choice: SubtitleChoice?) {
        // An addon subtitle replaces the file's own track, and the other way round.
        selectedEmbeddedTrackId = null
        userChoseSubtitle = true
        if (choice == null) {
            selectedSubtitleUrl = null
            subtitleCues = emptyList()
            return
        }
        subtitleBusy = true
        scope.launch {
            try {
                val response = nuvioHttp.get(choice.url)
                if (response.status.value !in 200..299) {
                    throw IllegalStateException("HTTP ${response.status.value}")
                }
                val cues = parseSubtitles(response.bodyAsText())
                subtitleCues = cues
                selectedSubtitleUrl = choice.url
                if (cues.isEmpty()) status = "ملف الترجمة فارغ أو غير مدعوم"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر تحميل الترجمة: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                subtitleBusy = false
            }
        }
    }

    LaunchedEffect(selectedItem) {
        if (selectedItem != null) lastDetailItem = selectedItem
    }

    /** Loads the first few catalogs of an addon so home shows more than one shelf. */
    fun loadHomeShelves(addon: InstalledAddon, manifest: StremioManifest) {
        scope.launch {
            val shelves = coroutineScope {
                manifest.catalogs.take(MAX_HOME_SHELVES).map { catalog ->
                    async {
                        val items = try {
                            repository.loadCatalog(addon.manifestUrl, catalog.type, catalog.id)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            emptyList()
                        }
                        if (items.isEmpty()) null else CinemaxShelf(
                            title = catalog.name ?: catalog.id,
                            items = distinctTitles(items),
                            manifestUrl = addon.manifestUrl,
                            catalogType = catalog.type,
                            catalogId = catalog.id,
                            supportsSkip = catalog.supportsExtra("skip"),
                            genreOptions = catalog.extra
                                .firstOrNull { it.name == "genre" }
                                ?.options
                                .orEmpty()
                        )
                    }
                }.awaitAll().filterNotNull()
            }
            homeShelves.clear()
            homeShelves.addAll(shelves)
        }
    }

    // Subtitle lists come from the chosen stream and from every subtitle addon,
    // then the preferred language is applied automatically.
    LaunchedEffect(playbackUrl, playingStream, selectedItem?.id, selectedVideo?.id) {
        subtitleOptions.clear()
        selectedSubtitleUrl = null
        subtitleCues = emptyList()
        embeddedSubtitleTracks.clear()
        selectedEmbeddedTrackId = null
        userChoseSubtitle = false
        embeddedAudioTracks.clear()
        selectedAudioTrackId = null
        showAudioPicker = false
        playerPositionMs = 0L
        playerDurationMs = 0L
        playerStarted = false
        playerMessage = null
        playerControls = null
        controlsVisible = true
        playbackSpeed = preferences.preferredSpeed
        subtitleDelayMs = 0L
        hud = null
        val stream = playingStream ?: return@LaunchedEffect
        val item = selectedItem ?: return@LaunchedEffect
        val collected = mutableListOf<SubtitleChoice>()
        stream.stream.subtitles.forEach { subtitle ->
            collected.add(
                SubtitleChoice(subtitle.lang, subtitle.label ?: subtitle.lang, stream.addonName, subtitle.url)
            )
        }
        val episodeOrItemId = selectedVideo?.id ?: item.id
        // A public-domain row carries a made-up id; the addons know the film by its IMDb id.
        val mediaId = if (isPublicDomainId(item.id)) publicDomainImdbId(item.id) else episodeOrItemId
        val hints = stream.stream.behaviorHints
        val extra = mutableMapOf<String, String>()
        hints.videoHash?.let { extra["videoHash"] = it }
        hints.videoSize?.let { extra["videoSize"] = it.toString() }
        subtitleBusy = true
        try {
            // Every subtitle addon is asked at once, with a cap, so one slow
            // provider cannot delay the picker.
            val found = coroutineScope {
                addons
                    .filter { AddonCapabilities.supports(it, "subtitles", item.type) }
                    .map { addon ->
                        async {
                            try {
                                withTimeout(SUBTITLE_TIMEOUT_MS) {
                                    repository.loadSubtitles(addon.manifestUrl, item.type, mediaId, extra)
                                }.map { subtitle ->
                                    SubtitleChoice(
                                        subtitle.lang,
                                        subtitle.label ?: subtitle.lang,
                                        addon.name,
                                        subtitle.url
                                    )
                                }
                            } catch (_: TimeoutCancellationException) {
                                emptyList<SubtitleChoice>()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Throwable) {
                                emptyList<SubtitleChoice>()
                            }
                        }
                    }
                    .awaitAll()
                    .flatten()
            }
            collected.addAll(found)
        } finally {
            subtitleBusy = false
        }
        subtitleOptions.addAll(collected.distinctBy { it.url })
        val preferred = preferences.preferredSubtitleLanguage
        if (preferred != "off") {
            val index = languageIndex(subtitleOptions.map { it.lang }, preferred)
            if (index != null) applySubtitle(subtitleOptions[index])
        }
    }

    // The heads-up text a native gesture produces fades out on its own.
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(1400)
            hud = null
        }
    }

    // The player chrome gets out of the way while the video plays. The system
    // player handles its own gestures, so the chrome stays as the way out.
    LaunchedEffect(controlsVisible, playbackUrl, preferences.preferredPlayer) {
        if (controlsVisible && playbackUrl != null && preferences.preferredPlayer != "system") {
            delay(4000)
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        val restoredAddon = activeAddon
        if (restoredAddon != null) {
            loading = true
            try {
                val manifest = repository.loadManifest(restoredAddon.manifestUrl)
                val refreshed = restoredAddon.copy(
                    name = manifest.name,
                    version = manifest.version,
                    description = manifest.description,
                    resources = manifest.resourceNames().toList(),
                    types = manifest.types,
                    idPrefixes = manifest.idPrefixes,
                    catalogs = manifest.catalogs
                )
                if (refreshed != restoredAddon) {
                    val index = addons.indexOfFirst { it.id == restoredAddon.id }
                    if (index >= 0) {
                        addons[index] = refreshed
                        addonStore.save(addons.toList())
                    }
                    activeAddon = refreshed
                }
                availableCatalogs.clear()
                availableCatalogs.addAll(manifest.catalogs)
                val catalog = manifest.catalogs.firstOrNull()
                if (catalog != null) {
                    activeCatalogType = catalog.type
                    activeCatalogId = catalog.id
                    activeCatalogSupportsSearch = catalog.supportsExtra("search")
                    activeCatalogSupportsSkip = catalog.supportsExtra("skip")
                    catalogItems.addAll(repository.loadCatalog(restoredAddon.manifestUrl, catalog.type, catalog.id))
                    loadHomeShelves(refreshed, manifest)
                    status = "تم استعادة ${restoredAddon.name} • ${catalogItems.size} عنصرًا"
                } else {
                    status = "تم استعادة ${restoredAddon.name}؛ لا يوجد كتالوج معلن."
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر استعادة الكتالوج: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                loading = false
            }
        }
    }

    // Refresh the stored capability and catalog descriptors of the other addons,
    // so cross-addon search works for addons installed before those fields existed.
    LaunchedEffect(Unit) {
        val stored = addons.toList().filter { it.id != activeAddon?.id }
        var changed = false
        for (addon in stored) {
            val manifest = try {
                repository.loadManifest(addon.manifestUrl)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                continue
            }
            val refreshed = addon.copy(
                name = manifest.name,
                version = manifest.version,
                description = manifest.description,
                resources = manifest.resourceNames().toList(),
                types = manifest.types,
                idPrefixes = manifest.idPrefixes,
                catalogs = manifest.catalogs,
                configurable = manifest.behaviorHints.configurable
            )
            if (refreshed != addon) {
                val index = addons.indexOfFirst { it.id == addon.id }
                if (index >= 0) {
                    addons[index] = refreshed
                    changed = true
                }
            }
        }
        if (changed) addonStore.save(addons.toList())

        // Refresh the installed Nuvio repositories at launch so provider names,
        // versions and script file names stay current without re-adding them.
        var repositoriesChanged = false
        for (installed in nuvioRepositories.toList()) {
            val refreshed = try {
                nuvioClient.loadRepository(installed.manifestUrl)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                continue
            }
            if (refreshed.name != installed.name || refreshed.scrapers.size != installed.scrapers.size) {
                val index = nuvioRepositories.indexOfFirst { it.manifestUrl == installed.manifestUrl }
                if (index >= 0) {
                    nuvioRepositories[index] = refreshed
                    repositoriesChanged = true
                }
            }
        }
        if (repositoriesChanged) nuvioStore.saveRepositories(nuvioRepositories.toList())
    }

    fun selectAddon(addon: InstalledAddon) {
        if (activeAddon?.id == addon.id) return
        activeAddon = addon
        catalogItems.clear()
        selectedItem = null
        browseShelf = null
        playbackUrl = null
        streamOptions.clear()
        searchQuery = ""
        searchResults.clear()
        loading = true
        scope.launch {
            try {
                val manifest = repository.loadManifest(addon.manifestUrl)
                availableCatalogs.clear()
                availableCatalogs.addAll(manifest.catalogs)
                val catalog = manifest.catalogs.firstOrNull()
                if (catalog == null) {
                    activeCatalogType = null
                    activeCatalogId = null
                    activeCatalogSupportsSearch = false
                    activeCatalogSupportsSkip = false
                    status = "تم اختيار ${addon.name}؛ لا يوجد كتالوج معلن."
                } else {
                    activeCatalogType = catalog.type
                    activeCatalogId = catalog.id
                    activeCatalogSupportsSearch = catalog.supportsExtra("search")
                    activeCatalogSupportsSkip = catalog.supportsExtra("skip")
                    val loaded = repository.loadCatalog(addon.manifestUrl, catalog.type, catalog.id)
                    catalogItems.addAll(loaded)
                    loadHomeShelves(addon, manifest)
                    status = "المصدر النشط: ${addon.name} • ${loaded.size} عنصرًا"
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر فتح ${addon.name}: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                loading = false
            }
        }
    }

    /**
     * Asks every installed addon for its manifest, so a dead one is visible
     * before it is used. The results are keyed by addon id.
     */
    fun checkAddons() {
        if (addonCheckRunning || addons.isEmpty()) return
        addonCheckRunning = true
        val targets = addons.toList()
        scope.launch {
            try {
                val results = coroutineScope {
                    targets.map { addon ->
                        async {
                            val manifest = try {
                                repository.loadManifest(addon.manifestUrl)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Throwable) {
                                null
                            }
                            Triple(addon.id, manifest != null, manifest?.version)
                        }
                    }.awaitAll()
                }
                addonHealth = results.associate { it.first to it.second }
                addonUpdates = results.mapNotNull { entry ->
                    val version = entry.third ?: return@mapNotNull null
                    val installed = targets.firstOrNull { it.id == entry.first }?.version
                    if (installed != null && version != installed) entry.first to version else null
                }.toMap()
                val dead = results.count { !it.second }
                val updates = addonUpdates.size
                status = if (dead == 0 && updates == 0) "كل الإضافات (${results.size}) تستجيب"
                else buildString {
                    if (dead > 0) append("$dead متوقف")
                    if (updates > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("$updates تحديث متاح")
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر فحص المصادر: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                addonCheckRunning = false
            }
        }
    }

    /** Re-reads one addon's manifest and stores the refreshed descriptor. */
    fun updateAddon(addon: InstalledAddon) {
        scope.launch {
            try {
                val manifest = repository.loadManifest(addon.manifestUrl)
                val refreshed = addon.copy(
                    name = manifest.name,
                    version = manifest.version,
                    description = manifest.description,
                    resources = manifest.resourceNames().toList(),
                    types = manifest.types,
                    idPrefixes = manifest.idPrefixes,
                    catalogs = manifest.catalogs,
                    configurable = manifest.behaviorHints.configurable
                )
                val index = addons.indexOfFirst { it.id == addon.id }
                if (index >= 0) addons[index] = refreshed
                addonStore.save(addons.toList())
                repository.clearCaches()
                addonUpdates = addonUpdates - addon.id
                if (activeAddon?.id == addon.id) activeAddon = refreshed
                status = "حُدّثت ${refreshed.name} إلى ${refreshed.version}"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر تحديث ${addon.name}: ${error.message ?: "خطأ غير معروف"}"
            }
        }
    }

    /** Updates every addon the last check found a newer version for. */
    fun updateAllAddons() {
        addons.filter { it.id in addonUpdates }.forEach { updateAddon(it) }
    }

    /** Drops every addon the last check found dead. */
    fun removeDeadAddons() {
        val dead = addons.filter { addonHealth[it.id] == false }
        if (dead.isEmpty()) return
        val deadIds = dead.map { it.id }.toSet()
        addons.removeAll { it.id in deadIds }
        addonStore.save(addons.toList())
        addonHealth = addonHealth - deadIds
        if (activeAddon?.id in deadIds) activeAddon = addons.lastOrNull()
        status = "أُزيلت ${dead.size} إضافة متوقفة"
    }

    /** Pins one source for the open title, or unpins it when it already is. */
    fun togglePinnedSource(option: StreamOption) {
        val item = selectedItem ?: return
        val url = option.stream.url ?: return
        val key = selectedVideo?.id ?: item.id
        if (pinnedSourceUrl == url) {
            preferredSourceStore.clear(key)
            pinnedSourceUrl = null
            status = "أُزيل تثبيت المصدر"
        } else {
            preferredSourceStore.save(key, url)
            pinnedSourceUrl = url
            status = "المصدر المفضل: ${option.stream.name ?: option.addonName}"
        }
    }

    /**
     * Re-checks the links that failed recently with a lightweight request and
     * clears their record when one answers, so a source that came back is offered
     * again without interrupting the video that is playing.
     */
    fun retryFailedLinks() {
        val now = Clock.System.now().toEpochMilliseconds()
        val pending = streamOptions
            .mapNotNull { option ->
                val url = option.stream.url ?: return@mapNotNull null
                val fingerprint = streamFingerprint(option)
                if (sourceStats[fingerprint]?.isFailedRecently(now) == true) fingerprint to url else null
            }
            .distinctBy { it.first }
            .take(5)
        if (pending.isEmpty()) return
        scope.launch {
            val recovered = mutableListOf<String>()
            for ((fingerprint, url) in pending) {
                val alive = try {
                    http.head(url).status.value in 200..399
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    false
                }
                if (alive) {
                    statsStore.recordSuccess(fingerprint, null)
                    recovered += fingerprint
                }
            }
            if (recovered.isNotEmpty()) {
                sourceStats = sourceStats +
                    recovered.associateWith { statsStore.load(it) ?: SourceStats() }
                status = "عاد ${recovered.size} مصدر للعمل"
            }
        }
    }

    // While a video plays, the failed links are retried in the background.
    LaunchedEffect(playbackUrl) {
        if (playbackUrl == null) return@LaunchedEffect
        while (true) {
            delay(60_000)
            retryFailedLinks()
        }
    }

    /** Marks an episode watched by hand, or clears the mark when it already has one. */
    fun toggleEpisodeWatched(video: StremioVideo) {
        val key = video.id
        watchedEpisodes = if (key in watchedEpisodes) {
            val next = watchedEpisodes - key
            watchedStore.save(next)
            status = "أُزيلت علامة المشاهدة: ${episodeLabel(video)}"
            next
        } else {
            val next = watchedEpisodes + key
            watchedStore.save(next)
            // A manual mark replaces the resume point, so the episode stops
            // appearing in continue-watching.
            watchProgressStore.remove(key)
            watchProgressItems.removeAll { it.key == key }
            status = "تمت المشاهدة: ${episodeLabel(video)}"
            next
        }
    }

    /** Counts the playable links one episode has, reusing the streams cache. */
    fun checkEpisodeSources(video: StremioVideo) {
        val item = selectedItem ?: return
        if (checkingEpisodeId != null) return
        checkingEpisodeId = video.id
        scope.launch {
            try {
                val found = if (isArchiveItemId(item.id)) {
                    emptyList()
                } else {
                    streamsFrom(repository, addons.toList(), item.type, video.id)
                }
                val playable = mergeSources(found).count { it.option.stream.url != null }
                episodeSourceCounts = episodeSourceCounts + (video.id to playable)
                status = if (playable == 0) "لا مصادر لهذه الحلقة الآن" else "$playable مصدرًا لهذه الحلقة"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر فحص الحلقة: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                checkingEpisodeId = null
            }
        }
    }

    /** Snapshot of the viewer's data, as text for the clipboard. */
    fun exportBackup(): String = encodeBackup(
        CinemaxBackup(
            favorites = favoriteItems.toList(),
            progress = watchProgressItems.toList(),
            watched = watchedEpisodes.toList(),
            addons = addons.toList()
        )
    )

    /** Restores a snapshot; a text that is not one is reported, not applied. */
    fun importBackup(raw: String) {
        val backup = decodeBackup(raw)
        if (backup == null) {
            status = "النص المنسوخ ليس نسخة احتياطية صالحة"
            return
        }
        favoriteItems.clear()
        favoriteItems.addAll(distinctTitles(backup.favorites))
        favoritesStore.save(favoriteItems.toList())
        watchProgressStore.restore(backup.progress)
        watchProgressItems.clear()
        watchProgressItems.addAll(backup.progress)
        watchedEpisodes = backup.watched.toSet()
        watchedStore.save(watchedEpisodes)
        if (backup.addons.isNotEmpty()) {
            addons.clear()
            addons.addAll(backup.addons)
            addonStore.save(addons.toList())
            repository.clearCaches()
            activeAddon = addons.lastOrNull()
        }
        status = "تمت استعادة ${backup.summary()}"
    }

    /**
     * Reads Stremio's public addon collection so a new install can pick addons
     * without hunting for a manifest URL.
     */
    fun loadAddonDirectory() {
        if (directoryLoading) return
        directoryLoading = true
        scope.launch {
            try {
                val entries = addonDirectory.load()
                directoryEntries = entries
                status = if (entries.isEmpty()) "لم أجد إضافات في القائمة العامة"
                else "${entries.size} إضافة في القائمة العامة"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر تحميل قائمة الإضافات: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                directoryLoading = false
            }
        }
    }

    /** Forgets what the app learned about link health. */
    fun clearSourceStats() {
        statsStore.clear()
        sourceStats = emptyMap()
        status = "تم مسح سجل جودة المصادر"
    }

    /** Reloads the active addon's catalog and shelves, for the home refresh button. */
    fun refreshHome() {
        val addon = activeAddon ?: return
        // A manual refresh must show fresh data, not the cached answer.
        repository.clearCaches()
        loading = true
        scope.launch {
            try {
                val manifest = repository.loadManifest(addon.manifestUrl)
                availableCatalogs.clear()
                availableCatalogs.addAll(manifest.catalogs)
                val catalog = manifest.catalogs.firstOrNull()
                if (catalog != null) {
                    val items = repository.loadCatalog(addon.manifestUrl, catalog.type, catalog.id)
                    catalogItems.clear()
                    catalogItems.addAll(items)
                }
                loadHomeShelves(addon, manifest)
                status = "تم تحديث الكتالوج • ${catalogItems.size} عنصرًا"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر تحديث الكتالوج: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                loading = false
            }
        }
    }

    /** Pages the built-in Internet Archive shelf, which has no Stremio catalog. */
    fun loadArchivePage(shelf: CinemaxShelf, replace: Boolean) {
        if (browseLoadingMore) return
        if (!replace && browseExhausted) return
        browseLoadingMore = true
        scope.launch {
            try {
                val page = if (replace) 1 else browseArchivePage
                val fetched = archive.popular(
                    rows = 60,
                    page = page,
                    genre = browseGenre,
                    sort = archiveSortValue(browseSort)
                )
                val merged = appendCatalogPage(
                    if (replace) emptyList() else browseItems.toList(),
                    fetched
                )
                browseItems.clear()
                browseItems.addAll(merged.items)
                browseExhausted = merged.exhausted || fetched.isEmpty()
                if (fetched.isNotEmpty()) browseArchivePage = page + 1
                status = "${shelf.title} • ${merged.items.size} عنوانًا"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                browseExhausted = true
                status = "تعذر تحميل المزيد: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                browseLoadingMore = false
            }
        }
    }

    /**
     * Requests one page of the opened shelf for the current genre. `replace`
     * starts the list again (first page, or after a genre change); otherwise the
     * page is appended. The Stremio protocol carries the genre and the skip in
     * the same extra segment, and a page that brings nothing new ends the list.
     */
    fun loadShelfPage(replace: Boolean) {
        val shelf = browseShelf ?: return
        if (shelf.archive) {
            loadArchivePage(shelf, replace)
            return
        }
        val manifestUrl = shelf.manifestUrl ?: return
        val type = shelf.catalogType ?: return
        val catalogId = shelf.catalogId ?: return
        if (browseLoadingMore) return
        if (!replace && browseExhausted) return
        browseLoadingMore = true
        scope.launch {
            try {
                val extra = catalogPageExtra(browseGenre, if (replace) 0 else browseItems.size)
                val page = repository.loadCatalog(manifestUrl, type, catalogId, extra)
                val merged = if (replace) {
                    appendCatalogPage(emptyList(), page)
                } else {
                    appendCatalogPage(browseItems.toList(), page)
                }
                browseItems.clear()
                browseItems.addAll(merged.items)
                browseExhausted = merged.exhausted
                status = if (merged.exhausted) {
                    "${shelf.title} • ${merged.items.size} عنوانًا"
                } else {
                    "${shelf.title} • ${merged.items.size} عنوانًا (يمكن المزيد)"
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                browseExhausted = true
                status = "تعذر تحميل المزيد: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                browseLoadingMore = false
            }
        }
    }

    /** Switches the opened grid to another sort order, restarting it. */
    fun selectShelfSort(option: String) {
        if (browseSort == option) return
        browseSort = option
        if (browseShelf?.archive == true) browseStore.saveSort(option)
        browseItems.clear()
        browseExhausted = false
        browseArchivePage = 1
        loadShelfPage(replace = true)
    }

    /** Switches the opened grid to another genre, restarting it from page one. */
    fun selectShelfGenre(genre: String?) {
        if (browseGenre == genre) return
        browseGenre = genre
        if (browseShelf?.archive == true) browseStore.saveGenre(genre)
        browseItems.clear()
        browseExhausted = false
        loadShelfPage(replace = true)
    }

    /** Adds or removes a title from the saved list, from a long press on a poster. */
    fun toggleFavorite(item: StremioMetaPreview) {
        hapticTap()
        val wasSaved = item.id in favoriteIds
        favoriteItems.removeAll { it.id == item.id }
        if (!wasSaved) favoriteItems.add(item)
        favoritesStore.save(favoriteItems.toList())
        status = if (wasSaved) "أزيل من قائمتي: ${item.name}" else "أضيف إلى قائمتي: ${item.name}"
    }

    /** Opens a home shelf as a full grid, keeping the page it already loaded. */
    fun openShelf(shelf: CinemaxShelf) {
        browseShelf = shelf
        // The built-in library continues with the genre and order last used.
        browseGenre = if (shelf.archive) browseStore.loadGenre() else null
        browseSort = if (shelf.archive) browseStore.loadSort() else null
        browseItems.clear()
        browseItems.addAll(shelf.items)
        browseExhausted = !shelf.isBrowsable
        // An archive shelf already holds page one, so the next request is page two.
        browseArchivePage = if (shelf.archive) 2 else 1
        status = "${shelf.title} • ${shelf.items.size} عنوانًا"
        // A restored genre or order needs page one fetched again with it applied.
        if (shelf.archive && (browseGenre != null || browseSort != null)) {
            loadShelfPage(replace = true)
        }
    }

    /** "فيلم الليلة": a random title from the library that is not in progress. */
    fun pickTonight() {
        val inProgress = watchProgressItems.map { it.key }.toSet()
        val everything = (favoriteItems + catalogItems + archiveItems).distinctBy { it.id }
        val notStarted = everything.filter { it.id !in inProgress }
        val pool = notStarted.ifEmpty { everything }
        tonightPick = if (pool.isEmpty()) null else pool[Random.nextInt(pool.size)]
    }

    LaunchedEffect(catalogItems.size, favoriteItems.size, watchProgressItems.size) {
        val pool = (favoriteItems + catalogItems).distinctBy { it.id }
        val current = tonightPick
        if (pool.isEmpty()) {
            tonightPick = null
        } else if (current == null || pool.none { it.id == current.id }) {
            pickTonight()
        }
    }

    /** Opens the player for a source the user picked in the detail screen. */
    fun startPlayback(option: StreamOption, mediaUrl: String) {
        val item = selectedItem ?: return
        lastSourceStore.save(selectedVideo?.id ?: item.id, mediaUrl, streamFingerprint(option))
        // Remember which quality this addon tends to give this viewer.
        streamBadges(option.stream.name, option.stream.title).firstOrNull()?.let { quality ->
            sourcePreferenceStore.recordPlayedQuality(option.addonId, quality)
            sourcePreferences = sourcePreferenceStore.loadAll()
        }
        lastPlayedUrl = mediaUrl
        usingSystemFallback = false
        val meta = selectedMeta
        playingItem = item.copy(
            name = meta?.name ?: item.name,
            poster = meta?.poster ?: item.poster,
            background = meta?.background ?: item.background,
            description = meta?.description ?: item.description,
            releaseInfo = meta?.releaseInfo ?: item.releaseInfo,
            imdbRating = meta?.imdbRating ?: item.imdbRating
        )
        playingVideo = selectedVideo
        playingStream = option
        playerMessage = if (isNativeFullFormatPlayerAvailable()) {
            null
        } else {
            unsupportedVideoContainer(mediaUrl, option.stream.behaviorHints.filename)?.let { ext ->
                "صيغة ${ext.uppercase()} لا يعمل بها مشغل النظام. اختر مشغل MPV من الإعدادات أو مصدرًا MP4 أو HLS."
            }
        }
        playbackUrl = mediaUrl
        attemptedSourceUrls = setOf(mediaUrl)
        showPlayerSources = false
        sourceNotice = null
        playbackFingerprint = streamFingerprint(option)
        playbackStartedAtMs = Clock.System.now().toEpochMilliseconds()
        successRecorded = false
    }

    /**
     * Plays another source in the running player, without leaving it: the new
     * link replaces the current one and the player reloads itself.
     */
    fun switchToSource(option: StreamOption, url: String) {
        val item = playingItem ?: return
        lastSourceStore.save(playingVideo?.id ?: item.id, url, streamFingerprint(option))
        lastPlayedUrl = url
        playingStream = option
        attemptedSourceUrls = attemptedSourceUrls + url
        showPlayerSources = false
        sourceNotice = null
        playbackUrl = url
        playbackFingerprint = streamFingerprint(option)
        playbackStartedAtMs = Clock.System.now().toEpochMilliseconds()
        successRecorded = false
        usingSystemFallback = false
    }

    /**
     * The next untried direct link, used when the current source fails to play.
     * Three links at most, so a broken title cannot bounce through a long list.
     */
    fun nextPlayableSource(): Pair<StreamOption, String>? =
        nextUntriedSource(
            streamOptions.toList(),
            attemptedSourceUrls,
            lastPlayedUrl = lastPlayedUrl,
            stats = sourceStats,
            nowEpochMillis = Clock.System.now().toEpochMilliseconds()
        )?.let { option -> option.stream.url?.let { url -> option to url } }


    /**
     * Verifies one addon URL, installs it and makes it the active source. Both
     * the paste field and the ready-made sources go through it.
     */
    fun installAddon(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || loading) return
        val normalized = com.cinemax.app.addons.StremioAddonClient.manifestUrl(trimmed)
        if (normalized == null) {
            status = "رابط الإضافة يجب أن يبدأ بـ https://"
            return
        }
        loading = true
        installingSourceUrl = normalized
        status = "جارٍ التحقق من الإضافة..."
        scope.launch {
            try {
                val manifest = repository.loadManifest(trimmed)
                availableCatalogs.clear()
                availableCatalogs.addAll(manifest.catalogs)
                val installed = InstalledAddon(
                    manifestUrl = normalized, id = manifest.id, name = manifest.name,
                    version = manifest.version, description = manifest.description,
                    installedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    resources = manifest.resourceNames().toList(),
                    types = manifest.types,
                    idPrefixes = manifest.idPrefixes,
                    catalogs = manifest.catalogs,
                    configurable = manifest.behaviorHints.configurable
                )
                addons.removeAll { it.id == installed.id }
                addons.add(installed)
                addonStore.save(addons.toList())
                repository.clearCaches()
                activeAddon = installed
                selectedItem = null
                playbackUrl = null
                streamOptions.clear()
                searchQuery = ""
                searchResults.clear()
                catalogItems.clear()
                val catalog = manifest.catalogs.firstOrNull()
                if (catalog != null) {
                    activeCatalogType = catalog.type
                    activeCatalogId = catalog.id
                    activeCatalogSupportsSearch = catalog.supportsExtra("search")
                    activeCatalogSupportsSkip = catalog.supportsExtra("skip")
                    val loaded = repository.loadCatalog(normalized, catalog.type, catalog.id)
                    catalogItems.addAll(loaded)
                    loadHomeShelves(installed, manifest)
                    status = "تم تثبيت ${manifest.name} وجلب ${loaded.size} عنصرًا"
                } else {
                    activeCatalogType = null
                    activeCatalogId = null
                    activeCatalogSupportsSearch = false
                    activeCatalogSupportsSkip = false
                    status = "تم تثبيت ${manifest.name}، ولا تعلن الإضافة عن كتالوج."
                }
                addonUrl = ""
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر تثبيت الإضافة: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                loading = false
                installingSourceUrl = null
            }
        }
    }

    // Whether the search tab can ask anything with the current type choice.
    val canSearchNow = if (searchType == "all") {
        addons.isNotEmpty()
    } else {
        addons.any { AddonCapabilities.supports(it, "catalog", searchType) }
    }

    /**
     * Runs one cross-addon search and replaces any search still in flight, so a
     * slow addon can never hold the next query back. Results are shown addon by
     * addon: every arrival re-ranks what is already there instead of waiting for
     * the slowest source.
     */
    fun runSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return
        // The debounced effect and the button can fire together: one search is enough.
        if (searchJob?.isActive == true && lastSearchQuery == query) return
        lastSearchQuery = query
        searchJob?.cancel()
        val generation = searchGeneration + 1
        searchGeneration = generation
        searching = true
        searchResults.clear()
        val collected = mutableListOf<CatalogEntry>()
        val seenIds = mutableSetOf<String>()
        fun publish() {
            val filtered = collected.filter { matchesLibraryQuery(it.item.name, query) }
            // One row per title even when two catalogs list it under different ids.
            val unique = filtered.distinctBy { canonicalMediaKey(it.item) }
            val ranked = rankSearchResults(unique, query)
            searchResults.clear()
            searchResults.addAll(ranked)
            val responders = collected.map { it.addonName }.distinct().size
            status = if (ranked.isEmpty()) "البحث…"
            else "نتائج حتى الآن: ${ranked.size} من $responders إضافة…"
        }
        searchJob = scope.launch {
            try {
                val types = if (searchType == "all") listOf("movie", "series") else listOf(searchType)
                for (type in types) {
                    searchAllAddonsStreaming(repository, addons.toList(), type, query) { fresh ->
                        for (entry in fresh) {
                            if (seenIds.add(entry.item.id)) collected.add(entry)
                        }
                        if (searchGeneration == generation) publish()
                    }
                }
                val archiveHits = if (searchType != "series") {
                    try {
                        archive.search(query)
                            .map { CatalogEntry("archive", ARCHIVE_PROVIDER_NAME, it) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        emptyList()
                    }
                } else emptyList()
                for (entry in archiveHits) {
                    if (seenIds.add(entry.item.id)) collected.add(entry)
                }
                if (searchGeneration != generation) return@launch
                publish()
                searchHistoryStore.add(query)
                searchHistory = searchHistoryStore.load()
                val addonCount = searchResults.map { it.addonName }.distinct().size
                status = if (searchResults.isEmpty()) "لا نتائج في إضافاتك"
                else "نتائج البحث: ${searchResults.size} من $addonCount إضافة"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (searchGeneration == generation) {
                    status = "تعذر البحث: ${error.message ?: "خطأ غير معروف"}"
                }
            } finally {
                if (searchGeneration == generation) searching = false
            }
        }
    }

    // Typing searches on its own: a short pause after the last keystroke
    // replaces pressing the button, which still works.
    LaunchedEffect(searchQuery, searchType) {
        val query = searchQuery.trim()
        if (query.length < 2 || !canSearchNow) return@LaunchedEffect
        delay(600)
        runSearch(query)
    }

    val appAccent = when (preferences.accent) {
        "Blue" -> Color(0xFF22A7F0)
        "Rose" -> Color(0xFFEC4899)
        "Emerald" -> Color(0xFF10B981)
        "Amber" -> Color(0xFFF59E0B)
        else -> Accent
    }
    val appBackground = if (preferences.oledBlack) Color.Black else Ink
    val shelves = buildList {
        // The built-in public-domain library comes first so a fresh install has
        // something to play before any addon is added.
        if (archiveItems.isNotEmpty()) {
            add(
                CinemaxShelf(
                    title = ARCHIVE_PROVIDER_NAME,
                    items = archiveItems,
                    archive = true,
                    genreOptions = ARCHIVE_GENRES.map { it.first },
                    sortOptions = ARCHIVE_SORTS.map { it.first }
                )
            )
        }
        if (PUBLIC_DOMAIN_FILMS.isNotEmpty()) {
            add(
                CinemaxShelf(
                    title = PUBLIC_DOMAIN_PROVIDER,
                    items = publicDomainPreviews()
                )
            )
        }
        if (homeShelves.isNotEmpty()) {
            addAll(homeShelves)
        } else if (catalogItems.isNotEmpty()) {
            val title = availableCatalogs
                .firstOrNull { it.type == activeCatalogType && it.id == activeCatalogId }
                ?.name
                ?: "الأكثر شعبية"
            add(
                CinemaxShelf(
                    title = title,
                    items = distinctTitles(catalogItems.toList()),
                    manifestUrl = activeAddon?.manifestUrl,
                    catalogType = activeCatalogType,
                    catalogId = activeCatalogId,
                    supportsSkip = activeCatalogSupportsSkip,
                    genreOptions = availableCatalogs
                        .firstOrNull { it.type == activeCatalogType && it.id == activeCatalogId }
                        ?.extra
                        ?.firstOrNull { it.name == "genre" }
                        ?.options
                        .orEmpty()
                )
            )
        }
        if (favoriteItems.isNotEmpty()) add(CinemaxShelf("قائمتي", favoriteItems.toList()))
    }
    val activeSubtitleLabel = embeddedSubtitleTracks.firstOrNull { it.id == selectedEmbeddedTrackId }
        ?.let { embeddedSubtitleLabel(it) }
        ?: subtitleOptions.firstOrNull { it.url == selectedSubtitleUrl }?.label

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = appBackground, surface = Panel, primary = appAccent,
            onBackground = Color.White, onSurface = Color.White
        ),
        typography = cinemaxTypography()
    ) {
        Box(Modifier.fillMaxSize().background(appBackground)) {
            when (tab) {
                0 -> CinemaxHomeScreen(
                    heroItems = (catalogItems.toList().ifEmpty { favoriteItems.toList() }
                        .ifEmpty { archiveItems }).take(6),
                    shelves = shelves,
                    continueWatching = watchProgressItems.toList(),
                    loading = loading || loadingMeta,
                    status = status,
                    accent = appAccent,
                    background = appBackground,
                    onOpenItem = { item ->
                        pendingVideoId = null
                        selectedItem = item
                    },
                    onResume = { progress ->
                        pendingVideoId = progress.videoId
                        selectedItem = progress.item
                    },
                    onRemoveProgress = { progress ->
                        watchProgressStore.remove(progress.key)
                        watchProgressItems.removeAll { it.key == progress.key }
                    },
                    onAddAddon = { showAddDialog = true },
                    onNuvio = { showNuvioDialog = true },
                    onToggleFavorite = { toggleFavorite(it) },
                    error = archiveError,
                    onRetry = { loadArchive() },
                    versionLabel = appVersionLabel(),
                    tonightPick = tonightPick,
                    onShuffleTonight = { pickTonight() },
                    onRefresh = { refreshHome(); loadArchive() },
                    onBrowseShelf = { shelf -> openShelf(shelf) },
                    posterStatus = posterStatuses
                )
                1 -> CinemaxSearchScreen(
                    query = searchQuery,
                    onQuery = { searchQuery = it },
                    searching = searching,
                    results = searchResults.toList(),
                    status = status,
                    accent = appAccent,
                    onSearch = { runSearch(searchQuery) },
                    onClear = {
                        searchQuery = ""
                        searchResults.clear()
                    },
                    history = searchHistory,
                    onPickHistory = { entry ->
                        searchQuery = entry
                        searchResults.clear()
                    },
                    onClearHistory = {
                        searchHistoryStore.clear()
                        searchHistory = emptyList()
                    },
                    canSearch = canSearchNow,
                    type = searchType,
                    onType = { value ->
                        searchType = value
                        browseStore.saveSearchType(value)
                        searchResults.clear()
                    },
                    onToggleFavorite = { toggleFavorite(it) },
                    onOpenItem = { item ->
                        pendingVideoId = null
                        selectedItem = item
                    },
                    statuses = posterStatuses
                )
                2 -> CinemaxLibraryScreen(
                    favorites = sortLibrary(
                        favoriteItems.toList(),
                        librarySort,
                        watchProgressItems.map { it.item.id }
                    ),
                    continueWatching = watchProgressItems.toList(),
                    filter = libraryFilter,
                    accent = appAccent,
                    onFilter = { libraryFilter = it },
                    sort = librarySort,
                    onSort = { librarySort = it; browseStore.saveLibrarySort(it.name) },
                    query = libraryQuery,
                    onQuery = { libraryQuery = it },
                    onOpenItem = { item ->
                        pendingVideoId = null
                        selectedItem = item
                    },
                    onResume = { progress ->
                        pendingVideoId = progress.videoId
                        selectedItem = progress.item
                    },
                    onRemoveProgress = { progress ->
                        watchProgressStore.remove(progress.key)
                        watchProgressItems.removeAll { it.key == progress.key }
                    },
                    onRemoveFavorite = { item ->
                        hapticTap()
                        favoriteItems.removeAll { it.id == item.id }
                        favoritesStore.save(favoriteItems.toList())
                        status = "أزيل من قائمتي: ${item.name}"
                    }
                )
                else -> CinemaxSettingsScreen(
                    preferences = preferences,
                    onPreferences = { updated ->
                        preferences = updated
                        preferencesStore.save(updated)
                    },
                    addons = addons.toList(),
                    suggestedSourceCount = suggestedSources.size,
                    addonHealth = addonHealth,
                    addonUpdates = addonUpdates,
                    checkingAddons = addonCheckRunning,
                    onCheckAddons = { checkAddons() },
                    onUpdateAddon = { addon -> updateAddon(addon) },
                    onUpdateAllAddons = { updateAllAddons() },
                    onRemoveDeadAddons = { removeDeadAddons() },
                    onClearSourceStats = { clearSourceStats() },
                    sourceStatCount = sourceStats.size,
                    onExportBackup = { exportBackup() },
                    onImportBackup = { raw -> importBackup(raw) },
                    activeAddonId = activeAddon?.id,
                    onSelectAddon = { addon -> selectAddon(addon) },
                    onConfigureAddon = { addon ->
                        val url = com.cinemax.app.addons.StremioAddonClient.configureUrl(addon.manifestUrl)
                        if (url != null) {
                            configBrowserCurrent = url
                            configBrowserUrl = url
                            status = "هيّئ ${addon.name} ثم اضغط «تثبيت»"
                        } else {
                            status = "لا توجد صفحة إعداد لهذه الإضافة"
                        }
                    },
                    onRemoveAddon = { addon ->
                        addons.remove(addon)
                        addonStore.save(addons.toList())
                        repository.clearCaches()
                        if (activeAddon?.id == addon.id) {
                            activeAddon = null
                            catalogItems.clear()
                            homeShelves.clear()
                            searchResults.clear()
                            selectedItem = null
                            activeCatalogType = null
                            activeCatalogId = null
                            activeCatalogSupportsSearch = false
                            activeCatalogSupportsSkip = false
                            addons.lastOrNull()?.let { selectAddon(it) }
                        }
                        status = "تم تحديث قائمة الإضافات"
                    },
                    onAddAddon = { showAddDialog = true },
                    onNuvio = { showNuvioDialog = true },
                    tmdbKey = tmdbKey,
                    onTmdbKey = { value ->
                        tmdbKey = value
                        nuvioStore.saveTmdbApiKey(value)
                    },
                    nuvioRepositoryCount = nuvioRepositories.size,
                    nuvioProviderCount = nuvioProviders.size,
                    status = status,
                    versionLabel = appVersionLabel(),
                    accent = appAccent,
                    favoriteCount = favoriteItems.size,
                    progressCount = watchProgressItems.size,
                    badgePackUrl = badgePackUrl,
                    badgeCount = badgeFilters.size,
                    onBadgePack = { url ->
                        badgePackUrl = url
                        badgeStore.savePackUrl(url)
                    },
                    onClearProgress = {
                        watchProgressStore.clear()
                        watchProgressItems.clear()
                        status = "تم مسح سجل المشاهدة"
                    },
                    onClearFavorites = {
                        favoriteItems.clear()
                        favoritesStore.save(emptyList())
                        status = "تم مسح قائمة المفضلة"
                    },
                    onOpenRoadmap = { showRoadmap = true }
                )
            }

            AnimatedVisibility(
                visible = browseShelf != null && playbackUrl == null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160))
            ) {
                val shelf = browseShelf
                if (shelf != null) {
                    CinemaxCatalogScreen(
                        title = shelf.title,
                        items = browseItems.toList(),
                        loadingMore = browseLoadingMore,
                        canLoadMore = (shelf.supportsSkip || shelf.archive) && !browseExhausted,
                        accent = appAccent,
                        background = appBackground,
                        onOpen = { item ->
                            pendingVideoId = null
                            selectedItem = item
                        },
                        onLoadMore = { loadShelfPage(replace = false) },
                        onBack = { browseShelf = null },
                        statuses = posterStatuses,
                        genres = shelf.genreOptions,
                        selectedGenre = browseGenre,
                        onGenre = { genre -> selectShelfGenre(genre) },
                        sorts = shelf.sortOptions,
                        selectedSort = browseSort,
                        onSort = { option -> selectShelfSort(option) },
                        onToggleFavorite = { toggleFavorite(it) }
                    )
                }
            }

            AnimatedVisibility(
                visible = playbackUrl == null && selectedItem != null,
                // The page grows out of the artwork the viewer just tapped instead
                // of appearing flatly, which is the last piece of the detail polish.
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(240)),
                exit = fadeOut(tween(160)) + scaleOut(targetScale = 1.02f, animationSpec = tween(160))
            ) {
                val item = lastDetailItem
                if (item != null) {
                    val resumeProgress = watchProgressItems.firstOrNull {
                        it.key == (selectedVideo?.id ?: item.id)
                    } ?: watchProgressItems.firstOrNull { it.item.id == item.id }
                    CinemaxDetailScreen(
                        item = item,
                        meta = selectedMeta,
                        loadingMeta = loadingMeta,
                        sources = streamOptions.toList(),
                        loadingSources = loadingStreams,
                        sourcesMessage = status,
                        selectedVideo = selectedVideo,
                        activeSeason = activeSeason,
                        isFavorite = item.id in favoriteIds || favoriteItems.any { sameMedia(it, item) },
                        accent = appAccent,
                        background = appBackground,
                        lastPlayedUrl = lastPlayedUrl,
                        providerIssues = providerIssues,
                        sourceStats = sourceStats,
                        sourceProviders = sourceProviders,
                        hideDeadSources = preferences.hideDeadSources,
                        pinnedUrl = pinnedSourceUrl,
                        onPinSource = { option -> togglePinnedSource(option) },
                        preferredQuality = preferences.preferredQuality,
                        addonQualities = sourcePreferences.mapValues { it.value.quality },
                        packBadges = badgeFilters,
                        resumeLabel = resumeProgress?.let { "من ${formatClock(it.positionMillis)}" },
                        episodeProgress = watchProgressItems.associate { it.key to it.progressFraction } +
                            watchedEpisodes.associateWith { 1f },
                        onToggleFavorite = {
                            hapticTap()
                            if (item.id in favoriteIds || favoriteItems.any { sameMedia(it, item) }) {
                                favoriteItems.removeAll { it.id == item.id || sameMedia(it, item) }
                            } else {
                                val meta = selectedMeta
                                val saved = item.copy(
                                    name = meta?.name ?: item.name,
                                    poster = meta?.poster ?: item.poster,
                                    background = meta?.background ?: item.background,
                                    description = meta?.description ?: item.description,
                                    releaseInfo = meta?.releaseInfo ?: item.releaseInfo,
                                    imdbRating = meta?.imdbRating ?: item.imdbRating
                                )
                                favoriteItems.removeAll { it.id == item.id }
                                favoriteItems.add(saved)
                            }
                            favoritesStore.save(favoriteItems.toList())
                        },
                        onSelectVideo = { video ->
                            selectedVideo = video
                            autoplayQueued = false
                        },
                        onSeason = { season ->
                            activeSeason = season
                            selectedMeta?.videos?.firstOrNull { it.season == season }
                                ?.let { first -> selectedVideo = first }
                        },
                        onPlay = { option ->
                            option.stream.url?.let { url -> startPlayback(option, url) }
                        },
                        onOpenExternal = { url ->
                            if (!openExternalUrl(url)) status = "تعذر فتح الرابط الخارجي"
                        },
                        onRetrySources = { streamRefresh += 1 },
                        onToggleEpisodeWatched = { video -> toggleEpisodeWatched(video) },
                        episodeSourceCounts = episodeSourceCounts,
                        checkingEpisodeId = checkingEpisodeId,
                        onCheckEpisode = { video -> checkEpisodeSources(video) },
                        episodeLabels = episodeLabels,
                        onBack = { selectedItem = null }
                    )
                }
            }

            configBrowserUrl?.let { configUrl ->
                Dialog(
                    onDismissRequest = { configBrowserUrl = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Column(Modifier.fillMaxSize().background(Color.Black)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "إعداد الإضافة",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { openExternalUrl(configUrl) }) {
                                Text("Safari", color = Color.LightGray, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                val manifest = configBrowserCurrent
                                    ?.let { com.cinemax.app.addons.StremioAddonClient.manifestUrl(it) }
                                configBrowserUrl = null
                                if (manifest != null) {
                                    installAddon(manifest)
                                } else {
                                    status = "لم أجد رابط manifest في الصفحة؛ انسخ رابط الإضافة وألصقه"
                                }
                            }) {
                                Text("تثبيت", color = appAccent, fontSize = 13.sp)
                            }
                            TextButton(onClick = { configBrowserUrl = null }) {
                                Text("إغلاق", color = Color.White, fontSize = 12.sp)
                            }
                        }
                        InAppBrowser(
                            url = configUrl,
                            onUrl = { configBrowserCurrent = it },
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }
                }
            }

            playbackUrl?.let { mediaUrl ->
                val upcomingEpisode = nextEpisode(playingVideo, selectedMeta?.videos.orEmpty())
                val nearEnd = playerDurationMs > 0L && playerPositionMs > playerDurationMs - 45_000L
                CinemaxPlayerScreen(
                    title = playingItem?.name ?: "CINEMAX",
                    episodeLabelText = playingVideo?.let { episodeLabel(it) },
                    subtitleLabel = activeSubtitleLabel,
                    accent = appAccent,
                    message = playerMessage ?: sourceNotice,
                    loading = !playerStarted,
                    controlsVisible = controlsVisible,
                    hud = hud,
                    positionMs = playerPositionMs,
                    durationMs = playerDurationMs,
                    onSeek = { target -> playerControls?.seekTo(target) },
                    speed = playbackSpeed,
                    onSpeed = { speed ->
                        playbackSpeed = speed
                        playerControls?.setSpeed(speed)
                        if (preferences.preferredSpeed != speed) {
                            preferences = preferences.copy(preferredSpeed = speed)
                            preferencesStore.save(preferences)
                        }
                    },
                    nextEpisodeLabel = if (nearEnd) upcomingEpisode?.let { episodeLabel(it) } else null,
                    onNextEpisode = if (nearEnd && upcomingEpisode != null) {
                        {
                            autoplayQueued = true
                            playbackUrl = null
                            playingItem = null
                            playingVideo = null
                            playingStream = null
                            selectedVideo = upcomingEpisode
                            activeSeason = upcomingEpisode.season
                        }
                    } else null,
                    sourceLabel = playingStream?.let { option ->
                        listOfNotNull(
                            option.addonName,
                            option.stream.name,
                            if (preferences.preferredPlayer == "system" || usingSystemFallback) "AVPlayer" else "MPV"
                        ).joinToString(" • ")
                    },
                    onNextShortcut = upcomingEpisode?.let { next ->
                        {
                            autoplayQueued = true
                            playbackUrl = null
                            playingItem = null
                            playingVideo = null
                            playingStream = null
                            selectedVideo = next
                            activeSeason = next.season
                        }
                    },
                    onClose = {
                        playbackUrl = null
                        playingItem = null
                        playingVideo = null
                        playingStream = null
                        showSubtitlePicker = false
                        showPlayerSources = false
                        showAudioPicker = false
                    },
                    onSources = if (streamOptions.count { it.stream.url != null } > 1) {
                        { showPlayerSources = true }
                    } else null,
                    audioLabel = embeddedAudioTracks
                        .firstOrNull {
                            it.id == selectedAudioTrackId ||
                                (selectedAudioTrackId == null && it.selected)
                        }
                        ?.let { embeddedAudioLabel(it) },
                    onAudio = if (embeddedAudioTracks.size > 1) {
                        { showAudioPicker = true }
                    } else null,
                    onSubtitles = { showSubtitlePicker = true }
                ) {
                    Box(Modifier.fillMaxSize()) {
                        NativeVideoPlayer(
                            url = mediaUrl,
                            modifier = Modifier.fillMaxSize(),
                            initialPositionMillis = playingItem?.let { item ->
                                val key = playingVideo?.id ?: item.id
                                watchProgressItems.firstOrNull { it.key == key }?.positionMillis ?: 0L
                            } ?: 0L,
                            preferredSubtitleLanguage = preferences.preferredSubtitleLanguage.takeIf { it != "off" },
                            preferredAudioLanguage = preferences.preferredAudioLanguage.takeIf { it != "device" },
                            embeddedSubtitleTrackId = selectedEmbeddedTrackId,
                            selectedAudioTrackId = selectedAudioTrackId,
                            onEmbeddedAudioTracks = { tracks ->
                                if (tracks != embeddedAudioTracks.toList()) {
                                    embeddedAudioTracks.clear()
                                    embeddedAudioTracks.addAll(tracks)
                                }
                            },
                            useSystemPlayer = preferences.preferredPlayer == "system" || usingSystemFallback,
                            onEmbeddedSubtitleTracks = { tracks ->
                                if (tracks != embeddedSubtitleTracks.toList()) {
                                    embeddedSubtitleTracks.clear()
                                    embeddedSubtitleTracks.addAll(tracks)
                                }
                                if (!userChoseSubtitle && selectedSubtitleUrl == null &&
                                    selectedEmbeddedTrackId == null
                                ) {
                                    val preferred = preferences.preferredSubtitleLanguage
                                    if (preferred != "off") {
                                        val index = languageIndex(tracks.map { it.language }, preferred)
                                        if (index != null) selectedEmbeddedTrackId = tracks[index].id
                                    }
                                }
                            },
                            httpHeaders = playingStream?.stream?.behaviorHints?.proxyHeaders?.request.orEmpty(),
                            onProgress = { positionMillis, durationMillis ->
                                playerDurationMs = durationMillis
                                val item = playingItem
                                if (item != null && preferences.rememberPlaybackPosition) {
                                    val progress = WatchProgress(
                                        item = item,
                                        positionMillis = positionMillis,
                                        durationMillis = durationMillis,
                                        updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                                        videoId = playingVideo?.id,
                                        videoLabel = playingVideo?.let { episodeLabel(it) }
                                    )
                                    watchProgressStore.save(progress)
                                    watchProgressItems.removeAll { it.key == progress.key }
                                    if (positionMillis < durationMillis - 15_000L) watchProgressItems.add(0, progress)
                                }
                            },
                            onPosition = { position ->
                                playerPositionMs = position
                                if (position > 0L) {
                                    playerStarted = true
                                    sourceNotice = null
                                    val fingerprint = playbackFingerprint
                                    if (!successRecorded && fingerprint != null) {
                                        successRecorded = true
                                        val elapsed = (Clock.System.now().toEpochMilliseconds() -
                                            playbackStartedAtMs).coerceAtLeast(0L)
                                        statsStore.recordSuccess(fingerprint, elapsed)
                                        sourceStats = sourceStats +
                                            (fingerprint to (statsStore.load(fingerprint) ?: SourceStats()))
                                    }
                                }
                            },
                            onEnded = {
                                val next = if (preferences.autoplayNextEpisode) {
                                    nextEpisode(playingVideo, selectedMeta?.videos.orEmpty())
                                } else null
                                if (next != null) {
                                    autoplayQueued = true
                                    playbackUrl = null
                                    playingItem = null
                                    playingVideo = null
                                    playingStream = null
                                    selectedVideo = next
                                    activeSeason = next.season
                                    status = "الحلقة التالية: ${episodeLabel(next)}"
                                } else {
                                    // Nothing queued: leave the frozen last frame and
                                    // return to the detail screen instead.
                                    playbackUrl = null
                                    playingItem = null
                                    playingVideo = null
                                    playingStream = null
                                    showSubtitlePicker = false
                                    showAudioPicker = false
                                }
                            },
                            onError = { message ->
                                playerMessage = message
                                val failed = playbackFingerprint
                                if (failed != null) {
                                    statsStore.recordFailure(failed)
                                    sourceStats = sourceStats +
                                        (failed to (statsStore.load(failed) ?: SourceStats()))
                                }
                                // Engine failover first: the same link may play with the
                                // system player, which costs no source attempt.
                                if (!usingSystemFallback && !playerStarted &&
                                    preferences.preferredPlayer != "system" &&
                                    isNativeFullFormatPlayerAvailable()
                                ) {
                                    usingSystemFallback = true
                                    sourceNotice = "تعذّر مع MPV — نجرّب مشغل النظام"
                                } else if (!playerStarted) {
                                    val next = nextPlayableSource()
                                    if (next != null) {
                                        val label = next.first.stream.name ?: next.first.addonName
                                        switchToSource(next.first, next.second)
                                        sourceNotice = "تعذّر تشغيل المصدر — نجرّب: $label"
                                    } else {
                                        sourceNotice = "تعذّر تشغيل المصدر ولم يبقَ رابط آخر — افتح «المصادر» واختر غيره"
                                    }
                                }
                            },
                            onControlsReady = { playerControls = it },
                            onGestureTap = {
                                controlsVisible = !controlsVisible
                                hapticTap()
                            },
                            onGestureHud = { message -> hud = message }
                        )
                        val cue = activeCue(
                            subtitleCues,
                            (playerPositionMs - subtitleDelayMs).coerceAtLeast(0L)
                        )
                        if (cue != null) {
                            Text(
                                cue,
                                color = Color.White,
                                fontSize = when (preferences.subtitleSize) {
                                    "small" -> 13.sp
                                    "large" -> 21.sp
                                    else -> 16.sp
                                },
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 12.dp, end = 12.dp, bottom = 44.dp)
                                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (showPlayerSources) {
                Dialog(
                    onDismissRequest = { showPlayerSources = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth(0.94f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Panel)
                            .padding(16.dp)
                    ) {
                        Text("بدّل المصدر", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "المصادر مرتّبة بالأفضل. التبديل يبدأ من مصدر آخر ويُبقي المشغّل مفتوحًا.",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rankedSources(
                                streamOptions.toList(),
                                onlyDirect = true,
                                lastPlayedUrl = lastPlayedUrl
                            ).forEach { option ->
                                val url = option.stream.url ?: return@forEach
                                val current = url == playbackUrl
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (current) appAccent.copy(alpha = 0.12f) else Color(0xFF10131C))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            option.stream.name ?: option.stream.title ?: "مصدر",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            sourceProviders[streamFingerprint(option)]
                                                ?.joinToString(" · ")
                                                ?: option.addonName,
                                            color = Color.LightGray,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                    if (url == pinnedSourceUrl) {
                                        Text("مفضل", color = appAccent, fontSize = 10.sp)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    if (current) {
                                        Text("يعمل الآن", color = appAccent, fontSize = 11.sp)
                                    } else {
                                        TextButton(onClick = { switchToSource(option, url) }) {
                                            Text("تشغيل", fontSize = 12.sp, color = appAccent)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showPlayerSources = false }) { Text("إغلاق") }
                    }
                }
            }

            if (playbackUrl == null && selectedItem == null && browseShelf == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    CinemaxTabBar(
                        selected = tab,
                        accent = appAccent,
                        onSelect = { tab = it }
                    )
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("إضافة مصدر") },
                text = {
                    Column(
                        Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
                    ) {
                        Text("الصق رابط الإضافة أو رابط manifest.json", fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        val clipboard = LocalClipboardManager.current
                        OutlinedTextField(
                            value = addonUrl,
                            onValueChange = { addonUrl = it },
                            singleLine = true,
                            placeholder = { Text("https://example.com/manifest.json") }
                        )
                        TextButton(onClick = {
                            clipboard.getText()?.text?.trim()
                                ?.takeIf { it.startsWith("http") }
                                ?.let { addonUrl = it }
                        }) { Text("لصق من الحافظة", fontSize = 11.sp) }
                        if (suggestedSources.isNotEmpty()) {
                            val typed = suggestedQuery.trim()
                            val visible = if (typed.isEmpty()) suggestedSources
                            else suggestedSources.filter { matchesLibraryQuery(it.name, typed) }
                            Spacer(Modifier.height(18.dp))
                            Text(
                                "مصادر جاهزة (${suggestedSources.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "من ملفّاتك المصدَّرة، دُمجت وحُذف المكرر. اضغط «تثبيت» لإضافة المصدر.",
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = suggestedQuery,
                                onValueChange = { suggestedQuery = it },
                                singleLine = true,
                                placeholder = { Text("ابحث بين المصادر") }
                            )
                            Spacer(Modifier.height(4.dp))
                            if (visible.isEmpty()) {
                                Text("لا مصدر بهذا الاسم", fontSize = 12.sp, color = Color.LightGray)
                            }
                            for (source in visible) {
                                val installed = addons.any { it.manifestUrl == source.url }
                                val busy = installingSourceUrl == source.url
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        source.name,
                                        Modifier.weight(1f),
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                    when {
                                        busy -> Text("جارٍ…", fontSize = 11.sp, color = Color.LightGray)
                                        installed -> Text("مثبّت", fontSize = 11.sp, color = appAccent)
                                        else -> TextButton(
                                            enabled = !loading,
                                            onClick = { installAddon(source.url) }
                                        ) { Text("تثبيت", fontSize = 12.sp) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "استكشاف إضافات",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "قائمة إضافات المجتمع العامة من Stremio. تُجلب عند الطلب فقط.",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        TextButton(onClick = { loadAddonDirectory() }, enabled = !directoryLoading) {
                            Text(
                                if (directoryLoading) "جارٍ تحميل القائمة…" else "تحميل القائمة العامة",
                                fontSize = 12.sp
                            )
                        }
                        directoryEntries.take(40).forEach { entry ->
                            val entryName = entry.manifest?.name ?: entry.installUrl
                            val installed = addons.any { it.manifestUrl == entry.installUrl }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entryName, color = Color.White, fontSize = 12.sp, maxLines = 1)
                                    entry.manifest?.description?.takeIf { it.isNotBlank() }?.let { text ->
                                        Text(text, fontSize = 10.sp, color = Color.LightGray, maxLines = 1)
                                    }
                                }
                                if (installed) {
                                    Text("مثبّت", fontSize = 11.sp, color = appAccent)
                                } else {
                                    TextButton(
                                        enabled = !loading,
                                        onClick = { installAddon(entry.installUrl) }
                                    ) { Text("تثبيت", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(enabled = addonUrl.isNotBlank() && !loading, onClick = {
                        val input = addonUrl.trim()
                        showAddDialog = false
                        installAddon(input)
                    }) { Text("تحقق وأضف") }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") } }
            )
        }

        if (showNuvioDialog) {
            AlertDialog(
                onDismissRequest = { showNuvioDialog = false },
                title = { Text("مصادر Nuvio") },
                text = {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "ألصق رابط مستودع Nuvio (ملف manifest.json) لتثبيت مشاهده، ثم فعّل المصادر التي تريدها.",
                            fontSize = 12.sp, color = Color.LightGray
                        )
                        OutlinedTextField(
                            value = nuvioRepoInput,
                            onValueChange = { nuvioRepoInput = it },
                            singleLine = true,
                            label = { Text("رابط المستودع") }
                        )
                        Button(
                            enabled = nuvioRepoInput.isNotBlank() && !nuvioBusy,
                            onClick = {
                                val url = nuvioRepoInput.trim()
                                nuvioBusy = true
                                scope.launch {
                                    try {
                                        val repository = nuvioClient.loadRepository(url)
                                        nuvioRepositories.removeAll { it.manifestUrl == repository.manifestUrl }
                                        nuvioRepositories.add(repository)
                                        nuvioStore.saveRepositories(nuvioRepositories.toList())
                                        val enabledByDefault = repository.scrapers
                                            .filter { it.enabled }
                                            .map { "${repository.manifestUrl}#${it.id}" }
                                        if (enabledByDefault.isNotEmpty()) {
                                            nuvioEnabled.addAll(enabledByDefault)
                                            nuvioStore.saveEnabledProviders(nuvioEnabled.toSet())
                                        }
                                        nuvioRepoInput = ""
                                        status = "تم تثبيت ${repository.name} • ${repository.scrapers.size} مصدرًا"
                                    } catch (error: Throwable) {
                                        if (error is CancellationException) throw error
                                        status = "تعذر تثبيت مستودع Nuvio: ${error.message ?: "خطأ غير معروف"}"
                                    } finally {
                                        nuvioBusy = false
                                    }
                                }
                            }
                        ) { Text("تحقق وأضف") }
                        if (nuvioRepositories.isEmpty()) {
                            Text("لا يوجد مستودع مثبت بعد.", color = Color.LightGray, fontSize = 12.sp)
                        }
                        nuvioRepositories.forEach { repository ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${repository.name} • ${repository.version}",
                                    color = Color.White, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    nuvioRepositories.removeAll { it.manifestUrl == repository.manifestUrl }
                                    nuvioEnabled.removeAll { it.startsWith(repository.manifestUrl + "#") }
                                    nuvioStore.saveRepositories(nuvioRepositories.toList())
                                    nuvioStore.saveEnabledProviders(nuvioEnabled.toSet())
                                    status = "تم إزالة ${repository.name}"
                                }) { Text("إزالة", fontSize = 12.sp) }
                            }
                            repository.scrapers.forEach { scraper ->
                                val key = "${repository.manifestUrl}#${scraper.id}"
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(scraper.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    Switch(
                                        checked = key in nuvioEnabled,
                                        onCheckedChange = { checked ->
                                            if (checked) nuvioEnabled.add(key) else nuvioEnabled.remove(key)
                                            nuvioStore.saveEnabledProviders(nuvioEnabled.toSet())
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showNuvioDialog = false }) { Text("تم") } }
            )
        }

        if (showSubtitlePicker) {
            AlertDialog(
                onDismissRequest = { showSubtitlePicker = false },
                title = { Text("الترجمة") },
                text = {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("حجم الترجمة", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            listOf("small" to "صغير", "medium" to "وسط", "large" to "كبير")
                                .forEach { (value, label) ->
                                    CinemaxFilterChip(
                                        selected = preferences.subtitleSize == value,
                                        onClick = {
                                            preferences = preferences.copy(subtitleSize = value)
                                            preferencesStore.save(preferences)
                                        },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("تأخير الترجمة", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                subtitleDelayMs = (subtitleDelayMs - 500L).coerceAtLeast(-5_000L)
                            }) { Text("−0.5", fontSize = 12.sp) }
                            Text(
                                "${subtitleDelayMs / 1000.0} ث",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            TextButton(onClick = {
                                subtitleDelayMs = (subtitleDelayMs + 500L).coerceAtMost(10_000L)
                            }) { Text("+0.5", fontSize = 12.sp) }
                        }
                        SubtitleRow(
                            label = "إيقاف",
                            detail = "بدون ترجمة",
                            selected = selectedSubtitleUrl == null && selectedEmbeddedTrackId == null,
                            accent = appAccent
                        ) {
                            applySubtitle(null)
                            showSubtitlePicker = false
                        }
                        if (embeddedSubtitleTracks.isNotEmpty()) {
                            Text(
                                "ترجمات مدمجة في الفيديو نفسه",
                                color = appAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            embeddedSubtitleTracks.forEach { track ->
                                SubtitleRow(
                                    label = embeddedSubtitleLabel(track),
                                    detail = embeddedSubtitleDetail(track),
                                    selected = selectedEmbeddedTrackId == track.id,
                                    accent = appAccent
                                ) {
                                    userChoseSubtitle = true
                                    selectedEmbeddedTrackId = track.id
                                    selectedSubtitleUrl = null
                                    subtitleCues = emptyList()
                                    showSubtitlePicker = false
                                }
                            }
                        }
                        if (subtitleOptions.isNotEmpty()) {
                            Text(
                                "ترجمات من الإضافات",
                                color = appAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        subtitleOptions.forEach { choice ->
                            SubtitleRow(
                                label = choice.label,
                                detail = "${choice.source} • ${choice.lang}",
                                selected = choice.url == selectedSubtitleUrl,
                                accent = appAccent
                            ) {
                                applySubtitle(choice)
                                showSubtitlePicker = false
                            }
                        }
                        if (subtitleOptions.isEmpty() && embeddedSubtitleTracks.isEmpty()) {
                            Text(
                                "لا توجد ترجمات من إضافاتك لهذا العنصر. جرّب مصدرًا آخر أو ثبّت إضافة ترجمة.",
                                color = Color.LightGray, fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitlePicker = false }) { Text("إغلاق") }
                }
            )
        }

        if (showAudioPicker) {
            AlertDialog(
                onDismissRequest = { showAudioPicker = false },
                title = { Text("الصوت") },
                text = {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "مسارات الصوت المدمجة في الفيديو نفسه. اختيار مسار يبدّله فورًا بدون إعادة التشغيل.",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        embeddedAudioTracks.forEach { track ->
                            val current = (selectedAudioTrackId ?: embeddedAudioTracks.firstOrNull { it.selected }?.id) == track.id
                            SubtitleRow(
                                label = embeddedAudioLabel(track),
                                detail = embeddedAudioDetail(track),
                                selected = current,
                                accent = appAccent
                            ) {
                                selectedAudioTrackId = track.id
                                showAudioPicker = false
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioPicker = false }) { Text("إغلاق") }
                }
            )
        }

        if (showRoadmap) {
            Dialog(
                onDismissRequest = { showRoadmap = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                CinemaxRoadmapScreen(
                    phases = CinemaxRoadmap,
                    accent = appAccent,
                    background = appBackground,
                    versionLabel = appVersionLabel(),
                    onClose = { showRoadmap = false }
                )
            }
        }
    }
}

@Composable
private fun SubtitleRow(
    label: String,
    detail: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color.LightGray, fontSize = 11.sp)
        }
        if (selected) Text("✓", color = accent, fontSize = 16.sp)
    }
}

/**
 * Asks every installed addon that serves metadata at once and takes the first
 * answer in install order, so a dead addon cannot hold the detail screen for its
 * full request timeout. Each call is capped by [META_TIMEOUT_MS].
 */
private suspend fun metaFrom(
    repository: AddonRepository,
    addons: List<InstalledAddon>,
    type: String,
    mediaId: String
): StremioMeta? = coroutineScope {
    val candidates = addons.filter { AddonCapabilities.supportsMeta(it, type) }
    if (candidates.isEmpty()) return@coroutineScope null
    val requests = candidates.map { addon ->
        async {
            try {
                withTimeout(META_TIMEOUT_MS) {
                    repository.loadMeta(addon.manifestUrl, type, mediaId)
                }
            } catch (_: TimeoutCancellationException) {
                null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
        }
    }
    var found: StremioMeta? = null
    for (request in requests) {
        found = request.await()
        if (found != null) break
    }
    found
}

/**
 * Collects sources from every installed addon that serves streams for the type,
 * in parallel. A failing addon must not hide the sources of the others, and one
 * that hangs is abandoned after [STREAM_TIMEOUT_MS] instead of holding the list.
 */
private suspend fun streamsFrom(
    repository: AddonRepository,
    addons: List<InstalledAddon>,
    type: String,
    mediaId: String
): List<StreamOption> = coroutineScope {
    val requests = addons
        .filter { AddonCapabilities.supportsStreams(it, type, mediaId) }
        .map { addon ->
            async {
                try {
                    withTimeout(STREAM_TIMEOUT_MS) {
                        repository.loadStreams(addon.manifestUrl, type, mediaId)
                            .map { stream ->
                                // YouTube streams are opened as their official watch page.
                                val playable = if (stream.url == null && stream.infoHash == null &&
                                    stream.ytId != null
                                ) {
                                    stream.copy(externalUrl = "https://www.youtube.com/watch?v=${stream.ytId}")
                                } else stream
                                StreamOption(addon.id, addon.name, playable)
                            }
                    }
                } catch (_: TimeoutCancellationException) {
                    emptyList<StreamOption>()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    emptyList<StreamOption>()
                }
            }
        }
    requests.awaitAll().flatten().distinctBy { option ->
        option.stream.url ?: "torrent:${option.stream.infoHash}:${option.stream.fileIdx}"
    }
}

private const val META_TIMEOUT_MS = 10_000L
private const val STREAM_TIMEOUT_MS = 15_000L
private const val SUBTITLE_TIMEOUT_MS = 10_000L
