# RAIF party guest page

A single static page that lets someone **without the app** join a watch party
from a browser: they see what the party is watching, who is in it, they can chat
with the host, and they can **watch the host's stream inside the page**.

## Watching in the browser

The host app keeps the party's stream URL to itself until it is shared. When the
host has "مشاركة البث الخاص بي" on in the party panel (it is on by default for a
new party), the player broadcasts the link and the playback position on the
party's realtime channel. The page joins the same channel with the public anon
key — the `realtime.messages` policies allow the `anon` role on
`watch-party:%` topics — so the guest can:

- play the host's link with the "مشاهدة مع المضيف" button,
- follow the host's play, pause and seeks (the page corrects drift every 3 s),
- ask for the link when they join (it broadcasts `state_request`).

Two limits belong to the browser, not to the page:

- The `<video>` element only plays what the browser supports (MP4, WebM, HLS in
  Safari). An MKV stream from a debrid provider usually cannot be played here;
  the page says so and offers the "open in RAIF" button.
- A stream that needs custom request headers cannot be fetched by `<video>`.

If the stream does not appear, the page says the host has not shared it yet.

## Deploy on Cloudflare Pages (free, no card)

Cloudflare Pages takes **one folder or one zip**, and whatever is inside it
becomes the site root. That is the part that trips people up: uploading a `party`
folder that holds these files puts them at `/party/`, and uploading a zip built
from the folder itself nests them one level deeper still, so `_redirects` is no
longer at the root and the short links stop working.

### The way that stops needing uploads: connect the Git repository

1. Open <https://dash.cloudflare.com> and sign in to the account that owns
   `raif-party.pages.dev`.
2. **Workers & Pages → Create → Pages → Connect to Git** (import an existing
   repository).
3. Authorize GitHub and pick `ndaloo305-svg/raif-party`. The page files are at
   that repository's root, which is exactly what Pages wants.
4. Framework preset: **None**. Build command: **leave empty**. Build output
   directory: **leave empty** (or `/` if Cloudflare insists).
5. **Save and Deploy**.

From then on every push to `main` redeploys the page by itself, so a change to
`index.html` reaches the guest page without anyone uploading anything. A name is
taken by the existing upload project: either delete that project first, or give
the new one another name such as `raif-party-web`.

### The manual way: upload a zip of the contents

Build the zip so that `index.html`, `_redirects` and `og.png` sit at its **top
level**, not inside a folder:

    zip -j raif-party.zip index.html _redirects og.png

Then **Workers & Pages → the project → Create new deployment → Upload assets**,
choose that zip, and Deploy. Do not select the `party` folder itself. Each
deployment replaces the files of the one before it.

The page shows a `v4` chip next to the RAIF wordmark, so after a deploy you can
tell at a glance whether the live site carries the version that plays the stream
and hands it to an external player. Open

- `https://<project-name>.pages.dev/?code=ABC123` (what the app copies)
- `https://<project-name>.pages.dev/ABC123` (short form, needs `_redirects` at
  the root)

and look for `v4`. If the chip is missing, the old page is still being served and
the guest will see chat only. Both link forms work, and a link that carries a
partial code fills the form instead of starting empty.

`og.png` is the picture chat apps show in the link preview. The `_redirects`
catch-all rewrites every path to `index.html`, so the page cannot serve that
image itself: `og:image` points at the file in this repository on GitHub instead.
Keep `og.png` in the repository for that reason; uploading it with the page does
no harm.

## How it talks to the backend

The page uses the public Supabase anon key and only the watch party RPCs that
are already granted to anonymous users:

| RPC | Used for |
| --- | --- |
| `watch_party_preview` | party title, host, seat count before joining |
| `watch_party_join` | taking a seat with the browser's own device id |
| `watch_party_heartbeat` | staying in the party and refreshing members |
| `watch_party_list_messages` | chat history (polled every 3 s) |
| `watch_party_send_message` | sending a chat message |
| `watch_party_leave` | freeing the seat when the tab closes |

The player uses the realtime channel `watch-party:<party id>` (broadcast events
`stream`, `state`, `state_request`, `ended`) through `@supabase/supabase-js`,
loaded from jsDelivr at runtime. If that script cannot load, the page still works
for the party, the chat and the "open in RAIF" button.

No secret key is embedded, and the page never reads another account's data: the
RPCs are `security definer` functions that scope everything to the party id and
the browser's device id.

## Changing the project URL or key

Both constants are at the top of the script in `index.html`:

- `SUPABASE_URL`
- `ANON_KEY`

Replace them if the Supabase project ever moves.
