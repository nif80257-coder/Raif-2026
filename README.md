# Řaif

Řaif is a private iOS media app built from the NuvioMobile open-source project. It retains the upstream GPL-3.0 license and notices; see [LICENSE](./LICENSE) and [NOTICE](./NOTICE).

- Base NuvioMobile commit: `39417082688d7659f3abbf081c0c1630778da1a8`
- MPVKit commit: `da4b7da256a5d67dee74f576762ceb9067fc7a71`
- Minimum supported iOS version: 16.1

The product name is the ASCII `Raif` because it feeds `CFBundleExecutable`; every user-visible string, the icon and the display name use `Řaif`.

## Identity and entry

- The app target is `com.raif.stream`; the downloads widget extension is `com.raif.stream.downloadswidgetextension`.
- Launching the app opens an access-code gate. The code is `305`; no email or password is required, and the gate opens a local session.
- The visual identity is a copper accent (`#E3A15C`) on a warm near-black background, with the bundled `raif_display` typeface for headings.
- Branding lives in `composeApp/src/commonMain/composeResources/` (26 locales) and `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/AppBrandWordmark.kt`.

## Bundled sources

`composeApp/src/commonMain/kotlin/com/nuvio/app/features/addons/DefaultAddonSources.kt` mirrors `Raif 2/raif-sources.json` (60 manifests). They are installed once per profile on first launch, on top of nothing: a profile that already has sources keeps them, an entry the user removes is not reinstalled, and the seeded marker lives in `AddonStorage.areDefaultAddonsSeeded` / `markDefaultAddonsSeeded`.

The provider repository `https://raw.githubusercontent.com/tapframe/nuvio-providers/main/manifest.json` is seeded the same way by `fullCommonMain/.../plugins/PluginRepository.kt`.

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

## Distribution

Development installs are signed through the project's FreeMilk account against a registered iPhone; the build is a Release `iphoneos` archive of `iosApp/iosApp.xcodeproj`, and the install link points at the uploaded `.ipa`. This is not an App Store release, and Apple's device and provisioning limits still apply.
