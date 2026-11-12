# CINEMAX product gap analysis

Reference products reviewed: Nuvio Mobile (official product page and current mobile release notes), Stremio, and advanced mobile video-player capabilities. Nuvio current mobile release notes include custom subtitle styling, chapter markers, player engine failover, and improved PiP behavior. This is a capability comparison, not a claim that CINEMAX already implements every feature.

## Delivered in this UI pass
- Arabic-first dark visual system with selectable violet, blue, rose, emerald, and amber accent colors.
- OLED-black option and compact media-card density.
- Poster artwork and rating presentation in catalog cards.
- Persistent settings for appearance, resume preference, autoplay preference, and preferred audio/subtitle languages.
- Generated iOS AppIcon pipeline from the shared brand mark.
- Settings sections for appearance, playback, languages, and addon management.
- Season/episode browsing inside the item sheet and per-episode continue-watching entries.
- Source aggregation across every installed addon declaring the `stream` resource for the item's type, with addon labels on each source.
- Correct Stremio extra-segment encoding (`search=…&skip=…` in one path segment), which search and pagination need to work against real addons.
- Nuvio provider repositories: installation, per-provider enable switches, and a JavaScriptCore runtime that runs bundled `getStreams` scripts and merges their streams with Stremio results.
- Optional TMDB key resolving IMDb ids to the TMDB ids Nuvio providers take, cached locally.
- iOS-only target: the Android application module, sources and CI workflow were removed.
- Cross-addon search: one query fans out to every installed addon with a search-capable catalog, deduplicated and labelled by addon.
- Automatic next-episode playback after the current episode ends, driven by the real AVPlayer end-of-item notification and the stored preference.
- Playback audio session, item cleanup on close, and refreshed addon descriptors at launch.
- Stremio `subtitles` protocol support: addon-level and stream-level subtitle lists, SRT/WebVTT parsing and an in-player subtitle overlay with automatic language selection.
- Embedded (in-file) subtitle tracks: libmpv's track list is reported to the shared UI, listed in the same picker under "ترجمات مدمجة في الفيديو نفسه", and selected by track id with an ordinal fallback; the preferred language picks one automatically when no addon subtitle matches.
- Stream `behaviorHints` support: request headers for proxied sources, plus explicit messages for unsupported containers (MKV/AVI/WebM) and AVPlayer failures instead of a silent black screen.

## Gap matrix

| Capability | Nuvio | Stremio | CINEMAX status |
| --- | --- | --- | --- |
| Mobile-first catalog browsing | Yes | Yes | Catalog cards, season/episode picker, and cross-addon search |
| Artwork, ratings, metadata | Yes | Yes | Posters, available metadata and ratings from addon responses; rating sources not aggregated |\n| Custom subtitle appearance and font/render modes | Yes | Addon/player dependent | Not implemented |\n| Player engine fallback and playback diagnostics | Yes | Player dependent | Native AVPlayer / Media3 only; no fallback policy |\n| Chapters and chapter markers | Yes | Addon dependent | Not implemented |\n| Picture-in-picture | Platform support | Platform support | Not yet verified end-to-end |
| Addon catalogs | Yes | Core model | Stremio manifest/catalog support; Nuvio providers (fetch-based) run in JavaScriptCore; cheerio/crypto-js providers still unsupported |
| Cross-device sync | Account sync | Account sync | Not implemented |
| Multiple profiles | Yes | Limited/account based | Not implemented |
| Continue watching/history | Yes | Yes | Local continue-watching with per-episode resume for series; no account sync |
| Subtitle discovery and selection | Yes | Addon based | External subtitles from `subtitles` addons and streams, parsed (SRT/VTT) and rendered over the video; plus the file's own embedded tracks, listed and selected in the same picker |
| Audio/subtitle track controls | Yes | Yes | Preferred languages are applied to AVPlayer media selection and to embedded tracks; subtitles can be overridden per track in the picker, audio still by preference only |
| Intro/recap/outro skipping | Yes | Addon dependent | Not implemented |
| External player selection | Yes | Supported workflows | Not implemented |
| Playback speed/PiP/gestures | Platform-specific | Platform-specific | Not implemented in shared controls |
| Themes/OLED/accent colors | Yes | Limited | Implemented in settings |
| Privacy/security boundary | Client-side | Remote addons | HTTPS validation; executable JS remains disabled |

## Priority roadmap

### P0 — iPhone experience
1. Finish the home screen hierarchy: featured title, continue watching, category shelves, and clear bottom navigation.
2. Replace metadata dialogs with a full detail screen including artwork, synopsis, seasons/episodes, and source selection.
3. Implement favorites and watch progress locally before adding cloud sync.
4. Implement a real player settings overlay: track selection, subtitle styling, speed, aspect ratio, and picture-in-picture where supported.
5. Test VoiceOver, Dynamic Type, safe areas, and iPhone portrait/landscape behavior.

### P1 — media features
1. Subtitle provider integration and external subtitle loading.
2. Cross-addon search and source sorting/quality badges.
3. Continue-watching and episode progression persistence.
4. Optional Trakt/Simkl integrations, only after explicit account linking and secure token storage.
5. Intro/recap/outro markers through a supported metadata service.

### P2 — ecosystem
1. Profile support and cloud sync.
2. Nuvio plugin repository discovery only after a hardened JavaScript sandbox exists.
3. Chromecast/AirPlay and external player handoff.
4. TV-specific navigation and remote focus behavior.

## Important product constraints
- CINEMAX is a media client; it does not host or bundle media.
- Addon code must never be evaluated as ordinary JavaScript. The Nuvio runtime remains blocked until a sandbox with resource limits and explicit trust controls is available.
- A preference toggle is not evidence of a feature. Playback preferences must be wired into player behavior before being advertised as active.
- iOS AppIcon generation happens in macOS CI from the shared SVG asset.
