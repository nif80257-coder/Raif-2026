package com.nuvio.app.features.player

/** Writes an imported subtitle file somewhere the player can read it back. */
internal expect object LocalSubtitleFileStore {
    val isSupported: Boolean

    /** Returns the URL the player can open, or null when the file could not be written. */
    fun write(fileName: String, content: String): String?
}
