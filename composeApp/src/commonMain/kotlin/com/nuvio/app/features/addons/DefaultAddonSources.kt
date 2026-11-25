package com.nuvio.app.features.addons

/**
 * Add-ons installed automatically, so a profile opens with a curated source list instead of an
 * empty home screen.
 *
 * The bundle follows one rule: a bundled stream source must play without paying anyone and without
 * a middleman. That leaves two shapes — a source that answers with a plain `http(s)` link the
 * player opens itself, and a source that answers with an `infoHash` the app's own p2p engine
 * plays. A source that only works once a debrid account has been linked, or that carries someone
 * else's debrid key, profile or encrypted configuration inside its URL, is not bundled.
 *
 * Each stream source is therefore reached at the address its own author publishes. The four
 * subtitle providers are the exception: they answer nothing without the configuration that link
 * carries, so their configured link ships as it was set up, and it can be revoked by that person.
 * No password and no paid account sits in any of them.
 *
 * The bundle is installed once: entries already present are skipped, and the seed marker is only
 * written once at least one add-on actually installed, so an offline first launch retries later.
 */
internal data class DefaultAddonSource(
    val manifestUrl: String,
)

/**
 * Bundled sources that were dropped, and why each one failed the rule above.
 *
 * Matched by a fragment of the manifest URL rather than by host alone: two shipped sources share
 * `cinema.debrid.in` and one of them stayed, so a host match would have retired the survivor with
 * the casualty.
 */
internal val RetiredDefaultAddonFragments: List<String> = listOf(
    // Debrid services wearing an add-on's clothes: they answer "link your account" and nothing
    // else, so every title they are asked about costs a request and returns no file.
    "addon.torrin.app",                     // "Direct torrent playback is disabled on this server."
    "mediafusion.elfhosted.com",            // an empty list on every title tried.
    "froststream.cloutteam.com",
    "jackettio.elfhosted.com",
    "comet.elfhosted.com",
    "autostream-addon.onrender.com",
    // Carries an Orion API key that belongs to whoever built the URL, not to this app's user.
    "orion.baby-beamup.club",
    // Carries a Real-Debrid key inside the manifest URL, same problem.
    "profile=970d9cd3af6706d88733fe7ff57d3dbf",
    // Its bundled link was an encrypted config and its plain manifest declares no resource at all,
    // so whatever it answers with comes from the one configuration that link carried.
    "aiostreamsfortheweebsstable.midnightignite.me",
    // The first Absolute Cinema variant, which mixed torrent mirrors and a Seedr account into the
    // link list. Its http-mode replacement, without live TV, is what ships now.
    "|livetv=1",
    // Down when checked: a 404, a 503 or a 526 on the manifest itself.
    "sup-nyp1.onrender.com",
    "dexter.q-1208.fun",
    "rophim.q-1208.fun",
    "filmwhisper.dev",
    "cotonetnet-cotonet.hf.space",          // an empty list on every title tried.
    "150203dd784e-cinetorrent-addon",       // an empty list; its host answers 500 the rest of the time.
    "94c8cb9f702d-brazuca-torrents",        // answers HTTP 500 with "handler error" on every try.
    // Anime only, which this app does not carry.
    "onepace.e6ip.com",
    "fedew04.github.io",
    "stremio.yukistreams.xyz",
    // Live television, sports and news channels rather than films and series.
    "stremio-addon.ehtudo.app",
    "sc.k-20.xyz",
    // Catalogs, metadata or availability lists that return no playable file.
    "aiometadata.elfhosted.com",
    "7a82163c306e-stremio-netflix-catalog-addon",
    "dc-timeline-stremio.onrender.com",
    "marvel-timeline-stremio.onrender.com",
    "stremio-torrentfreak.krazykayaker.com",
    "top-streaming.stream",
    "cncverse.dpdns.org",                   // a Cloudstream bridge: HTTP links, but its catalog is
                                            // mostly IPTV, Donghua and anime.
    "discussio.elfhosted.com",              // links to a Reddit thread, never to a file.
    "arabcity.fly.dev",                     // catalog types the app cannot ask for; empty replies.
    "2ecbbd610840-stremio-ar.baby-beamup.club", // empty catalog and empty stream list.
    "peenoise.fkye.workers.dev",            // adult catalog.
    // The exact USA TV Next manifest, not every add-on hosted on raw.githubusercontent.com: that
    // host is where many hand-added add-ons live, and matching it by host retired all of them.
    "raw.githubusercontent.com/yowmamasita/usa-tv-next",
)

/**
 * Catalogs: the ones that stay — global, Arabic, and Arabic collections.
 *
 * Catalogs are not stream sources, so the rule above does not apply to them: they publish metadata
 * the home screen draws, and a profile needs at least one to open on anything.
 */
internal val KeptCatalogAddonHosts: List<String> = listOf(
    "v3-cinemeta.strem.io",
    "arabcinemeta.osamayousry.com",
    "collections.stremiolabar.com",
)

/** Subtitle providers, which are neither catalogs nor stream sources. */
internal val KeptSubtitleAddonHosts: List<String> = listOf(
    "subtito.com",
    "subdl.strem.top",
    "subsource.strem.top",
    "submaker.elfhosted.com",
    "opensubtitles-v3.strem.io",
)

internal fun isRetiredDefaultAddon(manifestUrl: String): Boolean =
    RetiredDefaultAddonFragments.any { fragment -> manifestUrl.contains(fragment, ignoreCase = true) }

/**
 * The three catalogs, the five subtitle providers, and every stream source that answers with a
 * direct link or a p2p info hash.
 */
private fun isKeptDefaultAddon(manifestUrl: String): Boolean =
    KeptCatalogAddonHosts.any { host -> manifestUrl.contains(host, ignoreCase = true) } ||
        KeptSubtitleAddonHosts.any { host -> manifestUrl.contains(host, ignoreCase = true) } ||
        !isRetiredDefaultAddon(manifestUrl)

/**
 * Every entry below was asked for a real title and answered with a link the player can open.
 *
 * The p2p sources sit at the end: the app plays their `infoHash` itself through the in-app torrent
 * engine, which is why they need no debrid service to work.
 */
internal val DefaultAddonSources: List<DefaultAddonSource> = listOf(
    // Catalogs.
    DefaultAddonSource("https://v3-cinemeta.strem.io/manifest.json"),
    DefaultAddonSource("https://arabcinemeta.osamayousry.com/c/rAEM3___ef_fCv-P_58BPWA/manifest.json"),
    DefaultAddonSource("https://collections.stremiolabar.com/api/addon/H4sIAAAAAAAAE51XW4_jJhj9L3kuUpKZaWf2cTVVV2q3UnekUS-qLALYZoyBBTy5VP3v_bAdxzjEZiuNJg45HA7f3f-syOrDXytncOV0sxOcZFYqXGXOMEm5LFCt3jmzq-_mMLZU-wUIlrxmSWQd8kxZU8BY1xiRSeZywQ_IKY02a3StMAGboLTi1M4J1Uo3ApsUSOQSO3VAKs85YehhHd3MAd3q3j6s5w4JcJGT2h0oQLn3GQOcRbc7Ei7X4iLnXvbXzDHTu2AdgmABKYmMco5Jp2rsFLOIqDqEEWYczzmjuWG2jDlaYMesQwMQtUi4adzJJfNa0M7v6ejAG8gvbzctjQTWI5JH5CAKb0TKN5IgwzAFQ1jEKHdcyfAGJS9KZLDzsTdykGBgGgPhQrPNl5KHK28fX__cT8RVzc5wUm3vslZYI-FMoAWTDHYbMWyfT3sVLunnutqHS4-f3j5PtDy__7gOl35Zb4qXcOnr4aPYTvRp-JFb3JiMNAKsJLC1nExNTLDguTKSY5k5UnfXKIyCz9loxMRbNiVue2QsN73dSsYDt2IhWjfOckLgMnpMOb1HxtIQZJT368fMOiwpavSA1YxwLKbpDWkFKa2xUcPJyXqpwTVOkdsBY7bCFiIL7KigEkiruYGvuyPCUGsbKN9OmeO1YGzBrx3p7LGlMkYlldgeebus1Q240zLfeyCSZL8hCt0-dDkdnB5Nbb9QKiGOe6Uo5LdWBqJ7kmGvv73-NEl1cI7aM3aO05anPTJ03Ijly-vPJt4nzhZwpeFCsCRrDdiIR2suad6QKs7zhk8nqFIMnJ1zyd2xyLQ9EjCCKjgk7VQGwoafPPWsHks4ynmK8h4Z0a0a32WsxtBSu__soIXyFXVcEEY2zV_Yp3gbKSki5jKotL2rq6G5UTXaPD1CE1XwR_EkurttEXndD1SRxkL9u7plST3QZm3WXLA1BBqOwM81IgqHUtEeAYYS9Xxaj_YkGD844vqK736aUYYXXEIgxEYRMOOY48Ycgp-2D3afeeSeVzxys5AWah-WytRwaGDfqF_rowX_H7_ds_Azlo6Trhz7vd3UgoaxZeCKbATL9GW86ureJBbvH3M60Uy5leyGX_wi9AetM80PvpnBsNwFOuylXE2pYPSo98pU_UQLWiBHST-EjIkLP_4yQUxjS2YySnrq8fgw01P_DzYSKBd3TRjjXhrPJW-fD0VIZmFmwNCRcsBIdaMWPP3w8utkxLn__Q_ZhFRXhRtGlPZF4TrO99iR8qK77x6wt7qxiUDhtA4CY854vWHAnQJqb8IUP4csMRwHLU5usx4WT8QCj0raTU3nV61l9WfkzEsYGDCBbwybeVGMX26nLa8HjI_6PhT9KEN8xkKKxIfN_h5zCRoDzt93mW2EWvQGVJgThLoedbFk-LzOVOYr7KLmcqdQjVMi6IycV5rAN4Yt6oNyK5hvWssCB-iCLRMYA9yiRt8La3hLcgkiL9h5lUmcIXBRZ9mIJkFiC1vw8gLTgIlUCM0wUTDqDkNLlAeeScVM-zHYrt8JTdicIM5JSAxpStS7nzMG6pH3xs0Jlvy0j5qv5ntX3JV31epvOH71YYX9iwQ9PxBxftLwYDTdrf79DwVrqfK2EwAA/manifest.json?lang=ar"),
    // Subtitles.
    DefaultAddonSource("https://subtito.com/user/83ba7951-84b5-4fc2-ad15-2e3fb07c645d/KyYZqjuw/manifest.json"),
    DefaultAddonSource("https://subsource.strem.top/c2tfOGRjZDUyY2NmZGI2ZjA1NTNjYjY2MGVlY2FjNjcxNGIwMjU3MDVhYjc4Nzk5OWUwMGY3MDM2MzAzMDEzZmZlYS9hcmFiaWMvaGlJbmNsdWRlL3R5cGU6MC8/manifest.json"),
    DefaultAddonSource("https://subdl.strem.top/c3ViZGxfRnFOOWlxUnRIVGxmVzR4aTdRendkd2FIRW1GdXMzelE3SlZKVDR5YmJrTS9BUi9oaUluY2x1ZGUv/manifest.json"),
    DefaultAddonSource("https://submaker.elfhosted.com/addon/80108f395ff085c4db50b4cab0ec4021/manifest.json"),
    DefaultAddonSource("https://opensubtitles-v3.strem.io/manifest.json"),
    // Stream sources that answer with a direct https link. Every one of them is reached at the
    // address its own author publishes: a `profile=`, an `enc_` blob or a `/u/<id>` path was the
    // configuration of one person, and a source installed from one of those stops answering the day
    // that person changes or revokes it.
    DefaultAddonSource("https://cinema.debrid.in/mode=http|livetv=0/manifest.json"),
    DefaultAddonSource("https://hd.plavio.site/manifest.json"),
    DefaultAddonSource("https://hdhub.thevolecitor.qzz.io/manifest.json"),
    DefaultAddonSource("https://pengu.uk/manifest.json"),
    DefaultAddonSource("https://stremio.alirostami.com/streams/manifest.json"),
    DefaultAddonSource("https://disney-arabic-dub.onrender.com/manifest.json"),
    DefaultAddonSource("https://manifest.desitvhub.eu.org/manifest.json"),
    DefaultAddonSource("https://muvibox.znzcloud.my.id/manifest.json"),
    DefaultAddonSource("https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json"),
    DefaultAddonSource("https://stremio-movierulz-taupe.vercel.app/manifest.json"),
    // Stream sources that answer with an info hash, played by the app's own p2p engine.
    DefaultAddonSource("https://torrentio.strem.fun/language=arabic|qualityfilter=480p,cam,other/manifest.json"),
    DefaultAddonSource("https://meteorfortheweebs.midnightignite.me/manifest.json"),
    DefaultAddonSource("https://torrentsdb.com/eyJsYW5ndWFnZSI6WyJhcmFiaWMiXX0=/manifest.json"),
    DefaultAddonSource("https://torrentclaw.com/api/stremio/manifest.json"),
    DefaultAddonSource("https://addon.peerflix.mov/manifest.json"),
    DefaultAddonSource("https://streamx.electron.al/manifest.json"),
).filter { source -> isKeptDefaultAddon(source.manifestUrl) }
    // One entry per kept catalog host, so ArabCinemeta's two variants count once.
    .distinctBy { source ->
        KeptCatalogAddonHosts.firstOrNull { host ->
            source.manifestUrl.contains(host, ignoreCase = true)
        } ?: source.manifestUrl
    }


/** The 1-based position of a bundled source, or null when the URL is not one of them. */
internal fun builtInSourceOrder(manifestUrl: String): Int? {
    val index = DefaultAddonSources.indexOfFirst { source -> source.manifestUrl == manifestUrl }
    return if (index >= 0) index + 1 else null
}

/** True when the source ships with the app rather than being added by the user. */
internal fun isBuiltInSource(manifestUrl: String): Boolean = builtInSourceOrder(manifestUrl) != null
