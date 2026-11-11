package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.raif_copyright
import nuvio.composeapp.generated.resources.raif_gpl_license_title
import nuvio.composeapp.generated.resources.raif_upstream_notice
import nuvio.composeapp.generated.resources.raif_upstream_repository_body
import nuvio.composeapp.generated.resources.raif_upstream_repository_title
import nuvio.composeapp.generated.resources.raif_gpl_license_body
import org.jetbrains.compose.resources.stringResource

private const val UpstreamRepositoryUrl = "https://github.com/NuvioMedia/NuvioMobile"
private const val GplLicenseUrl = "https://www.gnu.org/licenses/gpl-3.0.html"

@Composable
internal fun RaifLicenseHeader(isTablet: Boolean) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 4.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.raif_copyright),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.raif_upstream_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.raif_upstream_repository_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.raif_upstream_repository_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = UpstreamRepositoryUrl,
            modifier = Modifier.clickable { uriHandler.openUri(UpstreamRepositoryUrl) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(Res.string.raif_gpl_license_title),
            modifier = Modifier.clickable { uriHandler.openUri(GplLicenseUrl) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(Res.string.raif_gpl_license_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
