package com.nuvio.app.features.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.components.posterGridColumnCountForWidth
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.favorites_browse
import nuvio.composeapp.generated.resources.favorites_count
import nuvio.composeapp.generated.resources.favorites_empty_message
import nuvio.composeapp.generated.resources.favorites_empty_title
import nuvio.composeapp.generated.resources.favorites_loading
import nuvio.composeapp.generated.resources.favorites_no_results
import nuvio.composeapp.generated.resources.favorites_play
import nuvio.composeapp.generated.resources.favorites_remove
import nuvio.composeapp.generated.resources.favorites_remove_message
import nuvio.composeapp.generated.resources.favorites_remove_title
import nuvio.composeapp.generated.resources.favorites_search_placeholder
import nuvio.composeapp.generated.resources.favorites_sort_name
import nuvio.composeapp.generated.resources.favorites_sort_recent
import nuvio.composeapp.generated.resources.favorites_title
import org.jetbrains.compose.resources.stringResource

internal enum class FavoritesSortOption {
    RECENT,
    NAME,
}

/** Filters the saved library by name and orders it the way the screen asks. */
internal fun buildFavoriteItems(
    items: List<LibraryItem>,
    query: String,
    sort: FavoritesSortOption,
): List<LibraryItem> {
    val normalizedQuery = query.trim()
    val filtered = items.asSequence()
        .filter { item -> normalizedQuery.isEmpty() || item.name.contains(normalizedQuery, ignoreCase = true) }
    return when (sort) {
        FavoritesSortOption.RECENT -> filtered.sortedByDescending { item -> item.savedAtEpochMs }.toList()
        FavoritesSortOption.NAME -> filtered
            .sortedWith(
                compareBy<LibraryItem> { item -> item.name.lowercase() }
                    .thenByDescending { item -> item.savedAtEpochMs },
            )
            .toList()
    }
}

@Composable
internal fun FavoritesScreen(
    onBack: () -> Unit,
    onBrowseClick: () -> Unit,
    onItemClick: (LibraryItem) -> Unit,
    onPlayClick: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val library by remember {
        LibraryRepository.ensureLoaded()
        LibraryRepository.uiState
    }.collectAsStateWithLifecycle()
    val tokens = MaterialTheme.nuvio

    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(FavoritesSortOption.RECENT.name) }
    val sort = remember(sortName) {
        runCatching { FavoritesSortOption.valueOf(sortName) }.getOrDefault(FavoritesSortOption.RECENT)
    }
    var pendingRemoval by remember { mutableStateOf<LibraryItem?>(null) }

    val favorites = remember(library.items, query, sort) {
        buildFavoriteItems(items = library.items, query = query, sort = sort)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = tokens.colors.textPrimary,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.favorites_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = tokens.colors.textPrimary,
                )
                if (library.items.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.favorites_count, library.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = tokens.colors.accent,
                modifier = Modifier.size(22.dp),
            )
        }

        when {
            !library.isLoaded -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.favorites_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                    )
                }
            }

            library.items.isEmpty() -> FavoritesEmptyState(
                onBrowseClick = onBrowseClick,
                modifier = Modifier.fillMaxSize(),
            )

            else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = posterGridColumnCountForWidth(maxWidth)
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FavoritesSearchField(
                                query = query,
                                onQueryChange = { query = it },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip(
                                    label = stringResource(Res.string.favorites_sort_recent),
                                    selected = sort == FavoritesSortOption.RECENT,
                                    onClick = { sortName = FavoritesSortOption.RECENT.name },
                                )
                                Chip(
                                    label = stringResource(Res.string.favorites_sort_name),
                                    selected = sort == FavoritesSortOption.NAME,
                                    onClick = { sortName = FavoritesSortOption.NAME.name },
                                )
                            }
                        }
                    }
                    if (favorites.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(Res.string.favorites_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.colors.textMuted,
                                modifier = Modifier.padding(vertical = 32.dp),
                            )
                        }
                    }
                    items(
                        count = favorites.size,
                        key = { index -> "${favorites[index].type}|${favorites[index].id}" },
                    ) { index ->
                        val item = favorites[index]
                        FavoriteCard(
                            item = item,
                            onItemClick = { onItemClick(item) },
                            onPlayClick = { onPlayClick(item) },
                            onLongClick = { pendingRemoval = item },
                        )
                    }
                }
            }
        }
    }

    val removalTarget = pendingRemoval
    if (removalTarget != null) {
        NuvioStatusModal(
            isVisible = true,
            title = stringResource(Res.string.favorites_remove_title),
            message = stringResource(Res.string.favorites_remove_message, removalTarget.name),
            destructive = true,
            confirmText = stringResource(Res.string.favorites_remove),
            dismissText = stringResource(Res.string.action_cancel),
            onConfirm = {
                LibraryRepository.remove(removalTarget.id)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun FavoritesEmptyState(
    onBrowseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = tokens.colors.accent.copy(alpha = 0.85f),
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(Res.string.favorites_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(Res.string.favorites_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        NuvioPrimaryButton(
            text = stringResource(Res.string.favorites_browse),
            modifier = Modifier.padding(top = 20.dp),
            onClick = onBrowseClick,
        )
    }
}

@Composable
private fun FavoritesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.colors.surface)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = tokens.colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 14.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.colors.textPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.colors.accent),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.favorites_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textMuted,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = { onQueryChange("") })
                    .padding(6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCard(
    item: LibraryItem,
    onItemClick: () -> Unit,
    onPlayClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val imageUrl = item.poster ?: item.banner
    val detail = item.releaseInfo?.let { formatReleaseDateForDisplay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(14.dp))
                .background(tokens.colors.surface)
                .combinedClickable(onClick = onItemClick, onLongClick = onLongClick),
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(32.dp),
            ) {
                IconButton(onClick = onPlayClick) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(Res.string.favorites_play),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detail ?: item.type,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
