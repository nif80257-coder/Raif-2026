package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberLocalSubtitlePicker(
    onPicked: (fileName: String, contents: String) -> Unit,
): () -> Unit
