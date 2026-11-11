package com.nuvio.app.features.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvioTypeScale
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_sleep_timer
import nuvio.composeapp.generated.resources.player_sleep_timer_cancel
import nuvio.composeapp.generated.resources.player_sleep_timer_end_of_episode
import nuvio.composeapp.generated.resources.player_sleep_timer_locked_message
import nuvio.composeapp.generated.resources.player_sleep_timer_locked_title
import nuvio.composeapp.generated.resources.player_sleep_timer_minutes_15
import nuvio.composeapp.generated.resources.player_sleep_timer_minutes_30
import nuvio.composeapp.generated.resources.player_sleep_timer_minutes_45
import nuvio.composeapp.generated.resources.player_sleep_timer_minutes_60
import nuvio.composeapp.generated.resources.player_sleep_timer_resume
import org.jetbrains.compose.resources.stringResource

internal const val SleepTimerTickMs = 500L
internal const val SleepTimerEpisodeEndWindowMs = 1_500L

internal enum class PlayerSleepTimerOption(val minutes: Int?) {
    MINUTES_15(15),
    MINUTES_30(30),
    MINUTES_45(45),
    MINUTES_60(60),
    END_OF_EPISODE(null),
}

/** The last option the user picked, kept for the lifetime of the app session. */
internal object PlayerSleepTimerSession {
    var lastOption by mutableStateOf<PlayerSleepTimerOption?>(null)
}

/** True once the chosen timer has to stop playback. */
internal fun shouldSleepAtEpisodeEnd(
    option: PlayerSleepTimerOption?,
    snapshot: PlayerPlaybackSnapshot,
): Boolean {
    if (option != PlayerSleepTimerOption.END_OF_EPISODE) return false
    if (snapshot.isEnded) return true
    return snapshot.durationMs > 0L &&
        snapshot.positionMs >= snapshot.durationMs - SleepTimerEpisodeEndWindowMs
}

/** Countdown label such as "12:30" or "1:02:30". */
internal fun formatSleepTimerRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val paddedSeconds = seconds.toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:$paddedSeconds"
    } else {
        "$minutes:$paddedSeconds"
    }
}

@Composable
private fun sleepTimerOptionLabel(option: PlayerSleepTimerOption): String = when (option) {
    PlayerSleepTimerOption.MINUTES_15 -> stringResource(Res.string.player_sleep_timer_minutes_15)
    PlayerSleepTimerOption.MINUTES_30 -> stringResource(Res.string.player_sleep_timer_minutes_30)
    PlayerSleepTimerOption.MINUTES_45 -> stringResource(Res.string.player_sleep_timer_minutes_45)
    PlayerSleepTimerOption.MINUTES_60 -> stringResource(Res.string.player_sleep_timer_minutes_60)
    PlayerSleepTimerOption.END_OF_EPISODE -> stringResource(Res.string.player_sleep_timer_end_of_episode)
}

@Composable
internal fun PlayerSleepTimerModal(
    visible: Boolean,
    activeOption: PlayerSleepTimerOption?,
    onOptionSelected: (PlayerSleepTimerOption) -> Unit,
    onClearTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {},
                ),
            color = Color(0xFF161616),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.player_sleep_timer),
                        style = MaterialTheme.nuvioTypeScale.bodyLg.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }

                PlayerSleepTimerOption.entries.forEach { option ->
                    val selected = option == activeOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = sleepTimerOptionLabel(option),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.nuvioTypeScale.bodyMd,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                if (activeOption != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClearTimer)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.player_sleep_timer_cancel),
                            style = MaterialTheme.nuvioTypeScale.bodyMd,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerSleepTimerLockScreen(
    title: String,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = {}, indication = null, interactionSource = remember { MutableInteractionSource() }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = stringResource(Res.string.player_sleep_timer_locked_title),
                style = MaterialTheme.nuvioTypeScale.displayMd,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = title,
                style = MaterialTheme.nuvioTypeScale.bodyMd,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.player_sleep_timer_locked_message),
                style = MaterialTheme.nuvioTypeScale.bodySm,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onResume,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) {
                Text(
                    text = stringResource(Res.string.player_sleep_timer_resume),
                    style = MaterialTheme.nuvioTypeScale.bodyLg.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
