package com.nuvio.app.features.addons

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSurfaceCard
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.addon_installing
import nuvio.composeapp.generated.resources.addons_edit_link_placeholder
import nuvio.composeapp.generated.resources.addons_edit_link_save
import nuvio.composeapp.generated.resources.addons_edit_link_subtitle
import nuvio.composeapp.generated.resources.addons_edit_link_title
import nuvio.composeapp.generated.resources.addons_edit_name_label
import org.jetbrains.compose.resources.stringResource

/**
 * Edits the manifest link of an installed source in place.
 *
 * Many sources are configured entirely through their link — a language, a quality filter, a
 * profile id — so the useful edit is the link itself, and saving it here keeps the source where it
 * is in the list instead of adding a second copy beside it.
 */
@Composable
internal fun AddonEditUrlDialog(
    addon: ManagedAddon,
    onDismiss: () -> Unit,
) {
    var name by remember(addon.manifestUrl) {
        mutableStateOf(addon.userSetName ?: addon.manifest?.name.orEmpty())
    }
    var url by remember(addon.manifestUrl) { mutableStateOf(addon.manifestUrl) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canSave = !isSaving && url.isNotBlank()

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NuvioScreen(modifier = Modifier.fillMaxSize()) {
            stickyHeader {
                NuvioScreenHeader(
                    title = stringResource(Res.string.addons_edit_link_title, addon.displayTitle),
                    onBack = { if (!isSaving) onDismiss() },
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.addons_edit_link_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                NuvioInputField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    placeholder = stringResource(Res.string.addons_edit_name_label),
                )
            }

            item {
                NuvioInputField(
                    value = url,
                    onValueChange = {
                        url = it
                        errorMessage = null
                    },
                    placeholder = stringResource(Res.string.addons_edit_link_placeholder),
                )
            }

            item {
                NuvioSurfaceCard {
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    NuvioPrimaryButton(
                        text = if (isSaving) {
                            stringResource(Res.string.addon_installing)
                        } else {
                            stringResource(Res.string.addons_edit_link_save)
                        },
                        enabled = canSave,
                        onClick = {
                            isSaving = true
                            errorMessage = null
                            scope.launch {
                                val urlChanged = url.trim() != addon.manifestUrl
                                val nameChanged = name.trim() !=
                                    (addon.userSetName ?: addon.manifest?.name.orEmpty()).trim()
                                if (!urlChanged) {
                                    if (nameChanged) AddonRepository.renameAddon(addon.manifestUrl, name)
                                    isSaving = false
                                    onDismiss()
                                    return@launch
                                }
                                when (val result = AddonRepository.replaceAddon(addon.manifestUrl, url)) {
                                    is AddAddonResult.Success -> {
                                        // The replacement keeps the old name, so a new one is applied
                                        // after it, on the URL that is now installed.
                                        if (nameChanged) {
                                            AddonRepository.renameAddon(result.manifest.transportUrl, name)
                                        }
                                        isSaving = false
                                        AddonDirectoryRepository.markInstalled(result.manifest.transportUrl)
                                        onDismiss()
                                    }

                                    is AddAddonResult.Error -> {
                                        isSaving = false
                                        errorMessage = result.message
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
