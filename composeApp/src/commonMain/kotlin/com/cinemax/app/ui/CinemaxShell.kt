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
import com.cinemax.app.core.episodeLabel
import com.cinemax.app.core.rankedSources
import com.cinemax.app.core.streamBadges
import com.cinemax.app.formatClock
import com.cinemax.app.hapticTap
import kotlinx.coroutines.delay

/** One titled row of posters on the home screen. */
data class CinemaxShelf(val title: String, val items: List<StremioMetaPreview>)

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xF512141C))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.forEachIndexed { index, entry ->
            val active = selected == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable {
                        hapticTap()
                        onSelect(index)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    entry.second,
                    color = if (active) accent else CinemaxPalette.textMuted,
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
    versionLabel: String,
    tonightPick: StremioMetaPreview?,
    onShuffleTonight: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(top = 6.dp, bottom = 110.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CINEMAX", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onRefresh) {
                    Text("↻", color = accent, fontSize = 18.sp)
                }
            }
        }
        if (heroItems.isEmpty()) {
            if (loading) {
                item { ShimmerHero() }
                item { ShimmerShelf() }
            } else {
                item { CinemaxWelcomeCard(accent = accent, background = background, onAddAddon = onAddAddon, onNuvio = onNuvio) }
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
                        items(continueWatching) { progress ->
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
                        CinemaxSectionTitle(title = shelf.title, accent = accent)
                    }
                    item {
                        CinemaxRowAll {
                            items(shelf.items) { item ->
                                CinemaxPosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    badge = item.releaseInfo,
                                    accent = accent,
                                    onClick = { onOpenItem(item) }
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
                    .clip(RoundedCornerShape(26.dp))
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
                        fontSize = 28.sp,
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
            .clip(RoundedCornerShape(16.dp))
            .background(CinemaxPalette.surface)
            .clickable(onClick = onOpen)
    ) {
        Box(Modifier.fillMaxWidth().height(135.dp)) {
            AsyncImage(
                model = progress.item.background ?: progress.item.poster,
                contentDescription = progress.item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(CinemaxPalette.artworkScrim(CinemaxPalette.surface)))
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) { Text("✕", color = Color.White, fontSize = 10.sp) }
        }
        Column(Modifier.padding(10.dp)) {
            progress.videoLabel?.let {
                Text(it, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Text(
                progress.item.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.progressFraction },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = accent,
                trackColor = CinemaxPalette.surfaceHigh
            )
            Spacer(Modifier.height(5.dp))
            Text(remainingLabel(progress), color = CinemaxPalette.textMuted, fontSize = 10.sp)
        }
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
    onOpenItem: (StremioMetaPreview) -> Unit
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "الكل", "movie" to "أفلام", "series" to "مسلسلات").forEach { (value, label) ->
                FilterChip(
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
                    FilterChip(
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
                items(results.chunked(3)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { entry ->
                            GridPoster(
                                item = entry.item,
                                accent = accent,
                                onClick = { onOpenItem(entry.item) },
                                modifier = Modifier.weight(1f)
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
    onOpenItem: (StremioMetaPreview) -> Unit,
    onResume: (WatchProgress) -> Unit,
    onRemoveProgress: (WatchProgress) -> Unit
) {
    val visibleFavorites = favorites.filter { filter == "all" || it.type == filter }
    val visibleProgress = continueWatching.filter { filter == "all" || it.item.type == filter }
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("مكتبتي", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "الكل", "movie" to "أفلام", "series" to "مسلسلات").forEach { (value, label) ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { onFilter(value) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
        if (visibleProgress.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                CinemaxSectionTitle(title = "قيد المشاهدة", accent = accent)
            }
            items(visibleProgress) { progress ->
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
                    if (favorites.isEmpty()) "لا شيء محفوظ بعد. اضغط «＋ حفظ» في أي عنوان ليظهر هنا."
                    else "لا يوجد عنوان بهذا النوع في مكتبتك.",
                    color = CinemaxPalette.textMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            items(visibleFavorites.chunked(3)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        GridPoster(
                            item = item,
                            accent = accent,
                            onClick = { onOpenItem(item) },
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
    modifier: Modifier = Modifier
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
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
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
                        .background(CinemaxPalette.rating, RoundedCornerShape(5.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            color = CinemaxPalette.text,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
    resumeLabel: String?,
    episodeProgress: Map<String, Float>,
    onToggleFavorite: () -> Unit,
    onSelectVideo: (StremioVideo) -> Unit,
    onSeason: (Int) -> Unit,
    onPlay: (StreamOption) -> Unit,
    onBack: () -> Unit
) {
    val title = meta?.name ?: item.name
    val videos = meta?.videos.orEmpty()
    val overview = meta?.description ?: item.description
    val directCount = sources.count { it.stream.url != null }
    var onlyDirect by remember { mutableStateOf(false) }
    val ranked = rankedSources(sources, onlyDirect, lastPlayedUrl)
    val firstPlayable = ranked.firstOrNull { it.stream.url != null }
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
                CinemaxCircleButton(if (isFavorite) "★" else "☆") { onToggleFavorite() }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(
                        meta?.releaseInfo ?: item.releaseInfo,
                        (meta?.imdbRating ?: item.imdbRating)?.let { "★ $it" },
                        meta?.runtime
                    ).joinToString(" • "),
                    color = CinemaxPalette.textMuted,
                    fontSize = 12.sp
                )
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                    ) { Text(if (resumeLabel != null) "▶ أكمل $resumeLabel" else "▶ مشاهدة", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (isFavorite) "✓ محفوظ" else "＋ حفظ", fontWeight = FontWeight.SemiBold) }
                }
            }
            if (loadingMeta) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent) }
            }
            meta?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                item { Text(genres.joinToString("  •  "), color = accent, fontSize = 12.sp) }
            }
            overview?.takeIf { it.isNotBlank() }?.let { text ->
                item { Text(text, color = CinemaxPalette.text.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 21.sp) }
            }
            if (videos.isNotEmpty()) {
                item { CinemaxSectionTitle(title = "الحلقات", accent = accent) }
                val seasons = videos.mapNotNull { it.season }.distinct().sorted()
                if (seasons.size > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            seasons.forEach { season ->
                                FilterChip(
                                    selected = (activeSeason ?: seasons.first()) == season,
                                    onClick = { onSeason(season) },
                                    label = { Text("الموسم $season") }
                                )
                            }
                        }
                    }
                }
                val shown = videos
                    .filter { activeSeason == null || it.season == activeSeason }
                    .sortedBy { it.episode ?: 0 }
                items(shown) { episode ->
                    EpisodeCard(
                        episode = episode,
                        selected = selectedVideo?.id == episode.id,
                        progress = episodeProgress[episode.id] ?: 0f,
                        accent = accent,
                        onClick = { onSelectVideo(episode) }
                    )
                }
            }
            item { CinemaxSectionTitle(title = "مصادر المشاهدة", accent = accent) }
            if (sources.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !onlyDirect,
                            onClick = { onlyDirect = false },
                            label = { Text("الكل (${sources.size})", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = onlyDirect,
                            onClick = { onlyDirect = true },
                            label = { Text("مباشر ($directCount)", fontSize = 11.sp) }
                        )
                    }
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
                    Text(
                        sourcesMessage.ifBlank { "لم تُرجع إضافاتك مصادر لهذا العنوان." },
                        color = CinemaxPalette.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            } else if (ranked.isEmpty()) {
                item {
                    Text(
                        "لا يوجد مصدر مباشر لهذا العنوان. اضغط «الكل» لعرض مصادر التورنت.",
                        color = CinemaxPalette.textMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                items(ranked) { option ->
                    SourceCard(
                        option = option,
                        accent = accent,
                        isLastPlayed = lastPlayedUrl != null && option.stream.url == lastPlayedUrl,
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else CinemaxPalette.surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        episode.thumbnail?.let { thumb ->
            Box(Modifier.width(110.dp).height(62.dp).clip(RoundedCornerShape(9.dp))) {
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
            if (progress > 0.01f) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = accent,
                    trackColor = CinemaxPalette.surfaceHigh
                )
            }
        }
        if (progress >= 0.95f) {
            Text("✓", color = accent, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SourceCard(
    option: StreamOption,
    accent: Color,
    isLastPlayed: Boolean,
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
            if (isLastPlayed) {
                Spacer(Modifier.width(6.dp))
                CinemaxPill("آخر رابط", accent)
            }
            Spacer(Modifier.width(6.dp))
            Text(option.addonName, color = accent, fontSize = 11.sp, maxLines = 1)
        }
        stream.title?.let { Text(it, color = CinemaxPalette.textMuted, fontSize = 12.sp) }
        val badges = streamBadges(stream.name, stream.title)
        if (badges.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.forEach { badge -> CinemaxPill(badge, accent) }
            }
        }
        if (!playable) {
            Text(
                "مصدر تورنت: يحتاج محرك بث محلي مدمج (قيد التنفيذ).",
                color = CinemaxPalette.textMuted,
                fontSize = 11.sp
            )
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
    controlsVisible: Boolean,
    hud: String?,
    positionMs: Long,
    durationMs: Long,
    onSeek: (positionMs: Long) -> Unit,
    speed: Float,
    onSpeed: (speed: Float) -> Unit,
    nextEpisodeLabel: String?,
    onNextEpisode: (() -> Unit)?,
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
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
                        FilterChip(
                            selected = speed == option,
                            onClick = { onSpeed(option) },
                            label = { Text("×$option", fontSize = 11.sp) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
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
    activeAddonId: String?,
    onSelectAddon: (InstalledAddon) -> Unit,
    onRemoveAddon: (InstalledAddon) -> Unit,
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
                    FilterChip(
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
            FilterChip(
                selected = preferences.preferredPlayer != "system",
                onClick = { onPreferences(preferences.copy(preferredPlayer = "mpv")) },
                label = { Text("MPV (كل الصيغ)") }
            )
            FilterChip(
                selected = preferences.preferredPlayer == "system",
                onClick = { onPreferences(preferences.copy(preferredPlayer = "system")) },
                label = { Text("مشغل النظام") }
            )
        }

        Spacer(Modifier.height(8.dp))
        CinemaxSectionTitle(title = "الترجمة والصوت", accent = accent)
        Text("لغة الترجمة المفضلة", fontSize = 13.sp, color = CinemaxPalette.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = preferences.preferredSubtitleLanguage == "ar",
                onClick = { onPreferences(preferences.copy(preferredSubtitleLanguage = "ar")) },
                label = { Text("العربية") }
            )
            FilterChip(
                selected = preferences.preferredSubtitleLanguage == "en",
                onClick = { onPreferences(preferences.copy(preferredSubtitleLanguage = "en")) },
                label = { Text("English") }
            )
            FilterChip(
                selected = preferences.preferredSubtitleLanguage == "off",
                onClick = { onPreferences(preferences.copy(preferredSubtitleLanguage = "off")) },
                label = { Text("إيقاف") }
            )
        }
        Text("لغة الصوت المفضلة", fontSize = 13.sp, color = CinemaxPalette.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = preferences.preferredAudioLanguage == "device",
                onClick = { onPreferences(preferences.copy(preferredAudioLanguage = "device")) },
                label = { Text("لغة الجهاز") }
            )
            FilterChip(
                selected = preferences.preferredAudioLanguage == "ar",
                onClick = { onPreferences(preferences.copy(preferredAudioLanguage = "ar")) },
                label = { Text("العربية") }
            )
            FilterChip(
                selected = preferences.preferredAudioLanguage == "en",
                onClick = { onPreferences(preferences.copy(preferredAudioLanguage = "en")) },
                label = { Text("English") }
            )
        }
        Text("حجم ترجمة الإضافات", fontSize = 13.sp, color = CinemaxPalette.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("small" to "صغير", "medium" to "وسط", "large" to "كبير").forEach { (value, label) ->
                FilterChip(
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
        Text(status, color = CinemaxPalette.textMuted, fontSize = 12.sp, lineHeight = 18.sp)
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
                        if (activeAddonId == addon.id) "الكتالوج النشط • ${addon.version}" else addon.version,
                        color = if (activeAddonId == addon.id) accent else CinemaxPalette.textMuted,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = { onSelectAddon(addon) }) { Text("اختيار", fontSize = 12.sp) }
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
