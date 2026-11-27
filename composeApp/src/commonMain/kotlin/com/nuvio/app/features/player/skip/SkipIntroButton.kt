package com.nuvio.app.features.player.skip

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_skip
import nuvio.composeapp.generated.resources.player_skip_intro
import nuvio.composeapp.generated.resources.player_skip_outro
import nuvio.composeapp.generated.resources.player_skip_recap
import nuvio.composeapp.generated.resources.player_skip_movie_credits
import nuvio.composeapp.generated.resources.player_skip_to_post_credits
import org.jetbrains.compose.resources.stringResource

/**
 * The opening/recap/credits offer. It stays for as long as the segment plays, because skipping is
 * the viewer's choice: the button is offered, never pressed for them.
 */
@Composable
fun SkipIntroButton(
    interval: SkipInterval?,
    dismissed: Boolean,
    controlsVisible: Boolean,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    skipsToPostCredits: Boolean = false,
) {
    var lastType by remember { mutableStateOf(interval?.type) }
    var lastSkipsToPostCredits by remember { mutableStateOf(skipsToPostCredits) }
    val canSkip = interval?.internalSkipAction(emptyList()) != null
    if (canSkip) {
        lastType = interval.type
        lastSkipsToPostCredits = skipsToPostCredits
    }
    val shouldShow = canSkip && (!dismissed || controlsVisible)

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.8f),
        exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.8f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E1E).copy(alpha = 0.85f))
                .clickable(enabled = canSkip) { onSkip() }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = skipLabel(lastType, lastSkipsToPostCredits),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun skipLabel(type: String?, skipsToPostCredits: Boolean): String =
    when (type?.lowercase()) {
        "intro", "op", "mixed-op" -> stringResource(Res.string.player_skip_intro)
        "outro", "ed", "mixed-ed", "credits" -> stringResource(
            if (skipsToPostCredits) Res.string.player_skip_to_post_credits else Res.string.player_skip_outro,
        )
        "recap" -> stringResource(Res.string.player_skip_recap)
        "movie-credits" -> stringResource(
            if (skipsToPostCredits) Res.string.player_skip_to_post_credits else Res.string.player_skip_movie_credits,
        )
        else -> stringResource(Res.string.player_skip)
    }
