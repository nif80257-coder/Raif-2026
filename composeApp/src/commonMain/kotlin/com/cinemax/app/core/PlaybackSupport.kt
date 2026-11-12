package com.cinemax.app.core

/**
 * Container formats AVPlayer (iOS) cannot open. Stremio torrent/debrid sources are
 * frequently MKV, which plays as a black screen in the native player, so the UI
 * can say why instead of leaving the user with a blank frame.
 */
private val unsupportedContainers = setOf(
    "mkv", "avi", "flv", "wmv", "webm", "mpg", "mpeg", "ts", "m2ts", "iso", "rar", "zip", "7z"
)

/** The unsupported container extension, or null when AVPlayer should handle it. */
fun unsupportedVideoContainer(url: String?, filename: String?): String? {
    val candidates = listOfNotNull(filename, url?.substringBefore('?')?.substringBefore('#'))
    for (candidate in candidates) {
        val name = candidate.substringAfterLast('/', candidate).lowercase()
        val extension = name.substringAfterLast('.', "")
        if (extension.isNotEmpty() && extension in unsupportedContainers) return extension
    }
    return null
}
