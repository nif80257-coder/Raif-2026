package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_subtitle_fetch
import nuvio.composeapp.generated.resources.player_subtitle_fetching
import org.jetbrains.compose.resources.stringResource

/**
 * A one-tap subtitle affordance over the video: it stays on screen while playback runs without a
 * subtitle, so fetching subtitles from the installed sources is never a hunt through the settings
 * panel. A tap fetches the add-on subtitles (once per item) and opens the subtitle panel.
 */
@Composable
internal fun PlayerSubtitleQuickAction(
    visible: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 220)) + scaleIn(tween(durationMillis = 220), initialScale = 0.9f),
        exit = fadeOut(tween(durationMillis = 160)) + scaleOut(tween(durationMillis = 160), targetScale = 0.9f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E1E).copy(alpha = 0.86f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Color.White,
                )
            } else {
                Text(
                    text = "CC",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(
                    if (isLoading) Res.string.player_subtitle_fetching else Res.string.player_subtitle_fetch,
                ),
                modifier = Modifier.padding(start = 8.dp),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}
