package com.cinemax.app

/** Reads a text file that ships in the app bundle (a resource or a script). */
expect fun readBundledText(name: String, extension: String): String?
