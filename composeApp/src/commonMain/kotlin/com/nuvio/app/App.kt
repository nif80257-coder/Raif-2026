package com.nuvio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import com.nuvio.app.core.ui.NativeProfileSwitcherController
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.configurePlatformImageLoader
import com.nuvio.app.core.ui.platformProvidesImageLoader
import com.nuvio.app.features.settings.RaifUpdatesRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import kotlinx.coroutines.delay
import com.nuvio.app.navigation.AppRoute
import com.nuvio.app.navigation.TabsRoute

/** Pause before the automatic add-on update check, so it does not race the first screen. */
private const val STARTUP_UPDATE_CHECK_DELAY_MS = 2_000L

fun disposeRoute(route: AppRoute) {
    disposeRouteResources(route)
}

@OptIn(ExperimentalCoilApi::class)
@Composable
@Preview
fun App(
    initialTab: AppScreenTab = AppScreenTab.Home,
    initialRoute: AppRoute = TabsRoute,
    useNativeNavigation: Boolean = false,
    useNativeTabBar: Boolean = false,
    useTabletFloatingTabBar: Boolean = false,
    ownsAppRuntime: Boolean = true,
    bypassAppGate: Boolean = false,
    onNavigate: ((AppRoute, launchSingleTop: Boolean) -> Unit)? = null,
    onGoBack: (() -> Unit)? = null,
    onReplace: ((AppRoute) -> Unit)? = null,
    onActivate: ((AppScreenTab) -> Unit)? = null,
    onAppReady: ((Boolean) -> Unit)? = null,
    onTabTitles: ((home: String, search: String, library: String, profile: String, switchProfile: String, addProfile: String) -> Unit)? = null,
    nativeProfileSwitcherController: NativeProfileSwitcherController? = null,
    appGateController: AppGateController? = null,
) {
    AppEnvironment {
        AppGate(
            initialTab = initialTab,
            initialRoute = initialRoute,
            useNativeNavigation = useNativeNavigation,
            useNativeTabBar = useNativeTabBar,
            useTabletFloatingTabBar = useTabletFloatingTabBar,
            ownsAppRuntime = ownsAppRuntime,
            bypassAppGate = bypassAppGate,
            renderMainContent = true,
            onNavigate = onNavigate,
            onGoBack = onGoBack,
            onReplace = onReplace,
            onActivate = onActivate,
            onAppReady = onAppReady,
            onMainContentMountChanged = null,
            onMainContentVisibleChanged = null,
            onTabTitles = onTabTitles,
            nativeProfileSwitcherController = nativeProfileSwitcherController,
            appGateController = appGateController,
        )
    }
}

@Composable
internal fun AppEnvironment(content: @Composable () -> Unit) {
    if (!platformProvidesImageLoader) {
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .components {
                    add(CustomPosterFallbackInterceptor())
                    add(SvgDecoder.Factory())
                    add(
                        coil3.network.ktor3.KtorNetworkFetcherFactory(
                            cacheStrategy = { coil3.network.cachecontrol.CacheControlCacheStrategy() },
                        ),
                    )
                }
                .configurePlatformImageLoader()
                .build()
        }
    }
    val selectedTheme by remember {
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.selectedTheme
    }.collectAsStateWithLifecycle()
    val amoledEnabled by remember {
        ThemeSettingsRepository.amoledEnabled
    }.collectAsStateWithLifecycle()

    val customThemeColors by ThemeSettingsRepository.customThemeColors.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        // The daily add-on update check opens dozens of requests. Starting it two seconds in
        // keeps it from competing with the first frame and the home shelves on a cold launch.
        delay(STARTUP_UPDATE_CHECK_DELAY_MS)
        RaifUpdatesRepository.runAutomaticDailyCheck()
    }

    NuvioTheme(appTheme = selectedTheme, amoled = amoledEnabled, customThemeColors = customThemeColors) {
        content()
    }
}
