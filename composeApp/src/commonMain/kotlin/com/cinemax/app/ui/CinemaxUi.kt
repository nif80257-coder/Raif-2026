package com.cinemax.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cinemax.app.core.PosterStatus

/** CINEMAX palette: near-black canvas, elevated cards and one accent colour. */
object CinemaxPalette {
    val background = Color(0xFF08090D)
    val surface = Color(0xFF12141C)
    val surfaceHigh = Color(0xFF1A1D28)
    val outline = Color(0xFF262A38)
    val text = Color(0xFFF6F7FB)
    val textMuted = Color(0xFF98A0B0)
    val rating = Color(0xFFFFC857)

    /** Bottom scrim that fades artwork into the page background. */
    fun artworkScrim(bottom: Color): Brush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.45f to bottom.copy(alpha = 0.55f),
        1f to bottom
    )
}

/** Small rounded label such as "4K" or "HDR". */
@Composable
fun CinemaxPill(text: String, accent: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(accent.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

/** Section header with an accent tick, used for every poster row. */
@Composable
fun CinemaxSectionTitle(title: String, accent: Color, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(16.dp).background(accent, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(title, color = CinemaxPalette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * Full-bleed artwork with the page background fading in underneath: the
 * Stremio-style featured title at the top of the home screen.
 */
@Composable
fun CinemaxHero(
    title: String,
    overview: String?,
    imageUrl: String?,
    metaLine: String?,
    rating: String?,
    badges: List<String>,
    accent: Color,
    background: Color,
    onWatch: () -> Unit,
    onDetails: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(CinemaxPalette.surfaceHigh)
        )
        Box(Modifier.fillMaxSize().background(CinemaxPalette.artworkScrim(background)))
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.take(3).forEach { CinemaxPill(it, accent) }
                }
            }
            Text(
                title,
                color = CinemaxPalette.text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rating?.let {
                    Text("★ $it", color = CinemaxPalette.rating, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                metaLine?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = CinemaxPalette.textMuted, fontSize = 12.sp, maxLines = 1)
                }
            }
            overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = CinemaxPalette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onWatch,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("▶  مشاهدة", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDetails,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("التفاصيل", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Poster with a 2:3 artwork ratio, a corner badge and the title underneath. */
@Composable
fun CinemaxPosterCard(
    name: String,
    poster: String?,
    badge: String?,
    width: Dp = 126.dp,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    status: PosterStatus? = null,
    // Grids hand in a fillMaxWidth modifier instead of a fixed poster width.
    modifier: Modifier = Modifier.width(width)
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
                .clip(RoundedCornerShape(14.dp))
                .background(CinemaxPalette.surfaceHigh)
        ) {
            AsyncImage(
                model = poster,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            badge?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .background(accent, RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            PosterStatusMarks(status = status, accent = accent)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            name,
            color = CinemaxPalette.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The two marks a poster carries from the user's own history: a bar across the
 * bottom of the artwork for what was started, and a star for what is saved.
 * Called inside the artwork box, which is what it aligns against.
 */
@Composable
fun BoxScope.PosterStatusMarks(status: PosterStatus?, accent: Color) {
    val progress = status?.progress
    if (progress != null && progress > 0.005f) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0x66000000))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(accent)
            )
        }
    }
    if (status?.saved == true) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(7.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0xB3000000)),
            contentAlignment = Alignment.Center
        ) {
            Text("★", color = accent, fontSize = 10.sp)
        }
    }
}

/** Horizontally scrolling poster shelf. */
@Composable
fun CinemaxRow(
    content: LazyListScope.() -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

/** Round translucent control used over artwork (back, favourite, close). */
@Composable
fun CinemaxCircleButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0x8C000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 19.sp, textAlign = TextAlign.Center)
    }
}

/** Bottom navigation: home, search, my list, settings. */
@Composable
fun CinemaxBottomBar(selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    val entries = listOf(
        "الرئيسية" to "⌂",
        "بحث" to "⌕",
        "قائمتي" to "★",
        "الإعدادات" to "⚙"
    )
    NavigationBar(containerColor = CinemaxPalette.surface, tonalElevation = 0.dp) {
        entries.forEachIndexed { index, entry ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = {
                    Text(
                        entry.second,
                        fontSize = 19.sp,
                        color = if (selected == index) accent else CinemaxPalette.textMuted
                    )
                },
                label = {
                    Text(
                        entry.first,
                        fontSize = 11.sp,
                        color = if (selected == index) accent else CinemaxPalette.textMuted
                    )
                }
            )
        }
    }
}

/** Card container used for grouped settings and addon lists. */
@Composable
fun CinemaxCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CinemaxPalette.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}
