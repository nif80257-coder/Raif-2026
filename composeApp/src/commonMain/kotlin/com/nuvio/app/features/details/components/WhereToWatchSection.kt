package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.WatchProvider
import com.nuvio.app.features.tmdb.WatchProviderKind
import com.nuvio.app.features.tmdb.WhereToWatch
import com.nuvio.app.features.tmdb.WhereToWatchRepository
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The services a title can be watched on in the profile's region, from TMDB's watch providers.
 * It draws nothing when the integration is off or TMDB has no providers for the title, rather
 * than leaving an empty heading behind.
 */
@Composable
fun WhereToWatchSection(
    meta: MetaDetails,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val settings = TmdbSettingsRepository.snapshot()
    if (!settings.enabled) return

    var data by remember(meta.id, meta.type) { mutableStateOf<WhereToWatch?>(null) }
    var attempted by remember(meta.id, meta.type) { mutableStateOf(false) }

    LaunchedEffect(meta.id, meta.type, meta.imdbId, settings.language) {
        data = null
        attempted = false
        data = WhereToWatchRepository.load(
            videoId = meta.id,
            type = meta.type,
            imdbId = meta.imdbId,
            language = settings.language,
        )
        attempted = true
    }

    val whereToWatch = data
    if (whereToWatch == null) {
        if (!attempted) return
        return
    }

    val uriHandler = LocalUriHandler.current
    DetailSection(
        title = stringResource(Res.string.details_where_to_watch),
        modifier = modifier,
        showHeader = showHeader,
    ) {
        whereToWatch.groups.forEach { group ->
            Text(
                text = watchProviderKindLabel(group.kind),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.providers.forEach { provider ->
                    WatchProviderChip(provider = provider)
                }
            }
        }
        whereToWatch.link?.takeIf { it.isNotBlank() }?.let { link ->
            TextButton(onClick = { runCatching { uriHandler.openUri(link) } }) {
                Text(text = stringResource(Res.string.details_where_to_watch_tmdb))
            }
        }
    }
}

@Composable
private fun watchProviderKindLabel(kind: WatchProviderKind): String = stringResource(
    when (kind) {
        WatchProviderKind.Stream -> Res.string.details_where_to_watch_stream
        WatchProviderKind.Free -> Res.string.details_where_to_watch_free
        WatchProviderKind.Ads -> Res.string.details_where_to_watch_ads
        WatchProviderKind.Rent -> Res.string.details_where_to_watch_rent
        WatchProviderKind.Buy -> Res.string.details_where_to_watch_buy
    },
)

@Composable
private fun WatchProviderChip(provider: WatchProvider) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        provider.logoUrl?.let { logo ->
            AsyncImage(
                model = logo,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = provider.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
