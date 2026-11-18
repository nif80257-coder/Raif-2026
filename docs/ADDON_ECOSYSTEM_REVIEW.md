# Addon ecosystem review — what the four reference repositories offer

Sources read: `Stremio/addon-helloworld` (SDK example), `doingodswork/awesome-stremio`
(curated list), `Stremio/stremio-public-domain` (curated public-domain addon) and
`user-griffin/official-sources` (legitimate availability addon for Nuvio/Stremio).

Legend for "in CINEMAX": **yes** = shipped and working, **partial** = some path
exists but a piece is missing or unplayable, **no** = absent.

| Feature / source | Repository | What it is | In CINEMAX? | Value to the viewer | Difficulty | Priority (1–5) |
| --- | --- | --- | --- | --- | --- | --- |
| `stream.url` direct link | addon-helloworld | Plain HTTP(S) video file or HLS | yes | plays anything the addon offers | — | — |
| `stream.infoHash` torrent | addon-helloworld, awesome | Magnet/infohash P2P entry | partial | hundreds of imported sources are torrent-only today | hard (needs a torrent engine) | 3 |
| `stream.ytId` YouTube | addon-helloworld | Stream is a YouTube video id | no | many public-domain/cartoon addons ship YouTube only | easy (open externally) | 4 |
| `stream.externalUrl` deep link | addon-helloworld, official-sources | Row that opens a provider's app/page instead of playing | no | "where can I legally watch this" rows, and Netflix/Shahid-style addons | easy | 5 |
| `subtitles` resource + stream subtitles | addon-helloworld | Addon-provided captions | yes | Arabic subtitles from SubDL/Subtito etc. | — | — |
| `behaviorHints.proxyHeaders` | addon-helloworld | Per-stream request headers | yes | private/debrid links that need headers | — | — |
| `behaviorHints.bingeGroup` | Stremio protocol | Marks streams that belong to the same binge chain | partial | picking the same release for the next episode automatically | easy | 3 |
| `behaviorHints.filename/videoSize` | Stremio protocol | Exact file identity | partial | better de-duplication and subtitle matching | medium | 2 |
| MetaHub artwork (`images.metahub.space`) | addon-helloworld | Poster/backdrop fallback by IMDb id | no | posters when the addon sends none | easy | 2 |
| Catalog extras: `search`, `skip`, `genre` | addon-helloworld | Search and paging in catalogs | yes | search across addons, "load more" grids | — | — |
| `meta` handler (cast, director, videos) | addon-helloworld | Rich metadata and episode lists | yes | detail screen, episodes, next-episode | — | — |
| `channel` / `tv` content types (live TV) | addon-helloworld, awesome | Live channels rather than films | partial | live TV addons would open at all | medium | 3 |
| Podcast addons | awesome-stremio | Audio shows as content | partial | podcasts in the same app | easy | 2 |
| Kitsu anime catalogs | awesome-stremio | Anime catalogs with `kitsu:` ids | partial | anime browsing; ids are not mapped to TMDB for Nuvio providers | medium | 3 |
| IMDb list / IMDb tag catalogs | awesome-stremio | Any IMDb list as a catalog | yes (as addons) | curated lists | — | — |
| Top movies catalogs (IMDb 250, Oscars) | awesome-stremio | Ready-made quality catalogs | yes (as addons) | discovery | — | — |
| OpenSubtitles official addon | awesome-stremio | Subtitles from OpenSubtitles | yes (as addon) | Arabic subtitles | — | — |
| Torrentio / Orion / Deflix (debrid) | awesome-stremio | Resolve torrents through RealDebrid/AllDebrid/Premiumize | no | instant 4K links without a torrent engine | hard (accounts, API, ToS) | 4 |
| Stream Quality Filter (dedupe + sort by quality) | awesome-stremio | Removes duplicates, sorts by resolution | yes (built in) | cleaner source sheet | — | — |
| Addon discover list (`api.strem.io/addonscollection.json`) | awesome-stremio | A public catalogue of installable addons | no | install useful addons without hunting GitHub | medium | 5 |
| Catalog builder / addon publish helper | awesome-stremio | Web tools for addon authors | no | none for viewers | — | 1 |
| Stremio Downloader | awesome-stremio | Downloads a stream for offline viewing | no | offline flights/travel | hard | 4 |
| PimpMyStremio (local addon runner) | awesome-stremio | Runs local/legacy addon code | no | nothing on iOS (no local node runtime) | impossible on iOS | 1 |
| Curated public-domain collection with an exclusion list | stremio-public-domain | IMDb ids + verified PD streams, `exclude/meta.json` guards | partial (our free library uses the Internet Archive instead) | more legal free content, curated | easy | 3 |
| Legal availability ("where to watch") via TMDB/JustWatch-style data | official-sources | Official provider links per region | no | answers "أين أشاهده قانونيًا" | medium | 4 |
| Configured manifest token (`/c/<base64url>/manifest.json`) | official-sources | Non-sensitive prefs encoded in the addon URL | partial (we open the configure page and install the returned URL) | per-region/per-subscription setup without an account | easy | 3 |
| Region + subscription preferences for an addon | official-sources | User picks country and services | no | relevance of the availability rows | medium | 3 |
| Per-addon ordering of source groups | official-sources (Nuvio behaviour) | Addon order beats global ranking | partial (we rank globally by score/health) | put a trusted provider's rows on top | easy | 2 |
| Safe empty behaviour when a key is missing | official-sources | No key → no results, never a fake link | yes (same policy) | trust | — | — |
| Local development addon over `127.0.0.1` | addon-helloworld | Local addon for testing | yes (loopback is allowed) | developers | — | — |

## Ranked backlog of what CINEMAX does not have

1. **`externalUrl` rows** — the single cheapest big win: every "official sources"
   addon (Netflix, Shahid, Prime, and the whole `official-sources` project) speaks
   only this field, and we currently drop those rows silently.
2. **Addon discover list** — a browse-and-install screen fed by
   `api.strem.io/addonscollection.json` (name, description, manifest URL), turning
   an empty install screen into a store.
3. **`ytId` streams** — open the YouTube watch page (or an `externalUrl` row);
   many public-domain/cartoon addons are YouTube only.
4. **Debrid resolution** — turns the unplayable torrent rows into instant direct
   links without shipping a torrent client; requires accounts and their ToS.
5. **Live TV (`channel`/`tv`)** — accept those catalog types, filter the home feed
   and play the stream like any other; EPG is a second step.
6. **Offline downloads** — the only feature viewers ask for that we have none of.
7. **`bingeGroup`** — carry the chosen release into the next episode.
8. **Legal availability rows** — "أين أشهد قانونيًا" with region awareness.
9. **Kitsu→TMDB id mapping** — anime streams from Nuvio providers.
10. **MetaHub poster fallback** — posters for addons that send none.
11. **Podcasts** and **per-addon group ordering** — smallers refinements.

## Legal and security notes

- **Torrent/P2P addons** (Torrentio, 1337x, PirateBay, RARBG, HorribleSubs) link to
  copyrighted material. Shipping a torrent engine would make the app a
  distributer of infringing copies in most jurisdictions; that is why it stays
  out. Debrid services resolve the same material through a paid account — their
  own terms forbid some uses and the account can be banned.
- **Public-domain lists** are a legal claim, not a fact: PD status differs by
  country and by edition (a restored score or new dub can re-copyright a film).
  `stremio-public-domain` itself keeps an `exclude/meta.json` because mistakes
  happen. Any bundled list must carry its source, its licence and a quick way to
  remove an entry.
- **Official availability / deep links** are the safest content model: we never
  host, proxy or decrypt anything. The risks are contractual, not criminal:
  Watchmode requires attribution and respects a caching policy, its free tier has
  no episode-level links, and API keys must stay server-side.
- **Third-party addons** are untrusted JSON. CINEMAX never executes addon
  JavaScript for Stremio addons, accepts HTTPS only (loopback excepted) and
  encodes every resource path. Keep that rule for any new field.
- **The in-app configure browser** can reach any page an addon points at. It
  should keep showing the address, stay HTTPS-only and never receive credentials.
- **YouTube** streams must be opened as the official watch page or embed; scraping
  the media URL breaks YouTube's terms.

## First five to build, with steps

1. **`externalUrl` support** — add the field to `StremioStream`; in the source
   merge keep such rows; in `SourceCard` render them as "فتح في التطبيق" (no
   play icon); pressing calls `openExternalUrl`. Nothing else changes, because
   these rows never reach the player.
2. **Addon discover screen** — fetch the public addons collection on demand,
   show name/description/icon in a sheet above "إضافة مصدر", and on tap hand the
   manifest URL to the existing `installAddon`. Cache it a day.
3. **`ytId` streams** — treat `ytId` as an external row
   (`https://www.youtube.com/watch?v=<id>`) or, later, an embedded player.
4. **Live TV types** — allow `channel`/`tv` in `AddonCapabilities`, build shelves
   from those catalogs, and let the player use the existing stream path; label
   them "مباشر" and hide the seek bar.
5. **Public-domain bundle** — import `include/streams.json` + `include/meta.json`
   from `stremio-public-domain` into the app bundle as a second built-in source
   next to the Internet Archive shelf, honouring its exclusion list and keeping
   the licence and provenance in the file.
