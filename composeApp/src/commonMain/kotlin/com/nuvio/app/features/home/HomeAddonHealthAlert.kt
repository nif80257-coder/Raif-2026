package com.nuvio.app.features.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.settings.UnresponsiveAddonAlert
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_addon_alert_dismiss
import nuvio.composeapp.generated.resources.home_addon_alert_title
import nuvio.composeapp.generated.resources.home_addon_alert_view
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HomeAddonHealthAlertBanner(
    alert: UnresponsiveAddonAlert,
    onOpen: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = tokens.colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(tokens.borders.hairline, tokens.colors.warning.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = tokens.colors.warning,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.home_addon_alert_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alert.name.isNotBlank()) {
                    Text(
                        text = alert.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = { onOpen(alert.manifestUrl) }) {
                Text(
                    text = stringResource(Res.string.home_addon_alert_view),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            IconButton(
                onClick = { onDismiss(alert.manifestUrl) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.home_addon_alert_dismiss),
                    tint = tokens.colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
