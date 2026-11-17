package com.cinemax.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigation
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/** Reports every finished navigation, which is how the configure page's URL is read. */
private class BrowserDelegate : NSObject(), WKNavigationDelegateProtocol {
    var onUrl: ((String) -> Unit)? = null

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        val address = webView.URL?.absoluteString ?: return
        onUrl?.invoke(address)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun InAppBrowser(url: String, onUrl: (String) -> Unit, modifier: Modifier) {
    val delegate = remember { BrowserDelegate() }
    val currentOnUrl by rememberUpdatedState(onUrl)
    delegate.onUrl = { address -> currentOnUrl(address) }
    val webView = remember(url) {
        val parsed = NSURL(string = url)
        WKWebView(
            frame = CGRectZero.readValue(),
            configuration = WKWebViewConfiguration()
        ).apply {
            navigationDelegate = delegate
            if (parsed != null) loadRequest(NSURLRequest(uRL = parsed))
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.navigationDelegate = null
        }
    }
    UIKitView(factory = { webView }, modifier = modifier)
}
