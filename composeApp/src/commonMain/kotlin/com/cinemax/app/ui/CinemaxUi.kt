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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cinemax.app.core.PosterStatus

/**
 * Palette and shapes after Swiftfin (the open-source Jellyfin client for iOS): a
 * pure black canvas, white type, one accent colour, and frosted translucent
 * capsules for every control instead of opaque boxes.
 */
object CinemaxPalette {
    val background = Color.Black
    val surface = Color(0xFF0E0E10)
    val surfaceHigh = Color(0xFF1C1C1E)
    val outline = Color(0xFF2C2C2E)
    val text = Color.White
    val textMuted = Color(0xFF8E8E93)
    val rating = Color.White

    /** Translucent fill for chips, secondary buttons and circular controls. */
    val frosted = Color(0x33FFFFFF)
    val frostedStrong = Color(0x59FFFFFF)

    /** The pink-red heart Swiftfin draws on a saved poster. */
    val favorite = Color(0xFFFF4B83)

    /** Bottom scrim that fades artwork into the page background. */
    fun artworkScrim(bottom: Color): Brush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.40f to bottom.copy(alpha = 0.45f),
        1f to bottom
    )
}

/** Capsule label such as "4K", "HDR" or "مفضل", frosted like Swiftfin's chips. */
@Composable
fun CinemaxPill(text: String, accent: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(CinemaxPalette.frosted, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * Filter chip in Swiftfin's style: a translucent capsule that turns solid white
 * with black text when selected.
 */
@Composable
fun CinemaxFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) CinemaxPalette.text else CinemaxPalette.frosted)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) Color.Black else Color.White
        ) {
            label()
        }
    }
}

/** Section header: bold 20sp title over a hairline, with room to breathe. */
@Composable
fun CinemaxSectionTitle(title: String, accent: Color, trailing: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = CinemaxPalette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CinemaxPalette.outline.copy(alpha = 0.7f))
        )
    }
}

/**
 * Full-bleed artwork with the page background fading in underneath: the featured
 * title at the top of the home screen, with Swiftfin's black-on-accent play pill.
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
    Box(Modifier.fillMaxWidth().height(430.dp)) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(CinemaxPalette.surfaceHigh)
        )
        Box(Modifier.fillMaxSize().background(CinemaxPalette.artworkScrim(background)))
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.take(3).forEach { CinemaxPill(it, accent) }
                }
            }
            Text(
                title,
                color = CinemaxPalette.text,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rating?.let {
                    Text(
                        "★ $it",
                        color = CinemaxPalette.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(CinemaxPalette.frosted, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                metaLine?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = CinemaxPalette.textMuted, fontSize = 13.sp, maxLines = 1)
                }
            }
            overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = CinemaxPalette.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onWatch,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                    modifier = Modifier.weight(1.4f).height(52.dp)
                ) {
                    Text("▶  مشاهدة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Button(
                    onClick = onDetails,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CinemaxPalette.frosted,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text("التفاصيل", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

/** Poster with a 2:3 artwork ratio, Swiftfin's small radius and heart/bar marks. */
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
    val scale by animateFloatAsState(targetValue = if (pressed) 0.96f else 1f)
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
                        .background(accent, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            // A soft scrim at the foot of the poster keeps the heart, the progress
            // bar and any bright artwork from fighting each other.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x8C000000))))
            )
            PosterStatusMarks(status = status, accent = accent)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            color = CinemaxPalette.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The marks a poster carries from the user's own history, drawn the way Swiftfin
 * does it: a pink heart for a saved title and a thin accent bar for progress.
 */
@Composable
fun BoxScope.PosterStatusMarks(status: PosterStatus?, accent: Color) {
    val progress = status?.progress
    if (progress != null && progress > 0.005f) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
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
                .align(Alignment.BottomEnd)
                .padding(7.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(CinemaxPalette.favorite),
            contentAlignment = Alignment.Center
        ) {
            Text("♥", color = Color.White, fontSize = 11.sp)
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

/** Round frosted control used over artwork, the shape of Swiftfin's back button. */
@Composable
fun CinemaxCircleButton(label: String, contentDescription: String? = null, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CinemaxPalette.frostedStrong)
            .clickable(onClick = onClick)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
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
            .clip(RoundedCornerShape(20.dp))
            .background(CinemaxPalette.surfaceHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}
