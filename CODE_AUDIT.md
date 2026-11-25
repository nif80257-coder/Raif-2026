# Řaif Code Audit

Generated for the working tree at commit `37e5d66` (branch `rebuild`). Scope: **1,269 Kotlin files / 228,734 lines** (`composeApp/src`, KMP + Compose Multiplatform) and **14 Swift files / 4,690 lines** (`iosApp`, SwiftUI/UIKit shell + MPVKit bridge), plus `docs/watch-party-backend.sql`, `web/party/index.html`, `iosApp/**/Info.plist` and the Gradle/Xcode configuration. **271 Kotlin test files** exist and were only sampled.

This was a **targeted** audit, not a line-by-line read of 228k lines: the Kotlin compiler's warning set was captured from a full rebuild, the 15 largest files were reviewed, and ~30 files were opened to verify each Critical/High claim. `MPVKit/` is a third-party submodule and is treated as a black box. **No code changes were made** — this report is the input for the plans in §13 and §14.

Findings cite `path:LINE` so they can be opened straight from Xcode or the Files view.

---

## 1. Executive summary

1. **[High] An add-on is installed from a link with no confirmation** — §6.1 — `composeApp/src/commonMain/kotlin/com/nuvio/app/MainAppContent.kt:745-763`. Any `stremio://…` / `https://…` link adds a source that can return arbitrary streams and adult catalogues; the app registers the `stremio` scheme, so any web page or message can trigger it.
2. **[High] `runBlocking` on the UI path in 20+ publishers** — §3.1 — `…/home/HomeCatalogSettingsRepository.kt:669`, `…/collection/CollectionRepository.kt:156,172,324`, `…/player` equivalents. Localized strings are fetched synchronously while state is published, which freezes the frame that triggered the publish.
3. **[High] Unsynchronized `mutableMapOf` state shared between main and `Dispatchers.Default`** — §3.2 — `…/streams/StreamDisplayPreferences.kt:13-40` read inside the load coroutine at `…/streams/StreamsRepository.kt:773` and `…/player/PlayerStreamsRepository.kt:673`. Kotlin/Native has no thread-safe plain maps; concurrent read/write is a crash waiting for a bad interleaving.
4. **[High] Two stream repositories that duplicate ~450 lines each** — §9.1 — `…/streams/StreamsRepository.kt` (896) and `…/player/PlayerStreamsRepository.kt` (745). Every list rule (cache, timeout, badge, presentation, trim) is implemented twice and drifts: the new “one link per quality and audio” rule had to be wired into both.
5. **[High] Default sources carry other people's personal configs and ids** — §6.3 — `…/addons/DefaultAddonSources.kt:118,119,127,128,129,131,141,143,144,145`. Encrypted AIOStreams configs, `profile=` ids, `user/` ids: they can be revoked or blocked by their owners, and the traffic is attributed to them — while `README.md:20` states “none needs a debrid account or carries someone else's key”.
6. **[High] `PlayerSettingsStorage` is a ~200-function interface behind a 1,424-line iOS object** — §9.4 — `…/player/PlayerSettingsStorage.kt:1-213`, `…/player/PlayerSettingsStorage.ios.kt` (1,424), `…/player/PlayerSettingsRepository.kt` (1,411). Adding one switch touched six files; every new setting multiplies that.
7. **[Medium/High] The search field no longer searches what the list hid** — §5.1 — `…/player/PlayerStreamList.kt:64-68`. The panel now searches the shortened list, so a link the switch cut cannot be found by typing its name — the exact link the viewer is looking for (a dub, a language).
8. **[Medium] The active profile is a plain variable read during composition** — §3.4 — `…/profiles/ProfileRepository.kt:87` read at `…/streams/StreamModels.kt:394`. Switching profile does not recompose the list, and background readers race with the switch.
9. **[Medium] Secrets are split between Keychain and `NSUserDefaults`** — §6.4 — `…/tmdb/TmdbSettingsStorage.ios.kt:51-54`, `…/mdblist/MdbListSettingsStorage.ios.kt:46-49`, `…/player/PlayerSettingsStorage.ios.kt:1009` versus the Keychain code already present at `…/mdblist/MdbListAuthPersistence.ios.kt:21`.
10. **[Medium] 20 of the 24 advertised localizations are partial** — §8.1 — `iosApp/iosApp/Info.plist:15-43` versus `values-he/strings.xml` (776 keys of 2,877). Those locales show a mixed English UI while the system language picker advertises them as translated.

---

## 2. Quick wins (≤30 min each)

- **Delete the dead class** `PlayerInstalledStreamAddonTarget` — `…/player/PlayerStreamsRepository.kt:718-728`. It duplicates `StreamFetchSupport.kt:88` and is referenced nowhere. §9.2.
- **Add `-Xexpect-actual-classes`** to the Kotlin compiler options — the rebuild emits that Beta warning for ~80 `expect/actual` objects in `composeApp/src/iosMain`. §4.1.
- **Log the access gate code as a build flag, not a literal** — `…/auth/AuthScreen.kt:1655`. §6.6.
- **Stop logging stream URLs** — `…/streams/StreamsRepository.kt:582` (`"Fetching streams from: $url"`) and `…/details/MetaDetailsRepository.kt:293`. A debrid or signed link in the device console is a credential in a log. §6.7.
- **Name the timeout constants once** — `STREAM_SOURCE_TIMEOUT_MS`, `MIN_STREAM_SOURCE_TIMEOUT_MS`, `WARM_BYTES` exist, but `3_000`, `6`, `64 * 1024` are repeated in `…/streams/StreamFetchSupport.kt:55-58` and `…/streams/StreamLinkWarmer.kt:20`. §9.8.
- **Fix the README sentence** — `README.md:20` claims the defaults carry no one else's key; the sources at `DefaultAddonSources.kt:127-145` do. §9.7.
- **Use the window's screen instead of `UIScreen.main`** — `iosApp/iosApp/Player/MPVPlayerBridge.swift:362,502`. §4.2.
- **Replace `UIDevice.current.orientation`** with the window scene's geometry — `iosApp/iosApp/OrientationLockCoordinator.swift:110,124`. §4.3.

---

## 3. Concurrency

### 3.1 `runBlocking` on state-publish paths (localized strings)
- **Location:** `…/home/HomeCatalogSettingsRepository.kt:669`; `…/home/HomeCatalogDefinitions.kt:75,83`; `…/collection/FolderDetailRepository.kt:139,168,234`; `…/collection/CollectionRepository.kt:156,172,324`; `…/debrid/DirectDebridResolver.kt:195-198`; `…/streams/StreamFetchSupport.kt:431`; `…/trakt/TraktLibraryRepository.kt:814`; `…/trakt/TraktAuthRepository.kt:542`; `…/trakt/TraktCommentsRepository.kt:231`; `…/trakt/TraktPublicListSourceResolver.kt:257,316`; `…/mdblist/MdbListMessages.kt:32`; `…/addons/AddonManifestParser.kt:167`; `…/addons/AddonRepository.kt:909`; `…/details/RuntimeFormat.kt:26`; `…/details/MetaDetailsParser.kt:313,316,340`.
- **What:** `runBlocking { getString(…) }` turns a suspend resource lookup into a blocking call wherever a label must be built outside a coroutine.
- **Why:** each of these runs while state is being published — on the main thread for the settings/collection paths — so opening a folder or finishing a load freezes the frame until the resource dispatcher answers. On iOS the resource dispatcher itself needs the main queue for some paths, which turns the freeze into a deadlock risk.
- **Action:** route every label through the existing `…/core/i18n/LocalizedUiText.kt` snapshot cache (it was built for exactly this), or fetch in a suspend step before publishing and publish the resolved label. No `runBlocking` in `commonMain`.
- **Severity:** High

### 3.2 Shared `mutableMapOf` caches read from `Dispatchers.Default`
- **Location:** `…/streams/StreamDisplayPreferences.kt:13-47` (`cache`, `hideJunkCache`, `oneLinkPerQualityCache`); read at `…/streams/StreamsRepository.kt:773-774` and `…/player/PlayerStreamsRepository.kt:673-674` (inside `val job = scope.launch { … }` on `Dispatchers.Default`), written from the settings UI on main at `…/settings/StreamsSettingsPage.kt:196-236`.
- **What:** a plain `MutableMap` cache is mutated from the main thread and read from a background coroutine during a load.
- **Why:** Kotlin/Native offers no thread-safe `mutableMapOf`; concurrent access is illegal and can corrupt the map or abort the app — and here it is on the hot path every time a source list loads. 128 `mutableMapOf` sites exist across 233 singleton objects, and this is the verified instance of that shape.
- **Action:** publish an immutable snapshot (an `AtomicReference`-like holder or `MutableStateFlow`) and read only the snapshot from coroutines; or confine every display preference to the main thread and pass values into the load as parameters — the load already reads them once (`measuredSpeeds`, `streamBadgeRules`), so pass these the same way.
- **Severity:** High

### 3.3 `StreamLinkWarmer` keeps cross-thread mutable state
- **Location:** `…/streams/StreamLinkWarmer.kt:83-110` (`requestedKey`, `job`); invoked from `…/player/PlayerStreamsRepository.kt:424` and `…/streams/StreamsRepository.kt:449` inside the load coroutine, cancelled from `…/player/PlayerStreamsRepository.kt:134-138` and `…/streams/StreamsRepository.kt:857` on main.
- **What:** a plain `var` is written from `Dispatchers.Default` and read/cleared from the main thread.
- **Why:** a cancel can be lost when the two interleave, which leaves a connection open after the sheet closed (the warm-up's whole contract is that it is dropped by the tap), and it is the same unsynchronized-state class as §3.2.
- **Action:** keep the warm job and its key inside one `CoroutineScope`-confined holder guarded by the same lock, or make the key a `MutableStateFlow` and cancel by collecting it on a single dispatcher.
- **Severity:** Medium

### 3.4 `ProfileRepository.activeProfileId` is a plain field read during composition
- **Location:** `…/profiles/ProfileRepository.kt:87` (`val activeProfileId: Int get() = activeProfileIndex`), read at `…/streams/StreamModels.kt:394` from a data-class getter used by `…/streams/StreamsScreen.kt:721` and `…/player/PlayerStreamList.kt:64-68`.
- **What:** the display rule reads the active profile through a non-observable field inside composition.
- **Why:** switching profile does not invalidate the list, so the previous profile’s “one link per quality and audio” choice stays applied until something else recomposes; background readers see the value without any ordering guarantee.
- **Action:** expose the active profile as observable state (`StateFlow<Int>`) and read it with `collectAsStateWithLifecycle` at the screen, then pass the resolved rule into the list model instead of reading a global inside a getter.
- **Severity:** Medium

---

## 4. API modernity

### 4.1 `expect`/`actual` classes are still Beta — ~80 warnings per build
- **Location:** every `composeApp/src/iosMain/**/*.ios.kt` `actual object` (e.g. `…/player/PlayerSettingsStorage.ios.kt:1`, `…/streams/StreamLinkCacheStorage.ios.kt:6-8`). A full rebuild reports the `KT-61573` Beta notice once per file.
- **What:** the compiler warns that `expect`/`actual` classes are in Beta and will change.
- **Why:** the warning noise hides real warnings in CI logs and blocks “zero warnings” hygiene; a future Kotlin may change the rules.
- **Action:** add `-Xexpect-actual-classes` to the Kotlin compiler options in `composeApp/build.gradle.kts`, and long-term replace the `expect object` storage singletons with an `interface` + platform factory (which also fixes §9.4).
- **Severity:** Medium

### 4.2 `UIScreen.main` is deprecated (iOS 26 multi-window)
- **Location:** `iosApp/iosApp/Player/MPVPlayerBridge.swift:362`, `:502`.
- **What:** `UIScreen.main.nativeScale` is used as a fallback for the layer scale.
- **Why:** `UIScreen.main` is deprecated and wrong on iPad/Split View and on the foldable iPhone; it also ties the bridge to a single screen.
- **Action:** read the scale from the view's own window scene (`view.window?.windowScene?.screen`) or from `view.traitCollection.displayScale`, and fall back to the trait collection, never to a global screen.
- **Severity:** Medium

### 4.3 `UIDevice.current.orientation` for lock decisions
- **Location:** `iosApp/iosApp/OrientationLockCoordinator.swift:110`, `:124`.
- **What:** the coordinator branches on the device orientation rather than the window scene's geometry.
- **Why:** device orientation is deprecated for multi-window and reports the physical device even when the scene is rotated differently; the lock can be applied to the wrong orientation.
- **Action:** use `windowScene.effectiveGeometry` (or `interfaceOrientation`) and `requestGeometryUpdate` as the single source of truth; keep `UIDevice` only as a last-resort fallback.
- **Severity:** Medium

---

## 5. Bugs / logic errors

### 5.1 Search reads the shortened list, not the returned links
- **Location:** `…/player/PlayerStreamList.kt:64-68` (`allStreams` now comes from `visibleGroups`, then `filterPlayerStreams`), with the rule at `…/streams/StreamModels.kt:394-403`.
- **What:** when the optional “one link per quality and audio” switch is on, the search field filters the shortened list — a row the switch cut can never be found by typing its name.
- **Why:** search is the way to find a specific dub or resolution in a long list; hiding rows from search makes the switch a trap, and the empty result reads as “the source returned nothing”.
- **Action:** search over `groups` (everything the sources returned) and mark the matched rows, or turn the switch off while a query is active; the list’s own order stays the capped one.
- **Severity:** Medium

### 5.2 Administered deep links do work before navigating
- **Location:** `…/MainAppContent.kt:730-732` (`MetaDetailsRepository.fetch` inside the deep-link effect before `navController.navigate`).
- **What:** a meta deep link waits for a network fetch to get a title string, then navigates.
- **Why:** the tap that opened the link is followed by a blank screen for as long as the add-ons take; a slow source can make the link look broken.
- **Action:** navigate immediately with the id (the details screen already loads its own meta), and let the title arrive from state; never gate navigation on a fetch.
- **Severity:** Medium

### 5.3 Two implementations of the watch-party protocol
- **Location:** `web/party/index.html:407-1719` (Supabase JS, 6-char code, 4 s heartbeat, `watch-party:` + party id topic) versus `…/party/PartyRepository.kt` (841) and `…/party/PartySync.kt` (102).
- **What:** the browser client and the app implement the same channel, code alphabet, heartbeat and message shapes independently.
- **Why:** any protocol change (a field, a timeout, a topic prefix) must be made twice or the web guest silently stops matching the app; the two are already maintained in different languages with no shared specification beyond `docs/watch-party-backend.sql`.
- **Action:** make the SQL functions and a small written protocol note the single source of truth, and add one contract test that pins topic name, code alphabet and message fields for both clients.
- **Severity:** Medium

### 5.4 The access gate is a hardcoded client-side string
- **Location:** `…/auth/AuthScreen.kt:1655` (`private const val ACCESS_CODE = "305"`), with a `bypassAppGate` default at `…/App.kt:43-61`.
- **What:** the “gate” compares input to a constant compiled into the app.
- **Why:** it is not authentication — anyone with the binary can read it — and it is presented in the README as the app’s entry control; the only thing it protects is an accidental tap.
- **Action:** keep it as a child lock if that is the intent, but name it that in the UI and the README, move the constant to a build flag, and never describe it as access control.
- **Severity:** Medium

---

## 6. Security

### 6.1 An add-on is installed from a deep link without confirmation
- **Location:** `…/core/deeplink/AppUrlBridge.kt:85-90` and `:123` (any `stremio://` host becomes `AppDeepLink.AddonInstall`), consumed at `…/MainAppContent.kt:745-763` (`AddonRepository.addAddon(deepLink.manifestUrl)` directly), scheme registered at `iosApp/iosApp/Info.plist:55-57` (`nuvio`, `raif`, `stremio`).
- **What:** a link opened from Safari, Messages or another app installs a source with no sheet, no host shown and no confirmation.
- **Why:** the installed source then returns whatever streams it wants (including adult content and arbitrary hosts), it is indistinguishable from a source the user chose, and it is registered under a scheme other apps already use — so a `stremio://` link meant for Stremio can be taken by this app. This is the one path where a third party controls what the player loads.
- **Action:** open a confirmation sheet that shows the manifest host and name and requires a tap before `addAddon`, reuse the existing `AddonInstallModalState` flow in `…/addons/AddonsScreen.kt:116-269` for deep links too, and drop the `stremio` scheme from `Info.plist` unless interoperability is explicitly wanted.
- **Severity:** High

### 6.2 A real Supabase project URL and anon JWT are committed, with open Realtime policies
- **Location:** `web/party/index.html:411-412` (`SUPABASE_URL`, `ANON_KEY`), policies at `docs/watch-party-backend.sql:655-680` (`for select`/`for insert` to `anon` on `realtime.messages using (realtime.topic() like 'watch-party:%')`).
- **What:** the hosted project and its anonymous key are in the repository, and any anonymous client may subscribe to and publish on any `watch-party:%` topic.
- **Why:** anon keys are designed to be public, so the key itself is not the exposure — the policies are: they scope by topic prefix only, not by membership. A party id is a UUID (`web/party/index.html:704`, `:858`), which keeps enumeration impractical, but the database’s own functions check membership and Realtime does not — two authorization models for the same data.
- **Action:** keep the anon key public but add a `watch_party_member_can_read(topic)` check to the Realtime policy (or move guests to a signed-in session), document the key as public in `docs/`, and rotate the project if the key was ever treated as a secret.
- **Severity:** Medium

### 6.3 Default sources carry other people's configs, profile ids and user ids
- **Location:** `…/addons/DefaultAddonSources.kt:118` (`/c/rAEM3…`), `:119` (compressed collection config), `:121` (`subtito.com/user/83ba7951…`), `:127` (AIOStreams encrypted identity `{"i":…,"e":…}`), `:128` (`profile=65fef633…`), `:129`, `:131`, `:141`, `:143`/`:144` (`addon.peerflix.mov/enc_…`), `:145` (`streamx.electron.al/u_3fa8…`).
- **What:** the bundled sources point at personal configs, user ids and encrypted tokens belonging to someone else.
- **Why:** those owners can revoke or block the config at any time (the source disappears for every user of this build), the requests are attributed to their accounts and rate limits, and it contradicts `README.md:20` (“none needs a debrid account or carries someone else's key”). `:140` additionally ships `"allowAdult":true`, so one default source returns adult results while the app’s own adult filter is described as hiding them.
- **Action:** replace each personal URL with an owner-neutral endpoint (a public manifest or the repository’s own config), re-check every stream entry, and update `README.md` and `docs/raif-sources.json` to say exactly what remains; treat `allowAdult` as a user choice, not a shipped default.
- **Severity:** High

### 6.4 API keys live in `NSUserDefaults`, tokens live in the Keychain
- **Location:** `…/tmdb/TmdbSettingsStorage.ios.kt:51-54`, `…/mdblist/MdbListSettingsStorage.ios.kt:46-49`, `…/player/PlayerSettingsStorage.ios.kt:1009` (IntroDB key) against `…/mdblist/MdbListAuthPersistence.ios.kt:21`, `…/simkl/SimklPlatform.ios.kt:101`, `…/servers/ServerStorage.ios.kt:21`.
- **What:** two storage tiers exist for secrets: Keychain for OAuth/credentials, `NSUserDefaults` for API keys.
- **Why:** `NSUserDefaults` is written into the app container and into device backups in clear text; the same class of secret is protected in one path and not the other, and the Keychain code is already linked (`commoncrypto`, `keychain-test.entitlements`).
- **Action:** move every key/token to the existing Keychain helper behind one `SecretStore`, and keep only non-secret preferences in `NSUserDefaults`.
- **Severity:** Medium

### 6.5 `NSAllowsArbitraryLoads` is on app-wide
- **Location:** `iosApp/iosApp/Info.plist:68-72`.
- **What:** ATS is disabled for every host.
- **Why:** the player and the sources contact arbitrary hosts, so this is deliberate — but it is app-wide, it weakens TLS policy for the app’s own backends, and App Review usually asks for a justification.
- **Action:** keep the arbitrary loads for the player’s hosts but write the justification into the review notes, and scope the app’s own requests (updates, Supabase, Trakt) to HTTPS-only code paths.
- **Severity:** Medium

### 6.6 Stream URLs are written to the device log
- **Location:** `…/streams/StreamsRepository.kt:582` (`"Fetching streams from: $url"`), `…/details/MetaDetailsRepository.kt:293`, `…/simkl/SimklAuthRepository.kt:248` (error text), `…/trakt/TraktAuthRepository.kt:413` (transport error text).
- **What:** full request URLs and error bodies are logged.
- **Why:** debrid and signed stream links carry credentials in the query or path; a console capture or a support log then contains a working link.
- **Action:** log the host only (or a hash), and never the query; add a `redactUrl` helper and use it at every stream/add-on log site.
- **Severity:** Medium

---

## 7. Performance

### 7.1 Presentation is recomputed per group per answer, in two places
- **Location:** `…/streams/StreamsRepository.kt:420-429` (`presentStreamGroup` → badges → debrid) and `…/player/PlayerStreamsRepository.kt:395-404`, applied again on the final merge at `:667-680` and `:673-680`.
- **What:** every answer passes through badge and debrid presentation, then the whole list passes through the display merge.
- **Why:** 30 answers × (badges + debrid + sort + dedupe) run on the load coroutine for each open of the sheet; it is wasted work on a path that is already the slowest thing the user waits for.
- **Action:** present once, when the group is published, and make the final merge a pure display transform; or compute the presentation lazily in the state getter with a memo keyed by the group’s content.
- **Severity:** Medium

### 7.2 Every settings write serializes the full preference payload
- **Location:** `…/player/PlayerSettingsStorage.ios.kt:1228-1300` (`exportToSyncPayload` builds a `JsonObject` of ~100 keys), called from every `publish`/save path in `…/player/PlayerSettingsRepository.kt:1300-1411`.
- **What:** a single toggle rebuilds and stores the entire settings payload, then queues a sync upload.
- **Why:** the settings screen writes on every switch and slider step; the JSON build plus the sync outbox write is main-thread work for one boolean, and it grows with every setting added (§9.4).
- **Action:** store the changed key only (the storage layer already has per-key functions), and build the sync payload once when the screen closes or in a debounced background step.
- **Severity:** Medium

### 7.3 The SwiftUI shell is one 1,552-line view
- **Location:** `iosApp/iosApp/ContentView.swift:1-1552`, alongside `…/Player/NuvioControlsView.swift` (1,006) in `MPVKit/Sources/DesktopMPVBridge`.
- **What:** the app shell, tab chrome, quick actions and player host live in one view body.
- **Why:** the shell re-evaluates whenever any of its state changes, including during playback, and a single body of that size makes the cost invisible until measured.
- **Action:** split by tab and by overlay, give each row its own `Equatable` state, and measure with the SwiftUI Instruments lane before and after; treat this as a measured change, not a guess.
- **Severity:** Medium

---

## 8. SwiftUI / UI

### 8.1 Localization is advertised for 24 languages, shipped complete for a handful
- **Location:** `iosApp/iosApp/Info.plist:15-43` (`CFBundleLocalizations`, 24 entries) against `composeApp/src/commonMain/composeResources/values-he/strings.xml` (776 keys) versus `values/strings.xml` (2,877). English and Arabic are key-for-key complete (2,877 / 2,877, verified by key diff); the rest are partial.
- **What:** the system language list offers locales the app has only partly translated.
- **Why:** a user picking Hebrew, Japanese or Polish gets a mixed English/translated UI with no indication, which reads as a broken translation rather than a fallback.
- **Action:** either complete the locales (one batch per locale, the same key diff loop that finished Arabic) or drop the incomplete ones from `CFBundleLocalizations` and say in the README that English and Arabic are the supported languages.
- **Severity:** Medium

### 8.2 Player controls and the sources panel are oversized SwiftUI/Compose surfaces
- **Location:** `MPVKit/Sources/DesktopMPVBridge/NuvioControlsView.swift` (1,006), `…/player/PlayerControls.kt` (1,056), `…/source` panels in `…/streams/StreamsScreen.kt` (1,284).
- **What:** controls and lists are single composables/views with many inline sub-composables.
- **Why:** any tick or state change recomposes the whole surface; extracting the rows and giving them stable keys is the cheapest win when the controls feel heavy.
- **Action:** extract each row into its own composable with an `Equatable` state, and reuse the existing `DuplicateSafeLazyKeys` helper for list identity.
- **Severity:** Medium

### 8.3 Dynamic Type and dark mode on the SwiftUI side need a pass
- **Location:** `MPVKit/Sources/DesktopMPVBridge/NuvioControlsView.swift`, `…/NuvioSharedComponents.swift`, `…/NuvioPlayerDesignTokens.swift`.
- **What:** the player chrome is drawn with fixed sizes and colors in its own tokens.
- **Why:** the app is a video app first, and system text size and appearance changes are exactly where a custom chrome breaks; this was not measured, so it is listed as a pass to run, not a confirmed defect.
- **Action:** run the accessibility inspector over the player and the sources sheet at the largest Dynamic Type size and in both appearances, and map the design tokens onto `MaterialTheme`/`Font.TextStyle` where possible.
- **Severity:** Low

---

## 9. Dead code / duplication / refactor

### 9.1 Two stream repositories, ~450 duplicated lines each
- **Locations:** `…/streams/StreamsRepository.kt` (896) and `…/player/PlayerStreamsRepository.kt` (745); the duplicated blocks are the load preamble (`:199-367` / `:199-367`), the per-answer presentation (`:395-405` / `:406-429`), the plugin merge (`:567-661` / `:612-662`) and the final display merge (`:664-705` / `:664-680`).
- **Action:** extract one `StreamListEngine` that owns the fetch, cache, timeouts and per-group presentation, and let each repository own only its state and its cache key; the player variant then differs by a target-state parameter, not by a copied file.
- **Severity:** High

### 9.2 Dead class duplicating the installed-add-on target
- **Location:** `…/player/PlayerStreamsRepository.kt:718-728` (`private data class PlayerInstalledStreamAddonTarget`), duplicate of `…/streams/StreamFetchSupport.kt:88-94`; referenced nowhere.
- **Action:** delete it.
- **Severity:** Low

### 9.3 Oversized files (over 1,000 lines)
- **Locations and sizes:** `…/details/MetaDetailsScreen.kt` (2,677), `…/collection/CollectionEditorScreen.kt` (2,610), `…/autosync/AutoSyncTimelineRetime.kt` (2,373), `…/tmdb/TmdbMetadataService.kt` (2,252), `…/MainAppContent.kt` (2,198), `…/home/HomeScreen.kt` (2,171), `…/watchprogress/WatchProgressRepository.kt` (1,996), `…/addons/AddonsScreen.kt` (1,790), `…/settings/DebridSettingsPage.kt` (1,714), `…/trakt/TraktProgressRepository.kt` (1,705), `…/auth/AuthScreen.kt` (1,655), `iosApp/iosApp/ContentView.swift` (1,552), `MPVKit/…/MPVPlayerBridge.swift` (1,514), `…/player/PlayerSettingsStorage.ios.kt` (1,424), `…/player/PlayerSettingsRepository.kt` (1,411).
- **Action:** split by responsibility (screen → sections, repository → store + policy + writer), one file per batch, with the tests as the safety net; do not rewrite them for style alone.
- **Severity:** Medium

### 9.4 Settings plumbing is per-key, ~200 functions deep
- **Locations:** `…/player/PlayerSettingsStorage.kt:1-213` (the interface), `…/player/PlayerSettingsStorage.ios.kt` (1,424: key constant + load/save + sync encode + sync decode for each setting), `…/player/PlayerSettingsRepository.kt` (1,411: default + field + reset + load + setter + publish for each setting), plus `…/settings/PlaybackStreamSettings.kt` and `…/settings/SettingsSearch.kt` per toggle.
- **What:** one preference is six edits in five files.
- **Why:** every new setting risks a missed decode or a wrong default (only a test catches it), and the file sizes above are the result.
- **Action:** one `PlayerSettings` data class (the UI state already is one) plus one storage blob per profile, a generated key list for sync, and a single `set(update: (PlayerSettings) -> PlayerSettings)` entry point; the switch rows then call `set { it.copy(streamWarmTopLinkEnabled = value) }`.
- **Severity:** High

### 9.5 Duplicated localized-string helper
- **Locations:** `…/trakt/TraktLibraryRepository.kt:814` and `…/trakt/TraktAuthRepository.kt:542` (the same `private fun localizedString(resource) = runBlocking { getString(resource) }`).
- **Action:** one helper in `LocalizedUiText` (see §3.1), deleted from both.
- **Severity:** Low

### 9.6 The watch-party protocol is implemented twice
- **Locations:** `web/party/index.html` (1,719) and `…/party/PartyRepository.kt` (841) with `…/party/PartySync.kt` (102); see §5.3.
- **Action:** one written protocol note plus a contract test; keep the browser client thin.
- **Severity:** Medium

### 9.7 Documentation drift in the README
- **Location:** `README.md:20-22` (“none needs a debrid account or carries someone else's key”, “the anime scrapers … are dropped”).
- **What:** the source inventory no longer matches the code (see §6.3), and the provider-policy sentence should be re-checked against `…/plugins/ProviderScraperPolicy.kt`.
- **Action:** regenerate the sentence from `docs/raif-sources.json` in the same commit that changes the sources, or move the count into that JSON and link to it.
- **Severity:** Medium

### 9.8 Repeated timing constants
- **Locations:** `…/streams/StreamFetchSupport.kt:35,43,55-58` (`15_000`, `6_000`, `average * 6L + 3_000`) and `…/streams/StreamLinkWarmer.kt:20` (`64 * 1024`); the connection timeouts are repeated across `…/addons/AddonPlatform.ios.kt:134-137` and `…/streams/StreamLinkWarmPlatform.ios.kt:13-21`.
- **Action:** one `StreamTiming` object with the budgets and their reasons, referenced by every site.
- **Severity:** Low

---

## 10. Cross-cutting recommendations

1. **One stream-list engine, two presentations.** §9.1 is not a file-size problem, it is a design problem: the app has one behaviour and two implementations. Every fix in this audit that touches stream lists has to be made twice until the engine is extracted — do that first, before any further list feature.
2. **No blocking on the UI path, ever.** §3.1 and §7.2 share one root cause: a synchronous bridge (`runBlocking`, a full-payload write) where an asynchronous value would do. The repository already has the right tool (`LocalizedUiText`, per-key storage); the work is applying it, and a lint rule that rejects `runBlocking` in `commonMain` keeps it applied.
3. **Decide the threading model and write it down.** §3.2, §3.3 and §3.4 are three instances of one question: which state may be touched from which thread. Either confine state to the main thread and pass immutable snapshots into the background loads (the loads already take `measuredSpeeds` and `badgeRules` this way), or make every shared cache an explicit atomic/flow. Pick one and add it to `CONTRIBUTING.md`.
4. **Make the settings layer a data model, not a list of keys.** §9.4 blocks every settings feature the roadmap wants. One data class, one blob, one setter, one sync snapshot.
5. **Treat the bundled sources and their credentials as a release surface.** §6.3, §9.7 and the README sentence are the same problem seen from three sides: the source inventory is a shipped asset with owners, secrets and expiry. Re-verify it on a schedule and generate the documentation from it.

---

## 11. What was NOT audited

- **Line-by-line review of all 228,734 Kotlin lines.** The audit used compiler ground truth (a full `--rerun-tasks` rebuild), the largest files, and targeted pattern searches; files outside those paths were not read.
- **Algorithmic correctness** of `AutoSyncTimelineRetime.kt` (2,373), `WatchProgressRules.kt` (621), the subtitle retiming and the Simkl/Trakt reconciliation logic.
- **`MPVKit/`** — third-party submodule, treated as a black box (its Swift bridge in `iosApp` was reviewed, its C libraries were not).
- **Third-party dependency internals** — Ktor, Compose Multiplatform, Supabase JS, MPVKit binaries.
- **`../CINEMAX`** — another project's folder, out of scope.
- **Test quality and coverage** — 271 test files were counted and sampled, not judged for whether they would catch these bugs; no coverage run was made.
- **CI reliability** — GitHub Actions workflow definitions were not reviewed; earlier runs on this project were cancelled for capacity, which is recorded here as context, not as a finding.
- **Localization wording** — Arabic and English are key-complete (2,877 each), but no native-speaker review was done; the other 22 locales were measured, not read.
- **App Store review posture** — §6.5 (ATS) and §8.1 (partial localizations) are flagged as review risks, but no review simulation was run.
- **Measurement** — no Instruments trace, no first-frame timing, no memory graph was taken in this audit; every performance finding is a code-level observation that needs measurement before it is called a regression.

---

## 12. Verification

For each Critical/High item, the lines that prove the claim:

- **§3.1** — open `…/home/HomeCatalogSettingsRepository.kt`, line `669`: `subtitle = runBlocking { getString(…) }` inside the state publish; open `…/collection/CollectionRepository.kt:324` for the same call in a non-suspend path.
- **§3.2** — open `…/streams/StreamDisplayPreferences.kt`, lines `13-47` (plain `mutableMapOf` caches), then `…/streams/StreamsRepository.kt:773-774`: the same caches are read inside `scope.launch` (`:369`).
- **§3.3** — open `…/streams/StreamLinkWarmer.kt`, lines `83-110`: `private var requestedKey` written from the launch at `…/player/PlayerStreamsRepository.kt:424` and cleared from `:134-138`.
- **§3.4** — open `…/profiles/ProfileRepository.kt:87` and `…/streams/StreamModels.kt:394`: a non-observable field read inside a getter used by composition (`…/streams/StreamsScreen.kt:721`).
- **§5.1** — open `…/player/PlayerStreamList.kt:64-68`: `allStreams` comes from `visibleGroups` before `filterPlayerStreams`.
- **§6.1** — open `…/MainAppContent.kt:745-763`: `AddonRepository.addAddon(deepLink.manifestUrl)` runs in the effect with no sheet; `…/core/deeplink/AppUrlBridge.kt:85-90` shows the scheme mapping; `iosApp/iosApp/Info.plist:55-57` registers `stremio`.
- **§6.2** — open `web/party/index.html:411-412` and `docs/watch-party-backend.sql:655-680`: the Realtime policies are `to anon` scoped by topic prefix only.
- **§6.3** — open `…/addons/DefaultAddonSources.kt`, lines `127`, `128`, `143` and decode their path segments: each carries a config, a `profile=` id or an encrypted token that belongs to one person.
- **§7.2** — open `…/player/PlayerSettingsStorage.ios.kt:1228-1300`: `exportToSyncPayload` rebuilds the full key set; trace one call from `PlayerSettingsRepository.kt:1300-1411`.
- **§8.1** — compare `iosApp/iosApp/Info.plist:15-43` with `values-he/strings.xml` (776 keys) and `values/strings.xml` (2,877): the advertised list is longer than the translated set.
- **§9.1** — open both repositories at the cited blocks and compare them side by side; the per-answer presentation and the final merge are line-for-line equivalents.
- **§9.4** — open `…/player/PlayerSettingsStorage.kt:1-213`: each setting has `load` + `save`, and the iOS file repeats each one four times.

If any item does not reproduce when opened at the cited lines, it should be demoted or dropped rather than carried forward.

---

<!-- status: P0 + P1 done in d245c23. P2 partly done in a7f1a48 (14 of 20 runBlocking sites moved to LocalizedUiText; compiler warnings triaged). Remaining: 6 runBlocking sites, engine, settings model, SQL, locales. -->

## 13. خطة الإصلاح (مرتبة، بأربع دفعات)

> **الحالة — `d245c23` ثم `a7f1a48`**، وكلاهما تحقّق بالكومبايل وبناء `iosApp`.
>
> **تم**: الكلاس الميت، `-Xexpect-actual-classes` (أظهر 6 تعبيرات ميتة و~12 استدعاءً مهجورًا كان التحذير يخفيها)، تسجيل المضيف بدل الرابط، `UIScreen`/`UIDevice`، جملة README، ورقة تأكيد تثبيت المصدر من رابط، Keychain لمفاتيح TMDB/MDBList/IntroDB، حصر التجهيز في خيط واحد، مفاتيح العرض تُقرأ قبل الـ coroutine، البحث فوق كل الروابط، الرابط الوصفي لا ينتظر الشبكة، و**14 من 20** موضع `runBlocking` انتقلت إلى `LocalizedUiText`. ومعها: الأيقونات المرآتية، مؤشر التقدم بصيغة lambda، `PrimaryScrollableTabRow`، `kotlinx.io.IOException`، وحذف الأسطر الميتة.
>
> **تبقّى**: 6 مواضع `runBlocking` (المسارات المنسّقة `FolderDetailRepository:168,234` و`CollectionRepository:172,324` ومُساعدا Trakt)، ترحيل 7 مواضع `rememberModalBottomSheetState`، `BasicTextField` بنسخته الجديدة، محرّك قائمة واحد، نموذج الإعدادات كبيانات، وسياسات Realtime في SQL (قرار منتج).

### دفعة P0 — اليوم (لا تغيير في السلوك، مخاطر منخفضة)
1. حذف `PlayerInstalledStreamAddonTarget` من `PlayerStreamsRepository.kt:718-728` — §9.2.
2. `-Xexpect-actual-classes` في `composeApp/build.gradle.kts` — §4.1.
3. حذف تسجيل الروابط من السجلات: `StreamsRepository.kt:582`, `MetaDetailsRepository.kt:293` — §6.6.
4. `UIScreen.main` و`UIDevice.current.orientation` — §4.2، §4.3.
5. تصحيح جملة `README.md:20` أو تحديثها من `docs/raif-sources.json` — §9.7.
**التحقق:** `compileKotlinIosSimulatorArm64` + `compileTestKotlinIosSimulatorArm64` + `xcode_build`، ثم مراجعة الـ diff.

### دفعة P1 — هذا الأسبوع (أمان وسلوك)
1. **تأكيد تثبيت الإضافة من رابط** عبر ورقة تُظهر المضيف والاسم، وإسقاط `stremio` من `Info.plist` أو توثيق سبب إبقائه — §6.1.
2. **إخفاء المفاتيح المضمّنة** في `DefaultAddonSources.kt` واستبدالها بروابط عامة، مع إعادة التحقق من كل مصدر بث، وتحديث `docs/raif-sources.json` — §6.3.
3. **نقل مفاتيح API إلى Keychain** خلف `SecretStore` واحد — §6.4.
4. **إصلاح البحث ليعمل على كل الروابط** لا على المقصوصة — §5.1.
5. **إزالة `runBlocking`** من المسارات العشرة الأخطر (home/collection/player) بالتوجيه إلى `LocalizedUiText` — §3.1 (الجزء الأول).
**التحقق:** اختبارات جديدة (البحث مع المفتاح مفعّل/مغلق، تأكيد التثبيت، قراءة الإعدادات من Keychain)، وبناء، ثم تجربة على الجهاز.

### دفعة P2 — الأسبوعان القادمان (الهيكل)
1. **`StreamListEngine` واحد** يحل مكان `StreamsRepository` + `PlayerStreamsRepository` — §9.1. هذا هو أكبر تغيير، ويُنفَّذ خلف نفس السلوك مع اختبارات القائمة الحالية كشبكة أمان.
2. **نموذج الإعدادات كبيانات**: `PlayerSettings` + blob واحد + `set {}` واحد، وإزالة ~600 سطر من `PlayerSettingsStorage`/`.ios`/`Repository` — §9.4.
3. **حدود الخيوط**: تمرير snapshots إلى الـ load بدل قراءة الخرائط المشتركة، وإزالة الحالة المشتركة في `StreamLinkWarmer` — §3.2، §3.3.
4. **الملف الشخصي كحالة قابلة للمراقبة** — §3.4.
**التحقق:** نفس المجموعة الوظيفية + قياس زمن فتح القائمة قبل/بعد، والتأكد من أن كل اختبارات `commonTest` تمر (لا تُشغَّل على هذا الجهاز؛ تُشغَّل في CI).

### دفعة P3 — تحسين مستمر (نظافة)
1. تقسيم الملفات العملاقة واحدًا في كل مرة (ابدأ بـ `MainAppContent` ثم `HomeScreen` ثم `MetaDetailsScreen`) — §9.3.
2. `StreamTiming` للثوابت — §9.8.
3. عقد بروتوكول watch-party + اختبار — §5.3، §9.6.
4. تقرير ما لم يُدقَّق (§11) يُعاد مع كل جولة، حتى لا تُنسى البنود بلا قياس.

---

## 14. الخطة التطويرية

**المدى القريب (أسبوعان) — إكمال ما بدأته جولات السرعة والروابط:**
- **قائمة واحدة بكل الجودات والأصوات** مع فلترة اختيارية حسب الجودة واللغة (الصوت) بدل مفتاح ثنائي: شريحة «الجودة» وشريحة «الصوت» فوق القائمة، مبنية على `qualityRank` و`audioTrackKey` الموجودين.
- **عدّاد صحة المصادر** في الشاشة (زمن الاستجابة، نسبة الروابط الصالحة) — البيانات موجودة في `AddonSpeedMemory` و`SourceHealthReport`، والناقص عرضها.
- **نقل التجهيز المسبق إلى الجهاز وقياسه**: أول إطار بعد اللمسة، وقياس أثر التجهيز عليه، قبل أي توسّع في السلوك.

**المدى المتوسط (شهر) — موثوقية:**
- **CI موثوق**: الحزم الملغاة بالكامل تُدار بـ concurrency limits أو runners خاصة، واختبارات `commonTest` تعمل على كل دفعة — اليوم لا تُعرف نتيجة الاختبارات إلا من CI.
- **تغطية الجودة**: ملفات الاختبار 271، لكن لا تغطية معروفة لـ `StreamListEngine` قبل استخراجه؛ أضف اختبارات عقد للـ engine أولًا.
- **تدويل مكتمل**: إكمال 2–3 لغات كاملة على طريقة العربية (مقارنة المفاتيح، نفس الحجم)، أو إسقاط غير المكتمل من `CFBundleLocalizations`.

**المدى الطويل (ربع سنة) — المنتج:**
- **إعدادات كطبقة بيانات** تفتح الباب لمزامنة الإعدادات بين الأجهزة (المزامنة موجودة بالفعل عبر `ProfileSettingsSync`).
- **سطح واجهة SwiftUI أصغر**: تقسيم الـ shell + قياسات Instruments قبل/بعد، وربط رموز التصميم بـ Dynamic Type.
- **watch party كمنتج**: عقد واحد بين الويب والتطبيق، صلاحيات قراءة/كتابة مشتقة من العضوية لا من بادئة الموضوع، واختبارات عقد.
- **صحة المصادر كميزة**: مصدر يتوقف → يُعلَّم ويُستبدل، بدل أن يبدو «لا نتائج» للمشاهد.

**مؤشرات القياس (بدونها تبقى الخطة رأيًا):**
- زمن من فتح قائمة المصادر إلى أول صف، ومن اللمسة إلى أول إطار — تُقاس على جهاز واحد وفي نفس الشبكة قبل/بعد كل دفعة.
- عدد `runBlocking` في `commonMain` (89 اليوم) وعدد مواقع `mutableMapOf` المشتركة (128) — يجب أن ينخفضا مع كل دفعة.
- نسبة الاختبارات التي تمر في CI، وحجم الملفات العملاقة (15 اليوم) — يجب أن ينخفضا تدريجيًا.
