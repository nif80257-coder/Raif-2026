package com.nuvio.app.features.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.RaifDisplayFamily

/**
 * The RAIF lockup, drawn from the app's own name: an R whose bowl is the sweep of the Arabic ra and
 * whose counter is a play triangle, and the wordmark with its A turned into that same copper play
 * mark.
 *
 * The whole lockup scales with the height its container gives it, so the same mark works at 18 dp in
 * a settings row and at 72 dp on the launch screen.
 */
@Composable
internal fun AppBrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface

    BoxWithConstraints(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription ?: "RAIF"
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        val lockupHeight = maxHeight
            .takeIf { it.isSpecified && it.value.isFinite() }
            ?.coerceIn(16.dp, 96.dp)
            ?: 44.dp
        val monogramSize = lockupHeight * 0.78f
        val wordSize = (lockupHeight.value * 0.44f).sp

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(lockupHeight * 0.15f),
            ) {
                RaifMonogram(
                    modifier = Modifier
                        .size(monogramSize)
                        .clearAndSetSemantics {},
                    accentColor = accent,
                    lineColor = ink,
                )
                RaifWordmarkLine(
                    modifier = Modifier.clearAndSetSemantics {},
                    wordSize = wordSize,
                    ink = ink,
                    accent = accent,
                )
            }
        }
    }
}

@Composable
private fun RaifWordmarkLine(
    modifier: Modifier,
    wordSize: TextUnit,
    ink: Color,
    accent: Color,
) {
    val style = MaterialTheme.typography.titleLarge.copy(
        fontFamily = RaifDisplayFamily,
        fontSize = wordSize,
        lineHeight = wordSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = (wordSize.value * 0.07f).sp,
        color = ink,
    )
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(text = "R", style = style)
        PlayTriangle(
            modifier = Modifier
                .padding(bottom = (wordSize.value * 0.2f).dp)
                .size(
                    width = (wordSize.value * 0.5f).dp,
                    height = (wordSize.value * 0.58f).dp,
                ),
            color = accent,
        )
        Text(text = "IF", style = style)
    }
}

@Composable
private fun PlayTriangle(
    modifier: Modifier,
    color: Color,
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val radius = minOf(width, height) * 0.14f
        val path = Path().apply {
            moveTo(0f, radius)
            quadraticBezierTo(0f, 0f, radius, 0f)
            lineTo(width - radius, height * 0.5f - radius * 0.6f)
            quadraticBezierTo(width, height * 0.5f, width - radius, height * 0.5f + radius * 0.6f)
            lineTo(radius, height)
            quadraticBezierTo(0f, height, 0f, height - radius)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
private fun RaifMonogram(
    modifier: Modifier,
    accentColor: Color,
    lineColor: Color,
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = width * 0.13f
        val stemX = width * 0.30f
        val bowlCenter = Offset(stemX, height * 0.42f)
        val bowlRadius = height * 0.24f

        // Stem of the R, which is also where the Arabic ra begins.
        drawLine(
            color = lineColor,
            start = Offset(stemX, height * 0.16f),
            end = Offset(stemX, height * 0.86f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        // The bowl: a sweep that reads as the ra curve rather than a closed letter O.
        drawArc(
            color = lineColor,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(bowlCenter.x - bowlRadius, bowlCenter.y - bowlRadius),
            size = Size(bowlRadius * 2f, bowlRadius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Play triangle in the counter: the name's mark and the app's verb in one glyph.
        val triangleLeft = stemX + bowlRadius * 0.10f
        val triangleRight = stemX + bowlRadius * 0.58f
        val triangleHalfHeight = height * 0.10f
        drawPath(
            path = Path().apply {
                moveTo(triangleLeft, bowlCenter.y - triangleHalfHeight)
                lineTo(triangleRight, bowlCenter.y)
                lineTo(triangleLeft, bowlCenter.y + triangleHalfHeight)
                close()
            },
            color = accentColor,
        )

        // Leg of the R.
        drawLine(
            color = lineColor,
            start = Offset(
                stemX + bowlRadius * 0.62f,
                bowlCenter.y + bowlRadius * 0.62f,
            ),
            end = Offset(width * 0.86f, height * 0.86f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
