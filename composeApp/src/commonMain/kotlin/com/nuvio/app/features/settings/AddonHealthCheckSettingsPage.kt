package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AddonHealthCheckContent(isTablet: Boolean) {
    var focusedAddonUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        val pendingFocus = AddonHealthCheckRepository.consumeFocusedAddonUrl()
        if (pendingFocus != null) {
            focusedAddonUrl = pendingFocus
            AddonHealthCheckRepository.checkAddon(pendingFocus)
        }
    }
    val addonsState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val results by AddonHealthCheckRepository.results.collectAsStateWithLifecycle()
    val checking by AddonHealthCheckRepository.checking.collectAsStateWithLifecycle()
    val isCheckingAll by AddonHealthCheckRepository.isCheckingAll.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val tokens = MaterialTheme.nuvio
    var pendingDeleteUrl by remember { mutableStateOf<String?>(null) }

    val addons = remember(addonsState.addons, focusedAddonUrl) {
        val focused = focusedAddonUrl
        if (focused == null) {
            addonsState.addons
        } else {
            addonsState.addons.sortedByDescending { addon -> addon.manifestUrl == focused }
        }
    }

    SettingsSection(
        title = stringResource(Res.string.addon_health_check_title),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(Res.string.addon_health_check_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textSecondary,
                )
                Button(
                    onClick = { AddonHealthCheckRepository.checkAll() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = addons.isNotEmpty() && !isCheckingAll,
                ) {
                    Text(
                        text = stringResource(
                            if (isCheckingAll) {
                                Res.string.addon_health_check_checking_all
                            } else {
                                Res.string.addon_health_check_run_all
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (addons.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.addon_health_check_no_addons),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                }
            }
        }
    }

    addons.forEach { addon ->
        val result = results[addon.manifestUrl]
        val isChecking = addon.manifestUrl in checking
        SettingsSection(title = addon.displayTitle, isTablet = isTablet) {
            AddonHealthCard(
                isTablet = isTablet,
                addon = addon,
                result = result,
                isChecking = isChecking,
                onCheck = { AddonHealthCheckRepository.checkAddon(addon.manifestUrl) },
                onOpenManifest = {
                    runCatching { uriHandler.openUri(addon.manifestUrl) }
                },
                onToggleEnabled = {
                    AddonRepository.setAddonEnabled(addon.manifestUrl, !addon.enabled)
                },
                onDelete = { pendingDeleteUrl = addon.manifestUrl },
            )
        }
    }

    val deleteUrl = pendingDeleteUrl
    if (deleteUrl != null) {
        NuvioStatusModal(
            title = stringResource(Res.string.addon_health_check_remove_title),
            message = stringResource(Res.string.addon_health_check_remove_message),
            isVisible = true,
            destructive = true,
            confirmText = stringResource(Res.string.action_yes),
            dismissText = stringResource(Res.string.action_no),
            onConfirm = {
                AddonHealthCheckRepository.forget(pendingDeleteUrl.orEmpty())
                AddonRepository.removeAddon(pendingDeleteUrl.orEmpty())
                pendingDeleteUrl = null
            },
            onDismiss = { pendingDeleteUrl = null },
        )
    }
}

@Composable
private fun AddonHealthCard(
    isTablet: Boolean,
    addon: ManagedAddon,
    result: AddonCheckResult?,
    isChecking: Boolean,
    onCheck: () -> Unit,
    onOpenManifest: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    SettingsGroup(isTablet = isTablet) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                }
                Text(
                    text = statusLabel(result),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (result?.status == AddonHealthStatus.DOWN) {
                        MaterialTheme.colorScheme.error
                    } else {
                        tokens.colors.textPrimary
                    },
                )
            }

            if (result == null) {
                Text(
                    text = stringResource(Res.string.addon_health_check_not_tested),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
            } else {
                result.responseTimeMs?.let { elapsed ->
                    Text(
                        text = stringResource(Res.string.addon_health_check_response_time, elapsed),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textSecondary,
                    )
                }
                Text(
                    text = result.manifestVersion?.let { version ->
                        stringResource(Res.string.addon_health_check_manifest_version, version)
                    } ?: stringResource(Res.string.addon_health_check_manifest_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.manifestParsed) tokens.colors.textSecondary else MaterialTheme.colorScheme.error,
                )
                result.changedSinceLastCheck?.let { changed ->
                    Text(
                        text = stringResource(
                            if (changed) {
                                Res.string.addon_health_check_manifest_changed
                            } else {
                                Res.string.addon_health_check_manifest_unchanged
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                }
                Text(
                    text = stringResource(
                        Res.string.addon_health_check_types,
                        supportedTypesLabel(result.supportedTypes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textSecondary,
                )
                Text(
                    text = stringResource(
                        Res.string.addon_health_check_known_test,
                        result.knownTest.videoId,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
                Text(
                    text = stringResource(
                        Res.string.addon_health_check_streams,
                        knownContentCount(result.knownTest.streamCount, result.knownTest.streamsTimedOut, result.knownTest.streamsError),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textSecondary,
                )
                Text(
                    text = stringResource(
                        Res.string.addon_health_check_subtitles,
                        knownContentCount(
                            result.knownTest.subtitleCount,
                            result.knownTest.subtitlesTimedOut,
                            result.knownTest.subtitlesError,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textSecondary,
                )
                val error = result.manifestError
                if (error != null) {
                    Text(
                        text = stringResource(Res.string.addon_health_check_error, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCheck,
                    enabled = !isChecking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(Res.string.addon_health_check_test),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onOpenManifest,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(Res.string.addon_health_check_open_manifest),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onToggleEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (addon.enabled) {
                                Res.string.addon_health_check_disable
                            } else {
                                Res.string.addon_health_check_enable
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(Res.string.addon_health_check_remove),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun statusLabel(result: AddonCheckResult?): String = when (result?.status) {
    AddonHealthStatus.OK -> stringResource(Res.string.addon_health_check_status_ok)
    AddonHealthStatus.SLOW -> stringResource(Res.string.addon_health_check_status_slow)
    AddonHealthStatus.DOWN -> stringResource(Res.string.addon_health_check_status_down)
    AddonHealthStatus.DISABLED -> stringResource(Res.string.addon_health_check_status_disabled)
    null -> stringResource(Res.string.addon_health_check_status_unknown)
}

@Composable
private fun supportedTypesLabel(types: Set<AddonSupportedType>): String {
    if (types.isEmpty()) return stringResource(Res.string.addon_health_check_types_none)
    val catalogs = if (AddonSupportedType.CATALOGS in types) {
        stringResource(Res.string.addon_health_check_type_catalogs)
    } else {
        null
    }
    val meta = if (AddonSupportedType.META in types) {
        stringResource(Res.string.addon_health_check_type_meta)
    } else {
        null
    }
    val streams = if (AddonSupportedType.STREAMS in types) {
        stringResource(Res.string.addon_health_check_type_streams)
    } else {
        null
    }
    val subtitles = if (AddonSupportedType.SUBTITLES in types) {
        stringResource(Res.string.addon_health_check_type_subtitles)
    } else {
        null
    }
    return listOfNotNull(catalogs, meta, streams, subtitles).joinToString(", ")
}

@Composable
private fun knownContentCount(count: Int?, timedOut: Boolean, error: String?): String = when {
    count != null -> count.toString()
    timedOut -> stringResource(Res.string.addon_health_check_known_timeout)
    error != null -> stringResource(Res.string.addon_health_check_known_failed)
    else -> stringResource(Res.string.addon_health_check_known_not_supported)
}
