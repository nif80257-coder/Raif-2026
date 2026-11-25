package com.nuvio.app.features.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.addons_deep_link_description
import nuvio.composeapp.generated.resources.addons_deep_link_install
import nuvio.composeapp.generated.resources.addons_deep_link_title
import org.jetbrains.compose.resources.stringResource

/**
 * The question a link has to answer before a source joins the list.
 *
 * A `stremio://` link installs without a trace of where it came from — the source then decides
 * what the player loads, and the scheme is shared with other apps, so a page or a message can
 * start it. The link's host is shown and the install is one tap away; the default is to add
 * nothing.
 */
@Composable
internal fun AddonDeepLinkInstallDialog(
    manifestUrl: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val host = manifestUrl.substringAfter("://").substringBefore('/').ifBlank { manifestUrl }
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.addons_deep_link_title),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.addons_deep_link_description, host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.action_cancel),
                    onClick = onDismiss,
                )
                DialogButton(
                    text = stringResource(Res.string.addons_deep_link_install),
                    onClick = onInstall,
                )
            }
        }
    }
}
