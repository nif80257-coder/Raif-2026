package com.nuvio.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.watching.application.WatchLogUndo
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.watch_log_undo_action
import nuvio.composeapp.generated.resources.watch_log_undo_message
import org.jetbrains.compose.resources.stringResource

/**
 * Small bottom bar that offers to undo the last watch-log change for the [WatchLogUndo] window.
 */
@Composable
fun WatchLogUndoBar(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val snapshot by WatchLogUndo.state.collectAsStateWithLifecycle()
    val bottomInset = nuvioBottomNavigationBarInsets()
        .asPaddingValues()
        .calculateBottomPadding()

    AnimatedVisibility(
        visible = snapshot != null,
        modifier = modifier.padding(
            start = tokens.spacing.screenHorizontal,
            end = tokens.spacing.screenHorizontal,
            bottom = bottomInset + NuvioTokens.Space.s16,
        ),
        enter = fadeIn(tween(tokens.motion.fastMillis)) +
            slideInVertically(tween(tokens.motion.fastMillis)) { height -> height },
        exit = fadeOut(tween(tokens.motion.fastMillis)) +
            slideOutVertically(tween(tokens.motion.fastMillis)) { height -> height },
    ) {
        Surface(
            shape = tokens.shapes.card,
            color = tokens.colors.surfacePopover,
            tonalElevation = tokens.elevation.playerControls,
            shadowElevation = tokens.elevation.modal,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = NuvioTokens.Space.s16,
                        end = NuvioTokens.Space.s8,
                        top = NuvioTokens.Space.s8,
                        bottom = NuvioTokens.Space.s8,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = tokens.colors.textSecondary,
                    modifier = Modifier.size(tokens.icons.md),
                )
                Text(
                    text = stringResource(Res.string.watch_log_undo_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { WatchLogUndo.undo() },
                    colors = ButtonDefaults.textButtonColors(contentColor = tokens.colors.accent),
                ) {
                    Text(
                        text = stringResource(Res.string.watch_log_undo_action),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
