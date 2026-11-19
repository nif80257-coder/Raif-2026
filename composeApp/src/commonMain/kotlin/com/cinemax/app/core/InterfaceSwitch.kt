package com.cinemax.app.core

/**
 * Asks the iOS host to show the SwiftUI interface instead of this one. The
 * Swift layer owns the window, so this is a request, not a navigation.
 */
expect fun openCinemaShell()
