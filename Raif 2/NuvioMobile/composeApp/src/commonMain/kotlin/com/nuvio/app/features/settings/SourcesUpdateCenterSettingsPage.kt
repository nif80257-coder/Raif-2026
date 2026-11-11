package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SourcesUpdateCenterScreenContent(isTablet: Boolean) {
    LaunchedEffect(Unit) {
        RaifUpdatesRepository.runAutomaticDailyCheck()
        RaifUpdatesRepository.refreshAppRelease()
    }
    val addonResults by RaifUpdatesRepository.addonResults.collectAsStateWithLifecycle()
    val isCheckingAddons by RaifUpdatesRepository.isCheckingAddons.collectAsStateWithLifecycle()
    val lastCheckEpochMs by RaifUpdatesRepository.lastAddonCheckEpochMs.collectAsStateWithLifecycle()
    val appState by RaifUpdatesRepository.appState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val tokens = MaterialTheme.nuvio

    val results = addonResults.values.toList()
    val changedCount = results.count { it.state == AddonUpdateState.CHANGED }
    val unchangedCount = results.count { it.state == AddonUpdateState.UNCHANGED }
    val lastCheckText = lastCheckEpochMs?.let(::formatLocalDateTime)

    SettingsSection(
        title = stringResource(Res.string.updates_addons_section),
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
                    text = stringResource(Res.string.updates_addons_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textSecondary,
                )
                Button(
                    onClick = { RaifUpdatesRepository.checkAddons(force = true) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCheckingAddons,
                ) {
                    Text(
                        text = stringResource(
                            if (isCheckingAddons) {
                                Res.string.updates_checking
                            } else {
                                Res.string.updates_check_all
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (lastCheckText != null) {
                        stringResource(Res.string.updates_last_check, lastCheckText)
                    } else {
                        stringResource(Res.string.updates_never_checked)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
                if (results.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.updates_changed_count, changedCount) +
                            " · " +
                            stringResource(Res.string.updates_unchanged_count, unchangedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textSecondary,
                    )
                }
            }
        }
    }

    if (results.isEmpty()) {
        SettingsSection(
            title = stringResource(Res.string.updates_results_section),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                Text(
                    text = stringResource(Res.string.updates_no_results),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    } else {
        results
            .sortedWith(compareBy<AddonUpdateResult> { it.state != AddonUpdateState.CHANGED }.thenBy { it.name })
            .forEach { result ->
                SettingsSection(title = result.name, isTablet = isTablet) {
                    SettingsGroup(isTablet = isTablet) {
                        AddonUpdateRow(result = result)
                    }
                }
            }
    }

    SettingsSection(
        title = stringResource(Res.string.updates_app_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.updates_installed_version, appState.installedVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textPrimary,
                )
                if (appState.isLoading) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(Res.string.updates_checking),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textSecondary,
                        )
                    }
                }
                val appError = appState.error
                if (appError != null) {
                    Text(
                        text = stringResource(Res.string.updates_app_error, appError),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val latest = appState.latest
                if (latest != null) {
                    Text(
                        text = stringResource(Res.string.updates_center_latest_version, latest.version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textPrimary,
                    )
                    latest.releaseDate?.takeIf(String::isNotBlank)?.let { date ->
                        Text(
                            text = stringResource(Res.string.updates_release_date, date),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textSecondary,
                        )
                    }
                    latest.notes?.takeIf(String::isNotBlank)?.let { notes ->
                        Text(
                            text = stringResource(Res.string.updates_notes),
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.colors.textPrimary,
                        )
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textSecondary,
                        )
                    }
                    if (appState.updateAvailable) {
                        val downloadUrl = latest.downloadUrl
                        if (downloadUrl != null) {
                            Button(
                                onClick = { runCatching { uriHandler.openUri(downloadUrl) } },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(Res.string.updates_open_download),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(Res.string.updates_up_to_date),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textMuted,
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.updates_ios_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
                OutlinedButton(
                    onClick = { RaifUpdatesRepository.refreshAppRelease() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !appState.isLoading,
                ) {
                    Text(
                        text = stringResource(Res.string.updates_refresh_app),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddonUpdateRow(result: AddonUpdateResult) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = addonUpdateStateLabel(result.state),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = when (result.state) {
                AddonUpdateState.ERROR -> MaterialTheme.colorScheme.error
                AddonUpdateState.CHANGED -> tokens.colors.textPrimary
                else -> tokens.colors.textSecondary
            },
        )
        val previous = result.previousVersion
        val current = result.currentVersion
        if (previous != null && current != null) {
            Text(
                text = stringResource(Res.string.updates_version_line, previous, current),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textSecondary,
            )
        } else if (current != null) {
            Text(
                text = stringResource(Res.string.updates_first_version_line, current),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textSecondary,
            )
        }
        val error = result.error
        if (error != null) {
            Text(
                text = stringResource(Res.string.updates_addon_error, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        DiffLine(Res.string.updates_added_catalogs, result.diff.addedCatalogs)
        DiffLine(Res.string.updates_removed_catalogs, result.diff.removedCatalogs)
        DiffLine(Res.string.updates_added_types, result.diff.addedTypes)
        DiffLine(Res.string.updates_removed_types, result.diff.removedTypes)
        DiffLine(Res.string.updates_added_services, result.diff.addedResources)
        DiffLine(Res.string.updates_removed_services, result.diff.removedResources)
    }
}

@Composable
private fun DiffLine(
    label: org.jetbrains.compose.resources.StringResource,
    values: List<String>,
) {
    if (values.isEmpty()) return
    Text(
        text = stringResource(label, values.joinToString(", ")),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.nuvio.colors.textMuted,
    )
}

@Composable
private fun addonUpdateStateLabel(state: AddonUpdateState): String = when (state) {
    AddonUpdateState.CHANGED -> stringResource(Res.string.updates_state_changed)
    AddonUpdateState.UNCHANGED -> stringResource(Res.string.updates_state_unchanged)
    AddonUpdateState.NOT_CHECKED -> stringResource(Res.string.updates_state_not_checked)
    AddonUpdateState.ERROR -> stringResource(Res.string.updates_state_error)
}
