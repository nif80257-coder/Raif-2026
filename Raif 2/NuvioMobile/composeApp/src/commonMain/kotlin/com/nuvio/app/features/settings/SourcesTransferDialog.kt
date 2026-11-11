package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.share.PlatformShare
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.servers.ServerRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private sealed interface SourcesTransferUiState {
    object Idle : SourcesTransferUiState
    object Working : SourcesTransferUiState
    data class Preview(val analysis: SourcesTransferAnalysis) : SourcesTransferUiState
    data class Report(val report: SourcesTransferImportReport) : SourcesTransferUiState
    data class CredentialWarning(
        val document: SourcesTransferDocument,
        val findings: List<SourcesCredentialFinding>,
    ) : SourcesTransferUiState
}

@Composable
internal fun SourcesTransferDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tokens = MaterialTheme.nuvio
    var importPayload by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var uiState by remember { mutableStateOf<SourcesTransferUiState>(SourcesTransferUiState.Idle) }
    var backupAvailable by remember { mutableStateOf(SourcesTransferRepository.hasBackup()) }

    val title = stringResource(Res.string.sources_transfer_title)
    val description = stringResource(Res.string.sources_transfer_description)
    val exportTitle = stringResource(Res.string.sources_transfer_export_title)
    val exportAction = stringResource(Res.string.sources_transfer_export_action)
    val exportEmpty = stringResource(Res.string.sources_transfer_export_empty)
    val importTitle = stringResource(Res.string.sources_transfer_import_title)
    val importPlaceholder = stringResource(Res.string.sources_transfer_import_placeholder)
    val inspectAction = stringResource(Res.string.sources_transfer_import_inspect)
    val emptyError = stringResource(Res.string.sources_transfer_error_empty)
    val malformedError = stringResource(Res.string.sources_transfer_error_malformed)
    val versionError = stringResource(Res.string.sources_transfer_error_version)
    val previewTitle = stringResource(Res.string.sources_transfer_preview_title)
    val totalLabel = stringResource(Res.string.sources_transfer_preview_total)
    val importableLabel = stringResource(Res.string.sources_transfer_preview_importable)
    val duplicatesLabel = stringResource(Res.string.sources_transfer_preview_duplicates)
    val incompatibleLabel = stringResource(Res.string.sources_transfer_preview_incompatible)
    val invalidLabel = stringResource(Res.string.sources_transfer_preview_invalid)
    val mergeLabel = stringResource(Res.string.sources_transfer_preview_merge)
    val replaceLabel = stringResource(Res.string.sources_transfer_preview_replace)
    val workingLabel = stringResource(Res.string.sources_transfer_working)
    val reportTitle = stringResource(Res.string.sources_transfer_report_title)
    val importedLabel = stringResource(Res.string.sources_transfer_report_imported)
    val skippedLabel = stringResource(Res.string.sources_transfer_report_skipped)
    val failedLabel = stringResource(Res.string.sources_transfer_report_failed)
    val undoLabel = stringResource(Res.string.sources_transfer_undo)
    val credentialsTitle = stringResource(Res.string.sources_transfer_credentials_title)
    val credentialsMessage = stringResource(Res.string.sources_transfer_credentials_message)
    val credentialsContinue = stringResource(Res.string.sources_transfer_credentials_continue)
    val cancelLabel = stringResource(Res.string.sources_transfer_cancel)
    val closeLabel = stringResource(Res.string.sources_transfer_close)
    val serverSignInLabel = stringResource(Res.string.sources_transfer_skip_server)
    val invalidEntryLabel = stringResource(Res.string.sources_transfer_skip_invalid)

    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        ServerRepository.ensureLoaded()
        backupAvailable = SourcesTransferRepository.hasBackup()
    }

    val shareDocument: (SourcesTransferDocument) -> Unit = { document ->
        val redacted = document.copy(sources = document.sources.map(::redactSourcesTransferEntry))
        PlatformShare.shareText(
            text = SourcesTransferCodec.encode(redacted),
            fileName = "raif-sources.json",
            subject = title,
        )
        uiState = SourcesTransferUiState.Idle
    }

    val onExportClick: () -> Unit = {
        errorMessage = null
        val document = SourcesTransferRepository.buildDocument()
        if (document.sources.isEmpty()) {
            errorMessage = exportEmpty
        } else {
            val scan = scanSourcesForCredentials(document.sources)
            if (scan.hasFindings) {
                uiState = SourcesTransferUiState.CredentialWarning(
                    document = document,
                    findings = scan.findings,
                )
            } else {
                shareDocument(document)
            }
        }
    }

    val onInspectClick: () -> Unit = {
        errorMessage = null
        when (val parsed = SourcesTransferCodec.decode(importPayload)) {
            is SourcesTransferParseResult.Success -> {
                uiState = SourcesTransferUiState.Preview(
                    analysis = SourcesTransferRepository.analyze(parsed.document),
                )
            }

            is SourcesTransferParseResult.Failure -> {
                errorMessage = when (parsed.reason) {
                    SourcesTransferParseFailure.EMPTY -> emptyError
                    SourcesTransferParseFailure.MALFORMED -> malformedError
                    SourcesTransferParseFailure.UNSUPPORTED_VERSION -> versionError
                }
            }
        }
    }

    val runImport: (SourcesTransferAnalysis, SourcesTransferImportMode) -> Unit = { analysis, mode ->
        scope.launch {
            uiState = SourcesTransferUiState.Working
            val report = SourcesTransferRepository.apply(analysis = analysis, mode = mode)
            backupAvailable = SourcesTransferRepository.hasBackup()
            uiState = SourcesTransferUiState.Report(report)
        }
    }

    val onUndoClick: () -> Unit = {
        val backup = SourcesTransferRepository.storedBackup()
        if (backup is SourcesTransferParseResult.Success) {
            scope.launch {
                val analysis = SourcesTransferRepository.analyze(backup.document)
                uiState = SourcesTransferUiState.Working
                SourcesTransferRepository.apply(
                    analysis = analysis,
                    mode = SourcesTransferImportMode.REPLACE,
                    keepBackup = true,
                )
                backupAvailable = SourcesTransferRepository.hasBackup()
                uiState = SourcesTransferUiState.Idle
            }
        }
    }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = title,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textSecondary,
            )

            Text(
                text = exportTitle,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.colors.textPrimary,
            )
            Button(
                onClick = onExportClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = exportAction, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Text(
                text = importTitle,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.colors.textPrimary,
            )
            OutlinedTextField(
                value = importPayload,
                onValueChange = { value ->
                    importPayload = value
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodySmall,
                placeholder = {
                    Text(text = importPlaceholder, style = MaterialTheme.typography.bodySmall)
                },
            )
            OutlinedButton(
                onClick = onInspectClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = inspectAction, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            val message = errorMessage
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when (val state = uiState) {
                SourcesTransferUiState.Idle -> Unit

                SourcesTransferUiState.Working -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = workingLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textSecondary,
                    )
                }

                is SourcesTransferUiState.Preview -> {
                    Text(
                        text = previewTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.colors.textPrimary,
                    )
                    PreviewCount(text = totalLabel.withCount(state.analysis.totalCount))
                    PreviewCount(text = importableLabel.withCount(state.analysis.importableCount))
                    PreviewCount(text = duplicatesLabel.withCount(state.analysis.duplicateCount))
                    PreviewCount(text = incompatibleLabel.withCount(state.analysis.incompatibleCount))
                    PreviewCount(text = invalidLabel.withCount(state.analysis.invalidCount))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { runImport(state.analysis, SourcesTransferImportMode.MERGE) },
                            modifier = Modifier.weight(1f),
                            enabled = state.analysis.importableCount > 0,
                        ) {
                            Text(text = mergeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { runImport(state.analysis, SourcesTransferImportMode.REPLACE) },
                            modifier = Modifier.weight(1f),
                            enabled = state.analysis.importableCount > 0,
                        ) {
                            Text(text = replaceLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                is SourcesTransferUiState.Report -> {
                    Text(
                        text = reportTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.colors.textPrimary,
                    )
                    PreviewCount(text = importedLabel.withCount(state.report.imported.size))
                    if (state.report.skipped.isNotEmpty()) {
                        PreviewCount(text = skippedLabel.withCount(state.report.skipped.size))
                        state.report.skipped.take(4).forEach { skipped ->
                            val reason = when (skipped.reason) {
                                SourcesTransferSkipReason.SERVER_REQUIRES_SIGN_IN -> serverSignInLabel
                                SourcesTransferSkipReason.INVALID_ENTRY -> invalidEntryLabel
                            }
                            PreviewCount(text = "• ${skipped.name} — $reason")
                        }
                    }
                    if (state.report.failed.isNotEmpty()) {
                        Text(
                            text = failedLabel.withCount(state.report.failed.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        state.report.failed.take(4).forEach { failed ->
                            Text(
                                text = "• ${failed.name} — ${failed.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                is SourcesTransferUiState.CredentialWarning -> {
                    Text(
                        text = credentialsTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = credentialsMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textSecondary,
                    )
                    state.findings.take(5).forEach { finding ->
                        Text(
                            text = "• ${finding.sourceName} — ${finding.field}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { shareDocument(state.document) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = credentialsContinue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = { uiState = SourcesTransferUiState.Idle },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = cancelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (backupAvailable) {
                OutlinedButton(
                    onClick = onUndoClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = undoLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        DialogButtons {
            OutlinedButton(onClick = onDismiss) {
                Text(text = closeLabel)
            }
        }
    }
}

@Composable
private fun PreviewCount(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.nuvio.colors.textSecondary,
    )
}

private fun String.withCount(count: Int): String =
    if (contains("%1\$d")) replace("%1\$d", count.toString()) else "$this $count"
