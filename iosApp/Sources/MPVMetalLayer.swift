#if canImport(Libmpv)

import UIKit
import QuartzCore

/// CALayer that libmpv renders into through MoltenVK.
final class MPVMetalLayer: CAMetalLayer {
    override init() {
        super.init()
        framebufferOnly = true
        backgroundColor = UIColor.black.cgColor
        contentsGravity = .resize
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }
}


#endif
