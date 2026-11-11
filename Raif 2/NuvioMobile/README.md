# RAIF

RAIF is a private iOS media app built from the NuvioMobile open-source project. It retains the upstream GPL-3.0 license and notices; see [LICENSE](./LICENSE) and [NOTICE](./NOTICE).

- Base NuvioMobile commit: `39417082688d7659f3abbf081c0c1630778da1a8`
- MPVKit commit: `da4b7da256a5d67dee74f576762ceb9067fc7a71`
- Minimum supported iOS version: 16.1

## Build for the iOS Simulator

Requirements: macOS, Xcode, and Java 17 or newer.

```bash
git submodule update --init --depth 1 MPVKit
./scripts/prepare-ios-dependencies.sh
touch local.properties
env NUVIO_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  'PRODUCT_NAME=$(TARGET_NAME)' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The dependency preparation script downloads NuvioEngine.xcframework 0.1.6 and verifies its SHA-256 before use. `local.properties` may remain empty for a basic simulator build; keep any local credentials out of version control.

The shared application uses Kotlin Multiplatform and Compose Multiplatform; the iOS shell and media playback use SwiftUI/UIKit and MPVKit.
