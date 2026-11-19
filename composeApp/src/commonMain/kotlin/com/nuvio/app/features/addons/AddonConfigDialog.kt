package com.nuvio.app.features.addons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.features.settings.SettingsSecretTextField
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The form a manifest describes in `config[]`. Filling it installs a configured copy of the
 * add-on: the typed values become the query of its manifest link, so the original stays as it is
 * and the configured one is installed beside it.
 */
@Composable
internal fun AddonConfigDialog(
    addon: ManagedAddon,
    onOpenWebsite: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val fields = remember(addon.manifestUrl) { addon.manifest?.configFields.orEmpty() }
    var values by remember(addon.manifestUrl) {
        mutableStateOf(fields.associate { field -> field.key to field.default.orEmpty() })
    }
    var isInstalling by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canInstall = fields.hasAllRequiredValues(values)

    Dialog(
        onDismissRequest = { if (!isInstalling) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NuvioScreen(modifier = Modifier.fillMaxSize()) {
            stickyHeader {
                NuvioScreenHeader(
                    title = stringResource(Res.string.addons_config_title, addon.displayTitle),
                    onBack = { if (!isInstalling) onDismiss() },
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.addons_config_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            fields.forEach { field ->
                item(key = "config:${field.key}") {
                    ConfigFieldCard(
                        field = field,
                        value = values[field.key].orEmpty(),
                        onChange = { value -> values = values + (field.key to value) },
                    )
                }
            }

            item {
                NuvioSurfaceCard {
                    if (!canInstall) {
                        Text(
                            text = stringResource(Res.string.addons_config_missing_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    NuvioPrimaryButton(
                        text = if (isInstalling) {
                            stringResource(Res.string.addon_installing)
                        } else {
                            stringResource(Res.string.addons_config_install)
                        },
                        enabled = !isInstalling && canInstall,
                        onClick = {
                            isInstalling = true
                            errorMessage = null
                            scope.launch {
                                val configuredUrl = buildConfiguredManifestUrl(
                                    manifestUrl = addon.manifestUrl,
                                    values = fields.map { field -> field.key to values[field.key].orEmpty() },
                                )
                                when (val result = AddonRepository.addAddon(configuredUrl)) {
                                    is AddAddonResult.Success -> {
                                        isInstalling = false
                                        AddonDirectoryRepository.markInstalled(configuredUrl)
                                        onDismiss()
                                    }

                                    is AddAddonResult.Error -> {
                                        isInstalling = false
                                        errorMessage = result.message
                                    }
                                }
                            }
                        },
                    )
                    onOpenWebsite?.let { openWebsite ->
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = openWebsite) {
                            Text(text = stringResource(Res.string.addons_config_open_website))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigFieldCard(
    field: AddonConfigField,
    value: String,
    onChange: (String) -> Unit,
) {
    NuvioSurfaceCard {
        Text(
            text = field.title ?: field.key,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        field.description?.let { description ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (field.isChoice) {
            Column {
                field.options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onChange(option) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == option, onClick = null)
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        } else if (field.isSecret) {
            SettingsSecretTextField(
                value = value,
                onValueChange = onChange,
                label = field.title ?: field.key,
            )
        } else {
            NuvioInputField(
                value = value,
                onValueChange = onChange,
                placeholder = field.key,
            )
        }
        if (field.required) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.addons_config_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
