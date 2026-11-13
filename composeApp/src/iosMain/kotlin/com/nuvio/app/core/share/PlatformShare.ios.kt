package com.nuvio.app.core.share

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPopoverPresentationController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
actual object PlatformShare {
    actual fun shareText(text: String, fileName: String, subject: String) {
        val presenter = topViewController() ?: return
        val path = NSTemporaryDirectory() + fileName
        (text as NSString).writeToFile(
            path = path,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        val controller = UIActivityViewController(
            activityItems = listOf(NSURL.fileURLWithPath(path)),
            applicationActivities = null,
        )
        val popover: UIPopoverPresentationController? = controller.popoverPresentationController
        if (popover != null) {
            popover.sourceView = presenter.view
            presenter.view.bounds.useContents {
                popover.sourceRect = CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
            }
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }

    actual fun shareUrl(url: String, subject: String) {
        val presenter = topViewController() ?: return
        val controller = UIActivityViewController(
            activityItems = listOf(NSURL(string = url), subject),
            applicationActivities = null,
        )
        val popover: UIPopoverPresentationController? = controller.popoverPresentationController
        if (popover != null) {
            popover.sourceView = presenter.view
            presenter.view.bounds.useContents {
                popover.sourceRect = CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
            }
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }

    private fun topViewController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) {
            controller = controller?.presentedViewController
        }
        return controller
    }
}
