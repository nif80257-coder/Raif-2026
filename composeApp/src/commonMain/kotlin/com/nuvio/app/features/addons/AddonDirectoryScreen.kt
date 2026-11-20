package com.nuvio.app.features.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioInfoBadge
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSurfaceCard
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The add-ons an installed source lists through `addon_catalog`, opened from the add-on list so
 * a source can be browsed and its entries installed without pasting a manifest URL.
 */
@Composable
internal fun AddonDirectoryDialog(
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AddonDirectoryContent(onClose = onDismiss)
    }
}

@Composable
private fun AddonDirectoryContent(
    onClose: () -> Unit,
) {
    val state by AddonDirectoryRepository.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var installingUrl by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }

    val visibleEntries = remember(state.entries, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            state.entries
        } else {
            state.entries.filter { entry ->
                entry.name.lowercase().contains(needle) ||
                    entry.description.lowercase().contains(needle)
            }
        }
    }
    val genericLoadError = stringResource(Res.string.addons_directory_error)

    NuvioScreen(modifier = Modifier.fillMaxSize()) {
        stickyHeader {
            NuvioScreenHeader(
                title = state.title.ifBlank { stringResource(Res.string.addons_directory_title) },
                onBack = onClose,
            )
        }

        item {
            Text(
                text = stringResource(Res.string.addons_directory_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!state.isLoading && state.entries.isNotEmpty()) {
            item(key = "directory-count") {
                Text(
                    text = stringResource(Res.string.addons_directory_count, state.entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.isLoading) {
            item {
                Text(
                    text = stringResource(Res.string.addons_directory_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.isLoading && state.errorMessage != null) {
            item {
                NuvioSurfaceCard {
                    Text(
                        text = state.errorMessage?.takeIf(String::isNotBlank) ?: genericLoadError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        installError?.let { message ->
            item(key = "install-error") {
                NuvioSurfaceCard {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (state.entries.isNotEmpty()) {
            item {
                NuvioInputField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(Res.string.addons_directory_search_placeholder),
                )
            }
            if (state.entries.any { entry -> entry.isP2p }) {
                item {
                    Text(
                        text = stringResource(Res.string.addons_directory_p2p_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!state.isLoading && state.errorMessage == null && visibleEntries.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.addons_directory_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(items = visibleEntries, key = { entry -> entry.manifestUrl }) { entry ->
            AddonDirectoryEntryCard(
                entry = entry,
                isInstalling = installingUrl == entry.manifestUrl,
                canInstall = installingUrl == null,
                onInstall = {
                    if (installingUrl == null) {
                        installingUrl = entry.manifestUrl
                        installError = null
                        scope.launch {
                            when (val result = AddonRepository.addAddon(entry.manifestUrl)) {
                                is AddAddonResult.Success -> {
                                    installingUrl = null
                                    AddonDirectoryRepository.markInstalled(entry.manifestUrl)
                                }

                                is AddAddonResult.Error -> {
                                    installingUrl = null
                                    installError = result.message
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun AddonDirectoryEntryCard(
    entry: AddonDirectoryEntry,
    isInstalling: Boolean,
    canInstall: Boolean,
    onInstall: () -> Unit,
) {
    NuvioSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            DirectoryLogo(url = entry.logoUrl)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.types.isNotEmpty() || entry.isP2p || entry.isAdult || entry.isInstalled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        entry.types.forEach { type ->
                            NuvioInfoBadge(text = type)
                        }
                        if (entry.isP2p) {
                            NuvioInfoBadge(text = stringResource(Res.string.addons_directory_p2p))
                        }
                        if (entry.isAdult) {
                            NuvioInfoBadge(text = stringResource(Res.string.addons_directory_adult))
                        }
                        if (entry.isInstalled) {
                            NuvioInfoBadge(text = stringResource(Res.string.addons_directory_installed))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        NuvioPrimaryButton(
            text = when {
                entry.isInstalled -> stringResource(Res.string.addons_directory_installed)
                isInstalling -> stringResource(Res.string.addon_installing)
                else -> stringResource(Res.string.addons_directory_install)
            },
            enabled = !entry.isInstalled && canInstall,
            onClick = onInstall,
        )
    }
}

@Composable
private fun DirectoryLogo(url: String?) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
