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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.AddonFailureMemory
import com.nuvio.app.features.addons.AddonPauseExceptions
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.SourceHealthInput
import com.nuvio.app.features.addons.SourceHealthReport
import com.nuvio.app.features.addons.SourceHealthRow
import com.nuvio.app.features.addons.SourceHealthStatus
import com.nuvio.app.features.addons.addonFailureReasonLabel
import com.nuvio.app.features.addons.failureAgeLabel
import com.nuvio.app.features.addons.searchableTitle
import com.nuvio.app.features.addons.sourceHealthReport
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.AddonSpeedMemory
import com.nuvio.app.features.streams.formatSourceSpeedSeconds
import kotlinx.coroutines.launch
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

    // The health section reads the three memories the stream requests write (failures, measured
    // speed and result count) once per change, instead of letting each row read storage again.
    // Resuming a source bumps the key so the section reflects the state it just changed.
    var healthRefreshKey by remember { mutableStateOf(0) }
    val healthReport = remember(addonsState.addons, healthRefreshKey) {
        val profileId = ProfileRepository.activeProfileId
        val failures = AddonFailureMemory.snapshot()
        val speeds = AddonSpeedMemory.speedByManifestUrl(profileId)
        val results = AddonSpeedMemory.resultsByManifestUrl(profileId)
        val lastSuccesses = AddonSpeedMemory.lastSuccessByManifestUrl(profileId)
        val neverPaused = AddonPauseExceptions.neverPausedManifestUrls(profileId)
        sourceHealthReport(
            addonsState.addons.map { addon ->
                SourceHealthInput(
                    manifestUrl = addon.manifestUrl,
                    name = addon.searchableTitle(),
                    enabled = addon.enabled,
                    neverPaused = addon.manifestUrl in neverPaused,
                    paused = addon.manifestUrl in failures.pausedManifestUrls,
                    failureStreak = failures.failureStreaks[addon.manifestUrl] ?: 0,
                    failureReason = failures.failureReasons[addon.manifestUrl],
                    lastFailureAtMs = failures.lastFailureAtMs[addon.manifestUrl],
                    lastSuccessAtMs = lastSuccesses[addon.manifestUrl],
                    speedMs = speeds[addon.manifestUrl],
                    resultCount = results[addon.manifestUrl],
                )
            },
        )
    }
    val healthScope = rememberCoroutineScope()
    val resumedToast = stringResource(Res.string.source_health_resumed)

    if (healthReport.summary.total > 0) {
        SourceHealthSection(
            isTablet = isTablet,
            report = healthReport,
            onResume = { row ->
                healthScope.launch {
                    AddonFailureMemory.recordSuccess(row.manifestUrl)
                    AddonSpeedMemory.forget(ProfileRepository.activeProfileId, row.manifestUrl)
                    healthRefreshKey += 1
                    NuvioToastController.show(resumedToast)
                }
            },
        )
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

/** How many sources the section names before it stops and points at the list below. */
private const val SourceHealthVisibleRows = 6

/**
 * The state of every source as the last stream request left it, worst first.
 *
 * The check cards below answer "does this source's manifest exist"; this answers the question the
 * user actually has when a title opens with too few links: which source is costing me, and what
 * would happen if I tried it again.
 */
@Composable
private fun SourceHealthSection(
    isTablet: Boolean,
    report: SourceHealthReport,
    onResume: (SourceHealthRow) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    SettingsSection(
        title = stringResource(Res.string.source_health_title),
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
                    text = stringResource(
                        Res.string.source_health_summary,
                        report.summary.answering,
                        report.summary.enabled,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textSecondary,
                )
                if (report.attention.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.source_health_all_good),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textSecondary,
                    )
                } else {
                    Text(
                        text = stringResource(
                            Res.string.source_health_attention,
                            report.summary.needsAttention,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = tokens.colors.textPrimary,
                    )
                    report.attention.take(SourceHealthVisibleRows).forEach { row ->
                        SourceHealthRowContent(
                            row = row,
                            onResume = { onResume(row) },
                        )
                    }
                    val hiddenCount = report.attention.size - SourceHealthVisibleRows
                    if (hiddenCount > 0) {
                        Text(
                            text = stringResource(Res.string.source_health_more, hiddenCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceHealthRowContent(
    row: SourceHealthRow,
    onResume: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sourceHealthDetail(row),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.status == SourceHealthStatus.Paused || row.status == SourceHealthStatus.Failing) {
            TextButton(onClick = onResume) {
                Text(
                    text = stringResource(Res.string.source_health_resume),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One line under a source name: what state it is in, then the measurements that explain it.
 *
 * The numbers come from the same memories the stream request writes, so a source that was slow
 * and empty an hour ago is described by that answer rather than by a fresh probe.
 */
@Composable
private fun sourceHealthDetail(row: SourceHealthRow): String {
    val status = when (row.status) {
        SourceHealthStatus.Paused -> stringResource(Res.string.addons_badge_paused)
        SourceHealthStatus.Failing -> stringResource(Res.string.source_health_status_failing)
        SourceHealthStatus.Silent -> stringResource(Res.string.source_health_status_silent)
        SourceHealthStatus.Slow -> stringResource(Res.string.source_health_status_slow)
        SourceHealthStatus.Untested -> stringResource(Res.string.source_health_status_untested)
        SourceHealthStatus.Healthy -> stringResource(Res.string.addon_health_check_status_ok)
        SourceHealthStatus.Disabled -> stringResource(Res.string.addon_health_check_status_disabled)
    }
    val measurements = listOfNotNull(
        row.failureReason?.let { reason -> addonFailureReasonLabel(reason) },
        row.lastFailureAtMs?.let { failedAt -> failureAgeLabel(failedAt) },
        // `failureAgeLabel` formats any age, so the same words say when the source last answered.
        row.lastSuccessAtMs
            ?.let { answeredAt -> failureAgeLabel(answeredAt) }
            ?.let { age -> stringResource(Res.string.source_health_last_answer, age) },
        row.speedMs
            ?.takeIf { speedMs -> speedMs > 0L }
            ?.let { speedMs ->
                stringResource(
                    Res.string.addons_badge_speed,
                    formatSourceSpeedSeconds(speedMs),
                )
            },
        row.resultCount?.let { count -> stringResource(Res.string.addons_badge_results, count) },
    )
    return (listOf(status) + measurements).joinToString(separator = " · ")
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
