package com.cinemax.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shows a web page inside the app and reports the address it lands on, so an
 * addon's own configure page can be used without leaving CINEMAX.
 */
@Composable
expect fun InAppBrowser(url: String, onUrl: (String) -> Unit, modifier: Modifier = Modifier)
