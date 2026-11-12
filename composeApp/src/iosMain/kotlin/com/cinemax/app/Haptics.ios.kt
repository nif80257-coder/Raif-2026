package com.cinemax.app

import platform.UIKit.UISelectionFeedbackGenerator

actual fun hapticTap() {
    val generator = UISelectionFeedbackGenerator()
    generator.prepare()
    generator.selectionChanged()
}
