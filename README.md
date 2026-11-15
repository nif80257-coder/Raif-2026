# CINEMAX

CINEMAX is an early-stage, Arabic-first iPhone streaming client built with Kotlin Multiplatform and Compose Multiplatform. The Android target has been removed: the app targets iOS only.

## Implemented in the current repository
- The episodes heading counts what is finished ("الحلقات • 3/10 مشاهدة"), the library's sort order and the search tab's type filter are remembered between launches, and the continue-watching card shows both the time left and the watched percentage.
- The archive detail screen shows the film's running time ("1 س 32 د") read from the item's `length`, and the continue-watching card shows the percentage watched next to the time left.
- While the grid's search field is active, the closest names come first (exact, then prefix, then containment) instead of the API's order.
- The catalog grid has a local search field that filters the loaded list by name with the same Arabic folding as the rest of the app, and its counter shows "12 من 60" while filtering.
- The player offers a "التالي ‹" shortcut whenever a next episode exists, not only in the last 45 seconds, and the top bar shows the source that is playing ("Addon • 1080p").
- The library grid's genre and sort choice is remembered between launches (`cinemax.browse.genre` / `cinemax.browse.sort`), and opening the shelf restores it, fetching page one again so the chips and the posters always agree.
- Subtitle size (صغير / وسط / كبير) is now reachable inside the subtitle sheet while watching, not only from settings.
- A long press on a saved poster in the library removes it from the list, with the same haptic and status line as the other surfaces.
- The player shows "جارٍ تجهيز المشاهدة…" with a small spinner until the first frame's position arrives, so a slow archive file no longer looks like a dead black screen.
- Archive sidecar subtitles are labelled with the language name ("الإنجليزية"، "العربية") instead of the raw file name, and fall back to the file name when the language is unknown.
- When the built-in library cannot be reached, home shows what went wrong and an "أعد المحاولة" button instead of an empty page.
- A long press on any poster — home shelves, the "المزيد" grid and search results — adds or removes that title from the saved list, with a haptic tap and a status line naming what happened ("أضيف إلى قائمتي: …").
- The archive grid also has sort chips (الأكثر تحميلًا، الأحدث إضافة، أبجديًا) mapped to the archive's sort fields; changing the order restarts the grid from page one and keeps the chosen genre.
- The built-in "المكتبة الحرة" shelf opens as a full grid like any other and pages itself through the archive's `page` parameter (60 films per page, duplicates dropped, "نهاية القائمة" when a page brings nothing new), so the whole 28k-film collection is reachable and not just the first shelf. The grid also has genre chips (أكشن، مغامرة، كوميدي، دراما، رعب، خيال علمي، غربي، جريمة، رومانسي، وثائقي، رسوم متحركة، صامت) mapped to the archive's own subjects — «غربي» returns 724 films — and the chosen genre is kept across pages.
- A built-in "المكتبة الحرة" shelf of public-domain films from the Internet Archive, so a fresh install has real, playable content before any addon is added: it lists ~28k archive.org feature films (60 of the most downloaded on the shelf), opens the item's metadata (title, year, synopsis, subjects) and plays the item's own direct HTTPS video file. Sidecar `.srt` / `.vtt` files that sit beside the video are attached as subtitles with their language read from the file name, so these older films come with the captions the archive already has. Search also looks through it (unless the user picked مسلسلات), and its results are labelled with the source. No key, no account and no torrent engine are involved, and the shelf stays first on home.
- Nuvio-style shell: a floating pill tab bar (الرئيسية / بحث / مكتبتي / الإعدادات), a full-screen detail screen with backdrop, seasons, episodes and sources, and a full-screen player instead of dialogs.
- Home feed with an auto-advancing hero pager (dots whose active dot is a bar), a continue-watching row of landscape cards with progress and time-left pills, and one poster shelf per catalog. A search tab, a library tab (in progress + saved) and a settings tab complete the four destinations.
- Full-screen player with the chrome the Nuvio player has: the overlay hides itself after four seconds and a tap brings it back, a seek bar with real positions, speed chips (×0.5 to ×2), a next-episode card in the last 45 seconds, and subtitle delay (±0.5 s) in the subtitle sheet.
- Native playback gestures on the video surface itself (Compose cannot receive touches over a UIKit-hosted view, so the mpv controller owns them): single tap toggles the overlay, double tap seeks ±10 s, a horizontal drag scrubs, and a vertical drag sets the volume (right half) or screen brightness (left half), each with a heads-up readout.
- Picture-in-picture on the AVPlayer path, haptic feedback on tab changes and saving a title, and a press-scale animation on posters.
- Source sheet ranked like Nuvio's: direct links first, then by resolution and format (4K, 1080p, HDR, HEVC), a "مباشر" filter that hides torrent-only entries, and a "آخر رابط" pill on the link that played last for that title, pinned to the top.
- Nuvio provider diagnostics: a provider that throws or times out is reported in the source sheet ("مصادر Nuvio تعذّرت") with its reason instead of failing silently, and installed provider repositories refresh at launch.
- Safe-area handling on every screen: the tab bar, the detail sheet header, the player chrome and the four tabs respect the notch and the home indicator instead of drawing under them.
- Choosing a season selects that season's first episode, so the source list matches what the user is looking at; the search tab explains that a catalog addon is needed instead of doing nothing; closing the player also closes the subtitle sheet.
- Home shows up to five shelves, one per catalog the active addon declares (movies, series, popular…), each loaded in parallel, instead of a single shelf.
- The library has أفلام / مسلسلات filters that apply to both saved titles and continue-watching, with a clear empty state per filter, and four sort orders for the saved grid: الأحدث مشاهدة, الأحدث إضافة, أبجديًا and الأعلى تقييمًا. Titles with no watch history keep their saved order after the watched ones, so the list does not reshuffle for no reason. A search field above them filters both the saved grid and continue-watching by name, locally and with the same Arabic spelling folding as network search.
- Home extras: the hero backdrop gets a slow Ken Burns zoom, a shimmer skeleton replaces the spinner while a catalog loads, and a "فيلم الليلة" card picks a title you have not started yet with a "بدّل" shuffle.
- Search has أفلام / مسلسلات type chips, so a series query searches series catalogs instead of the addon's first (usually movie) catalog. Typing searches on its own after a short pause (the button still works), and results are ranked by how closely the name matches — exact, then prefix, then containment — with the Arabic alef forms, alef maqsura, ta marbuta and harakat folded so «الاسد» finds «الأسد».
- The detail screen's main button becomes "▶ أكمل من 12:34" when a stored resume position exists for that title.
- Home has a ↻ refresh button that reloads the active addon's catalog and shelves without leaving the tab, search can look through movies and series at once ("الكل"), and settings shows how many titles are saved or in progress with buttons to clear either list.
- Every home shelf opens as a full poster grid ("المزيد"), and a catalog that declares the `skip` extra is paged from there: the next page is requested with `skip` counted from what is on screen, duplicates are dropped, and a page that brings nothing new ends the list with "نهاية القائمة".
- When the catalog declares a `genre` extra with options, the full grid shows genre chips; choosing one restarts the grid from page one, and later pages send the genre and the `skip` together in the single extra path segment (`genre=Action&skip=100`).
- Posters carry the user's own state: a thin accent bar across the bottom of the artwork for how far a title was watched (the most recently updated episode wins for a series) and a star for what is saved, on the home shelves, the catalog grid and the search results.
- Playback speed is remembered: changing it in the player (or on the next episode) keeps it for the next launch, and when a title ends with autoplay off the player closes back to the detail screen instead of holding the last frame.
- The detail screen fades in and out instead of appearing instantly, and its last opened title is kept during the exit so the animation never flashes empty content.
- Episode list shows a progress bar for each episode you have started and a ✓ once you finished it, and the settings sheet sizes addon subtitles (صغير / وسط / كبير). Each episode also shows its release date in Arabic ("5 يناير 2020") read from the addon's `released` field, and an episode dated after today reads "قريبًا • …" in the accent colour so an episode with no sources yet explains why; a value that is not a date shows nothing.
- Search tab keeps the last eight queries as one-tap chips with a "مسح السجل" action.
- Shared Arabic-first Compose interface with responsive media cards, remote poster artwork, and persistent appearance/playback preferences.
- CINEMAX visual identity with an iOS AppIcon generated from the shared SVG brand mark.
- Persistent user preferences for accent color, OLED black background, compact cards, resume preference, autoplay preference, and preferred subtitle/audio languages.
- iOS-only target: the Android application module, its sources and CI workflow were removed; the iOS Compose view controller is the only platform entry point.
- Stremio manifest, catalog, metadata and stream models.
- HTTPS manifest URL validation, preservation of addon configuration query tokens, and encoded Stremio resource paths.
- HTTP repository methods for manifests, catalogs (including declared search extras), metadata details, and streams.
- Add-on installation, switching/removal, persistent descriptors, catalog switching, search extras, and pagination when declared by the addon.
- Cross-addon search: one query searches every installed addon whose catalog declares a `search` extra, results deduplicated and labelled with the addon they came from.
- Season and episode browsing for series, with per-episode source requests (`tt1234:1:2` instead of the series id) and per-episode continue-watching entries.
- Automatic next-episode playback when the current episode ends and the preference is on.
- Source aggregation from every installed addon that declares the `stream` resource for the item's type (honouring `idPrefixes`), instead of only the active catalog addon; a failing addon does not hide the others' sources.
- Nuvio provider support: install a provider repository (`manifest.json` with `scrapers`), enable individual providers, and run their bundled CommonJS `getStreams(tmdbId, mediaType, season, episode)` scripts in JavaScriptCore on iOS.
- Nuvio results are merged with Stremio results in one source list, each labelled with its addon or provider.
- Optional TMDB API key that resolves IMDb ids to TMDB ids (cached locally) so Nuvio providers receive the ids they expect.
- Playback runs on libmpv (MPVKit) through a Swift bridge registered at launch, so MKV, AVI, HEVC and the other common containers play with hardware decoding; AVPlayer remains the fallback if the bridge is absent. Audio session, progress reporting, end-of-item detection and cleanup are shared by both.
- Source quality/format badges (4K, 1080p, HDR, HEVC, CAM, REMUX) read from the stream description, and an empty-source message that names how many addons and Nuvio providers were asked.
- Subtitle support: the player collects subtitles from the `subtitles` resource of every addon and from the stream's own `subtitles` list, downloads the chosen file, parses SRT/WebVTT and renders it over the video, auto-selecting the preferred language. The same picker lists the subtitle tracks embedded in the video file itself (internal MKV/MP4 tracks), which libmpv renders with their own styling; an embedded track matching the preferred language is selected when no addon subtitle was found, and choosing one of the two kinds replaces the other.
- Streams that declare `behaviorHints.proxyHeaders` play with those HTTP headers, and a source the fallback AVPlayer cannot open (MKV, AVI, …) says so instead of showing a silent black screen; playback errors are shown in the player.
- Subtitle and audio language preferences are applied to the playable in-stream tracks when the stream exposes them.
- Stored addon descriptors are refreshed from their manifests at launch, so addons installed earlier gain resource/type/id-prefix and catalog data.
- Shared protocol tests and iOS GitHub Actions workflows for the app build and the shared unit tests.
- An in-app roadmap screen (Settings → خطة التطوير) that renders the phased plan for reaching Nuvio-level polish; the long-form version is `docs/ROADMAP_NUVIO.md`.

## Known limitations
- Nuvio providers may `require` two bundled modules, which the runtime now provides through a shim shipped as `iosApp/Sources/nuvio-compat.js` and evaluated in its own guarded step (a failure there leaves `require` untouched): a static-HTML `cheerio` subset (tag/class/id/attribute selectors, `find`, `text`, `html`, `attr`, `each`, `map`, `first/last/eq`, `children`, `parent`, `next`, `hasClass`) and the `crypto-js` surface most scrapers use (`MD5`, `SHA256`, hex and base64 encodings). `tools/nuvio-compat.test.js` checks the same file with Node against known MD5/SHA-256 vectors and scraping patterns. Providers that need a real DOM or a full crypto library still fail, and only `fetch`-based providers were the previous limit.
- Nuvio providers need TMDB ids. Without a TMDB key, the raw item id is passed through, so many providers will find nothing for IMDb-based catalogs.
- If the mpv bridge is unavailable (for example a build without the Swift package), the AVPlayer fallback cannot decode MKV/AVI/WebM. ASS/SSA styling is only applied to embedded tracks, which libmpv draws itself; external subtitle files are rendered from parsed SRT/WebVTT cues, so their override tags and positioning are ignored. Profiles and account sync, intro/recap/outro skip services, Apple signing, and end-to-end physical-device testing remain to be implemented.

## Build
Requirements: macOS with Xcode 16+ and JDK 17+. `iosApp/CINEMAX.xcodeproj` is generated from `iosApp/project.yml` with XcodeGen and kept in the repository as generated output; its "Build CinemaxKit" run script links the static Kotlin framework with the committed Gradle wrapper, so the app can be built without a manual Gradle step (see `iosApp/README.md`). The iOS CI workflow builds the device and simulator frameworks, regenerates the project with XcodeGen, and packages unsigned Simulator and iPhone `.app` outputs. The iPhone output is not signed and is not directly installable or distributable.

## Optional signed iOS release

A manual workflow named **iOS signed release** is available under GitHub Actions. It archives the iPhone app and exports an App Store Connect IPA when the repository has these GitHub Actions secrets configured:

- `IOS_DISTRIBUTION_CERTIFICATE_BASE64`: base64-encoded Apple Distribution `.p12` certificate.
- `IOS_DISTRIBUTION_CERTIFICATE_PASSWORD`: password for that `.p12` file.
- `IOS_APP_STORE_PROFILE_BASE64`: base64-encoded App Store provisioning profile for `com.cinemax.app`.
- `IOS_TEAM_ID`: Apple Developer Team ID.

The certificate and provisioning profile must belong to the same Apple Developer team and match the app's bundle identifier. Add secrets in the repository's **Settings → Secrets and variables → Actions**, then run **Actions → iOS signed release → Run workflow**. Never commit signing certificates, provisioning profiles, passwords, or private keys to the repository. Without these owner-provided Apple signing assets, the workflow intentionally stops before signing.

## Compatibility and safety
Stremio addons are remote HTTP services; their responses are parsed as data and never evaluated as code. Nuvio providers are executable JavaScript: they run only after the user enables them, inside a JavaScriptCore context with no filesystem, keychain or native API access, HTTPS-only `fetch`, bounded timers and timeouts, and unsupported modules rejected. Their output is still parsed as data. Compatibility is provider-specific and must be tested. Use only content sources you are authorized to access. CINEMAX does not host media.
