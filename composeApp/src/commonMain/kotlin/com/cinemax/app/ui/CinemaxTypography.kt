package com.cinemax.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Arabic-first type scale. The platform's own Arabic face (SF Arabic) is used,
 * but every style gets what a connected script needs: no letter spacing (which
 * breaks the joins) and a 1.45× line height, so stacked Arabic lines breathe
 * instead of colliding.
 */
private fun arabic(style: TextStyle): TextStyle = style.copy(
    letterSpacing = 0.sp,
    lineHeight = (style.fontSize.value * 1.45f).sp
)

/** The app's type scale: the Material defaults, re-cut for Arabic. */
fun cinemaxTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = arabic(base.displayLarge),
        displayMedium = arabic(base.displayMedium),
        displaySmall = arabic(base.displaySmall),
        headlineLarge = arabic(base.headlineLarge),
        headlineMedium = arabic(base.headlineMedium),
        headlineSmall = arabic(base.headlineSmall),
        titleLarge = arabic(base.titleLarge),
        titleMedium = arabic(base.titleMedium),
        titleSmall = arabic(base.titleSmall),
        bodyLarge = arabic(base.bodyLarge),
        bodyMedium = arabic(base.bodyMedium),
        bodySmall = arabic(base.bodySmall),
        labelLarge = arabic(base.labelLarge),
        labelMedium = arabic(base.labelMedium),
        labelSmall = arabic(base.labelSmall)
    )
}
