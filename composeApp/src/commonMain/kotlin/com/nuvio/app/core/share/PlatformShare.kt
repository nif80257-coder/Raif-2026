package com.nuvio.app.core.share

internal expect object PlatformShare {
    fun shareText(text: String, fileName: String, subject: String)
    fun shareUrl(url: String, subject: String)
}