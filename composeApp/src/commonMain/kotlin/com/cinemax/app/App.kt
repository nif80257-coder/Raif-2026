package com.cinemax.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cinemax.app.addons.AddonCapabilities
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
import com.cinemax.app.addons.searchAllAddons
import com.cinemax.app.archive.ARCHIVE_GENRES
import com.cinemax.app.archive.ARCHIVE_PROVIDER_NAME
import com.cinemax.app.archive.ARCHIVE_SORTS
import com.cinemax.app.archive.archiveSortValue
import com.cinemax.app.archive.ArchiveClient
import com.cinemax.app.archive.archiveIdentifier
import com.cinemax.app.archive.isArchiveItemId
import com.cinemax.app.core.CinemaxRoadmap
import com.cinemax.app.core.LibrarySort
import com.cinemax.app.core.EmbeddedSubtitleTrack
import com.cinemax.app.core.SubtitleCue
import com.cinemax.app.core.activeCue
import com.cinemax.app.core.appVersionLabel
import com.cinemax.app.core.embeddedSubtitleDetail
import com.cinemax.app.core.embeddedSubtitleLabel
import com.cinemax.app.core.episodeLabel
import com.cinemax.app.core.languageIndex
import com.cinemax.app.core.nextEpisode
import com.cinemax.app.core.parseSubtitles
import com.cinemax.app.core.posterStatusById
import com.cinemax.app.core.rankSearchResults
import com.cinemax.app.core.sortLibrary
import com.cinemax.app.core.unsupportedVideoContainer
import com.cinemax.app.nuvio.InstalledNuvioRepository
import com.cinemax.app.nuvio.NuvioMediaIds
import com.cinemax.app.nuvio.NuvioProviderClient
import com.cinemax.app.nuvio.NuvioProviderEntry
import com.cinemax.app.nuvio.NuvioProviderStore
import com.cinemax.app.nuvio.TmdbIdResolver
import com.cinemax.app.nuvio.createNuvioHttpClient
import com.cinemax.app.nuvio.createNuvioScriptRuntime
import com.cinemax.app.nuvio.nuvioSourcesFrom
import com.cinemax.app.ui.CinemaxCatalogScreen
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
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

private val Ink = Color(0xFF090B12)
private val Panel = Color(0xFF171B28)
private val Accent = Color(0xFF8B5CF6)

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
    var searchHistory by remember { mutableStateOf(searchHistoryStore.load()) }
    var searchType by remember { mutableStateOf("movie") }
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
    var librarySort by remember { mutableStateOf(LibrarySort.Recent) }
    var libraryQuery by remember { mutableStateOf("") }
    val watchProgressItems = remember { mutableStateListOf<WatchProgress>().apply { addAll(watchProgressStore.load()) } }
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
    var showAddDialog by remember { mutableStateOf(false) }
    var addonUrl by remember { mutableStateOf("") }
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
    val embeddedSubtitleTracks = remember { mutableStateListOf<EmbeddedSubtitleTrack>() }
    var selectedEmbeddedTrackId by remember { mutableStateOf<Long?>(null) }
    // Set once a subtitle choice was made, so a late track list cannot override it.
    var userChoseSubtitle by remember { mutableStateOf(false) }
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

    // Metadata may come from any installed addon that declares the `meta`
    // resource, and a series episode is chosen before its sources are requested.
    LaunchedEffect(selectedItem?.id, pendingVideoId, addonSignature) {
        val item = selectedItem
        if (item == null) {
            selectedMeta = null
            metaResolved = false
            selectedVideo = null
            activeSeason = null
            loadingMeta = false
            return@LaunchedEffect
        }
        if (lastOpenedItemId != item.id) {
            lastOpenedItemId = item.id
            selectedVideo = null
            activeSeason = null
        }
        metaResolved = false
        loadingMeta = true
        val meta = try {
            if (isArchiveItemId(item.id)) {
                archive.meta(archiveIdentifier(item.id))
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
        lastPlayedUrl = lastSourceStore.load(item.id)
        val videos = meta?.videos.orEmpty()
        val nextVideo = videos.firstOrNull { it.id == pendingVideoId }
            ?: videos.firstOrNull { it.id == selectedVideo?.id }
            ?: videos.firstOrNull()
        if (nextVideo?.id != selectedVideo?.id) selectedVideo = nextVideo
        if (nextVideo?.season != activeSeason) activeSeason = nextVideo?.season
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
    LaunchedEffect(selectedItem?.id, selectedVideo?.id, metaResolved, addonSignature, nuvioSignature) {
        val item = selectedItem
        if (item == null || !metaResolved) {
            streamOptions.clear()
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
            val stremioSources = if (isArchiveItemId(item.id)) {
                archive.streams(archiveIdentifier(item.id))
                    .map { StreamOption(item.id, ARCHIVE_PROVIDER_NAME, it) }
            } else {
                streamsFrom(repository, addons.toList(), item.type, mediaId)
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
            val found = stremioSources + nuvioSources
            streamOptions.addAll(found)
            if (autoplayQueued) {
                autoplayQueued = false
                val auto = streamOptions.firstOrNull { option ->
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
            } else "المصادر: ${found.size}"
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
                            items = items,
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
        val mediaId = selectedVideo?.id ?: item.id
        val hints = stream.stream.behaviorHints
        val extra = mutableMapOf<String, String>()
        hints.videoHash?.let { extra["videoHash"] = it }
        hints.videoSize?.let { extra["videoSize"] = it.toString() }
        subtitleBusy = true
        try {
            addons
                .filter { AddonCapabilities.supports(it, "subtitles", item.type) }
                .forEach { addon ->
                    val found = try {
                        repository.loadSubtitles(addon.manifestUrl, item.type, mediaId, extra)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    found.forEach { subtitle ->
                        collected.add(
                            SubtitleChoice(subtitle.lang, subtitle.label ?: subtitle.lang, addon.name, subtitle.url)
                        )
                    }
                }
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
                catalogs = manifest.catalogs
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

    /** Reloads the active addon's catalog and shelves, for the home refresh button. */
    fun refreshHome() {
        val addon = activeAddon ?: return
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

    /** Opens a home shelf as a full grid, keeping the page it already loaded. */
    fun openShelf(shelf: CinemaxShelf) {
        browseShelf = shelf
        browseGenre = null
        browseSort = null
        browseItems.clear()
        browseItems.addAll(shelf.items)
        browseExhausted = !shelf.isBrowsable
        // An archive shelf already holds page one, so the next request is page two.
        browseArchivePage = if (shelf.archive) 2 else 1
        status = "${shelf.title} • ${shelf.items.size} عنوانًا"
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
        browseItems.clear()
        browseExhausted = false
        browseArchivePage = 1
        loadShelfPage(replace = true)
    }

    /** Switches the opened grid to another genre, restarting it from page one. */
    fun selectShelfGenre(genre: String?) {
        if (browseGenre == genre) return
        browseGenre = genre
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
        lastSourceStore.save(item.id, mediaUrl)
        lastPlayedUrl = mediaUrl
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
    }


    // Whether the search tab can ask anything with the current type choice.
    val canSearchNow = if (searchType == "all") {
        addons.isNotEmpty()
    } else {
        addons.any { AddonCapabilities.supports(it, "catalog", searchType) }
    }

    /** Runs one cross-addon search and ranks the merged results. */
    fun runSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty() || searching) return
        searching = true
        scope.launch {
            try {
                val types = if (searchType == "all") listOf("movie", "series") else listOf(searchType)
                val addonHits = types
                    .flatMap { type -> searchAllAddons(repository, addons.toList(), type, query) }
                val archiveHits = if (searchType != "series") {
                    runCatching { archive.search(query) }.getOrNull().orEmpty()
                        .map { CatalogEntry("archive", ARCHIVE_PROVIDER_NAME, it) }
                } else emptyList()
                val results = (addonHits + archiveHits).distinctBy { it.item.id }
                val ranked = rankSearchResults(results, query)
                searchHistoryStore.add(query)
                searchHistory = searchHistoryStore.load()
                searchResults.clear()
                searchResults.addAll(ranked)
                val addonCount = ranked.map { it.addonName }.distinct().size
                status = if (ranked.isEmpty()) "لا نتائج في إضافاتك"
                else "نتائج البحث: ${ranked.size} من $addonCount إضافة"
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                status = "تعذر البحث: ${error.message ?: "خطأ غير معروف"}"
            } finally {
                searching = false
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
                    items = catalogItems.toList(),
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

    MaterialTheme(colorScheme = darkColorScheme(
        background = appBackground, surface = Panel, primary = appAccent,
        onBackground = Color.White, onSurface = Color.White
    )) {
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
                    onRefresh = { refreshHome() },
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
                    onSort = { librarySort = it },
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
                    }
                )
                else -> CinemaxSettingsScreen(
                    preferences = preferences,
                    onPreferences = { updated ->
                        preferences = updated
                        preferencesStore.save(updated)
                    },
                    addons = addons.toList(),
                    activeAddonId = activeAddon?.id,
                    onSelectAddon = { addon -> selectAddon(addon) },
                    onRemoveAddon = { addon ->
                        addons.remove(addon)
                        addonStore.save(addons.toList())
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
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(160))
            ) {
                val item = lastDetailItem
                if (item != null) {
                    val resumeProgress = watchProgressItems.firstOrNull { it.item.id == item.id }
                    CinemaxDetailScreen(
                        item = item,
                        meta = selectedMeta,
                        loadingMeta = loadingMeta,
                        sources = streamOptions.toList(),
                        loadingSources = loadingStreams,
                        sourcesMessage = status,
                        selectedVideo = selectedVideo,
                        activeSeason = activeSeason,
                        isFavorite = item.id in favoriteIds,
                        accent = appAccent,
                        background = appBackground,
                        lastPlayedUrl = lastPlayedUrl,
                        providerIssues = providerIssues,
                        resumeLabel = resumeProgress?.let { "من ${formatClock(it.positionMillis)}" },
                        episodeProgress = watchProgressItems.associate { it.key to it.progressFraction },
                        onToggleFavorite = {
                            hapticTap()
                            if (item.id in favoriteIds) {
                                favoriteItems.removeAll { it.id == item.id }
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
                        onBack = { selectedItem = null }
                    )
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
                    message = playerMessage,
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
                    onClose = {
                        playbackUrl = null
                        playingItem = null
                        playingVideo = null
                        playingStream = null
                        showSubtitlePicker = false
                    },
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
                            useSystemPlayer = preferences.preferredPlayer == "system",
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
                            onPosition = { playerPositionMs = it; if (it > 0L) playerStarted = true },
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
                                }
                            },
                            onError = { playerMessage = it },
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
                    Column {
                        Text("الصق رابط الإضافة أو رابط manifest.json", fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = addonUrl,
                            onValueChange = { addonUrl = it },
                            singleLine = true,
                            placeholder = { Text("https://example.com/manifest.json") }
                        )
                    }
                },
                confirmButton = {
                    Button(enabled = addonUrl.isNotBlank() && !loading, onClick = {
                        val input = addonUrl.trim()
                        showAddDialog = false
                        loading = true
                        status = "جارٍ التحقق من الإضافة..."
                        scope.launch {
                            try {
                                val manifest = repository.loadManifest(input)
                                availableCatalogs.clear()
                                availableCatalogs.addAll(manifest.catalogs)
                                val normalized = com.cinemax.app.addons.StremioAddonClient.manifestUrl(input)!!
                                val installed = InstalledAddon(
                                    manifestUrl = normalized, id = manifest.id, name = manifest.name,
                                    version = manifest.version, description = manifest.description,
                                    installedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                                    resources = manifest.resourceNames().toList(),
                                    types = manifest.types,
                                    idPrefixes = manifest.idPrefixes,
                                    catalogs = manifest.catalogs
                                )
                                addons.removeAll { it.id == installed.id }
                                addons.add(installed)
                                addonStore.save(addons.toList())
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
                            }
                        }
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

/** Asks each installed addon that serves metadata until one returns the item. */
private suspend fun metaFrom(
    repository: AddonRepository,
    addons: List<InstalledAddon>,
    type: String,
    mediaId: String
): StremioMeta? {
    for (addon in addons.filter { AddonCapabilities.supportsMeta(it, type) }) {
        val meta = try {
            repository.loadMeta(addon.manifestUrl, type, mediaId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        if (meta != null) return meta
    }
    return null
}

/**
 * Collects sources from every installed addon that serves streams for the type,
 * in parallel. A failing addon must not hide the sources of the others.
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
                val found = try {
                    repository.loadStreams(addon.manifestUrl, type, mediaId)
                        .map { StreamOption(addon.id, addon.name, it) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    emptyList<StreamOption>()
                }
                found
            }
        }
    requests.awaitAll().flatten().distinctBy { option ->
        option.stream.url ?: "torrent:${option.stream.infoHash}:${option.stream.fileIdx}"
    }
}
