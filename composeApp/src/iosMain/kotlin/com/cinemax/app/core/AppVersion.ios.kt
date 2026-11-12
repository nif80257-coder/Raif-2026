package com.cinemax.app.core

import platform.Foundation.NSBundle

actual fun appVersionLabel(): String {
    val bundle = NSBundle.mainBundle
    val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    val build = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String
    return when {
        version != null && build != null -> "$version ($build)"
        build != null -> build
        version != null -> version
        else -> "—"
    }
}
