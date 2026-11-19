package com.cinemax.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory

/**
 * The shared UI as a UIKit view controller. Uncaught Kotlin failures during the
 * first composition are recorded by [CinemaxDiagnostics] and reported by the
 * Swift host on the next launch, since a composition error cannot be caught.
 *
 * Every measurement is raised a little before the UI is built: the Arabic text
 * and the controls had been drawn too small for comfortable reading, and a
 * density/font-scale nudge grows the lot together — posters, chips, buttons and
 * type — without touching each screen's numbers.
 */
fun MainViewController() = ComposeUIViewController {
    // Compose Multiplatform needs the singleton image loader configured with a
    // network fetcher itself; otherwise posters never load on iOS.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
    // Installed before the first composition, so a failure to build the shared UI
    // is recorded and can be shown on the next launch.
    CinemaxDiagnostics.install()
    // Grows the whole UI a notch without ignoring the reader's own text size: the
    // system font scale is multiplied, never replaced.
    val systemDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = systemDensity.density * 1.1f,
            fontScale = systemDensity.fontScale * 1.08f
        )
    ) {
        App()
    }
}
