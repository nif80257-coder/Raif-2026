package com.nuvio.app.features.party

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_remove
import nuvio.composeapp.generated.resources.party_history_empty_action
import nuvio.composeapp.generated.resources.party_history_empty_message
import nuvio.composeapp.generated.resources.party_history_empty_title
import nuvio.composeapp.generated.resources.party_history_ended
import nuvio.composeapp.generated.resources.party_history_hosted
import nuvio.composeapp.generated.resources.party_history_joined
import nuvio.composeapp.generated.resources.party_history_live
import nuvio.composeapp.generated.resources.party_history_open_live
import nuvio.composeapp.generated.resources.party_history_reinvite
import nuvio.composeapp.generated.resources.party_history_remove_message
import nuvio.composeapp.generated.resources.party_history_remove_title
import nuvio.composeapp.generated.resources.party_history_title
import nuvio.composeapp.generated.resources.party_history_watch_content
import nuvio.composeapp.generated.resources.party_invite_copied
import nuvio.composeapp.generated.resources.party_seats
import nuvio.composeapp.generated.resources.party_sign_in_required
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private const val HostedTab = 0
private const val JoinedTab = 1

@Composable
internal fun PartyHistoryScreen(
    onBack: () -> Unit,
    onOpenContent: (PartyHistoryEntry) -> Unit,
    onOpenParty: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val entries by PartyHistoryRepository.entries.collectAsStateWithLifecycle()
    val partyState by PartyRepository.state.collectAsStateWithLifecycle()
    val livePartyId = partyState.party?.id
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(HostedTab) }
    var removeTarget by remember { mutableStateOf<PartyHistoryEntry?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { PartyHistoryRepository.refresh() }

    val hosted = remember(entries) { entries.filter { it.host } }
    val joined = remember(entries) { entries.filterNot { it.host } }
    val shown = if (tab == HostedTab) hosted else joined

    fun reinvite(entry: PartyHistoryEntry) {
        if (busy) return
        if (!PartyRepository.canUse) {
            scope.launch { NuvioToastController.show(getString(Res.string.party_sign_in_required)) }
            return
        }
        busy = true
        scope.launch {
            val error = PartyRepository.create(entry.media)
            busy = false
            val party = PartyRepository.state.value.party
            if (error == null && party != null) {
                clipboard.setText(AnnotatedString(partyInviteLink(party.code)))
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                NuvioToastController.show(getString(Res.string.party_invite_copied))
                onOpenParty()
            } else if (error != null) {
                NuvioToastController.show(getString(error.messageRes))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.background),
    ) {
        NuvioScreenHeader(
            title = stringResource(Res.string.party_history_title),
            onBack = onBack,
        )
        HistoryTabs(
            tab = tab,
            hostedCount = hosted.size,
            joinedCount = joined.size,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal),
        )
        Spacer(Modifier.height(16.dp))
        if (shown.isEmpty()) {
            HistoryEmptyState(
                onStart = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = tokens.spacing.screenHorizontal),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    bottom = nuvioSafeBottomPadding(tokens.spacing.screenBottom),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = shown, key = { it.id }) { entry ->
                    PartyHistoryCard(
                        entry = entry,
                        live = entry.id == livePartyId,
                        busy = busy,
                        onOpen = {
                            if (entry.id == livePartyId) onOpenParty() else onOpenContent(entry)
                        },
                        onReinvite = { reinvite(entry) },
                        onWatchContent = { onOpenContent(entry) },
                        onRemove = { removeTarget = entry },
                    )
                }
            }
        }
    }

    removeTarget?.let { entry ->
        ConfirmDialog(
            title = stringResource(Res.string.party_history_remove_title),
            message = stringResource(Res.string.party_history_remove_message, entry.title),
            confirmLabel = stringResource(Res.string.action_remove),
            onConfirm = {
                PartyHistoryRepository.remove(entry.id)
                removeTarget = null
            },
            onDismiss = { removeTarget = null },
        )
    }
}

@Composable
private fun HistoryTabs(
    tab: Int,
    hostedCount: Int,
    joinedCount: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hosted = stringResource(Res.string.party_history_hosted)
    val joined = stringResource(Res.string.party_history_joined)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryTab(
            label = hosted,
            count = hostedCount,
            selected = tab == HostedTab,
            onClick = { onSelect(HostedTab) },
            modifier = Modifier.weight(1f),
        )
        HistoryTab(
            label = joined,
            count = joinedCount,
            selected = tab == JoinedTab,
            onClick = { onSelect(JoinedTab) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HistoryTab(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) tokens.colors.accent.copy(alpha = 0.18f) else tokens.colors.surfaceCard)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 0) "$label · $count" else label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) tokens.colors.accent else tokens.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PartyHistoryCard(
    entry: PartyHistoryEntry,
    live: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onReinvite: () -> Unit,
    onWatchContent: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val haptic = LocalHapticFeedback.current
    val date = remember(entry.startedAtEpochMs) { formatLocalDateTime(entry.startedAtEpochMs) }
    val seats = stringResource(Res.string.party_seats, entry.members, entry.maxMembers)
    val status = stringResource(if (live) Res.string.party_history_live else Res.string.party_history_ended)
    val meta = listOf(date, seats, status).filter { it.isNotBlank() }.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.colors.surfaceCard)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRemove()
                },
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PartyHistoryPoster(url = entry.poster, title = entry.title)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (live) LiveBadge()
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live) {
                HistoryActionButton(
                    text = stringResource(Res.string.party_history_open_live),
                    primary = true,
                    enabled = true,
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                )
            } else {
                HistoryActionButton(
                    text = stringResource(Res.string.party_history_reinvite),
                    primary = true,
                    enabled = !busy,
                    onClick = onReinvite,
                    modifier = Modifier.weight(1f),
                )
                HistoryActionButton(
                    text = stringResource(Res.string.party_history_watch_content),
                    primary = false,
                    enabled = true,
                    onClick = onWatchContent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PartyHistoryPoster(url: String?, title: String) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.take(1),
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.colors.textMuted,
            )
        }
    }
}

@Composable
private fun LiveBadge() {
    val tokens = MaterialTheme.nuvio
    val liveColor = Color(0xFF4EC28A)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(liveColor.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(liveColor),
        )
        Text(
            text = stringResource(Res.string.party_history_live),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = liveColor,
        )
    }
}

@Composable
private fun HistoryActionButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) tokens.colors.accent else tokens.colors.surfaceElevated)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .then(Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = (if (primary) Color.White else tokens.colors.textPrimary).copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistoryEmptyState(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(tokens.colors.surfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = tokens.colors.textMuted,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(Res.string.party_history_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.party_history_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        HistoryActionButton(
            text = stringResource(Res.string.party_history_empty_action),
            primary = true,
            enabled = true,
            onClick = onStart,
        )
    }
}
