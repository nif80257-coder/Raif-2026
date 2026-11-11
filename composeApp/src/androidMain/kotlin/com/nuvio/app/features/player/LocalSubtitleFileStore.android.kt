package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal actual object LocalSubtitleFileStore {
    actual val isSupported: Boolean = false

    actual fun write(fileName: String, content: String): String? = null
}

@Composable
internal actual fun rememberLocalSubtitlePicker(
    onPicked: (fileName: String, contents: String) -> Unit,
): () -> Unit = remember { {} }
