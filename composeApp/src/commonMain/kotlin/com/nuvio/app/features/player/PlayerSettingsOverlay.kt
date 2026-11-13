package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_audio
import nuvio.composeapp.generated.resources.compose_player_subtitles
import nuvio.composeapp.generated.resources.player_video_settings_title
import org.jetbrains.compose.resources.stringResource

/** Which settings body the unified player settings overlay shows. */
internal enum class PlayerSettingsTab {
    Subtitles,
    Audio,
    Video,
}

/**
 * One overlay instead of three: the subtitle, audio and video settings bodies share a
 * single [PlayerOverlayScaffold] with a tab row above the rails. State stays with the
 * existing per-panel booleans; [onTabSelected] simply flips them, and dismissal keeps its
 * previous per-panel callback.
 */
@Composable
internal fun PlayerSettingsOverlay(
    visible: Boolean,
    activeTab: PlayerSettingsTab,
    onTabSelected: (PlayerSettingsTab) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleContent: @Composable () -> Unit,
    audioContent: @Composable () -> Unit,
    videoContent: @Composable () -> Unit,
) {
    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentPadding = PaddingValues(start = 52.dp, end = 52.dp, top = 36.dp, bottom = 76.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PlayerSettingsTabRow(
                    activeTab = activeTab,
                    onTabSelected = onTabSelected,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    when (activeTab) {
                        PlayerSettingsTab.Subtitles -> subtitleContent()
                        PlayerSettingsTab.Audio -> audioContent()
                        PlayerSettingsTab.Video -> videoContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSettingsTabRow(
    activeTab: PlayerSettingsTab,
    onTabSelected: (PlayerSettingsTab) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val items = listOf(
        PlayerSettingsTab.Subtitles to Res.string.compose_player_subtitles,
        PlayerSettingsTab.Audio to Res.string.compose_player_audio,
        PlayerSettingsTab.Video to Res.string.player_video_settings_title,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (tab, labelRes) ->
            val shape = RoundedCornerShape(percent = 50)
            val selected = tab == activeTab
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (selected) tokens.colors.accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                    )
                    .border(
                        1.dp,
                        if (selected) tokens.colors.accent.copy(alpha = 0.5f) else tokens.colors.borderDefault,
                        shape,
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(labelRes),
                    color = if (selected) tokens.colors.accent else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}