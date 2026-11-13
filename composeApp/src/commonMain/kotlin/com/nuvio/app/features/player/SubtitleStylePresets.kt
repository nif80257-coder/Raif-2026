package com.nuvio.app.features.player

import androidx.compose.ui.graphics.Color
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.subtitle_preset_cinematic
import nuvio.composeapp.generated.resources.subtitle_preset_default
import nuvio.composeapp.generated.resources.subtitle_preset_large
import nuvio.composeapp.generated.resources.subtitle_preset_readable
import org.jetbrains.compose.resources.StringResource

internal data class SubtitleStylePreset(
    val labelRes: StringResource,
    val apply: (SubtitleStyleState) -> SubtitleStyleState,
)

/**
 * One-tap looks for the styled subtitle overlay. Each preset starts from the factory defaults
 * and keeps the playback flags (SDH stripping, forced subtitles, language filtering) that are
 * not part of the look.
 */
internal val SubtitleStylePresets: List<SubtitleStylePreset> = listOf(
    SubtitleStylePreset(Res.string.subtitle_preset_default) { it.basis() },
    SubtitleStylePreset(Res.string.subtitle_preset_cinematic) {
        it.basis().copy(
            fontSizeSp = 20,
            bold = false,
            textColor = Color.White,
            backgroundColor = Color.Transparent,
            outlineEnabled = false,
            outlineColor = Color.Black,
            bottomOffset = 24,
            appearance = SubtitleAppearance(shadowEnabled = true, shadowOpacity = 80, shadowSize = 3),
        )
    },
    SubtitleStylePreset(Res.string.subtitle_preset_readable) {
        it.basis().copy(
            fontSizeSp = 22,
            bold = true,
            textColor = Color.White,
            backgroundColor = Color.Black.copy(alpha = 0.55f),
            outlineEnabled = false,
            outlineColor = Color.Black,
            bottomOffset = 24,
            appearance = SubtitleAppearance(backgroundRadius = 12),
        )
    },
    SubtitleStylePreset(Res.string.subtitle_preset_large) {
        it.basis().copy(
            fontSizeSp = 28,
            bold = true,
            textColor = Color(0xFFFFD700),
            backgroundColor = Color.Transparent,
            outlineEnabled = true,
            outlineColor = Color.Black,
            bottomOffset = 28,
            appearance = SubtitleAppearance(),
        )
    },
)

/** True when the current style is exactly what [preset] would produce for it. */
internal fun SubtitleStyleState.matchesPreset(preset: SubtitleStylePreset): Boolean =
    preset.apply(this) == this

private fun SubtitleStyleState.basis(): SubtitleStyleState = SubtitleStyleState.DEFAULT.copy(
    stripSdh = stripSdh,
    useForcedSubtitles = useForcedSubtitles,
    showOnlyPreferredLanguages = showOnlyPreferredLanguages,
)