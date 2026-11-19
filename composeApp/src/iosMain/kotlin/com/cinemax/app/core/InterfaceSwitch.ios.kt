package com.cinemax.app.core

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults

actual fun openCinemaShell() {
    NSUserDefaults.standardUserDefaults.setBool(false, "cinemax.ui.classic")
    NSNotificationCenter.defaultCenter.postNotificationName("CinemaxSwitchInterface", null)
}
