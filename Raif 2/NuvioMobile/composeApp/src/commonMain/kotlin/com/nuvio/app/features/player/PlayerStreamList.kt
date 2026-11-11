package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.streams.LocalStreamSizeLabelFormat
import com.nuvio.app.features.streams.StreamBadgeSettingsRepository
import com.nuvio.app.features.streams.StreamCard
import com.nuvio.app.features.streams.StreamCardSkeleton
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamsUiState
import com.nuvio.app.features.streams.isSelectableForPlayback
import com.nuvio.app.features.streams.rememberStreamSizeLabelFormat
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_no_streams_found
import nuvio.composeapp.generated.resources.compose_player_search_no_results
import nuvio.composeapp.generated.resources.compose_player_search_sources_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerStreamList(
    streamsUiState: StreamsUiState,
    onStreamSelected: (StreamItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = 8.dp,
        top = 14.dp,
        end = 8.dp,
        bottom = 8.dp,
    ),
    currentStream: PlayerCurrentStream? = null,
    currentLabel: String? = null,
) {
    val debridSettings by remember {
        DebridSettingsRepository.ensureLoaded()
        DebridSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val streamBadgeSettings by remember {
        StreamBadgeSettingsRepository.ensureLoaded()
        StreamBadgeSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val allStreams = streamsUiState.allStreams
    val streams = remember(allStreams, searchQuery) {
        filterPlayerStreams(allStreams, searchQuery)
    }
    val visibleGroups = streamsUiState.filteredGroups

    when {
        allStreams.isEmpty() && streamsUiState.isAnyLoading -> {
            Column(
                modifier = modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(4) {
                    StreamCardSkeleton()
                }
            }
        }

        allStreams.isEmpty() -> {
            val error = visibleGroups.firstOrNull { it.error != null }?.error
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = error ?: stringResource(Res.string.compose_player_no_streams_found),
                    color = Color.White.copy(alpha = if (error == null) 0.7f else 0.85f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        else -> {
            val streamKeys = remember(streams) { streams.stablePlayerKeys() }
            val currentStreamIndex = remember(streams, currentStream) {
                streams.currentPlayerStreamIndex(currentStream)
            }
            val formatStreamSize = rememberStreamSizeLabelFormat()
            CompositionLocalProvider(LocalStreamSizeLabelFormat provides formatStreamSize) {
                Column(modifier = modifier.fillMaxWidth()) {
                    PlayerPanelSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(Res.string.compose_player_search_sources_placeholder),
                    )
                    if (searchQuery.isNotBlank()) {
                        PlayerSearchResultsLabel(
                            count = streams.size,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (streams.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = stringResource(Res.string.compose_player_search_no_results),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = contentPadding,
                        ) {
                            itemsIndexed(
                                items = streams,
                                key = { index, _ -> streamKeys[index] },
                            ) { index, stream ->
                                StreamCard(
                                    stream = stream,
                                    enabled = stream.isSelectableForPlayback(debridSettings.canResolvePlayableLinks),
                                    appendInstantServiceToDefaultName = debridSettings.canResolvePlayableLinks &&
                                        !debridSettings.hasCustomStreamFormatting,
                                    showFileSizeBadges = streamBadgeSettings.showFileSizeBadges,
                                    showAddonLogo = streamBadgeSettings.showAddonLogo,
                                    badgePlacement = streamBadgeSettings.badgePlacement,
                                    isCurrent = index == currentStreamIndex,
                                    currentLabel = currentLabel,
                                    onClick = { onStreamSelected(stream) },
                                )
                            }
                            if (streamsUiState.isAnyLoading) {
                                item {
                                    StreamCardSkeleton()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
