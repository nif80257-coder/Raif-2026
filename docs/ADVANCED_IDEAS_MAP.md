# CINEMAX advanced ideas map

A working map of the large ideas left, ordered so that each tier makes the next
one cheaper. Nothing here is a promise; each entry says what it depends on and
what would prove it worked.

## Tier 0 — finished in the last passes

| Idea | State |
| --- | --- |
| Cross-addon source aggregation with per-file fingerprints | done |
| Source track record (success, failure, time to first frame) | done |
| Pinned source and last-played link per episode | done |
| Timeouts per addon, parallel metadata, TTL caches | done |
| Embedded audio and subtitle track pickers, fail-over with position kept | done |
| Per-episode source count on demand and "last available episode" | done |
| Player engine failover (MPV → AVPlayer on the same link, then the next source) | done |
| Swiftfin-style visual system | done |

## Tier 1 — next, all buildable on what exists

| Idea | Why | Depends on | Effort | Risk | Proof |
| --- | --- | --- | --- | --- | --- |
| Canonical title identity (one page per film/series across catalogs) | the last real gap in source unification; title+year fallback needs no key | fingerprint layer, favourites/progress migration | 2 sessions | high (stored keys) | opening the same film from two catalogs lands on one page |
| Addon configuration (`config` in the manifest) | "options that never show" is a missing feature, not a bug | a web view and re-install with the configured URL | 1–2 sessions | medium | a configurable addon returns its configured catalogs |
| Per-source preferences (quality/audio/subtitle per addon) | quality preference exists globally; per addon makes "best source" personal | `SourcePreferenceStore` | 1 session | low | the chosen tier sticks for that addon |
| Absolute episode numbering via TMDB episode lists | anime/long series numbered differently | user TMDB key | 1 session | medium | S02E05 and absolute 13 open the same episode |
| Per-episode availability counts | delivered as an on-demand check per episode | — | — | — | count matches the sheet |

## Tier 2 — later, each needs a new subsystem

| Idea | Why | Needs | Effort | Risk |
| --- | --- | --- | --- | --- |
| Torrent streaming (libtorrent/WebTorrent bridge) | hundreds of imported sources are torrent-only today | native library, background download, storage policy, legal review | large | high |
| Offline downloads | the one thing streaming clients still lack | torrent or direct-download manager, storage UI, DRM-free only | large | medium |
| Intro/outro skip (TheIntroDB / AniSkip) | the most requested player feature in Nuvio/Stremio clients | per-title timestamps API, player seek hook | medium | low |
| Trakt/Simkl scrobbling | history lives outside the phone | OAuth, background sync, conflict rules with local progress | large | medium |
| Player engine failover mpv ↔ AVPlayer | delivered for MPV → AVPlayer; the reverse is not needed because AVPlayer is the fallback | — | — | — | a link MPV rejects plays with the system player |
| Picture-in-picture on the mpv path | PiP today only on AVPlayer | a second AVPlayer layer or a custom PiP controller | large | high |
| Multi-user profiles | one phone, several viewers | per-profile stores, a profile picker like Swiftfin's | large | medium |
| Cast/AirPlay | big-screen viewing | AVRoutePickerView for AirPlay, Chromecast SDK for the rest | large | medium |
| Live TV (M3U/Xtream) | addons already ship live streams | channel model, EPG parsing, player reuse | large | medium |
| Subtitle translation / OCR | Arabic subtitles for files that lack them | on-device translation or a cloud API (cost, privacy) | large | high |

## Tier 3 — moonshots

- **Recommendation engine from watch history** (offline, on device; no account).
- **Shared watch parties** with synchronised playback.
- **Library import/export** as a single file (settings, favourites, progress, addons).
- **Voice search** with Arabic dialect handling on device.
- **Widget and Handoff**: "continue watching" on the Lock Screen, resume on another device.

## Sequencing rules

1. Nothing in Tier 2 starts before the Tier 1 identity work, because downloads and
   scrobbling both need one durable id per title.
2. Every tier-2 subsystem must ship behind a setting that can be turned off, and
   must leave the app usable when it fails.
3. Each item is done only when it has: a unit-tested pure core, a visible state in
   the UI, and a failure message that names the cause.

## Performance budget to hold

- Cold start to first shelf: under 2 s with caches warm, under 6 s cold.
- Source sheet first row: under 3 s when any addon answers.
- Detail screen: under 2 s when the metadata cache is warm.
- No single addon may hold a screen longer than 15 s (enforced by timeouts).
- Scrolling stays at 60 fps on a 3-year-old iPhone: no per-frame allocations in
  rows, stable keys everywhere.
