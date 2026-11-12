package com.nuvio.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(percent = 50)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        enabled = enabled,
        shape = shape,
        color = if (selected) tokens.colors.accent.copy(alpha = 0.16f) else Color.Transparent,
        contentColor = when {
            !enabled -> tokens.colors.textDisabled
            selected -> tokens.colors.accent
            else -> tokens.colors.textSecondary
        },
        border = BorderStroke(
            width = tokens.borders.thin,
            color = when {
                selected -> tokens.colors.accent.copy(alpha = 0.5f)
                enabled -> tokens.colors.borderSubtle
                else -> tokens.colors.borderDefault
            },
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = NuvioTokens.Space.s14,
                vertical = tokens.components.chipVerticalPadding,
            ),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
