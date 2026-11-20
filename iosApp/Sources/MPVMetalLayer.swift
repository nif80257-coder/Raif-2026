#if canImport(Libmpv)

import UIKit
import QuartzCore

/// CALayer that libmpv renders into through MoltenVK.
///
/// Two workarounds from the MPVKit demo live here: MoltenVK can force the
/// drawable size down to 1×1 to finish a presentation, which leaves the picture
/// black, so a smaller size is ignored; and the extended dynamic range flag may
/// only be written on the main thread, where the screen can act on it.
final class MPVMetalLayer: CAMetalLayer {
    override init() {
        super.init()
        framebufferOnly = true
        backgroundColor = UIColor.black.cgColor
        contentsGravity = .resize
        isOpaque = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override var drawableSize: CGSize {
        get { super.drawableSize }
        set {
            if Int(newValue.width) > 1 && Int(newValue.height) > 1 {
                super.drawableSize = newValue
            }
        }
    }

    override var wantsExtendedDynamicRangeContent: Bool {
        get { super.wantsExtendedDynamicRangeContent }
        set {
            if Thread.isMainThread {
                super.wantsExtendedDynamicRangeContent = newValue
            } else {
                DispatchQueue.main.sync {
                    super.wantsExtendedDynamicRangeContent = newValue
                }
            }
        }
    }
}

#endif
