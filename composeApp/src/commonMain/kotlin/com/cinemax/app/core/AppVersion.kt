package com.cinemax.app.core

/**
 * The version line shown in the app (for example "1.0.2 (90)"), read from the
 * platform bundle so it can never drift from the build that was shipped.
 */
expect fun appVersionLabel(): String
