package com.nuvio.app

import androidx.compose.runtime.Composable
import com.nuvio.app.features.library.FavoritesScreen
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.FavoritesRoute
import com.nuvio.app.navigation.NuvioNavigator

@Composable
internal fun FavoritesDestination(
    route: FavoritesRoute,
    navController: NuvioNavigator,
    onPlayItem: (LibraryItem) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    FavoritesScreen(
        onBack = onBack,
        onBrowseClick = onBack,
        onItemClick = { item ->
            navController.navigate(
                DetailRoute(type = item.type, id = item.id, title = item.name),
            )
        },
        onPlayClick = onPlayItem,
    )
}
