# Skills for this project

Reference documents the agent loads before working on a subject. They describe how this codebase
is expected to behave: Kotlin Multiplatform + Compose (`composeApp/`) with a Swift/SwiftUI Apple
layer (`iosApp/`, `MPVKit/`), a Stremio-style addon pipeline, and an MPV player.

These files are a copy of what the app already has enabled, kept in the repository so that a fresh
clone carries the same guidance. Everything listed here, and the skills named further down, is also
loadable by name from the app itself.

## UI, Swift and architecture

| Skill | Why it is here |
|---|---|
| `swiftui-pro` | Reviews `iosApp/ContentView.swift`, `Player/*.swift`, `MPVKit/Sources/DesktopMPVBridge/` |
| `swiftui-animation` | Player overlays, transitions, reduce-motion |
| `swiftui-layout-components` | Lists, grids, forms on the Apple side |
| `swiftui-navigation` | Stacks, sheets, tab bridges between Compose and SwiftUI |
| `swiftui-uikit-interop` | `UIViewRepresentable`/`UIHostingController` around MPV and Metal layers |
| `swift-concurrency` | `async/await`, actors, `@MainActor` in the player bridges |
| `swiftui-iphone-duo` | Adaptive layout for changing window sizes |
| `anti-ui-slop` | UI craft audit (works for Compose screens too) |
| `find-skills` | Looking up further skills |

## Sources, playback and performance

| Skill | Why it is here |
|---|---|
| `source-plugin-resilience` | Parallel addon fan-out, per-source timeout, circuit after repeated failures |
| `source-racing-speed` | Probing mirrors, first playable link, provider ranking by measured latency |
| `plugin-sandbox-validation` | Lenient manifest decoding, URL scheme checks, one bad addon stays contained |
| `ktor-client-performance` | One shared Ktor client, Darwin engine, timeouts, streamed bodies |
| `kmp-ktor` | Client lifetime, origin guard, bearer refresh, retries, WebSocket/SSE |
| `stream-cache-expiry` | Signed stream URLs, refresh before a 403, cleanup on launch |
| `addon-manifest-engine` | Manifest install/validation and stream extraction (loads from the app) |
| `stream-fetching-parser` | Header injection, M3U/M3U8 parsing, local caching (loads from the app) |
| `video-instant-startup` | Asset pre-warming, preroll, headers at asset creation, stalling policy |
| `avfoundation-hls-pip` | HLS bitrate tiers, PiP lifecycle, stall recovery with backoff |
| `libmpv-ffmpeg-ios` | libmpv teardown order, hardware decoding, cache caps, custom PiP |
| `subtitle-sync-parsing` | Windows-1256 Arabic fallback, cues parsed off main, binary-search lookup |
| `ios-ui-fluidity` | No parsing on main, downsampled posters, debounced and cancelled search |
| `swiftui-scroll-fluency` | Lazy containers, stable identity, equatable cells, bounded caches |
| `instant-search-fluency` | Local normalized index, Arabic letter folding, stale-result cancellation |
| `swiftdata` | Model hierarchies: register the base class, deep and type-filtered queries |
| `swiftdata/inheritance` | `@Model` on every subclass, `super.init()`, one level deep |
| `swiftdata-migration` | One-time idempotent migration out of `UserDefaults`, unique media ids |
| `ios-app-intents` | Siri/Shortcuts entry points over the watchlist and history |

## Enabled in the app, not vendored here

These carry large `references/` trees (several thousand lines) whose copies would drift from the
versioned originals, so they stay where the agent loads them by name: `app-intents`, `avkit`,
`kmp-network-layer`, `swiftui-performance-audit`, `swiftui-view-refactor`, `xcode-project-setup`,
`apple-ios-hig-designer`, `apple-ondevice-ai-integration`, `ios-appstore-release-qa`,
`swift-swiftui-ios-engineer`, `tmdb-metadata-catalog`, `watchlist-trakt-sync`.


Not installed at all, because they target a different stack: `expo-*`,
`vercel-react-native-skills` (React Native/Expo), `ai-sdk` (JS), `build-iphone-apps`,
`iphone-duo-*`, `figma-swiftui`, `sleek-design-mobile-apps`/`design-mobile-apps`.
