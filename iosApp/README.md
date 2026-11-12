# CINEMAX iOS app

This folder contains the native SwiftUI iOS application shell. The shared screens, the Stremio addon client and the Nuvio provider runtime are provided by the Kotlin Multiplatform `CinemaxKit` framework.

## Build on macOS

Requirements: Xcode 16+, JDK 17+.

`CINEMAX.xcodeproj` is generated from `project.yml` with XcodeGen and kept in the repository as generated output, so the app can be built directly, including from the app's own iOS build. Its "Build CinemaxKit" run script calls the committed Gradle wrapper (`sh ./gradlew`) to link the static `CinemaxKit` framework for the target platform, and `FRAMEWORK_SEARCH_PATHS` points at `composeApp/build/bin/iosArm64/$(KOTLIN_FRAMEWORK_FLAVOR)Framework` (device) or `composeApp/build/bin/iosSimulatorArm64/$(KOTLIN_FRAMEWORK_FLAVOR)Framework` (simulator), where `KOTLIN_FRAMEWORK_FLAVOR` is `debug` in the Debug configuration and `release` in Release. No manual Gradle step is needed:

```sh
xcodebuild -project CINEMAX.xcodeproj -scheme CINEMAX -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO
```

`project.yml` is the source of truth. After changing targets, sources, build settings or the build number, regenerate the project so the two stay in sync:

```sh
cd iosApp && rm -rf CINEMAX.xcodeproj && xcodegen generate --spec project.yml
```

The app uses `MPVKit` as a Swift package dependency for libmpv playback (MKV, HEVC, ASS subtitles), and `LaunchScreen.storyboard` as its launch storyboard: without a launch storyboard iOS runs the app letterboxed in Compatibility Mode.

For a physical iPhone, build the `iosArm64` framework and select an iPhone destination in Xcode. Device installation and App Store distribution require signing with an Apple Developer team and provisioning profile.

The project currently targets iOS 16.0 and supports iPhone and iPad. The iOS Simulator build is unsigned and is not an installable App Store IPA.
