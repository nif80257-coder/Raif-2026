package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual object LocalSubtitleFileStore {
    actual val isSupported: Boolean = true

    actual fun write(fileName: String, content: String): String? {
        val safeName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { character -> character.isLetterOrDigit() || character == '.' || character == '-' || character == '_' }
            .ifBlank { "local-subtitles.srt" }
        val path = NSTemporaryDirectory() + "raif-" + safeName
        val wrote = (content as NSString).writeToFile(
            path = path,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        return if (wrote) "file://$path" else null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readSubtitleText(path: String): String? {
    val data: NSData = NSFileManager.defaultManager.contentsAtPath(path) ?: return null
    val length = data.length.toInt()
    if (length <= 0) return ""
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, length.convert())
    }
    return bytes.decodeToString()
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberLocalSubtitlePicker(
    onPicked: (fileName: String, contents: String) -> Unit,
): () -> Unit {
    val presenter = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val latestOnPicked = rememberUpdatedState(onPicked)
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                scope.launch {
                    val payload = withContext(Dispatchers.Default) {
                        val accessed = url.startAccessingSecurityScopedResource()
                        try {
                            runCatching {
                                val path = requireNotNull(url.path)
                                val text = readSubtitleText(path) ?: error("Unreadable subtitle file")
                                url.lastPathComponent.orEmpty() to text
                            }.getOrNull()
                        } finally {
                            if (accessed) url.stopAccessingSecurityScopedResource()
                        }
                    }
                    if (payload != null) {
                        latestOnPicked.value(payload.first, payload.second)
                    }
                }
            }
        }
    }
    return {
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.subtitle", "public.text", "public.data"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
        )
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        presenter.presentViewController(picker, true, null)
    }
}
