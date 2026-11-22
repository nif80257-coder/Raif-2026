package com.nuvio.app.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioActionLabel
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.home.HomeCatalogSettingsItem
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.action_reset
import nuvio.composeapp.generated.resources.layout_hide_unreleased
import nuvio.composeapp.generated.resources.layout_hide_unreleased_sub
import nuvio.composeapp.generated.resources.layout_catalog_type
import nuvio.composeapp.generated.resources.layout_catalog_type_sub
import nuvio.composeapp.generated.resources.profile_label_number
import nuvio.composeapp.generated.resources.settings_homescreen_copy_description
import nuvio.composeapp.generated.resources.settings_homescreen_copy_done
import nuvio.composeapp.generated.resources.settings_homescreen_copy_empty
import nuvio.composeapp.generated.resources.settings_homescreen_copy_sheet_title
import nuvio.composeapp.generated.resources.settings_homescreen_copy_title
import nuvio.composeapp.generated.resources.settings_homescreen_empty_message
import nuvio.composeapp.generated.resources.settings_homescreen_empty_title
import nuvio.composeapp.generated.resources.settings_homescreen_keep_home_focused
import nuvio.composeapp.generated.resources.settings_homescreen_limit_reached
import nuvio.composeapp.generated.resources.settings_homescreen_load_failed_title
import nuvio.composeapp.generated.resources.settings_homescreen_no_sources_selected
import nuvio.composeapp.generated.resources.settings_homescreen_pin_to_move_toast
import nuvio.composeapp.generated.resources.settings_homescreen_section_catalogs
import nuvio.composeapp.generated.resources.settings_homescreen_section_catalogs_collections
import nuvio.composeapp.generated.resources.settings_homescreen_section_collections
import nuvio.composeapp.generated.resources.settings_homescreen_section_hero
import nuvio.composeapp.generated.resources.settings_homescreen_section_hero_sources
import nuvio.composeapp.generated.resources.settings_homescreen_selected_count
import nuvio.composeapp.generated.resources.settings_homescreen_show_hero
import nuvio.composeapp.generated.resources.settings_homescreen_show_hero_description
import nuvio.composeapp.generated.resources.settings_homescreen_summary
import nuvio.composeapp.generated.resources.settings_homescreen_summary_hint
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal fun LazyListScope.homescreenSettingsContent(
    isTablet: Boolean,
    heroEnabled: Boolean,
    showCatalogType: Boolean,
    hideUnreleasedContent: Boolean,
    items: List<HomeCatalogSettingsItem>,
    isCatalogLoading: Boolean,
    catalogErrorMessage: String?,
) {
    val selectedHeroSourceCount = items.count { it.heroSourceEnabled }
    val enabledCatalogCount = items.count { it.enabled }
    item {
        HomescreenSummaryCard(
            isTablet = isTablet,
            enabledCatalogCount = enabledCatalogCount,
            totalCatalogCount = items.size,
            selectedHeroSourceCount = selectedHeroSourceCount,
        )
    }
    item {
        HomescreenCopySection(isTablet = isTablet)
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_homescreen_section_hero),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_homescreen_show_hero),
                    description = stringResource(Res.string.settings_homescreen_show_hero_description),
                    checked = heroEnabled,
                    isTablet = isTablet,
                    onCheckedChange = HomeCatalogSettingsRepository::setHeroEnabled,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.layout_catalog_type),
                    description = stringResource(Res.string.layout_catalog_type_sub),
                    checked = showCatalogType,
                    isTablet = isTablet,
                    onCheckedChange = HomeCatalogSettingsRepository::setShowCatalogType,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.layout_hide_unreleased),
                    description = stringResource(Res.string.layout_hide_unreleased_sub),
                    checked = hideUnreleasedContent,
                    isTablet = isTablet,
                    onCheckedChange = HomeCatalogSettingsRepository::setHideUnreleasedContent,
                )
            }
        }
    }
    item {
        val catalogOnlyItems = items.filter { !it.isCollection }
        if (heroEnabled && catalogOnlyItems.isNotEmpty()) {
            var heroSourcesExpanded by remember { mutableStateOf(false) }
            SettingsSection(
                title = stringResource(Res.string.settings_homescreen_section_hero_sources),
                isTablet = isTablet,
            ) {
                HeroSourcesDropdown(
                    isTablet = isTablet,
                    items = catalogOnlyItems,
                    selectedHeroSourceCount = selectedHeroSourceCount,
                    expanded = heroSourcesExpanded,
                    onExpandedChange = { heroSourcesExpanded = it },
                )
            }
        }
    }
    item {
        if (isCatalogLoading && items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                NuvioLoadingIndicator(
                    modifier = Modifier.size(28.dp),
                )
            }
        } else if (catalogErrorMessage != null && items.isEmpty()) {
            HomeEmptyStateCard(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.settings_homescreen_load_failed_title),
                message = catalogErrorMessage,
                actionLabel = stringResource(Res.string.action_retry),
                onActionClick = AddonRepository::refreshAll,
            )
        } else if (items.isEmpty()) {
            HomeEmptyStateCard(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.settings_homescreen_empty_title),
                message = stringResource(Res.string.settings_homescreen_empty_message),
            )
        } else {
            val catalogCount = items.count { !it.isCollection }
            val collectionCount = items.count { it.isCollection }
            val sectionTitle = when {
                collectionCount > 0 && catalogCount > 0 -> stringResource(Res.string.settings_homescreen_section_catalogs_collections)
                collectionCount > 0 -> stringResource(Res.string.settings_homescreen_section_collections)
                else -> stringResource(Res.string.settings_homescreen_section_catalogs)
            }
            SettingsSection(
                title = sectionTitle,
                isTablet = isTablet,
                actions = {
                    NuvioActionLabel(
                        text = stringResource(Res.string.action_reset),
                        onClick = HomeCatalogSettingsRepository::resetToDefaults,
                    )
                },
            ) {
                val hapticFeedback = LocalHapticFeedback.current
                val pinToMoveToast = stringResource(Res.string.settings_homescreen_pin_to_move_toast)

                HomescreenCatalogList(
                    isTablet = isTablet,
                    items = items,
                    onPinnedDragAttempt = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        NuvioToastController.show(pinToMoveToast)
                    },
                )
            }
        }
    }
}

@Composable
private fun HeroSourcesDropdown(
    isTablet: Boolean,
    items: List<HomeCatalogSettingsItem>,
    selectedHeroSourceCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val noSourcesSelected = stringResource(Res.string.settings_homescreen_no_sources_selected)
    SettingsGroup(isTablet = isTablet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clickable { onExpandedChange(!expanded) },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        Res.string.settings_homescreen_selected_count,
                        selectedHeroSourceCount,
                        HomeCatalogSettingsRepository.HERO_SOURCE_SELECTION_LIMIT,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = items.filter { it.heroSourceEnabled }
                        .joinToString(separator = ", ") { it.displayTitle }
                        .ifBlank { noSourcesSelected },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                SettingsGroupDivider(isTablet = isTablet)
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        SettingsGroupDivider(isTablet = isTablet)
                    }
                    SettingsSwitchRow(
                        title = item.displayTitle,
                        description = if (!item.heroSourceEnabled &&
                            selectedHeroSourceCount >= HomeCatalogSettingsRepository.HERO_SOURCE_SELECTION_LIMIT
                        ) {
                            stringResource(
                                Res.string.settings_homescreen_limit_reached,
                                item.addonName,
                                HomeCatalogSettingsRepository.HERO_SOURCE_SELECTION_LIMIT,
                            )
                        } else {
                            item.addonName
                        },
                        checked = item.heroSourceEnabled,
                        enabled = item.heroSourceEnabled ||
                            selectedHeroSourceCount < HomeCatalogSettingsRepository.HERO_SOURCE_SELECTION_LIMIT,
                        isTablet = isTablet,
                        onCheckedChange = { HomeCatalogSettingsRepository.setHeroSourceEnabled(item.key, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomescreenCopySection(isTablet: Boolean) {
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val activeProfileIndex = profileState.activeProfile?.profileIndex
    val otherProfiles = remember(profileState.profiles, activeProfileIndex) {
        profileState.profiles.filterNot { it.profileIndex == activeProfileIndex }
    }
    if (otherProfiles.isEmpty()) return

    var showSheet by remember { mutableStateOf(false) }
    val copiedMessage = stringResource(Res.string.settings_homescreen_copy_done)
    val emptyMessage = stringResource(Res.string.settings_homescreen_copy_empty)

    SettingsGroup(isTablet = isTablet) {
        SettingsNavigationRow(
            title = stringResource(Res.string.settings_homescreen_copy_title),
            description = stringResource(Res.string.settings_homescreen_copy_description),
            isTablet = isTablet,
            onClick = { showSheet = true },
        )
    }

    if (showSheet) {
        HomescreenCopyFromProfileSheet(
            profiles = otherProfiles,
            onDismiss = { showSheet = false },
            onProfileChosen = { profile ->
                val copied = HomeCatalogSettingsRepository.copyFromProfile(profile.profileIndex)
                NuvioToastController.show(if (copied) copiedMessage else emptyMessage)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomescreenCopyFromProfileSheet(
    profiles: List<NuvioProfile>,
    onDismiss: () -> Unit,
    onProfileChosen: (NuvioProfile) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    NuvioModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(Res.string.settings_homescreen_copy_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
        NuvioBottomSheetDivider()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            items(profiles, key = { profile -> profile.profileIndex }) { profile ->
                NuvioBottomSheetActionRow(
                    title = profile.name.ifBlank {
                        stringResource(Res.string.profile_label_number, profile.profileIndex)
                    },
                    onClick = {
                        onProfileChosen(profile)
                        coroutineScope.launch {
                            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomescreenSummaryCard(
    isTablet: Boolean,
    enabledCatalogCount: Int,
    totalCatalogCount: Int,
    selectedHeroSourceCount: Int,
) {
    SettingsGroup(isTablet = isTablet) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_homescreen_keep_home_focused),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    Res.string.settings_homescreen_summary,
                    enabledCatalogCount,
                    totalCatalogCount,
                    selectedHeroSourceCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.settings_homescreen_summary_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomescreenCatalogList(
    isTablet: Boolean,
    items: List<HomeCatalogSettingsItem>,
    onPinnedDragAttempt: () -> Unit,
) {
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
    ) { from, to ->
        val fromItem = items.getOrNull(from.index)
        val toItem = items.getOrNull(to.index)
        if (fromItem?.isPinnedToTop == true || toItem?.isPinnedToTop == true) {
            return@rememberReorderableLazyListState
        }
        HomeCatalogSettingsRepository.moveByIndex(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    SettingsGroup(isTablet = isTablet) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (isTablet) 900.dp else 680.dp),
            state = lazyListState,
        ) {
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                ReorderableItem(
                    reorderableLazyListState,
                    key = item.key,
                    enabled = !item.isPinnedToTop,
                ) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

                    Surface(shadowElevation = elevation) {
                        Column {
                            if (index > 0) {
                                SettingsGroupDivider(isTablet = isTablet)
                            }
                            HomescreenCatalogRow(
                                item = item,
                                isTablet = isTablet,
                                expanded = expandedKey == item.key,
                                onExpandedChange = { shouldExpand ->
                                    expandedKey = if (shouldExpand) item.key else null
                                },
                                onTitleChange = { HomeCatalogSettingsRepository.setCustomTitle(item.key, it) },
                                onEnabledChange = { HomeCatalogSettingsRepository.setEnabled(item.key, it) },
                                dragHandleScope = this@ReorderableItem,
                                onPinnedDragAttempt = onPinnedDragAttempt,
                            )
                        }
                    }
                }
            }
        }
    }
}
