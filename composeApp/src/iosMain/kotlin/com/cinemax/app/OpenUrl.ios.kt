package com.cinemax.app

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openExternalUrl(url: String): Boolean {
    val parsed = NSURL(string = url) ?: return false
    if (!UIApplication.sharedApplication.canOpenURL(parsed)) return false
    UIApplication.sharedApplication.openURL(
        url = parsed,
        options = emptyMap<Any?, Any?>(),
        completionHandler = null
    )
    return true
}
