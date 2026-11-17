package com.cinemax.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cinemax.app.CinemaxPreferences
import com.cinemax.app.WatchProgress
import com.cinemax.app.addons.CatalogEntry
import com.cinemax.app.addons.InstalledAddon
import com.cinemax.app.addons.StreamOption
import com.cinemax.app.addons.StremioMeta
import com.cinemax.app.addons.StremioMetaPreview
import com.cinemax.app.addons.StremioVideo
import com.cinemax.app.core.LibrarySort
import com.cinemax.app.core.PosterStatus
import com.cinemax.app.core.BADGE_PACKS
import com.cinemax.app.core.BadgeFilter
import com.cinemax.app.core.matchingBadges
import com.cinemax.app.core.episodeLabel
import com.cinemax.app.core.episodeReleaseLabel
import com.cinemax.app.core.librarySortLabel
import com.cinemax.app.core.matchesLibraryQuery
import com.cinemax.app.core.bestSource
import com.cinemax.app.core.SourceStats
import com.cinemax.app.core.isFailedRecently
import com.cinemax.app.core.rankSources
import com.cinemax.app.core.streamBadges
import com.cinemax.app.core.streamFingerprint
import com.cinemax.app.core.streamLanguageLabel
import com.cinemax.app.formatClock
import com.cinemax.app.hapticTap
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * One titled row of posters on the home screen. The addon and catalog it came
 * from are kept so the row can be opened as a full grid and paged with `skip`.
 */
data class CinemaxShelf(
    val title: String,
    val items: List<StremioMetaPreview>,
    val manifestUrl: String? = null,
    val catalogType: String? = null,
    val catalogId: String? = null,
    val supportsSkip: Boolean = false,
    /** Genres the catalog declares, offered as chips in the full grid. */
    val genreOptions: List<String> = emptyList(),
    /** True for the built-in Internet Archive shelf, which pages itself. */
    val archive: Boolean = false,
    /** Sort orders the shelf offers (only the archive shelf has any for now). */
    val sortOptions: List<String> = emptyList()
) {
    val isBrowsable: Boolean
        get() = archive || (manifestUrl != null && catalogType != null && catalogId != null)
}

/**
 * Floating pill navigation, the shape Nuvio uses: inset from the edges, one
 * highlighted destination, labels under the glyphs.
 */
@Composable
fun CinemaxTabBar(selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    val entries = listOf(
        "الرئيسية" to "⌂",
        "بحث" to "⌕",
        "مكتبتي" to "▤",
        "الإعدادات" to "⚙"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xE61C1C1E))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            entries.forEachIndexed { index, entry ->
                val active = selected == index
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Color(0xFF3A3A3C) else Color.Transparent)
                        .clickable {
                            hapticTap()
                            onSelect(index)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        entry.second,
                        color = if (active) accent else Color.White,
                        fontSize = 17.sp
                    )
                    Text(
                        entry.first,
                        color = if (active) accent else CinemaxPalette.textMuted,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Home: hero pager, continue watching, then one poster shelf per catalog. */
@Composable
fun CinemaxHomeScreen(
    heroItems: List<StremioMetaPreview>,
    shelves: List<CinemaxShelf>,
    continueWatching: List<WatchProgress>,
    loading: Boolean,
    status: String,
    accent: Color,
    background: Color,
    onOpenItem: (StremioMetaPreview) -> Unit,
    onResume: (WatchProgress) -> Unit,
    onRemoveProgress: (WatchProgress) -> Unit,
    onAddAddon: () -> Unit,
    onNuvio: () -> Unit,
    onToggleFavorite: (StremioMetaPreview) -> Unit,
    error: String? = null,
    onRetry: () -> Unit = {},
    versionLabel: String,
    tonightPick: StremioMetaPreview?,
    onShuffleTonight: () -> Unit,
    onRefresh: () -> Unit,
    onBrowseShelf: (CinemaxShelf) -> Unit,
    posterStatus: Map<String, PosterStatus>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(top = 6.dp, bottom = 110.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "CINEMAX",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp,
                        color = CinemaxPalette.textMuted
                    )
                    Text("الرئيسية", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onRefresh) {
                    Text("↻", color = Color.White, fontSize = 20.sp)
                }
            }
        }
        if (heroItems.isEmpty()) {
            if (loading) {
                item { ShimmerHero() }
                item { ShimmerShelf() }
            } else {
                item { CinemaxWelcomeCard(accent = accent, background = background, onAddAddon = onAddAddon, onNuvio = onNuvio) }
                if (error != null) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CinemaxPalette.surface)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "المكتبة الحرة لم تُحمّل",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                error,
                                color = CinemaxPalette.textMuted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                            TextButton(onClick = onRetry) {
                                Text("أعد المحاولة", color = accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "لا يوجد كتالوج بعد. أضف Cinemeta أو أي إضافة كتالوج وسنملأ الصفحة بالبوسترات.",
                            color = CinemaxPalette.textMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("إصدار التطبيق: $versionLabel", color = CinemaxPalette.textMuted, fontSize = 11.sp)
                    }
                }
            }
        } else {
            item { CinemaxHeroPager(items = heroItems, accent = accent, onOpen = onOpenItem) }
            if (continueWatching.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    CinemaxSectionTitle(title = "أكمل المشاهدة", accent = accent)
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(continueWatching, key = { it.key }) { progress ->
                            ContinueWatchingCard(
                                progress = progress,
                                accent = accent,
                                onOpen = { onResume(progress) },
                                onRemove = { onRemoveProgress(progress) }
                            )
                        }
                    }
                }
            }
            shelves.forEach { shelf ->
                if (shelf.items.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        val more: (@Composable () -> Unit)? = if (shelf.isBrowsable) {
                            {
                                TextButton(onClick = { onBrowseShelf(shelf) }) {
                                    Text("المزيد", color = accent, fontSize = 12.sp)
                                }
                            }
                        } else null
                        CinemaxSectionTitle(title = shelf.title, accent = accent, trailing = more)
                    }
                    item {
                        CinemaxRowAll {
                            items(shelf.items, key = { it.id }) { item ->
                                CinemaxPosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    badge = item.releaseInfo,
                                    accent = accent,
                                    onClick = { onOpenItem(item) },
                                    onLongClick = { onToggleFavorite(item) },
                                    status = posterStatus[item.id]
                                )
                            }
                        }
                    }
                }
            }
            tonightPick?.let { pick ->
                item {
                    Spacer(Modifier.height(4.dp))
                    CinemaxSectionTitle(
                        title = "فيلم الليلة",
                        accent = accent,
                        trailing = {
                            TextButton(onClick = onShuffleTonight) {
                                Text("بدّل", color = accent, fontSize = 12.sp)
                            }
                        }
                    )
                }
                item { TonightPickCard(pick = pick, accent = accent, onOpen = { onOpenItem(pick) }) }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    status,
                    color = CinemaxPalette.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/** Poster shelf with the shared 16dp gutters. */
@Composable
private fun CinemaxRowAll(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

/** First-run card: the brand, one line of copy and the two ways to add sources. */
@Composable
private fun CinemaxWelcomeCard(
    accent: Color,
    background: Color,
    onAddAddon: () -> Unit,
    onNuvio: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(320.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1035), Color(0xFF0D0A18), background)))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(200.dp)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent)))
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text("CINEMAX", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("ليلتك تبدأ هنا", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(
                "أفلام ومسلسلات من إضافاتك، مشغّل واحد لكل الصيغ، وترجمات مدمجة.",
                color = CinemaxPalette.textMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAddAddon,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) { Text("＋ إضافة Stremio", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onNuvio,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Nuvio", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/**
 * Full-bleed hero pager: backdrop, title, one meta line, one white pill and
 * pager dots whose active dot is a bar.
 */
@Composable
private fun CinemaxHeroPager(
    items: List<StremioMetaPreview>,
    accent: Color,
    onOpen: (StremioMetaPreview) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val kenBurns = rememberInfiniteTransition()
    val zoom by kenBurns.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    LaunchedEffect(items.size) {
        if (items.size > 1) {
            while (true) {
                delay(6500)
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next)
            }
        }
    }
    Column {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CinemaxPalette.surfaceHigh)
            ) {
                AsyncImage(
                    model = item.background ?: item.poster,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                    }
                )
                Box(Modifier.fillMaxSize().background(CinemaxPalette.artworkScrim(CinemaxPalette.background)))
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp)
                ) {
                    Text(
                        item.name,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOfNotNull(item.releaseInfo, item.imdbRating?.let { "★ $it" }).joinToString(" • "),
                        color = CinemaxPalette.textMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onOpen(item) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) { Text("عرض التفاصيل", fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(if (active) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) accent else CinemaxPalette.textMuted.copy(alpha = 0.35f))
                )
            }
        }
    }
}

/** Landscape continue-watching card with a progress bar and a time-left pill. */
@Composable
private fun ContinueWatchingCard(
    progress: WatchProgress,
    accent: Color,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .clickable(onClick = onOpen)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(135.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CinemaxPalette.surfaceHigh)
        ) {
            AsyncImage(
                model = progress.item.background ?: progress.item.poster,
                contentDescription = progress.item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(CinemaxPalette.artworkScrim(CinemaxPalette.surface)))
            Text(
                remainingLabel(progress),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(CinemaxPalette.frostedStrong, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0x8C000000))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White, fontSize = 10.sp) }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x66000000))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.progressFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(accent)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            progress.item.name,
            color =Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val subtitle = progress.videoLabel
            ?: "${(progress.progressFraction * 100).toInt()}٪ مشاهدة"
        Text(
            subtitle,
            color = CinemaxPalette.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun remainingLabel(progress: WatchProgress): String {
    val minutes = ((progress.durationMillis - progress.positionMillis).coerceAtLeast(0L)) / 60_000L
    return if (minutes > 0L) "بقي $minutes د" else "بقي أقل من دقيقة"
}

/** A moving highlight used by the loading skeletons. */
@Composable
private fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition()
    val offset by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing))
    )
    return background(
        Brush.linearGradient(
            colors = listOf(
                CinemaxPalette.surface,
                CinemaxPalette.surfaceHigh,
                CinemaxPalette.surface
            ),
            start = Offset(offset, 0f),
            end = Offset(offset + 420f, 260f)
        )
    )
}

/** Placeholder for the hero while a catalog is loading. */
@Composable
private fun ShimmerHero() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(430.dp)
            .clip(RoundedCornerShape(26.dp))
            .shimmer()
    )
}

/** Placeholder row of posters under the hero. */
@Composable
private fun ShimmerShelf() {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Box(
                Modifier
                    .width(104.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .shimmer()
            )
        }
    }
}

/** "فيلم الليلة": one wide pick with a shuffle, so the page always has an end. */
@Composable
private fun TonightPickCard(
    pick: StremioMetaPreview,
    accent: Color,
    onOpen: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CinemaxPalette.surface)
            .clickable(onClick = onOpen)
    ) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            AsyncImage(
                model = pick.background ?: pick.poster,
                contentDescription = pick.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(CinemaxPalette.artworkScrim(CinemaxPalette.surface)))
            Text(
                pick.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                listOfNotNull(pick.releaseInfo, pick.imdbRating?.let { "★ $it" }).joinToString(" • ")
                    .ifBlank { "اقتراح عشوائي من مكتبتك" },
                color = CinemaxPalette.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            Text("شاهد الآن", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Search tab: one field, names the addons asked, and a poster grid of results. */
@Composable
fun CinemaxSearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    results: List<CatalogEntry>,
    status: String,
    accent: Color,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    history: List<String>,
    onPickHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    canSearch: Boolean,
    type: String,
    onType: (String) -> Unit,
    onToggleFavorite: (StremioMetaPreview) -> Unit,
    onOpenItem: (StremioMetaPreview) -> Unit,
    statuses: Map<String, PosterStatus>
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "بحث",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
        )
        if (results.isNotEmpty()) {
            Text(
                "${results.size} نتيجة",
                color = CinemaxPalette.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "الكل", "movie" to "أفلام", "series" to "مسلسلات").forEach { (value, label) ->
                CinemaxFilterChip(
                    selected = type == value,
                    onClick = { onType(value) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("اسم فيلم أو مسلسل") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("مسح", color = accent, fontSize = 11.sp) }
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onSearch,
            enabled = canSearch && query.isNotBlank() && !searching,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text(if (searching) "جارٍ البحث…" else "ابحث في كل إضافاتك") }
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                history.forEach { entry ->
                    CinemaxFilterChip(
                        selected = false,
                        onClick = { onPickHistory(entry) },
                        label = { Text(entry, fontSize = 11.sp) }
                    )
                }
                TextButton(onClick = onClearHistory) {
                    Text("مسح السجل", color = accent, fontSize = 11.sp)
                }
            }
        }
        if (!canSearch) {
            Spacer(Modifier.height(8.dp))
            Text(
                "لا يوجد كتالوج بعد: ثبّت إضافة كتالوج (مثل Cinemeta) من الإعدادات ليصبح البحث متاحًا.",
                color = CinemaxPalette.textMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        if (results.isEmpty()) {
            Text(
                if (status.isBlank()) "اكتب اسم عنوان، وسنبحث في كل إضافة عندك." else status,
                color = CinemaxPalette.textMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results.chunked(3), key = { row -> row.joinToString("|") { it.item.id } }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { entry ->
                            GridPoster(
                                item = entry.item,
                                accent = accent,
                                onClick = { onOpenItem(entry.item) },
                                onLongClick = { onToggleFavorite(entry.item) },
                                modifier = Modifier.weight(1f),
                                sourceLabel = entry.addonName,
                                status = statuses[entry.item.id]
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Library tab: what is in progress and what was saved. */
@Composable
fun CinemaxLibraryScreen(
    favorites: List<StremioMetaPreview>,
    continueWatching: List<WatchProgress>,
    filter: String,
    accent: Color,
    onFilter: (String) -> Unit,
    sort: LibrarySort,
    onSort: (LibrarySort) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onOpenItem: (StremioMetaPreview) -> Unit,
    onResume: (WatchProgress) -> Unit,
    onRemoveProgress: (WatchProgress) -> Unit,
    onRemoveFavorite: (StremioMetaPreview) -> Unit = {}
) {
    val visibleFavorites = favorites.filter {
        (filter == "all" || it.type == filter) && matchesLibraryQuery(it.name, query)
    }
    val visibleProgress = continueWatching.filter {
        (filter == "all" || it.item.type == filter) && matchesLibraryQuery(it.item.name, query)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("مكتبتي", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    "${favorites.size} محفوظًا • ${continueWatching.size} قيد المشاهدة",
                    color = CinemaxPalette.textMuted,
                    fontSize = 12.sp
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("ابحث في مكتبتك") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { onQuery("") }) {
                            Text("مسح", color = accent, fontSize = 11.sp)
                        }
                    }
                }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "الكل", "movie" to "أفلام", "series" to "مسلسلات").forEach { (value, label) ->
                    CinemaxFilterChip(
                        selected = filter == value,
                        onClick = { onFilter(value) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LibrarySort.entries.forEach { option ->
                    CinemaxFilterChip(
                        selected = sort == option,
                        onClick = { onSort(option) },
                        label = { Text(librarySortLabel(option), fontSize = 11.sp) }
                    )
                }
            }
        }
        if (visibleProgress.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                CinemaxSectionTitle(title = "قيد المشاهدة", accent = accent)
            }
            items(visibleProgress, key = { it.key }) { progress ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CinemaxPalette.surface)
                        .clickable { onResume(progress) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(10.dp))) {
                        AsyncImage(
                            model = progress.item.background ?: progress.item.poster,
                            contentDescription = progress.item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            progress.item.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        progress.videoLabel?.let {
                            Text(it, color = CinemaxPalette.textMuted, fontSize = 11.sp, maxLines = 1)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress.progressFraction },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = accent,
                            trackColor = CinemaxPalette.surfaceHigh
                        )
                    }
                    TextButton(onClick = { onRemoveProgress(progress) }) {
                        Text("✕", color = CinemaxPalette.textMuted, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            CinemaxSectionTitle(title = "المحفوظة", accent = accent)
        }
        if (visibleFavorites.isEmpty()) {
            item {
                Text(
                    when {
                        favorites.isEmpty() -> "لا شيء محفوظ بعد. اضغط «＋ حفظ» في أي عنوان ليظهر هنا."
                        query.isNotBlank() -> "لا عنوان في مكتبتك يطابق «${query.trim()}»."
                        else -> "لا يوجد عنوان بهذا النوع في مكتبتك."
                    },
                    color = CinemaxPalette.textMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            items(visibleFavorites.chunked(3), key = { row -> row.joinToString("|") { it.id } }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        GridPoster(
                            item = item,
                            accent = accent,
                            onClick = { onOpenItem(item) },
                            onLongClick = { onRemoveFavorite(item) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun GridPoster(
    item: StremioMetaPreview,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sourceLabel: String? = null,
    status: PosterStatus? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) 0.95f else 1f)
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(CinemaxPalette.surfaceHigh)
        ) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            item.imdbRating?.let { rating ->
                Text(
                    "★ $rating",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(CinemaxPalette.rating, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            PosterStatusMarks(status = status, accent = accent)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.name,
            color = CinemaxPalette.text,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        sourceLabel?.let {
            Text(it, color = CinemaxPalette.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Full-screen detail: backdrop, actions, overview, seasons, episodes and sources. */
@Composable
fun CinemaxDetailScreen(
    item: StremioMetaPreview,
    meta: StremioMeta?,
    loadingMeta: Boolean,
    sources: List<StreamOption>,
    loadingSources: Boolean,
    sourcesMessage: String,
    selectedVideo: StremioVideo?,
    activeSeason: Int?,
    isFavorite: Boolean,
    accent: Color,
    background: Color,
    lastPlayedUrl: String?,
    providerIssues: List<String>,
    /** Track record of each link, used to demote a source that just failed. */
    sourceStats: Map<String, SourceStats> = emptyMap(),
    /** Every addon that offers each file, keyed by its fingerprint. */
    sourceProviders: Map<String, List<String>> = emptyMap(),
    /** Hide links that failed in the last day instead of only demoting them. */
    hideDeadSources: Boolean = false,
    /** The link the user pinned for this title, tried first every time. */
    pinnedUrl: String? = null,
    onPinSource: (StreamOption) -> Unit = {},
    /** "auto", "4K", "1080p" or "720p": what "play the best source" prefers. */
    preferredQuality: String = "auto",
    /** Quality tier the viewer tends to pick from each addon, by addon id. */
    addonQualities: Map<String, String> = emptyMap(),
    packBadges: List<BadgeFilter> = emptyList(),
    resumeLabel: String?,
    episodeProgress: Map<String, Float>,
    onToggleFavorite: () -> Unit,
    onSelectVideo: (StremioVideo) -> Unit,
    onSeason: (Int) -> Unit,
    onPlay: (StreamOption) -> Unit,
    onRetrySources: (() -> Unit)? = null,
    onToggleEpisodeWatched: (StremioVideo) -> Unit = {},
    /** Playable source count per episode, filled on demand. */
    episodeSourceCounts: Map<String, Int> = emptyMap(),
    checkingEpisodeId: String? = null,
    onCheckEpisode: (StremioVideo) -> Unit = {},
    onBack: () -> Unit
) {
    val title = meta?.name ?: item.name
    val videos = meta?.videos.orEmpty()
    val overview = meta?.description ?: item.description
    val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
    val deadFingerprints = sources.map { streamFingerprint(it) }
        .filter { sourceStats[it]?.isFailedRecently(nowEpochMillis) == true }
        .toSet()
    val visibleSources = if (hideDeadSources) {
        sources.filterNot { streamFingerprint(it) in deadFingerprints }
    } else {
        sources
    }
    val hiddenDeadCount = sources.size - visibleSources.size
    val directCount = visibleSources.count { it.stream.url != null }
    val providerCount = sourceProviders.values.flatten().distinct().size
    var onlyDirect by remember { mutableStateOf(false) }
    var episodeQuery by remember { mutableStateOf("") }
    val ranked = rankSources(
        visibleSources,
        stats = sourceStats,
        onlyDirect = onlyDirect,
        lastPlayedUrl = lastPlayedUrl,
        pinnedUrl = pinnedUrl,
        preferredQualityByAddon = addonQualities,
        nowEpochMillis = nowEpochMillis
    )
    val firstPlayable = bestSource(
        visibleSources,
        preference = preferredQuality,
        stats = sourceStats,
        pinnedUrl = pinnedUrl,
        nowEpochMillis = nowEpochMillis
    )
    var qualityFilter by remember { mutableStateOf<String?>(null) }
    val shown = if (qualityFilter == null) {
        ranked
    } else {
        ranked.filter { qualityFilter in streamBadges(it.stream.name, it.stream.title) }
    }
    Column(Modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            AsyncImage(
                model = meta?.background ?: item.background ?: item.poster,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(CinemaxPalette.artworkScrim(background)))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CinemaxCircleButton("‹") { onBack() }
                Spacer(Modifier.weight(1f))
                CinemaxCircleButton(if (isFavorite) "♥" else "♡") { onToggleFavorite() }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (meta?.imdbRating ?: item.imdbRating)?.let { rating ->
                        Text(
                            "★ $rating",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(CinemaxPalette.frosted, RoundedCornerShape(50))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        listOfNotNull(
                            if (item.type == "series") "مسلسل" else "فيلم",
                            meta?.releaseInfo ?: item.releaseInfo,
                            meta?.runtime
                        ).joinToString(" • "),
                        color = CinemaxPalette.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { firstPlayable?.let(onPlay) },
                        enabled = firstPlayable != null,
                        modifier = Modifier.weight(1.4f).height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                    ) {
                        val bestQuality = firstPlayable?.let {
                            streamBadges(it.stream.name, it.stream.title).firstOrNull()
                        }
                        Text(
                            listOfNotNull(
                                if (resumeLabel != null) "▶ أكمل $resumeLabel" else "▶ مشاهدة",
                                bestQuality
                            ).joinToString(" • "),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onToggleFavorite,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinemaxPalette.frosted,
                            contentColor = Color.White
                        )
                    ) { Text(if (isFavorite) "✓ محفوظ" else "♥ حفظ", fontWeight = FontWeight.SemiBold) }
                }
            }
            if (loadingMeta) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent) }
            }
            meta?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        genres.take(8).forEach { genre -> CinemaxPill(genre, accent) }
                    }
                }
            }
            overview?.takeIf { it.isNotBlank() }?.let { text ->
                item { Text(text, color = CinemaxPalette.text.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 21.sp) }
            }
            meta?.director?.takeIf { it.isNotEmpty() }?.let { directors ->
                item {
                    Text(
                        "إخراج: ${directors.joinToString("، ")}",
                        color = CinemaxPalette.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
            meta?.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
                item {
                    Column {
                        Text(
                            "طاقم التمثيل",
                            color = CinemaxPalette.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            cast.take(12).forEach { person -> CinemaxPill(person, accent) }
                        }
                    }
                }
            }
            if (videos.isNotEmpty()) {
                item {
                    val watched = videos.count { (episodeProgress[it.id] ?: 0f) >= 0.95f }
                    val lastAvailable = videos
                        .filter { (episodeSourceCounts[it.id] ?: 0) > 0 }
                        .maxByOrNull { (it.season ?: 0) * 1000 + (it.episode ?: 0) }
                    val heading = buildString {
                        append(if (watched > 0) "الحلقات • $watched/${videos.size} مشاهدة" else "الحلقات")
                        lastAvailable?.episode?.let { append(" • متوفرة حتى الحلقة $it") }
                    }
                    CinemaxSectionTitle(title = heading, accent = accent)
                }
                val seasons = videos.mapNotNull { it.season }.distinct().sorted()
                if (seasons.size > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            seasons.forEach { season ->
                                CinemaxFilterChip(
                                    selected = (activeSeason ?: seasons.first()) == season,
                                    onClick = { onSeason(season) },
                                    label = { Text("الموسم $season") }
                                )
                            }
                        }
                    }
                }
                if (videos.size > 12) {
                    item {
                        OutlinedTextField(
                            value = episodeQuery,
                            onValueChange = { episodeQuery = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("ابحث عن حلقة", fontSize = 12.sp) }
                        )
                    }
                }
                val shown = videos
                    .filter { activeSeason == null || it.season == activeSeason }
                    .filter { episodeQuery.isBlank() || matchesLibraryQuery(episodeLabel(it), episodeQuery) }
                    .sortedBy { it.episode ?: 0 }
                items(shown, key = { it.id }) { episode ->
                    EpisodeCard(
                        episode = episode,
                        selected = selectedVideo?.id == episode.id,
                        progress = episodeProgress[episode.id] ?: 0f,
                        accent = accent,
                        onClick = { onSelectVideo(episode) },
                        onLongClick = { onToggleEpisodeWatched(episode) },
                        sourceCount = episodeSourceCounts[episode.id],
                        checking = checkingEpisodeId == episode.id,
                        onCheckSources = { onCheckEpisode(episode) }
                    )
                }
            }
            item { CinemaxSectionTitle(title = "مصادر المشاهدة", accent = accent) }
            if (sources.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CinemaxFilterChip(
                            selected = !onlyDirect,
                            onClick = { onlyDirect = false },
                            label = { Text("الكل (${visibleSources.size})", fontSize = 11.sp) }
                        )
                        CinemaxFilterChip(
                            selected = onlyDirect,
                            onClick = { onlyDirect = true },
                            label = { Text("مباشر ($directCount)", fontSize = 11.sp) }
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            null to "كل الجودات",
                            "4K" to "4K",
                            "1080p" to "1080p",
                            "720p" to "720p",
                            "480p" to "480p"
                        ).forEach { (value, label) ->
                            CinemaxFilterChip(
                                selected = qualityFilter == value,
                                onClick = { qualityFilter = value },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                item {
                    Text(
                        buildString {
                            append("${shown.size} مصدرًا")
                            if (providerCount > 1) append(" من $providerCount إضافة")
                            if (hiddenDeadCount > 0) append(" • أخفيت $hiddenDeadCount معطلة")
                        },
                        color = CinemaxPalette.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
            if (providerIssues.isNotEmpty() && !loadingSources) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CinemaxPalette.surface)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "مصادر Nuvio تعذّرت (${providerIssues.size})",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        providerIssues.take(6).forEach { issue ->
                            Text(issue, color = CinemaxPalette.textMuted, fontSize = 10.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
            if (loadingSources) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent) }
            } else if (sources.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            sourcesMessage.ifBlank { "لم تُرجع إضافاتك مصادر لهذا العنوان." },
                            color = CinemaxPalette.textMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                        onRetrySources?.let { retry ->
                            OutlinedButton(onClick = retry, shape = RoundedCornerShape(12.dp)) {
                                Text("أعد المحاولة", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else if (shown.isEmpty()) {
                item {
                    Text(
                        if (qualityFilter != null) {
                            "لا مصادر بجودة $qualityFilter. اختر «كل الجودات»."
                        } else if (hiddenDeadCount > 0) {
                            "كل المصادر معطلة أو مخفية. أوقف «إخفاء المصادر المعطلة» من الإعدادات لعرضها."
                        } else {
                            "لا يوجد مصدر مباشر لهذا العنوان. اضغط «الكل» لعرض مصادر التورنت."
                        },
                        color = CinemaxPalette.textMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                items(shown, key = { streamFingerprint(it) }) { option ->
                    SourceCard(
                        option = option,
                        accent = accent,
                        isLastPlayed = lastPlayedUrl != null && option.stream.url == lastPlayedUrl,
                        isPinned = pinnedUrl != null && option.stream.url == pinnedUrl,
                        onPin = { onPinSource(option) },
                        providers = sourceProviders[streamFingerprint(option)] ?: listOf(option.addonName),
                        packBadges = packBadges,
                        onPlay = onPlay
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: StremioVideo,
    selected: Boolean,
    progress: Float,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    sourceCount: Int? = null,
    checking: Boolean = false,
    onCheckSources: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) CinemaxPalette.surfaceHigh else CinemaxPalette.surface)
            .then(if (selected) Modifier.border(1.5.dp, accent, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        episode.thumbnail?.let { thumb ->
            Box(Modifier.width(110.dp).height(62.dp).clip(RoundedCornerShape(6.dp))) {
                AsyncImage(
                    model = thumb,
                    contentDescription = episode.name ?: episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                episodeLabel(episode),
                color = CinemaxPalette.text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2
            )
            episode.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = CinemaxPalette.textMuted, fontSize = 11.sp, maxLines = 2)
            }
            val release = remember(episode.released) {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                episodeReleaseLabel(episode.released, today)
            }
            release?.let {
                Text(
                    it.text,
                    color = if (it.upcoming) accent else CinemaxPalette.textMuted,
                    fontSize = 10.sp,
                    fontWeight = if (it.upcoming) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            if (progress > 0.01f) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = accent,
                    trackColor = CinemaxPalette.surfaceHigh
                )
            }
            when {
                checking -> Text(
                    "جارٍ فحص المصادر…",
                    color = CinemaxPalette.textMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                sourceCount != null -> Text(
                    if (sourceCount == 0) "لا مصادر الآن" else "متوفر في $sourceCount مصدر",
                    color = if (sourceCount == 0) CinemaxPalette.textMuted else accent,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                onCheckSources != null -> Text(
                    "كم مصدرًا؟",
                    color = CinemaxPalette.textMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp).clickable(onClick = onCheckSources)
                )
            }
        }
        if (progress >= 0.95f) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent),
                contentAlignment = Alignment.Center
            ) { Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SourceCard(
    option: StreamOption,
    accent: Color,
    isLastPlayed: Boolean,
    isPinned: Boolean = false,
    onPin: (() -> Unit)? = null,
    providers: List<String> = emptyList(),
    packBadges: List<BadgeFilter> = emptyList(),
    onPlay: (StreamOption) -> Unit
) {
    val stream = option.stream
    val playable = stream.url != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isLastPlayed) accent.copy(alpha = 0.10f) else CinemaxPalette.surface)
            .clickable(enabled = playable) { onPlay(option) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stream.name ?: stream.title ?: "مصدر",
                color = CinemaxPalette.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (isPinned) {
                Spacer(Modifier.width(6.dp))
                CinemaxPill("مفضل", accent)
            }
            if (isLastPlayed) {
                Spacer(Modifier.width(6.dp))
                CinemaxPill("آخر رابط", accent)
            }
            Spacer(Modifier.width(6.dp))
            Text(option.addonName, color = accent, fontSize = 11.sp, maxLines = 1)
        }
        stream.title?.let { Text(it, color = CinemaxPalette.textMuted, fontSize = 12.sp) }
        val badges = streamBadges(stream.name, stream.title)
        val matchedBadges = matchingBadges(
            packBadges,
            listOfNotNull(stream.name, stream.title).joinToString(" ")
        )
        if (matchedBadges.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                matchedBadges.forEach { badge ->
                    AsyncImage(
                        model = badge.imageURL,
                        contentDescription = badge.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(18.dp)
                            .aspectRatio(3.1f)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        } else if (badges.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.forEach { badge -> CinemaxPill(badge, accent) }
            }
        }
        streamLanguageLabel(stream.name, stream.title)?.let { label ->
            CinemaxPill(label, accent)
        }
        if (providers.size > 1) {
            Text(
                "متوفر في: ${providers.joinToString(" · ")}",
                color = CinemaxPalette.textMuted,
                fontSize = 10.sp,
                maxLines = 2
            )
        }
        if (!playable) {
            Text(
                "مصدر تورنت: يحتاج محرك بث محلي مدمج (قيد التنفيذ).",
                color = CinemaxPalette.textMuted,
                fontSize = 11.sp
            )
        }
        if (onPin != null) {
            TextButton(onClick = onPin) {
                Text(
                    if (isPinned) "★ مصدر مفضل" else "☆ اجعله المصدر المفضل",
                    color = accent,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Full-screen player chrome: a tap on the video hides or shows it, a seek bar,
 * speed chips, the subtitle entry and the next-episode card, over the video.
 */
@Composable
fun CinemaxPlayerScreen(
    title: String,
    episodeLabelText: String?,
    subtitleLabel: String?,
    accent: Color,
    message: String?,
    loading: Boolean = false,
    controlsVisible: Boolean,
    hud: String?,
    positionMs: Long,
    durationMs: Long,
    onSeek: (positionMs: Long) -> Unit,
    speed: Float,
    onSpeed: (speed: Float) -> Unit,
    nextEpisodeLabel: String?,
    onNextEpisode: (() -> Unit)?,
    sourceLabel: String? = null,
    onNextShortcut: (() -> Unit)? = null,
    /** Opens the in-player source picker; null hides the button. */
    onSources: (() -> Unit)? = null,
    /** The audio track playing, shown on the audio button. */
    audioLabel: String? = null,
    /** Opens the audio-track sheet; null hides the button. */
    onAudio: (() -> Unit)? = null,
    onClose: () -> Unit,
    onSubtitles: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CinemaxCircleButton("‹") { onClose() }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    episodeLabelText?.let {
                        Text(it, color = CinemaxPalette.textMuted, fontSize = 11.sp, maxLines = 1)
                    }
                    sourceLabel?.let {
                        Text(it, color = CinemaxPalette.textMuted, fontSize = 10.sp, maxLines = 1)
                    }
                }
                onAudio?.let {
                    TextButton(onClick = it) {
                        Text(
                            if (audioLabel != null) "الصوت • $audioLabel" else "الصوت",
                            color = accent,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
                onSources?.let {
                    TextButton(onClick = it) {
                        Text("المصادر", color = accent, fontSize = 12.sp, maxLines = 1)
                    }
                }
                TextButton(onClick = onSubtitles) {
                    Text(
                        if (subtitleLabel != null) "CC • $subtitleLabel" else "CC",
                        color = accent,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
        // The readout lives above the video, never over it: a UIKit-hosted video
        // view can draw above Compose content, so nothing important is overlaid.
        val banner = hud ?: message
        if (banner != null) {
            Text(
                banner,
                color = if (hud != null) Color.White else Color(0xFFFF8A80),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            )
        }
        if (loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = accent
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "جارٍ تجهيز المشاهدة…",
                    color = CinemaxPalette.textMuted,
                    fontSize = 11.sp
                )
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            content()
        }
        if (controlsVisible) {
            if (nextEpisodeLabel != null && onNextEpisode != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1A1D28))
                        .clickable(onClick = onNextEpisode)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("الحلقة التالية", color = CinemaxPalette.textMuted, fontSize = 10.sp)
                        Text(
                            nextEpisodeLabel,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Text("▶", color = accent, fontSize = 16.sp)
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatClock(positionMs), color = Color.White, fontSize = 11.sp)
                    Slider(
                        value = scrub ?: if (durationMs > 0L) {
                            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        } else 0f,
                        onValueChange = { scrub = it },
                        onValueChangeFinished = {
                            scrub?.let { fraction ->
                                if (durationMs > 0L) onSeek((fraction * durationMs).toLong())
                            }
                            scrub = null
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent
                        )
                    )
                    Text(formatClock(durationMs), color = Color.White, fontSize = 11.sp)
                    if (durationMs > positionMs) {
                        Text(
                            "−${formatClock(durationMs - positionMs)}",
                            color = CinemaxPalette.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onSeek((positionMs - 10_000L).coerceAtLeast(0L)) }) {
                        Text("−10 ث", color = accent, fontSize = 11.sp)
                    }
                    TextButton(onClick = { onSeek(positionMs + 10_000L) }) {
                        Text("+10 ث", color = accent, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("السرعة", color = CinemaxPalette.textMuted, fontSize = 10.sp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
                        CinemaxFilterChip(
                            selected = speed == option,
                            onClick = { onSpeed(option) },
                            label = { Text("×$option", fontSize = 11.sp) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    onNextShortcut?.let { next ->
                        TextButton(onClick = next) {
                            Text("التالي ‹", color = accent, fontSize = 11.sp)
                        }
                    }
                    TextButton(onClick = onSubtitles) {
                        Text(
                            if (subtitleLabel != null) subtitleLabel else "ترجمة",
                            color = accent,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Settings tab: appearance, playback, sources and about, in Nuvio's order. */
@Composable
fun CinemaxSettingsScreen(
    preferences: CinemaxPreferences,
    onPreferences: (CinemaxPreferences) -> Unit,
    addons: List<InstalledAddon>,
    /** How many merged, de-duplicated sources the app ships with. */
    suggestedSourceCount: Int = 0,
    /** Result of the manual manifest check, keyed by addon id. */
    addonHealth: Map<String, Boolean> = emptyMap(),
    checkingAddons: Boolean = false,
    onCheckAddons: () -> Unit = {},
    onRemoveDeadAddons: () -> Unit = {},
    onClearSourceStats: () -> Unit = {},
    /** How many source links the app has a track record for. */
    sourceStatCount: Int = 0,
    activeAddonId: String?,
    onSelectAddon: (InstalledAddon) -> Unit,
    onRemoveAddon: (InstalledAddon) -> Unit,
    /** Opens the addon's configure page when its manifest advertises one. */
    onConfigureAddon: (InstalledAddon) -> Unit = {},
    onAddAddon: () -> Unit,
    onNuvio: () -> Unit,
    tmdbKey: String,
    onTmdbKey: (String) -> Unit,
    nuvioRepositoryCount: Int,
    nuvioProviderCount: Int,
    status: String,
    versionLabel: String,
    accent: Color,
    favoriteCount: Int,
    progressCount: Int,
    badgePackUrl: String,
    badgeCount: Int,
    onBadgePack: (String) -> Unit,
    onClearProgress: () -> Unit,
    onClearFavorites: () -> Unit,
    onOpenRoadmap: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "الإعدادات",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
        )
        CinemaxSectionTitle(title = "المظهر", accent = accent)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Violet" to "بنفسجي", "Blue" to "أزرق", "Rose" to "وردي", "Emerald" to "أخضر", "Amber" to "ذهبي")
                .forEach { (tone, label) ->
                    CinemaxFilterChip(
                        selected = preferences.accent == tone,
                        onClick = { onPreferences(preferences.copy(accent = tone)) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
        }
        PreferenceSwitch("خلفية سوداء OLED", preferences.oledBlack) {
            onPreferences(preferences.copy(oledBlack = it))
        }
        PreferenceSwitch("بطاقات أكثر اختصارًا", preferences.compactCards) {
            onPreferences(preferences.copy(compactCards = it))
        }

        Spacer(Modifier.height(8.dp))
        CinemaxSectionTitle(title = "التشغيل", accent = accent)
        PreferenceSwitch("تذكّر موضع التوقف", preferences.rememberPlaybackPosition) {
            onPreferences(preferences.copy(rememberPlaybackPosition = it))
        }
        PreferenceSwitch("تشغيل الحلقة التالية تلقائيًا", preferences.autoplayNextEpisode) {
            onPreferences(preferences.copy(autoplayNextEpisode = it))
        }
        Text(
            "MPV يشغّل MKV وكل الصيغ، ومشغل النظام (AVPlayer) أخف لكنه لا يدعم MKV وAVI.",
            fontSize = 12.sp,
            color = CinemaxPalette.textMuted,
            lineHeight = 18.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CinemaxFilterChip(
                selected = preferences.preferredPlayer != "system",
                onClick = { onPreferences(preferences.copy(preferredPlayer = "mpv")) },
                label = { Text("MPV (كل الصيغ)") }
            )
            CinemaxFilterChip(
                selected = preferences.preferredPlayer == "system",
                onClick = { onPreferences(preferences.copy(preferredPlayer = "system")) },
                label = { Text("مشغل النظام") }
            )
        }

        Text("الجودة المفضلة عند التشغيل", fontSize = 13.sp, color = CinemaxPalette.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("auto" to "تلقائي", "4K" to "4K", "1080p" to "1080p", "720p" to "720p")
                .forEach { (value, label) ->
                    CinemaxFilterChip(
                        selected = preferences.preferredQuality == value,
                        onClick = { onPreferences(preferences.copy(preferredQuality = value)) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
        }

        Spacer(Modifier.height(8.dp))
        CinemaxSectionTitle(title = "الترجمة والصوت", accent = accent)
        Text("لغة الترجمة المفضلة", fontSize = 13.sp, color = CinemaxPalette.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CinemaxFilterChip(
                selected = preferences.preferredSubtitleLanguage == "ar",
                onClick = { onPreferences(preferences.copy(preferredSubtitleLanguage = "ar")) },
                label = { Text("العربية") }
            )
            CinemaxFilterChip(
                selected = preferences.preferredSubtitleLanguage == "en",
                onClick = { onPreferences(preferences.copy(preferredSubtitleLanguage = "en")) },
                label = { Text("English") }
            )
            CinemaxFilterChip(
                selected = preferences.preferredSubtitleLanguage == "off",
                onClick = { onPreferences(preferences.copy(preferredSubtitleLanguage = "off")) },
                label = { Text("إيقاف") }
            )
        }
        Text("لغة الصوت المفضلة", fontSize = 13.sp, color = CinemaxPalette.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CinemaxFilterChip(
                selected = preferences.preferredAudioLanguage == "device",
                onClick = { onPreferences(preferences.copy(preferredAudioLanguage = "device")) },
                label = { Text("لغة الجهاز") }
            )
            CinemaxFilterChip(
                selected = preferences.preferredAudioLanguage == "ar",
                onClick = { onPreferences(preferences.copy(preferredAudioLanguage = "ar")) },
                label = { Text("العربية") }
            )
            CinemaxFilterChip(
                selected = preferences.preferredAudioLanguage == "en",
                onClick = { onPreferences(preferences.copy(preferredAudioLanguage = "en")) },
                label = { Text("English") }
            )
        }
        Text("حجم ترجمة الإضافات", fontSize = 13.sp, color = CinemaxPalette.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("small" to "صغير", "medium" to "وسط", "large" to "كبير").forEach { (value, label) ->
                CinemaxFilterChip(
                    selected = preferences.subtitleSize == value,
                    onClick = { onPreferences(preferences.copy(subtitleSize = value)) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        CinemaxSectionTitle(title = "المصادر", accent = accent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddAddon, shape = RoundedCornerShape(12.dp)) { Text("＋ إضافة Stremio") }
            OutlinedButton(onClick = onNuvio, shape = RoundedCornerShape(12.dp)) { Text("Nuvio") }
        }
        if (suggestedSourceCount > 0) {
            Text(
                "لديك $suggestedSourceCount مصدرًا جاهزًا مدموجًا من ملفّاتك. افتح «إضافة Stremio» واختر «تثبيت» بجانب أي مصدر.",
                color = CinemaxPalette.textMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
        Text(status, color = CinemaxPalette.textMuted, fontSize = 12.sp, lineHeight = 18.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCheckAddons,
                enabled = !checkingAddons && addons.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (checkingAddons) "جارٍ الفحص…" else "فحص المصادر", fontSize = 12.sp)
            }
            val dead = addons.count { addonHealth[it.id] == false }
            if (dead > 0) {
                TextButton(onClick = onRemoveDeadAddons) {
                    Text("أزل $dead متوقفة", fontSize = 12.sp)
                }
            }
        }
        PreferenceSwitch("إخفاء المصادر المعطلة", preferences.hideDeadSources) {
            onPreferences(preferences.copy(hideDeadSources = it))
        }
        Text(
            "سجل جودة المصادر: $sourceStatCount رابطًا محفوظًا.",
            color = CinemaxPalette.textMuted,
            fontSize = 11.sp
        )
        TextButton(onClick = onClearSourceStats) {
            Text("مسح سجل جودة المصادر", fontSize = 12.sp)
        }
        if (addons.isEmpty()) {
            Text("لا توجد إضافة مثبتة بعد.", color = CinemaxPalette.textMuted, fontSize = 12.sp)
        }
        addons.forEach { addon ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        addon.name,
                        color = CinemaxPalette.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            append(
                                if (activeAddonId == addon.id) "الكتالوج النشط • ${addon.version}"
                                else addon.version
                            )
                            when (addonHealth[addon.id]) {
                                true -> append(" • يعمل")
                                false -> append(" • متوقف")
                                null -> Unit
                            }
                        },
                        color = when (addonHealth[addon.id]) {
                            false -> Color(0xFFFF6B6B)
                            else -> if (activeAddonId == addon.id) accent else CinemaxPalette.textMuted
                        },
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = { onSelectAddon(addon) }) { Text("اختيار", fontSize = 12.sp) }
                if (addon.configurable) {
                    TextButton(onClick = { onConfigureAddon(addon) }) { Text("إعداد", fontSize = 12.sp) }
                }
                TextButton(onClick = { onRemoveAddon(addon) }) { Text("إزالة", fontSize = 12.sp) }
            }
        }
        Text(
            "مزوّدو Nuvio: $nuvioRepositoryCount مستودعًا و$nuvioProviderCount مصدرًا مفعّلًا.",
            color = CinemaxPalette.textMuted,
            fontSize = 12.sp
        )
        OutlinedTextField(
            value = tmdbKey,
            onValueChange = onTmdbKey,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("مفتاح TMDB (اختياري)") }
        )
        Text(
            "مصادر Nuvio تعمل داخل محرك JavaScript معزول: بلا وصول للملفات أو مفاتيح الجهاز، وروابط HTTPS فقط.",
            fontSize = 11.sp,
            color = CinemaxPalette.textMuted,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(10.dp))
        CinemaxSectionTitle(title = "شارات المصادر (Fusion)", accent = accent)
        Text(
            "شارات مرسومة فوق كل مصدر بحسب اسمه وجودته، ويُظهر المختار $badgeCount شارة.",
            color = CinemaxPalette.textMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BADGE_PACKS.forEach { (label, url) ->
                CinemaxFilterChip(
                    selected = badgePackUrl == url,
                    onClick = { onBadgePack(url) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        CinemaxSectionTitle(title = "بيانات التطبيق", accent = accent)
        Text(
            "$favoriteCount عنوانًا محفوظًا • $progressCount قيد المشاهدة",
            color = CinemaxPalette.textMuted,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onClearProgress,
                enabled = progressCount > 0,
                shape = RoundedCornerShape(12.dp)
            ) { Text("مسح سجل المشاهدة", fontSize = 12.sp) }
            OutlinedButton(
                onClick = onClearFavorites,
                enabled = favoriteCount > 0,
                shape = RoundedCornerShape(12.dp)
            ) { Text("مسح المفضلة", fontSize = 12.sp) }
        }

        Spacer(Modifier.height(10.dp))
        CinemaxSectionTitle(title = "حول التطبيق", accent = accent)
        Text("CINEMAX • $versionLabel", color = CinemaxPalette.textMuted, fontSize = 12.sp)
        OutlinedButton(
            onClick = onOpenRoadmap,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) { Text("خطة التطوير — الطريق إلى Nuvio", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = CinemaxPalette.text)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
