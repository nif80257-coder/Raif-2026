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

`composeApp/src/commonMain/kotlin/com/nuvio/app/features/addons/DefaultAddonSources.kt` mirrors `docs/raif-sources.json` (24 manifests: 3 catalogs, 5 subtitle providers and 16 stream sources, each answering with a direct `https` link or an `infoHash` the app's own p2p engine plays). Every stream entry was asked for a real title and returned a playable link; none needs a debrid account. Each stream source is reached at the address its own author publishes — no `profile=`, `enc_` blob or `/u/<id>` path — so it keeps answering whoever installed it; the four configured subtitle providers are the exception, because their link carries the configuration without which they answer nothing, that configuration can be revoked by whoever set it up, and no password or paid account sits in any of them. They are installed once per profile on first launch, on top of nothing: a profile that already has sources keeps them, an entry the user removes is not reinstalled, and the seeded marker lives in `AddonStorage.areDefaultAddonsSeeded` / `markDefaultAddonsSeeded`.

The provider repositories are seeded the same way by `fullCommonMain/.../plugins/PluginRepository.kt` — the Nuvio project's own `tapframe/nuvio-providers` list, `michat88/nuvio-providers` and `TechEnthusiast47/phisher-nuvio-providers` (see `DefaultProviderRepositories` in `features/plugins/PluginModels.kt`). The two largest community lists (66 and 95 providers) are deliberately left out: every provider is a request per title, so they cost more load time than they return. The anime scrapers every list also carries are dropped when a repository is read (`features/plugins/ProviderScraperPolicy.kt`), because this app carries films and series. A repository whose host stops answering is left off the six-hour schedule after three failed refreshes in a row — settings shows the count under its name, and a manual refresh still goes through and can wake it — and a list whose every provider was dropped is refused at install instead of being installed empty. A single provider that fails three searches in a row rests for ten minutes (`features/plugins/PluginScraperHealth.kt`), so one dead site no longer costs ten seconds on every title.

## Add-on ecosystem

`docs/Raif-Addon-Ecosystem-Review.md` compares four Stremio add-on repositories with what this app does, and is also rendered in the app (Settings → Content discovery → Add-ons → "Open the ecosystem review"). From that review the app now reads `addonCatalogs` and `config` from a manifest, lists availability sources apart from playable ones, opens an `externalUrl` only as a web page (`https` or `http`, with the destination host shown first), and hides titles a profile marks as hidden along with sources that declare themselves adult. An installed source that declares `addon_catalog` can be browsed from the add-on screen and its entries installed in one tap, and a manifest that declares `config[]` gets an in-app form that installs a configured copy of the source. Installed sources are grouped by the repository host their manifest lives on, and a repository with more than one source folds away behind its own Show/Hide button, with Show all / Hide all above the list.

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
