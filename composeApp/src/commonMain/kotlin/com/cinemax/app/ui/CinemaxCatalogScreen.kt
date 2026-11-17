package com.cinemax.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinemax.app.addons.StremioMetaPreview
import com.cinemax.app.core.PosterStatus
import com.cinemax.app.core.matchesLibraryQuery
import com.cinemax.app.core.normalizeForSearch

/**
 * A whole catalog as a poster grid, the screen behind "المزيد" on a home shelf.
 * The first page is the shelf the user tapped; more pages are requested with the
 * Stremio `skip` extra and appended without duplicates.
 */
@Composable
fun CinemaxCatalogScreen(
    title: String,
    items: List<StremioMetaPreview>,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    accent: Color,
    background: Color,
    onOpen: (StremioMetaPreview) -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    statuses: Map<String, PosterStatus>,
    genres: List<String> = emptyList(),
    selectedGenre: String? = null,
    onGenre: (String?) -> Unit = {},
    sorts: List<String> = emptyList(),
    selectedSort: String? = null,
    onSort: (String) -> Unit = {},
    onToggleFavorite: (StremioMetaPreview) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val visibleItems = if (query.isBlank()) {
        items
    } else {
        // While searching, the closest names come first instead of the API order.
        val needle = normalizeForSearch(query)
        items.filter { matchesLibraryQuery(it.name, query) }
            .sortedWith(
                compareBy(
                    {
                        val name = normalizeForSearch(it.name)
                        when {
                            name == needle -> 0
                            name.startsWith(needle) -> 1
                            else -> 2
                        }
                    },
                    { it.name }
                )
            )
    }
    Column(Modifier.fillMaxSize().background(background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CinemaxCircleButton("‹") { onBack() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    if (query.isBlank()) "${items.size} عنوانًا" else "${visibleItems.size} من ${items.size}",
                    color = CinemaxPalette.textMuted,
                    fontSize = 11.sp
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            placeholder = { Text("ابحث داخل هذه القائمة", fontSize = 12.sp) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { query = "" }) { Text("مسح", color = accent, fontSize = 11.sp) }
                }
            }
        )
        if (genres.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CinemaxFilterChip(
                    selected = selectedGenre == null,
                    onClick = { onGenre(null) },
                    label = { Text("الكل", fontSize = 11.sp) }
                )
                genres.forEach { genre ->
                    CinemaxFilterChip(
                        selected = selectedGenre == genre,
                        onClick = { onGenre(genre) },
                        label = { Text(genre, fontSize = 11.sp) }
                    )
                }
            }
        }
        if (sorts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sorts.forEach { option ->
                    CinemaxFilterChip(
                        selected = (selectedSort ?: sorts.first()) == option,
                        onClick = { onSort(option) },
                        label = { Text(option, fontSize = 11.sp) }
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            gridItems(visibleItems) { item ->
                CinemaxPosterCard(
                    name = item.name,
                    poster = item.poster,
                    badge = item.releaseInfo,
                    accent = accent,
                    onClick = { onOpen(item) },
                    onLongClick = { onToggleFavorite(item) },
                    status = statuses[item.id],
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loadingMore -> CircularProgressIndicator(
                            Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = accent
                        )
                        canLoadMore -> Button(
                            onClick = onLoadMore,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color.Black
                            )
                        ) { Text("تحميل المزيد", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        else -> Text(
                            when {
                                items.isEmpty() -> "لا يوجد عنوان في هذا الكتالوج."
                                visibleItems.isEmpty() -> "لا عنوان في هذه القائمة يطابق «${query.trim()}»."
                                else -> "نهاية القائمة"
                            },
                            color = CinemaxPalette.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
