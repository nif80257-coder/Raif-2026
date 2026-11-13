package com.cinemax.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinemax.app.addons.StremioMetaPreview
import com.cinemax.app.core.PosterStatus

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
    statuses: Map<String, PosterStatus>
) {
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
                    "${items.size} عنوانًا",
                    color = CinemaxPalette.textMuted,
                    fontSize = 11.sp
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            gridItems(items) { item ->
                CinemaxPosterCard(
                    name = item.name,
                    poster = item.poster,
                    badge = item.releaseInfo,
                    accent = accent,
                    onClick = { onOpen(item) },
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
                            if (items.isEmpty()) "لا يوجد عنوان في هذا الكتالوج." else "نهاية القائمة",
                            color = CinemaxPalette.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
