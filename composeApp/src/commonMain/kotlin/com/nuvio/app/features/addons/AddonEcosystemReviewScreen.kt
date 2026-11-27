package com.nuvio.app.features.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioSurfaceCard
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The add-on ecosystem review (`docs/Raif-Addon-Ecosystem-Review.md`) as a full-screen sheet,
 * opened from the add-on list so the ranked backlog sits next to the sources it talks about.
 */
@Composable
internal fun AddonEcosystemReviewDialog(
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AddonEcosystemReviewContent(onBack = onDismiss)
    }
}

@Composable
internal fun AddonEcosystemReviewContent(
    onBack: () -> Unit,
) {
    val entries = AddonReviewEntries
    val byPriority = missingInPriorityOrder(entries)
    NuvioScreen(modifier = Modifier.fillMaxSize()) {
        stickyHeader {
            NuvioScreenHeader(
                title = stringResource(Res.string.addon_review_title),
                onBack = onBack,
                usesOwnHeaderBar = true,
            )
        }

        item { ReviewSummaryCard(entries = entries) }

        item { NuvioSectionLabel(text = stringResource(Res.string.addon_review_section_priorities)) }
        items(
            items = byPriority,
            key = { entry -> "priority:${entry.source.name}:${entry.feature}" },
        ) { entry ->
            ReviewEntryCard(entry = entry)
        }

        item { NuvioSectionLabel(text = stringResource(Res.string.addon_review_section_inventory)) }
        AddonReviewSource.entries.forEach { source ->
            item(key = "source:${source.name}") {
                Text(
                    text = source.label,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            items(
                items = entries.bySource(source),
                key = { entry -> "inventory:${entry.source.name}:${entry.feature}" },
            ) { entry ->
                ReviewEntryCard(entry = entry)
            }
        }

        item { NuvioSectionLabel(text = stringResource(Res.string.addon_review_section_fixes)) }
        items(
            items = AddonReviewFixes,
            key = { fix -> "fix:${fix.topic}" },
        ) { fix ->
            ReviewFixCard(fix = fix)
        }

        item { NuvioSectionLabel(text = stringResource(Res.string.addon_review_section_risks)) }
        items(
            items = AddonReviewRisks,
            key = { risk -> "risk:${risk.title}" },
        ) { risk ->
            ReviewRiskCard(risk = risk)
        }

        item { NuvioSectionLabel(text = stringResource(Res.string.addon_review_section_plan)) }
        items(
            items = AddonReviewPlan,
            key = { step -> "plan:${step.title}" },
        ) { step ->
            ReviewPlanCard(step = step)
        }

        item {
            Text(
                text = stringResource(Res.string.addon_review_doc_note),
                modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewSummaryCard(entries: List<AddonReviewEntry>) {
    NuvioSurfaceCard {
        Text(
            text = stringResource(
                Res.string.addon_review_summary_format,
                entries.countVerdict(AddonReviewVerdict.Present),
                entries.countVerdict(AddonReviewVerdict.Partial),
                entries.countVerdict(AddonReviewVerdict.Missing),
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.addon_review_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewEntryCard(entry: AddonReviewEntry) {
    NuvioSurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.feature,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            ReviewChip(
                text = stringResource(Res.string.addon_review_priority_format, entry.priority),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReviewChip(
                text = entry.verdict.label(),
                color = entry.verdict.color(),
            )
            ReviewChip(
                text = entry.difficulty.label(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReviewChip(
                text = entry.source.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = entry.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.addon_review_benefit_format, entry.benefit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewFixCard(fix: AddonReviewFix) {
    NuvioSurfaceCard {
        Text(
            text = fix.topic,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = fix.where,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.addon_review_problem_format, fix.problem),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.addon_review_fix_format, fix.fix),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewRiskCard(risk: AddonReviewRisk) {
    NuvioSurfaceCard {
        Text(
            text = risk.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = risk.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewPlanCard(step: AddonReviewStep) {
    NuvioSurfaceCard {
        Text(
            text = step.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        step.steps.forEachIndexed { index, line ->
            Text(
                text = "${index + 1}. $line",
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewChip(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AddonReviewVerdict.label(): String = stringResource(
    when (this) {
        AddonReviewVerdict.Present -> Res.string.addon_review_verdict_present
        AddonReviewVerdict.Partial -> Res.string.addon_review_verdict_partial
        AddonReviewVerdict.Missing -> Res.string.addon_review_verdict_missing
        AddonReviewVerdict.NotApplicable -> Res.string.addon_review_verdict_not_applicable
    },
)

@Composable
private fun AddonReviewVerdict.color(): Color = when (this) {
    AddonReviewVerdict.Present -> MaterialTheme.colorScheme.primary
    AddonReviewVerdict.Partial -> MaterialTheme.colorScheme.tertiary
    AddonReviewVerdict.Missing -> MaterialTheme.colorScheme.error
    AddonReviewVerdict.NotApplicable -> MaterialTheme.colorScheme.outline
}

@Composable
private fun AddonReviewDifficulty.label(): String = stringResource(
    when (this) {
        AddonReviewDifficulty.Easy -> Res.string.addon_review_difficulty_easy
        AddonReviewDifficulty.Medium -> Res.string.addon_review_difficulty_medium
        AddonReviewDifficulty.Hard -> Res.string.addon_review_difficulty_hard
    },
)
