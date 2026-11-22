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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import com.nuvio.app.features.addons.repositoryHost
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_paused_source_count
import nuvio.composeapp.generated.resources.home_paused_source_resume
import nuvio.composeapp.generated.resources.home_paused_source_title
import org.jetbrains.compose.resources.stringResource

/**
 * The shelf a paused source took off the home screen, kept in hand so the screen can say so
 * instead of the row disappearing between two launches with no explanation.
 *
 * A source is paused after three stream requests in a row failed ([AddonFailureMemory] for the
 * stream path); its catalogs stop being fetched, so its shelves vanish until the pause runs out
 * or the user resumes the source.
 */
data class PausedShelfNotice(
    val manifestUrl: String,
    /** The name the source shows under, or its host when the manifest was never renamed. */
    val sourceName: String,
    val hiddenShelfCount: Int,
)

/**
 * Picks the source worth telling the user about, or null when nothing is actually hidden.
 *
 * [definitions] is every shelf the home screen asked for and [fetchedDefinitions] the ones left
 * after the pause filter, so this reads the filter's own result rather than repeating its rule:
 * when the filter would have removed every shelf it keeps the whole list, and nothing is hidden.
 * The source that lost the most shelves wins, with the name and then the URL breaking a tie, so
 * the same situation always names the same source.
 */
internal fun pausedShelfNotice(
    definitions: List<HomeCatalogDefinition>,
    pausedManifestUrls: Set<String>,
    fetchedDefinitions: List<HomeCatalogDefinition>,
): PausedShelfNotice? {
    if (pausedManifestUrls.isEmpty()) return null
    if (fetchedDefinitions.size == definitions.size) return null
    val hidden = definitions.filter { definition -> definition.manifestUrl in pausedManifestUrls }
    if (hidden.isEmpty()) return null
    val picked = hidden.groupBy(HomeCatalogDefinition::manifestUrl)
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, List<HomeCatalogDefinition>>> { entry -> entry.value.size }
                .thenBy { entry -> entry.value.first().addonName.lowercase() }
                .thenBy { entry -> entry.key },
        )
        .first()
    val name = picked.value.first().addonName.ifBlank { picked.key.repositoryHost() }
    return PausedShelfNotice(
        manifestUrl = picked.key,
        sourceName = name,
        hiddenShelfCount = picked.value.size,
    )
}

@Composable
internal fun HomePausedShelfNoticeBanner(
    notice: PausedShelfNotice,
    onResume: () -> Unit,
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
                    text = stringResource(Res.string.home_paused_source_title, notice.sourceName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.home_paused_source_count, notice.hiddenShelfCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onResume) {
                Text(
                    text = stringResource(Res.string.home_paused_source_resume),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
