# CINEMAX source unification — feasibility study and phased plan

Status: **study only**. Nothing in this document is implemented yet; it is the review the request asked for ("ادرسها وشوف مدى نجاحها قبل تطبيقها"). Every claim below was checked against the current sources, with file and function names.

## 1. What the code already does

- **Cross-addon aggregation exists.** `streamsFrom()` (`composeApp/.../App.kt`) fans out to every installed addon that declares the `stream` resource for the type, in parallel, and a failing addon never hides the others. Nuvio provider streams are merged after it (`stremioSources + nuvioSources`).
- **A first, silent de-duplication exists** inside `streamsFrom`: `distinctBy { url ?: "torrent:$infoHash:$fileIdx" }`. It drops an exact repeat and keeps the first addon, so the surviving row loses the fact that others also carry it. It does not run across Nuvio, and it compares URLs byte for byte.
- **Ranking exists** (`core/SourceRanking.kt`): direct link beats torrent-only, 4K > 1080p > 720p > 480p, REMUX/HDR/HEVC bonus, CAM penalty, and the link that played last for the title is pinned first. Quality is read from the stream's name/title text (`core/StreamLabels.kt`), not from structured fields, because most addons put it there.
- **Last successful link per title exists** (`PlaybackSourceStore`, key `cinemax.last.source.$itemId`) and the source sheet marks it "آخر رابط".
- **Failover exists** (`nextUntriedSource`, three attempts, never repeating a link) and keeps the position: the player starts from the stored `WatchProgress` of the same title/episode, so a switch resumes where the user was.
- **The model already carries the identity fields** the request needs: `StremioStream.infoHash`, `fileIdx`, `behaviorHints.videoHash/filename/bingeGroup` (`addons/StremioModels.kt`).
- **Episodes come from one meta addon** (`selectedMeta.videos`, ids of the form `tt1234567:1:2`); `nextEpisode` sorts by season/episode.
- **Search is progressive** (per addon, as it answers) and each hit shows the addon name, not a source count.

## 2. Verdict per requested item

| # | Requested | Status | What is actually missing | Risk |
| --- | --- | --- | --- | --- |
| 1 | One page per film/series across catalogs | **Missing** | A detail page is keyed by the id the catalog happened to return (`item.id`). `TmdbIdResolver` only maps IMDb → TMDB for Nuvio providers, needs a user TMDB key, and there is no title+year fallback or fuzzy matcher. | High: merging entries changes navigation and every stored key (favorites, progress, last link). |
| 2 | Smart de-duplication, one row with its providers | **Partial** | Exact-match dedupe within Stremio only; no URL normalisation; Nuvio/archive not deduped; the merged row does not list its providers. | Low: pure function over the already-fetched list. |
| 3 | Group/rank by quality, language, dub, subtitle; score by response/health; hide dead; remember last good | **Partial** | Ranking is static text heuristics. No success/failure/latency history, no dead-source demotion, no language/dub signals beyond badges. | Low–medium: needs a small persistent stats store and a settings switch. |
| 4 | Episode matching (absolute numbering), per-episode availability | **Missing** | Videos come from one meta addon; no absolute↔season mapping; no per-episode source counts. | Medium: a correct absolute mapping needs TMDB episode lists (user key) or a heuristic. Availability counts cost N addons × N episodes requests. |
| 5 | Fail-over keeping position; retry failed sources in the background | **Mostly done** | Failover + position are done. Missing: retrying/recording failures without touching the running playback, and not auto-picking a link that already failed. | Low. |
| 6 | "Play best source", per-source preferences, source count in search | **Missing** | No best-source action, no per-source preference store, no counts in search results. | Low for the first two; the counts are the expensive one (a probe per hit × addons). |

## 3. The two problems the request says to fix first

1. **Overlapping sources** is exactly item 2 plus the missing provider list. Today the duplicate is dropped silently by `distinctBy`, so the sheet can look like "the wrong addon won" and identical rows from Nuvio still appear. This is the cheapest, highest-confidence fix in the whole list.
2. **Addon options that do not show** is not a UI bug: the manifest model has no `behaviorHints.configurable`/`config` field and the app has no configure flow at all, so there is nothing to show. Fixing it means modelling the flag, opening the addon's configure URL in a web view, and re-installing with the URL the page returns. That is a feature, not a patch, and it belongs after the merge work.

## 4. Proposed design

Small, testable pieces, following the existing `core/` + store pattern (the Kotlin multiplatform `Settings` stores already cover persistence; no Codable/UserDefaults layer is needed on iOS):

- `core/SourceFingerprint.kt` — `streamFingerprint(option)`: `torrent:<infohash>:<fileIdx>` or `url:` + a normalised URL (drop `utm_*`, `tr`, `token`, `sig`, `expires`; keep the path and the meaningful query, sorted). `mergeSources(list)` → `MergedSource(option, providers: List<String>)`, so a duplicate appears once and names every addon that carries it.
- `core/MediaIdentity.kt` — `MediaKey(imdb, tmdb, type, normalisedTitle, year)`; `sameTitle(a, b)` reusing `normalizeForSearch` (Arabic alef/ta marbuta folding already exists) with a ±1 year tolerance in both directions and a Latin/Arabic cross check for known alternate titles. Used only as a **fallback** when no id matches.
- `core/SourceStats.kt` + `SourceStatsStore` — per fingerprint: `lastSuccessAt`, `lastFailureAt`, `failures`, and a time-to-first-frame measure, which the player already observes (`onPosition > 0` vs `onError`). `rankedSources` gains a "demote dead / recently failed" step and an optional "hide dead" preference.
- `core/EpisodeNumbering.kt` — `EpisodeKey(season, episode)` from the video, then an absolute index fallback: `S01E03` parsed from name/title, else the video's position in the sorted list. This is the only mapping that works without a TMDB key, and it should be labelled as a heuristic.
- `SourcePreferenceStore` — per addon id plus a `*` default: preferred quality, audio language, subtitle language; feeds "play best source" and autoplay.
- Best-source button — `rankedSources(...)`, filtered by preferences and excluding dead/failed links, first result; the same function backs autoplay so the two cannot disagree.
- Search source counts — only for titles already probed in this session (cache keyed by `MediaKey`); probing every hit would multiply traffic by the number of addons.

## 4b. Status after the implementation pass

- **Phase 0 — done.** Fingerprints, URL normalisation, merged provider lists and
  cross-source de-duplication, with tests.
- **Phase 1 — done.** `SourceStatsStore`, demotion by track record, the
  "إخفاء المصادر المعطلة" preference, and the last-good link now stored as a
  fingerprint (`PlaybackSourceStore.loadFingerprint`) so a changed URL token still
  points at the source that worked.
- **Phase 2 — core done, page merging open.** `core/MediaIdentity.kt` resolves an
  identity (IMDb, then TMDB, then folded title + year, with a ±1 year and same-type
  rule) and is used for favourite toggling, the resume lookup and search-result
  de-duplication. `TitleAliasStore` remembers every catalog id a title has been
  opened under, and the source sheet of a film gathers the sources of all of them,
  which is the practical half of "one page per title"; the viewer chooses whether
  that merging happens ("دمج مصادر العنوان من كل الكتالوجات" in Settings, on by
  default). Merging two catalog entries into a single navigable page (and the
  one-time key migration) remains open because it changes navigation.
- **Phase 3 — mostly done.** Episode positions come from fields or an "S02E05"
  tag, `absoluteEpisodeIndex` is tested, and per-episode availability is an
  on-demand count with "متوفرة حتى الحلقة N". With a TMDB key, `TmdbSeasons`
  fetches the season lengths and `absoluteToSeasonEpisode` turns an absolute
  number into a season/episode label; without a key the tag/title fallback stays.
- **Phase 4 — partial.** Per-source quality preferences are remembered when a link
  plays (`SourcePreferenceStore`) and give that addon's tier a ranking bonus;
  addons that advertise `behaviorHints.configurable` now show "إعداد", which opens
  the addon's own configure page (`configureUrl` + `openExternalUrl`). An in-app
  configure web view and re-install with the returned URL remain open.
- **Phase 5 — blocked** as planned (needs a torrent engine).

## 5. Phases, in the order I would ship them


- **Phase 0 — data cleanliness (one session, low risk).** Fingerprints, URL normalisation, merged provider list, dedupe applied across Stremio + Nuvio + archive. Sheet shows "متوفر في 3 مصادر". No API or navigation change. Fixes the reported overlap directly.
- **Phase 1 — health and ordering.** Stats store, demote failed links, "إخفاء المصادر المعطلة" setting, last-good link stores the fingerprint (not only the URL) so it survives a URL with a changed token.
- **Phase 2 — one page per title.** Canonical id resolver (imdb → tmdb via the existing resolver, reverse mapping when the key is present), title+year fallback with the fuzzy matcher, merging of duplicate catalog entries, and a one-time migration of favorites, progress and last-link keys.
- **Phase 3 — series.** Episode matching with the absolute fallback; per-episode availability shown on demand (a "كم مصدر؟" action per episode) instead of pre-fetching every episode.
- **Phase 4 — preferences and addon options.** Best-source action, per-source preference store, then addon `config` support (manifest flag, configure page, re-install with the returned URL).
- **Phase 5 — blocked.** Torrent `infohash` de-duplication only becomes meaningful with a torrent engine; until then those rows stay unplayable and the "مباشر" filter hides them, which is the honest behaviour.

## 6. How success is proven without a device

- Unit tests per phase: URL normalisation drops tracking parameters and collapses two trackers with the same infohash; merge keeps every provider name; title+year matching handles Arabic folding and a ±1 year; absolute numbering maps `S01E03` and position 13; stats ordering demotes a failed link below a fresh one.
- Countable outcomes in the app: number of rows in the source sheet for a known film before/after Phase 0; ordering after a failure; failover attempt count; the same title opened from two catalogs landing on one page after Phase 2.

## 7. Risks and decisions the owner has to make

- **Over-merging**: two different films can share an Arabic title ("الأسد", "العار"). A merge must require a year match when both entries have a year, and must never merge across type (film vs series).
- **Which metadata wins** when two catalog entries merge: proposed rule — the entry with a poster, then the active catalog's entry, then the one with more fields; never lose the user's saved state.
- **Absolute numbering** without a TMDB key can only be heuristic; state that in the UI instead of pretending it is exact.
- **Search counts** must stay lazy, otherwise a single search multiplies into hundreds of requests.
- **Addon options** need a web-view configure flow; there is no way to make them appear without it.

## 8. Recommendation

Start with Phase 0 (de-duplication + provider list + normalised URLs + dedupe across Nuvio), because it fixes the two problems named in the note, is pure function work over data already in memory, and carries no migration risk. Phase 1 follows immediately since it reuses the same fingerprints. Phase 2 is the largest and riskiest change (canonical ids touch every stored key) and should only start once Phases 0–1 are verified in use.
