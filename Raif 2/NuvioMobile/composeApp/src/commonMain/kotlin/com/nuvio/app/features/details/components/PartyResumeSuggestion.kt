package com.nuvio.app.features.details.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.player.formatPlaybackTime
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.party_resume_suggestion_dismiss
import nuvio.composeapp.generated.resources.party_resume_suggestion_play
import nuvio.composeapp.generated.resources.party_resume_suggestion_title
import org.jetbrains.compose.resources.stringResource

internal data class PartyResumeCardData(
    val positionMs: Long,
    val onPlay: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun PartyResumeSuggestionCard(
    data: PartyResumeCardData,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = tokens.colors.surfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(tokens.borders.hairline, tokens.colors.accent.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tokens.colors.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(
                        Res.string.party_resume_suggestion_title,
                        formatPlaybackTime(data.positionMs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.colors.accent)
                    .clickable(onClick = data.onPlay)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.party_resume_suggestion_play),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
            IconButton(
                onClick = data.onDismiss,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.party_resume_suggestion_dismiss),
                    tint = tokens.colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
