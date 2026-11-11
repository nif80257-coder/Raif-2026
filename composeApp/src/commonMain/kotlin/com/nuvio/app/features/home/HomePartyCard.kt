package com.nuvio.app.features.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.sync.AppForegroundMonitor
import com.nuvio.app.core.sync.AppVisibility
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.party.ActiveParty
import com.nuvio.app.features.party.PartyMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_party_card_host_paused
import nuvio.composeapp.generated.resources.home_party_card_host_watching
import nuvio.composeapp.generated.resources.home_party_card_members
import nuvio.composeapp.generated.resources.home_party_card_watch
import nuvio.composeapp.generated.resources.party_episode_format
import nuvio.composeapp.generated.resources.party_history_live
import nuvio.composeapp.generated.resources.party_title
import org.jetbrains.compose.resources.stringResource

/**
 * What the home screen needs to describe a party the user is still part of.
 * The click target keeps the media so the player can be reopened on the same
 * content even when this card is showing the last state held over a return to
 * the foreground.
 */
internal data class HomePartyCardState(
    val media: PartyMedia,
    val members: Int,
    val hostPaused: Boolean,
)

/**
 * The card lives while the party does. A party that ends or is left clears the
 * state, so the card goes away too, but a short window after the app returns to
 * the foreground keeps the last card so a reconnecting session does not make it
 * flicker away and back.
 */
@Composable
internal fun rememberHomePartyCard(
    party: ActiveParty?,
    hostPaused: Boolean,
): HomePartyCardState? {
    val live = remember(party, hostPaused) {
        party?.let { active ->
            HomePartyCardState(
                media = active.media,
                members = active.members.size,
                hostPaused = hostPaused,
            )
        }
    }
    var cached by remember { mutableStateOf<HomePartyCardState?>(null) }
    var holdAcrossForeground by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppForegroundMonitor.events()
            .flowOn(Dispatchers.Main)
            .filter { it == AppVisibility.Foreground }
            .collect {
                holdAcrossForeground = true
                delay(FOREGROUND_HOLD_MS)
                holdAcrossForeground = false
            }
    }

    LaunchedEffect(live, holdAcrossForeground) {
        when {
            live != null -> cached = live
            !holdAcrossForeground -> cached = null
        }
    }

    return live ?: cached?.takeIf { holdAcrossForeground }
}

@Composable
internal fun HomePartyCard(
    card: HomePartyCardState,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val title = card.media.title.trim().takeIf { it.isNotEmpty() }
        ?: stringResource(Res.string.party_title)
    val season = card.media.season
    val episodeNumber = card.media.episode
    val episode = if (season != null && episodeNumber != null) {
        stringResource(Res.string.party_episode_format, season, episodeNumber)
    } else {
        card.media.episodeTitle.orEmpty()
    }
    val live = stringResource(Res.string.party_history_live)
    val members = stringResource(Res.string.home_party_card_members, card.members)
    val host = stringResource(
        if (card.hostPaused) {
            Res.string.home_party_card_host_paused
        } else {
            Res.string.home_party_card_host_watching
        },
    )
    val watch = stringResource(Res.string.home_party_card_watch)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = tokens.colors.surfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(tokens.borders.hairline, tokens.colors.accent.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomePartyPoster(poster = card.media.poster, title = title)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.isNotBlank()) {
                    Text(
                        text = episode,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(LiveColor),
                    )
                    Text(
                        text = live,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LiveColor,
                        maxLines = 1,
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                    )
                    Text(
                        text = members,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = host,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.colors.accent)
                    .clickable(onClick = onWatch)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = watch,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HomePartyPoster(poster: String?, title: String) {
    val tokens = MaterialTheme.nuvio
    val url = poster?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.colors.textMuted,
            )
        }
    }
}

private val LiveColor = Color(0xFF4EC28A)
private const val FOREGROUND_HOLD_MS = 8_000L
