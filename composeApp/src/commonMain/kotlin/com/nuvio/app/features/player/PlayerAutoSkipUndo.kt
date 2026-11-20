package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_auto_skip_undo
import org.jetbrains.compose.resources.stringResource

/** How long the way back stays offered after an automatic skip. */
internal const val AUTO_SKIP_UNDO_DURATION_MS = 6_000L

/**
 * What the last automatic skip moved away from. The opening/recap data can be wrong, so the skip is
 * announced with a way back instead of a confirmation that only says it happened.
 */
internal data class AutoSkipUndo(
    val message: String,
    val intervalStartMs: Long,
)

@Composable
internal fun PlayerAutoSkipUndo(
    message: String?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(durationMillis = 180)),
        exit = fadeOut(tween(durationMillis = 180)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E1E).copy(alpha = 0.9f))
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.orEmpty(),
                modifier = Modifier.weight(1f, fill = false),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onUndo,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.player_auto_skip_undo),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Puts playback back where the last automatic skip started. */
internal fun PlayerScreenRuntime.undoAutoSkip() {
    val undo = autoSkipUndo ?: return
    autoSkipUndo = null
    val durationMs = playbackSnapshot.durationMs
    val requestedMs = undo.intervalStartMs.coerceAtLeast(0L)
    val targetMs = if (durationMs > 0L) requestedMs.coerceAtMost(durationMs - 1) else requestedMs
    playerController?.seekTo(targetMs)
    scheduleProgressSyncAfterSeek()
    publishPartyPlayback(positionMs = targetMs)
    skipIntervalDismissed = true
}
