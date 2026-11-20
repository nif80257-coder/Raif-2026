package com.nuvio.app.features.addons

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioIconActionButton
import com.nuvio.app.core.ui.NuvioInfoBadge
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.features.home.ContentFilterRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.settings.SourcesTransferDialog
import com.nuvio.app.features.streams.AddonSpeedMemory
import com.nuvio.app.features.streams.formatSourceSpeedSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddonsScreen(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
) {
    NuvioScreen(modifier = modifier) {
        stickyHeader {
            NuvioScreenHeader(
                title = title ?: stringResource(Res.string.addon_title),
                onBack = onBack,
            ) {
            }
        }
        item {
            AddonsSettingsPageContent()
        }
    }
}

@Composable
internal fun AddonsSettingsPageContent(
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        AddonRepository.initialize()
    }

    val uiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    var addonUrl by rememberSaveable { mutableStateOf("") }
    var formMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var installModalState by remember { mutableStateOf<AddonInstallModalState?>(null) }
    var addonPendingDeletionUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val enterAddonUrlMessage = stringResource(Res.string.addons_error_enter_url)
    val usePersonalMediaCopy = AppFeaturePolicy.personalMediaAddonCopyEnabled
    var sourcesTransferVisible by rememberSaveable { mutableStateOf(false) }
    var addonReviewVisible by rememberSaveable { mutableStateOf(false) }
    var addonDirectoryVisible by rememberSaveable { mutableStateOf(false) }
    var configTarget by remember { mutableStateOf<ManagedAddon?>(null) }
    var urlEditTarget by remember { mutableStateOf<ManagedAddon?>(null) }
    var resetBuiltInVisible by rememberSaveable { mutableStateOf(false) }
    var isResettingBuiltIns by remember { mutableStateOf(false) }

    // Repositories the user folded away, so the screen keeps only the ones being worked on.
    var collapsedSourceRepositories by remember { mutableStateOf(setOf<String>()) }

    val overview = remember(uiState.addons) { uiState.addons.toOverview() }

    // Sources that failed three stream requests in a row and are waiting out their pause. The
    // set is recomputed when the installed list changes or the user resumes them.
    var health by remember { mutableStateOf(AddonsHealthUiState()) }
    var pausedSourcesRefreshToken by remember { mutableStateOf(0) }
    LaunchedEffect(pausedSourcesRefreshToken, uiState.addons.size) {
        // One storage read for both the paused set and the per-source streaks.
        val failures = AddonFailureMemory.snapshot()
        val profileId = ProfileRepository.activeProfileId
        health = AddonsHealthUiState(
            pausedManifestUrls = failures.pausedManifestUrls,
            neverPausedManifestUrls = AddonPauseExceptions.neverPausedManifestUrls(profileId),
            failureStreaks = failures.failureStreaks,
            failureReasons = failures.failureReasons,
            lastFailureAtMs = failures.lastFailureAtMs,
            sourceSpeeds = AddonSpeedMemory.speedByManifestUrl(profileId)
                .mapValues { (_, ms) -> formatSourceSpeedSeconds(ms) },
        )
    }

    // Typing stays responsive: the text field updates at once and the list filters after a
    // short pause, instead of scanning every installed add-on on each keystroke.
    var addonQueryInput by rememberSaveable { mutableStateOf("") }
    var addonQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(addonQueryInput) {
        if (addonQueryInput == addonQuery) return@LaunchedEffect
        delay(ADDON_SEARCH_DEBOUNCE_MS)
        addonQuery = addonQueryInput
    }
    val visibleAddons = remember(uiState.addons, addonQuery) {
        uiState.addons.matchingQuery(addonQuery)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(stringResource(Res.string.addons_section_overview))
        OverviewCard(overview = overview)

        if (health.pausedManifestUrls.isNotEmpty()) {
            PausedSourcesCard(
                pausedCount = health.pausedManifestUrls.size,
                onResume = {
                    coroutineScope.launch {
                        AddonFailureMemory.clearAll()
                        pausedSourcesRefreshToken++
                    }
                },
            )
        }

        SectionHeader(stringResource(Res.string.sources_transfer_title))
        Button(
            onClick = { sourcesTransferVisible = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.sources_transfer_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SectionHeader(stringResource(Res.string.addon_review_title))
        Button(
            onClick = { addonReviewVisible = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.addon_review_button),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(Res.string.addon_review_button_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeader(stringResource(Res.string.addons_group_filters))
        ContentFiltersCard(
            profileId = ProfileRepository.activeProfileId,
        )

        SectionHeader(stringResource(Res.string.addons_section_add_addon))
        AddAddonCard(
            addonUrl = addonUrl,
            formMessage = formMessage,
            usePersonalMediaCopy = usePersonalMediaCopy,
            onAddonUrlChange = {
                addonUrl = it
                formMessage = null
            },
            onAddClick = {
                val requestedUrl = addonUrl.trim()
                if (requestedUrl.isBlank()) {
                    formMessage = enterAddonUrlMessage
                    return@AddAddonCard
                }

                formMessage = null
                installModalState = AddonInstallModalState.Checking
                coroutineScope.launch {
                    val result = AddonRepository.addAddon(requestedUrl)
                    installModalState = when (result) {
                        is AddAddonResult.Success -> {
                            addonUrl = ""
                            AddonInstallModalState.Success(result.manifest.name)
                        }

                        is AddAddonResult.Error -> {
                            AddonInstallModalState.Error(result.message)
                        }
                    }
                }
            },
        )

        // Only a source that declares `addon_catalog` can be browsed; the rest install by URL.
        val directorySources = remember(uiState.addons) {
            uiState.addons.filter { addon ->
                addon.enabled && addon.manifest?.servesAddonDirectory() == true
            }
        }
        if (directorySources.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.addons_directory_section))
            AddonDirectorySourcesCard(
                sources = directorySources,
                onDirectoryClick = { manifestUrl, catalog ->
                    AddonDirectoryRepository.open(manifestUrl, catalog)
                    addonDirectoryVisible = true
                },
            )
        }

        SectionHeader(stringResource(Res.string.addons_section_installed))
        if (uiState.addons.isEmpty()) {
            EmptyStateCard(usePersonalMediaCopy = usePersonalMediaCopy)
        } else {
            NuvioInputField(
                value = addonQueryInput,
                onValueChange = { addonQueryInput = it },
                placeholder = stringResource(Res.string.addons_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            if (visibleAddons.isEmpty()) {
                Text(
                    text = stringResource(Res.string.addons_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // One index map for the whole list: searching the full list per row was O(n²).
            val indexByManifestUrl = remember(uiState.addons) {
                uiState.addons.withIndex().associate { (position, addon) ->
                    addon.manifestUrl to position
                }
            }
            // Built-in sources first, in the order the bundle lists them and numbered that way;
            // then the sources the user added, grouped by how they deliver their results.
            val builtInAddons = remember(visibleAddons) {
                visibleAddons
                    .mapNotNull { addon -> builtInSourceOrder(addon.manifestUrl)?.let { order -> order to addon } }
                    .sortedBy { (order, _) -> order }
                    .map { (_, addon) -> addon }
            }
            val userAddons = remember(visibleAddons) {
                visibleAddons
                    .filter { addon -> builtInSourceOrder(addon.manifestUrl) == null }
                    .sortedWith(
                        compareBy<ManagedAddon> { addon -> addon.transport().ordinal }
                            .thenBy { addon -> addon.repositoryHost() }
                            .thenBy { addon -> addon.displayTitle.lowercase() },
                    )
            }
            val displayAddons = builtInAddons + userAddons

            // Sources are grouped by the repository their manifest lives on, so a whole
            // repository can be folded away with one button and the rest of the list stays in
            // view. The header is drawn even while folded, which is how it is opened again.
            val repositoryHostByManifestUrl = remember(uiState.addons) {
                uiState.addons.associate { addon -> addon.manifestUrl to addon.repositoryHost() }
            }
            val sourceCountByRepository = remember(displayAddons) {
                displayAddons
                    .groupingBy { addon -> addon.repositoryHost().ifBlank { addon.manifestUrl } }
                    .eachCount()
            }
            // Only a repository that carries more than one source gets a foldable header; a
            // repository with a single source is the row itself, so a header would only add noise.
            val collapsibleRepositories = remember(sourceCountByRepository) {
                sourceCountByRepository.filterValues { count -> count > 1 }.keys
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        Res.string.addons_repositories_hidden_count,
                        collapsedSourceRepositories.size,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    enabled = collapsedSourceRepositories.isNotEmpty(),
                    onClick = { collapsedSourceRepositories = emptySet() },
                ) {
                    Text(stringResource(Res.string.addons_repositories_show_all))
                }
                TextButton(
                    enabled = collapsibleRepositories.isNotEmpty(),
                    onClick = { collapsedSourceRepositories = collapsibleRepositories },
                ) {
                    Text(stringResource(Res.string.addons_repositories_hide_all))
                }
            }

            var renderedSection: String? = null
            var renderedTransport: AddonTransport? = null
            var renderedRepository: String? = null
            displayAddons.forEach { addon ->
                val orderNumber = builtInSourceOrder(addon.manifestUrl)
                val section = if (orderNumber != null) "builtin" else "user"
                if (section != renderedSection) {
                    SectionHeader(
                        stringResource(
                            if (orderNumber != null) Res.string.addons_group_built_in
                            else Res.string.addons_group_user,
                        ),
                    )
                    if (orderNumber != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.addons_built_in_count, builtInAddons.size),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                enabled = !isResettingBuiltIns,
                                onClick = { resetBuiltInVisible = true },
                            ) {
                                Text(
                                    text = stringResource(
                                        if (isResettingBuiltIns) Res.string.addons_reset_running
                                        else Res.string.addons_reset,
                                    ),
                                )
                            }
                        }
                    }
                    renderedSection = section
                    renderedTransport = null
                    renderedRepository = null
                }
                if (orderNumber == null) {
                    val transport = addon.transport()
                    if (transport != renderedTransport) {
                        SectionHeader(
                            when (transport) {
                                AddonTransport.Torrent -> stringResource(Res.string.addons_group_torrent)
                                AddonTransport.Direct -> stringResource(Res.string.addons_group_direct)
                                AddonTransport.Official -> stringResource(Res.string.addons_group_official)
                                AddonTransport.Catalog -> stringResource(Res.string.addons_group_catalog)
                            },
                        )
                        renderedTransport = transport
                    }
                }

                val repositoryHost = repositoryHostByManifestUrl[addon.manifestUrl].orEmpty()
                val repositoryKey = repositoryHost.ifBlank { addon.manifestUrl }
                if (repositoryKey != renderedRepository) {
                    renderedRepository = repositoryKey
                    val repositorySourceCount = sourceCountByRepository[repositoryKey] ?: 0
                    if (repositorySourceCount > 1) {
                        val isRepositoryCollapsed = repositoryKey in collapsedSourceRepositories
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = repositoryHost.ifBlank { addon.displayTitle },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(
                                        Res.string.addons_repository_sources_count,
                                        repositorySourceCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    collapsedSourceRepositories = if (isRepositoryCollapsed) {
                                        collapsedSourceRepositories - repositoryKey
                                    } else {
                                        collapsedSourceRepositories + repositoryKey
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (isRepositoryCollapsed) Res.string.addons_repository_show
                                        else Res.string.addons_repository_hide,
                                    ),
                                )
                            }
                        }
                    }
                }
                if (repositoryKey in collapsedSourceRepositories) return@forEach
                val index = indexByManifestUrl[addon.manifestUrl] ?: -1
                val manifest = addon.manifest
                val behaviorHints = manifest?.behaviorHints
                val configFields = manifest?.configFields.orEmpty()
                val showConfigureAction = configFields.isNotEmpty() ||
                    behaviorHints?.configurable == true ||
                    behaviorHints?.configurationRequired == true
                val configureUrl = addon.manifestUrl.toConfigureUrl()
                InstalledAddonCard(
                    addon = addon,
                    orderBadge = orderNumber,
                    summary = addon.manifest?.description?.summaryLine().orEmpty().takeIf { it.isNotBlank() },
                    isPaused = addon.manifestUrl in health.pausedManifestUrls,
                    failureStreak = health.failureStreaks[addon.manifestUrl] ?: 0,
                    failureReason = health.failureReasons[addon.manifestUrl],
                    lastFailureAtMs = health.lastFailureAtMs[addon.manifestUrl],
                    measuredSpeed = health.sourceSpeeds[addon.manifestUrl],
                    isNeverPaused = addon.manifestUrl in health.neverPausedManifestUrls,
                    onToggleNeverPaused = {
                        AddonPauseExceptions.setNeverPaused(
                            profileId = ProfileRepository.activeProfileId,
                            manifestUrl = addon.manifestUrl,
                            neverPaused = addon.manifestUrl !in health.neverPausedManifestUrls,
                        )
                        pausedSourcesRefreshToken++
                    },
                    onResumeClick = if (addon.manifestUrl in health.pausedManifestUrls) {
                        {
                            coroutineScope.launch {
                                AddonFailureMemory.recordSuccess(addon.manifestUrl)
                                pausedSourcesRefreshToken++
                            }
                        }
                    } else {
                        null
                    },
                    onMoveUpClick = if (index > 0) {
                        { AddonRepository.moveAddon(index, index - 1) }
                    } else {
                        null
                    },
                    onMoveDownClick = if (index >= 0 && index < uiState.addons.lastIndex) {
                        { AddonRepository.moveAddon(index, index + 1) }
                    } else {
                        null
                    },
                    onDragStep = { step ->
                        val target = index + step
                        if (index >= 0 && target in uiState.addons.indices) {
                            AddonRepository.moveAddon(index, target)
                        }
                    },
                    onRefreshClick = {
                        AddonRepository.refreshAddon(
                            manifestUrl = addon.manifestUrl,
                            forceRefresh = true,
                        )
                    },
                    onEditClick = { urlEditTarget = addon },
                    onEnabledChange = { enabled ->
                        AddonRepository.setAddonEnabled(addon.manifestUrl, enabled)
                    },
                    onConfigureClick = when {
                        configFields.isNotEmpty() -> {
                            { configTarget = addon }
                        }

                        showConfigureAction && !configureUrl.isNullOrBlank() -> {
                            {
                                runCatching {
                                    uriHandler.openUri(configureUrl)
                                }
                            }
                        }

                        else -> null
                    },
                    onDeleteClick = { addonPendingDeletionUrl = addon.manifestUrl },
                )
            }
        }
    }

    val pendingDeletionUrl = addonPendingDeletionUrl
    if (sourcesTransferVisible) {
        SourcesTransferDialog(onDismiss = { sourcesTransferVisible = false })
    }
    if (addonReviewVisible) {
        AddonEcosystemReviewDialog(onDismiss = { addonReviewVisible = false })
    }
    if (resetBuiltInVisible) {
        NuvioStatusModal(
            title = stringResource(Res.string.addons_reset_confirm_title),
            message = stringResource(Res.string.addons_reset_confirm_message),
            isVisible = true,
            isBusy = isResettingBuiltIns,
            destructive = true,
            confirmText = stringResource(
                if (isResettingBuiltIns) Res.string.addons_reset_running
                else Res.string.addons_reset_confirm_action,
            ),
            dismissText = stringResource(Res.string.action_no),
            onConfirm = {
                if (!isResettingBuiltIns) {
                    isResettingBuiltIns = true
                    coroutineScope.launch {
                        AddonRepository.resetBuiltInSources()
                        isResettingBuiltIns = false
                        resetBuiltInVisible = false
                    }
                }
            },
            onDismiss = { if (!isResettingBuiltIns) resetBuiltInVisible = false },
        )
    }
    if (addonDirectoryVisible) {
        AddonDirectoryDialog(
            onDismiss = {
                addonDirectoryVisible = false
                AddonDirectoryRepository.close()
            },
        )
    }
    configTarget?.let { target ->
        AddonConfigDialog(
            addon = target,
            onOpenWebsite = target.manifestUrl.toConfigureUrl()
                ?.takeIf { it.isNotBlank() }
                ?.let { url ->
                    {
                        runCatching {
                            uriHandler.openUri(url)
                        }
                    }
                },
            onDismiss = { configTarget = null },
        )
    }
    urlEditTarget?.let { target ->
        AddonEditUrlDialog(
            addon = target,
            onDismiss = { urlEditTarget = null },
        )
    }
    if (pendingDeletionUrl != null) {
        NuvioStatusModal(
            title = stringResource(Res.string.addons_delete_confirm_title),
            message = stringResource(Res.string.action_delete_confirm_message),
            isVisible = true,
            destructive = true,
            confirmText = stringResource(Res.string.action_yes),
            dismissText = stringResource(Res.string.action_no),
            onConfirm = {
                AddonRepository.removeAddon(pendingDeletionUrl)
                addonPendingDeletionUrl = null
            },
            onDismiss = { addonPendingDeletionUrl = null },
        )
    }

    val modalState = installModalState
    if (modalState != null) {
        val modalTitle = when (modalState) {
            AddonInstallModalState.Checking -> stringResource(Res.string.addons_modal_checking_title)
            is AddonInstallModalState.Success -> stringResource(Res.string.addons_modal_success_title)
            is AddonInstallModalState.Error -> stringResource(Res.string.addons_modal_failure_title)
        }
        val modalMessage = when (modalState) {
            AddonInstallModalState.Checking -> stringResource(Res.string.addons_modal_checking_message)
            is AddonInstallModalState.Success -> stringResource(
                Res.string.addons_modal_success_message,
                modalState.addonName,
            )
            is AddonInstallModalState.Error -> modalState.reason
        }
        val modalConfirmText = when (modalState) {
            AddonInstallModalState.Checking -> stringResource(Res.string.addon_installing)
            is AddonInstallModalState.Success -> stringResource(Res.string.action_done)
            is AddonInstallModalState.Error -> stringResource(Res.string.action_close)
        }
        NuvioStatusModal(
            title = modalTitle,
            message = modalMessage,
            isVisible = true,
            isBusy = modalState.isBusy,
            confirmText = modalConfirmText,
            onConfirm = {
                if (!modalState.isBusy) {
                    installModalState = null
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    NuvioSectionLabel(text = text)
}

/**
 * The two content filters, beside the sources they act on: sources that declare themselves adult,
 * and the titles the user hid one by one.
 */
@Composable
private fun ContentFiltersCard(profileId: Int) {
    val revision by ContentFilterRepository.revision.collectAsStateWithLifecycle()
    val hidesAdult = remember(revision, profileId) {
        ContentFilterRepository.hidesAdultAddons(profileId)
    }
    val hiddenCount = remember(revision, profileId) {
        ContentFilterRepository.hiddenTitleCount(profileId)
    }
    var importVisible by remember { mutableStateOf(false) }
    NuvioSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.addons_filter_hide_adult),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.addons_filter_hide_adult_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = hidesAdult,
                onCheckedChange = { hide ->
                    ContentFilterRepository.setHideAdultAddons(profileId, hide)
                    ContentFilterRepository.notifyChanged()
                },
            )
        }
        if (hiddenCount > 0) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.addons_hidden_titles_count, hiddenCount),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        ContentFilterRepository.clearHidden(profileId)
                        ContentFilterRepository.notifyChanged()
                    },
                ) {
                    Text(text = stringResource(Res.string.addons_hidden_titles_clear))
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
        TextButton(onClick = { importVisible = true }) {
            Text(text = stringResource(Res.string.addons_hidden_import))
        }
    }
    if (importVisible) {
        HiddenTitleImportDialog(profileId = profileId, onDismiss = { importVisible = false })
    }
}

/** Pastes an exclusion list (`exclude/meta.json`) and hides every title it names. */
@Composable
private fun HiddenTitleImportDialog(
    profileId: Int,
    onDismiss: () -> Unit,
) {
    var payload by remember { mutableStateOf("") }
    var importedCount by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.addons_hidden_import_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.addons_hidden_import_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = payload,
                    onValueChange = {
                        payload = it
                        importedCount = null
                    },
                    label = { Text(stringResource(Res.string.addons_hidden_import_field)) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                importedCount?.let { count ->
                    Text(
                        text = if (count > 0) {
                            stringResource(Res.string.addons_hidden_import_added, count)
                        } else {
                            stringResource(Res.string.addons_hidden_import_nothing)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = payload.isNotBlank(),
                onClick = {
                    importedCount = ContentFilterRepository.importHiddenTitles(profileId, payload)
                },
            ) {
                Text(text = stringResource(Res.string.addons_hidden_import_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.action_close))
            }
        },
    )
}

@Composable
private fun OverviewCard(overview: AddonOverview) {
    NuvioSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverviewStat(
                value = overview.totalAddons.toString(),
                label = stringResource(Res.string.addons_overview_addons),
                modifier = Modifier.weight(1f),
            )
            VerticalSeparator()
            OverviewStat(
                value = overview.activeAddons.toString(),
                label = stringResource(Res.string.addons_overview_active),
                modifier = Modifier.weight(1f),
            )
            VerticalSeparator()
            OverviewStat(
                value = overview.totalCatalogs.toString(),
                label = stringResource(Res.string.addons_overview_catalogs),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OverviewStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VerticalSeparator() {
    Box(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .width(1.dp)
            .height(64.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun AddAddonCard(
    addonUrl: String,
    formMessage: String?,
    usePersonalMediaCopy: Boolean,
    onAddonUrlChange: (String) -> Unit,
    onAddClick: () -> Unit,
) {
    NuvioSurfaceCard {
        NuvioInputField(
            value = addonUrl,
            onValueChange = onAddonUrlChange,
            placeholder = stringResource(
                if (usePersonalMediaCopy) {
                    Res.string.addons_appstore_input_placeholder
                } else {
                    Res.string.addons_input_placeholder
                },
            ),
        )
        Spacer(modifier = Modifier.height(18.dp))
        NuvioPrimaryButton(
            text = stringResource(
                if (usePersonalMediaCopy) {
                    Res.string.addons_appstore_install_button
                } else {
                    Res.string.addons_install_button
                },
            ),
            enabled = addonUrl.isNotBlank(),
            onClick = onAddClick,
        )
        if (usePersonalMediaCopy) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(Res.string.addons_appstore_add_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        formMessage?.let { message ->
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private sealed interface AddonInstallModalState {
    val isBusy: Boolean

    data object Checking : AddonInstallModalState {
        override val isBusy: Boolean = true
    }

    data class Success(
        val addonName: String,
    ) : AddonInstallModalState {
        override val isBusy: Boolean = false
    }

    data class Error(
        val reason: String,
    ) : AddonInstallModalState {
        override val isBusy: Boolean = false
    }
}

private const val ADDON_SEARCH_DEBOUNCE_MS = 150L

/**
 * How long ago a source last failed, in the roughest unit that still reads usefully: minutes under
 * an hour, hours under a day, then days. Null when the time is unknown, so the card says nothing
 * rather than "0 minutes ago" for a record that has no timestamp.
 */
@Composable
private fun failureAgeLabel(lastFailureAtMs: Long?): String? {
    val lastFailureAt = lastFailureAtMs?.takeIf { it > 0L } ?: return null
    val age = failureAge(EpisodeReleaseDatePlatform.nowEpochMs() - lastFailureAt)
    return when (age.unit) {
        FailureAgeUnit.Now -> stringResource(Res.string.addons_failure_age_now)
        FailureAgeUnit.Minutes -> stringResource(Res.string.addons_failure_age_minutes, age.amount)
        FailureAgeUnit.Hours -> stringResource(Res.string.addons_failure_age_hours, age.amount)
        FailureAgeUnit.Days -> stringResource(Res.string.addons_failure_age_days, age.amount)
    }
}

/** The per-source health the add-ons screen draws, filled from a single storage read. */
private data class AddonsHealthUiState(
    val pausedManifestUrls: Set<String> = emptySet(),
    val neverPausedManifestUrls: Set<String> = emptySet(),
    val failureStreaks: Map<String, Int> = emptyMap(),
    val failureReasons: Map<String, AddonFailureReason> = emptyMap(),
    /** When each source last failed, so the card can say how long ago that was. */
    val lastFailureAtMs: Map<String, Long> = emptyMap(),
    /** Average stream-request time per manifest URL, already written as seconds. */
    val sourceSpeeds: Map<String, String> = emptyMap(),
)

/**
 * The reason a source failed last time, in the user's language. Null when nothing is known, so the
 * card falls back to the plain streak line.
 */
@Composable
private fun addonFailureReasonLabel(reason: AddonFailureReason?): String? = when (reason) {
    null -> null
    AddonFailureReason.Timeout -> stringResource(Res.string.addons_failure_reason_timeout)
    AddonFailureReason.NotFound -> stringResource(Res.string.addons_failure_reason_not_found)
    AddonFailureReason.ServerError -> stringResource(Res.string.addons_failure_reason_server)
    AddonFailureReason.Other -> stringResource(Res.string.addons_failure_reason_other)
}

@Composable
private fun EmptyStateCard(
    usePersonalMediaCopy: Boolean,
) {
    NuvioSurfaceCard {
        Text(
            text = stringResource(
                if (usePersonalMediaCopy) {
                    Res.string.addons_appstore_empty_title
                } else {
                    Res.string.addons_empty_title
                },
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (usePersonalMediaCopy) {
                    Res.string.addons_appstore_empty_subtitle
                } else {
                    Res.string.addons_empty_subtitle
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InstalledAddonCard(
    addon: ManagedAddon,
    orderBadge: Int?,
    summary: String?,
    isPaused: Boolean,
    failureStreak: Int,
    failureReason: AddonFailureReason?,
    lastFailureAtMs: Long?,
    measuredSpeed: String?,
    isNeverPaused: Boolean,
    onToggleNeverPaused: () -> Unit,
    onResumeClick: (() -> Unit)?,
    onMoveUpClick: (() -> Unit)?,
    onMoveDownClick: (() -> Unit)?,
    onDragStep: (Int) -> Unit,
    onEditClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onConfigureClick: (() -> Unit)?,
    onDeleteClick: () -> Unit,
) {
    val manifest = addon.manifest
    var extraEditing by remember { mutableStateOf<ExtraEditingTarget?>(null) }
    val extraValues by AddonCatalogExtraValues
        .values(ProfileRepository.activeProfileId)
        .collectAsStateWithLifecycle()

    extraEditing?.let { target ->
        AddonExtraValueDialog(
            target = target,
            currentValue = extraValues[target.storageKey()],
            onDismiss = { extraEditing = null },
        )
    }

    NuvioSurfaceCard {
        // A manifest may ship a background; it draws as a soft header behind the card's title.
        manifest?.backgroundUrl?.takeIf { it.isNotBlank() }?.let { background ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                AsyncImage(
                    model = background,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                ),
                            ),
                        ),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            AddonIconBadge(
                imageUrl = manifest?.logoUrl,
                icon = Icons.Rounded.Extension,
                tint = if (manifest != null) Color(0xFF71BDE8) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    orderBadge?.let { number ->
                        Text(
                            text = "#$number",
                            modifier = Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = addon.displayTitle,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                summary?.let { line ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                manifest?.version?.let { version ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.addons_version_format, version),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = addon.enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Long-press and drag this handle to move the source one slot per 72 dp.
            val dragStepPx = with(LocalDensity.current) { 72.dp.toPx() }
            var dragAccumulator by remember(addon.manifestUrl) { mutableStateOf(0f) }
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(Res.string.addons_drag_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp)
                    .pointerInput(addon.manifestUrl) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulator += dragAmount.y
                                if (dragAccumulator >= dragStepPx) {
                                    dragAccumulator = 0f
                                    onDragStep(1)
                                } else if (dragAccumulator <= -dragStepPx) {
                                    dragAccumulator = 0f
                                    onDragStep(-1)
                                }
                            },
                            onDragEnd = { dragAccumulator = 0f },
                            onDragCancel = { dragAccumulator = 0f },
                        )
                    },
            )
            onMoveUpClick?.let { onMoveUp ->
                NuvioIconActionButton(
                    icon = Icons.Rounded.ArrowUpward,
                    contentDescription = stringResource(Res.string.addons_move_up),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onMoveUp,
                )
            }
            onMoveDownClick?.let { onMoveDown ->
                NuvioIconActionButton(
                    icon = Icons.Rounded.ArrowDownward,
                    contentDescription = stringResource(Res.string.addons_move_down),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onMoveDown,
                )
            }
            NuvioIconActionButton(
                icon = Icons.Rounded.Edit,
                contentDescription = stringResource(Res.string.addons_edit_link),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onEditClick,
            )
            NuvioIconActionButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = stringResource(Res.string.addons_refresh),
                tint = MaterialTheme.colorScheme.primary,
                onClick = onRefreshClick,
            )
            onConfigureClick?.let { onConfigure ->
                NuvioIconActionButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = stringResource(Res.string.addons_configure),
                    tint = MaterialTheme.colorScheme.tertiary,
                    onClick = onConfigure,
                )
            }
            NuvioIconActionButton(
                icon = Icons.Rounded.Delete,
                contentDescription = stringResource(Res.string.addons_delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = onDeleteClick,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(18.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NuvioInfoBadge(
                text = when {
                    !addon.enabled -> stringResource(Res.string.addons_badge_disabled)
                    addon.isRefreshing -> stringResource(Res.string.addons_badge_refreshing)
                    manifest != null -> stringResource(Res.string.addons_badge_active)
                    else -> stringResource(Res.string.addons_badge_unavailable)
                },
            )
            manifest?.let {
                NuvioInfoBadge(text = stringResource(Res.string.addons_badge_resources, it.resources.size))
                NuvioInfoBadge(text = stringResource(Res.string.addons_badge_catalogs, it.catalogs.size))
                if (it.behaviorHints.configurable) {
                    NuvioInfoBadge(text = stringResource(Res.string.addons_badge_configurable))
                }
            }
            if (isPaused) {
                NuvioInfoBadge(text = stringResource(Res.string.addons_badge_paused))
            }
            measuredSpeed?.let { speed ->
                NuvioInfoBadge(text = stringResource(Res.string.addons_badge_speed, speed))
            }
        }

        if (failureStreak > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            val reasonLabel = addonFailureReasonLabel(failureReason)
            val ageLabel = failureAgeLabel(lastFailureAtMs)
            val detail = listOfNotNull(reasonLabel, ageLabel).joinToString(separator = " · ")
            Text(
                text = if (detail.isNotBlank()) {
                    stringResource(Res.string.addons_failure_streak_with_reason, failureStreak, detail)
                } else {
                    stringResource(Res.string.addons_failure_streak, failureStreak)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onResumeClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onResumeClick) {
                Text(text = stringResource(Res.string.addons_resume))
            }
        }

        if (failureStreak > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onToggleNeverPaused) {
                Text(
                    text = stringResource(
                        if (isNeverPaused) Res.string.addons_allow_pause else Res.string.addons_never_pause,
                    ),
                )
            }
        }

        when {
            addon.isRefreshing -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.addons_loading_manifest_details),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            addon.errorMessage != null && manifest == null -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = addon.errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            manifest != null -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = manifestSummary(manifest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                manifest.resources.forEach { resource ->
                    val supportedTypes = resource.types.joinToString(" / ")
                    val idRules = resource.idPrefixes.joinToString(prefix = " · ")
                    Text(
                        text = "${resource.name} · $supportedTypes$idRules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // A saved value's key is manifest#catalog#type#extra, so one pass over the keys
                // yields every catalog that has values, instead of one pass per catalog.
                val savedCatalogScopes = remember(extraValues, addon.manifestUrl) {
                    extraValues.keys.mapTo(mutableSetOf()) { key -> key.substringBeforeLast('#') }
                }
                manifest.catalogs.forEach { catalog ->
                    val hasSavedExtraValues =
                        "${addon.manifestUrl.trim()}#${catalog.id.trim()}#${catalog.type.trim()}" in savedCatalogScopes
                    Text(
                        text = "${catalog.name} (${catalog.type})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (hasSavedExtraValues) {
                        TextButton(
                            onClick = {
                                AddonCatalogExtraValues.clearCatalogValues(
                                    profileId = ProfileRepository.activeProfileId,
                                    manifestUrl = addon.manifestUrl,
                                    catalogId = catalog.id,
                                    type = catalog.type,
                                )
                            },
                        ) {
                            Text(text = stringResource(Res.string.addons_extra_reset_catalog))
                        }
                    }
                    catalog.extra.forEach { option ->
                        AddonCatalogExtraRow(
                            option = option,
                            savedValue = extraValues[
                                AddonCatalogExtraValues.storageKey(
                                    manifestUrl = addon.manifestUrl,
                                    catalogId = catalog.id,
                                    type = catalog.type,
                                    extraName = option.name,
                                )
                            ],
                            onClick = {
                                extraEditing = ExtraEditingTarget(
                                    manifestUrl = addon.manifestUrl,
                                    catalogId = catalog.id,
                                    type = catalog.type,
                                    option = option,
                                )
                            },
                        )
                    }
                }

                addon.errorMessage?.let { staleError ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = staleError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PausedSourcesCard(
    pausedCount: Int,
    onResume: () -> Unit,
) {
    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.addons_paused_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.addons_paused_message, pausedCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onResume) {
            Text(text = stringResource(Res.string.addons_paused_resume))
        }
    }
}

@Composable
private fun AddonIconBadge(
    imageUrl: String?,
    icon: ImageVector,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun manifestSummary(manifest: AddonManifest): String {
    val resources = manifest.resources.joinToString(separator = ", ") { it.name }
    val types = manifest.types.joinToString(separator = " / ") { it.replaceFirstChar(Char::uppercase) }
    return buildString {
        append(types)
        append(" • ")
        append(resources)
        if (manifest.idPrefixes.isNotEmpty()) {
            append(" • ")
            append(stringResource(Res.string.addons_summary_id_rules, manifest.idPrefixes.size))
        }
        if (manifest.behaviorHints.p2p) {
            append(" • P2P")
        }
    }
}

private fun String.toConfigureUrl(): String? {
    val base = substringBefore("?").trimEnd('/').removeSuffix("/manifest.json")
    val schemeEnd = base.indexOf("://")
    if (schemeEnd < 0) return null
    val authority = base.substring(schemeEnd + 3).substringBefore('/')
    if (authority.isBlank()) return null
    return "${base.substring(0, schemeEnd + 3)}$authority/configure"
}


private data class ExtraEditingTarget(
    val manifestUrl: String,
    val catalogId: String,
    val type: String,
    val option: AddonExtraProperty,
) {
    fun storageKey(): String =
        AddonCatalogExtraValues.storageKey(
            manifestUrl = manifestUrl,
            catalogId = catalogId,
            type = type,
            extraName = option.name,
        )
}

/**
 * One catalog extra of an installed add-on. Shows the saved value and opens the
 * picker; nothing is sent to the add-on until [CatalogRepository] builds its next
 * catalog request with the saved values.
 */
@Composable
private fun AddonCatalogExtraRow(
    option: AddonExtraProperty,
    savedValue: String?,
    onClick: () -> Unit,
) {
    val choices = option.options.takeIf { it.isNotEmpty() }
        ?.joinToString(prefix = ": ")
        .orEmpty()
    val required = if (option.isRequired) " *" else ""
    val limit = option.optionsLimit?.let { " · ≤$it" }.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Text(
            text = "${option.name}$choices$required$limit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = savedValue?.takeIf { it.isNotBlank() }
                ?.let { stringResource(Res.string.addons_extra_selected, it) }
                ?: stringResource(Res.string.addons_extra_tap_to_choose),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AddonExtraValueDialog(
    target: ExtraEditingTarget,
    currentValue: String?,
    onDismiss: () -> Unit,
) {
    val option = target.option
    var selected by remember(target) { mutableStateOf(currentValue) }
    var customValue by remember(target) { mutableStateOf(currentValue.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = option.name,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (option.options.isEmpty()) {
                    OutlinedTextField(
                        value = customValue,
                        onValueChange = { customValue = it },
                        label = { Text(stringResource(Res.string.addons_extra_custom_value)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    SelectableExtraChoice(
                        label = stringResource(Res.string.addons_extra_default),
                        checked = selected == null,
                        onSelect = { selected = null },
                    )
                    option.options.forEach { choice ->
                        SelectableExtraChoice(
                            label = choice,
                            checked = selected == choice,
                            onSelect = { selected = choice },
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.addons_extra_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = if (option.options.isEmpty()) {
                        customValue.trim().takeIf { it.isNotBlank() }
                    } else {
                        selected?.takeIf { it.isNotBlank() }
                    }
                    AddonCatalogExtraValues.setValue(
                        profileId = ProfileRepository.activeProfileId,
                        manifestUrl = target.manifestUrl,
                        catalogId = target.catalogId,
                        type = target.type,
                        extraName = option.name,
                        value = value,
                    )
                    onDismiss()
                },
            ) {
                Text(text = stringResource(Res.string.addons_extra_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.addons_extra_cancel))
            }
        },
    )
}

@Composable
private fun SelectableExtraChoice(
    label: String,
    checked: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = checked, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The installed sources that serve an add-on directory, each with the directories its manifest
 * declares. One row per directory opens the list it serves.
 *
 * Sources are listed by name and their directories by name — with the catch-all entry first — so
 * the section reads the same every time instead of following the manifest's own order.
 */
@Composable
private fun AddonDirectorySourcesCard(
    sources: List<ManagedAddon>,
    onDirectoryClick: (String, AddonCatalogRef) -> Unit,
) {
    val orderedSources = remember(sources) {
        sources.sortedBy { addon -> addon.manifest?.name.orEmpty().lowercase() }
    }
    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.addons_directory_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        orderedSources.forEach { addon ->
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = addon.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val catalogs = addon.manifest?.addonCatalogs.orEmpty().sortedWith(
                compareBy<AddonCatalogRef> { catalog -> !catalog.type.equals("all", ignoreCase = true) }
                    .thenBy { catalog -> catalog.name.lowercase() },
            )
            catalogs.forEach { catalog ->
                Spacer(modifier = Modifier.height(8.dp))
                DirectorySourceRow(
                    name = catalog.name,
                    type = catalog.type,
                    onClick = { onDirectoryClick(addon.manifestUrl, catalog) },
                )
            }
        }
    }
}

/** One directory a source serves: its name, the type it covers, and a tap to open it. */
@Composable
private fun DirectorySourceRow(
    name: String,
    type: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = localizedDirectoryTypeLabel(type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun localizedDirectoryTypeLabel(type: String): String = when (type.trim().lowercase()) {
    "all" -> stringResource(Res.string.addons_directory_all_types)
    "movie" -> stringResource(Res.string.media_movies)
    "series" -> stringResource(Res.string.media_series)
    "channel" -> stringResource(Res.string.media_channels)
    "tv" -> stringResource(Res.string.media_tv)
    "anime" -> stringResource(Res.string.media_anime)
    else -> type.replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
}
