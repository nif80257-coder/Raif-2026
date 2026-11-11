package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.streams.StreamItem
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_search_clear
import nuvio.composeapp.generated.resources.compose_player_search_results_count
import org.jetbrains.compose.resources.stringResource

/** Everything a source can be found by: its label, release name, description, addon and badges. */
internal fun playerStreamSearchText(stream: StreamItem): String =
    buildString {
        stream.name?.takeIf { it.isNotBlank() }?.let { append(it) }
        stream.title?.takeIf { it.isNotBlank() }?.let {
            append(' ')
            append(it)
        }
        stream.description?.takeIf { it.isNotBlank() }?.let {
            append(' ')
            append(it)
        }
        append(' ')
        append(stream.addonName)
        stream.behaviorHints.filename?.takeIf { it.isNotBlank() }?.let {
            append(' ')
            append(it)
        }
        stream.clientResolve?.filename?.takeIf { it.isNotBlank() }?.let {
            append(' ')
            append(it)
        }
        stream.badges.forEach { badge ->
            if (badge.name.isNotBlank()) {
                append(' ')
                append(badge.name)
            }
        }
    }

/** Filters the player's source list by name, release text, quality tokens and addon name. */
internal fun filterPlayerStreams(streams: List<StreamItem>, query: String): List<StreamItem> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return streams
    return streams.filter { stream ->
        playerStreamSearchText(stream).contains(normalized, ignoreCase = true)
    }
}

/**
 * Filters the episodes of the selected season by title and episode number. A bare number matches
 * that episode exactly, while `E3`, `ep3` and `S1E3` style queries are matched against the code.
 */
internal fun filterSeasonEpisodes(episodes: List<MetaVideo>, query: String): List<MetaVideo> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return episodes
    val codeQuery = normalized.lowercase().filterNot { it.isWhitespace() }
    val numberQuery = codeQuery
        .removePrefix("ep")
        .removePrefix("e")
        .removePrefix("#")
        .toIntOrNull()
    return episodes.filter { episode ->
        val titleMatches = episode.title.contains(normalized, ignoreCase = true)
        val numberMatches = numberQuery != null && episode.episode == numberQuery
        val codeMatches = episode.episode != null && (
            codeQuery.contains("e${episode.episode}") ||
                codeQuery == episode.episode.toString() ||
                (episode.season != null && codeQuery.contains("s${episode.season}e${episode.episode}"))
            )
        titleMatches || numberMatches || codeMatches
    }
}

@Composable
internal fun PlayerPanelSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.colors.surfaceCard)
            .border(1.dp, tokens.colors.borderDefault, shape)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = tokens.colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.colors.textPrimary),
            cursorBrush = SolidColor(tokens.colors.accent),
            decorationBox = { innerTextField ->
                androidx.compose.foundation.layout.Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textMuted,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.compose_player_search_clear),
                    tint = tokens.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun PlayerSearchResultsLabel(count: Int, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Text(
        text = stringResource(Res.string.compose_player_search_results_count, count),
        color = tokens.colors.textMuted,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(start = 4.dp),
    )
}
