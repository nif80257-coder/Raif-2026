package com.nuvio.app.features.addons

/**
 * Add-ons installed automatically, so a profile opens with the curated source list from
 * `Raif 2/raif-sources.json` instead of an empty home screen.
 *
 * The bundle is installed once: entries already present are skipped, and the seed marker is only
 * written once at least one add-on actually installed, so an offline first launch retries later.
 */
internal data class DefaultAddonSource(
    val manifestUrl: String,
)

/**
 * Bundled sources that were dropped after checking every host live.
 *
 * Five were down (404, 503 or 526), Comet was shipped with neither a debrid service nor torrents
 * so it could never return a stream, and AutoStream and Jackettio carried a Real-Debrid key
 * inside their manifest URLs — a key that is not the user's and cannot work for them.
 *
 * Matched by host so the exact long config URLs do not have to be repeated here.
 */
internal val RetiredDefaultAddonHosts: List<String> = listOf(
    // Down when checked.
    "autostream-addon.onrender.com",
    "comet.elfhosted.com",
    "froststream.cloutteam.com",
    "jackettio.elfhosted.com",
    "sup-nyp1.onrender.com",
    "dexter.q-1208.fun",
    "rophim.q-1208.fun",
    "filmwhisper.dev",
    // Carries an Orion API key that belongs to whoever built the URL, not to this app's user, so
    // every Orion link is useless here and the key must not ship.
    "orion.baby-beamup.club",
    // Anime only.
    "onepace.e6ip.com",
    "fedew04.github.io",
    // Catalogs, metadata or live channels beyond the three kept catalogs.
    "aiometadata.elfhosted.com",
    "7a82163c306e-stremio-netflix-catalog-addon",
    "dc-timeline-stremio.onrender.com",
    "marvel-timeline-stremio.onrender.com",
    "stremio-torrentfreak.krazykayaker.com",
    "top-streaming.stream",
    // The exact USA TV Next manifest, not every add-on hosted on raw.githubusercontent.com: that
    // host is where many hand-added add-ons live, and matching it by host retired all of them.
    "raw.githubusercontent.com/yowmamasita/usa-tv-next",
)

/**
 * The three catalog sources that stay installed: one global, one Arabic, one Arabic collections.
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
    RetiredDefaultAddonHosts.any { host -> manifestUrl.contains(host, ignoreCase = true) }

/**
 * Only the three catalogs, the subtitle providers, and every source that returns streams for
 * movies and series. Catalogs other than the kept three are dropped so the install stays small.
 */
private fun isKeptDefaultAddon(manifestUrl: String): Boolean =
    KeptCatalogAddonHosts.any { manifestUrl.contains(it, ignoreCase = true) } ||
        KeptSubtitleAddonHosts.any { manifestUrl.contains(it, ignoreCase = true) } ||
        !isRetiredDefaultAddon(manifestUrl)

internal val DefaultAddonSources: List<DefaultAddonSource> = listOf(
    DefaultAddonSource("https://aiometadata.elfhosted.com/stremio/defc0604-c821-4d84-827f-02239172cde0/manifest.json"),
    DefaultAddonSource("https://torrentio.strem.fun/language=arabic|qualityfilter=480p,cam,other/manifest.json"),
    DefaultAddonSource("https://autostream-addon.onrender.com/secondBest=1&realdebrid=H2W54DA7N4DOG34SVLGTLPBWWEMKQ2J6GTFEMM255NSEFICO6XCQ&lang_prio=EN,AR&blacklist=4K,1080p,Dolby,HDR,DV&include_nuvio=1/manifest.json"),
    DefaultAddonSource("https://subtito.com/user/83ba7951-84b5-4fc2-ad15-2e3fb07c645d/KyYZqjuw/manifest.json"),
    DefaultAddonSource("https://subsource.strem.top/c2tfOGRjZDUyY2NmZGI2ZjA1NTNjYjY2MGVlY2FjNjcxNGIwMjU3MDVhYjc4Nzk5OWUwMGY3MDM2MzAzMDEzZmZlYS9hcmFiaWMvaGlJbmNsdWRlL3R5cGU6MC8/manifest.json"),
    DefaultAddonSource("https://subdl.strem.top/c3ViZGxfRnFOOWlxUnRIVGxmVzR4aTdRendkd2FIRW1GdXMzelE3SlZKVDR5YmJrTS9BUi9oaUluY2x1ZGUv/manifest.json"),
    DefaultAddonSource("https://addon.torrin.app/cfg_YnS-K1q9tBBN1H2y21aWOESwVPFwE5CU/manifest.json"),
    DefaultAddonSource("https://aiostreamsfortheweebsstable.midnightignite.me/stremio/c1a28aae-ef02-4e53-bce9-f93fa1461517/eyJpIjoiQ0xiT1MxbnJ2d0oyMHdWMTJpd3R0UT09IiwiZSI6IkR0UFhCN1ZFZm05MHdsUW02ZHdiSTNSOFM5d2ZlQ0c0Rm5aZzhJdkNYbDA9IiwidCI6ImEifQ/manifest.json"),
    DefaultAddonSource("https://comet.elfhosted.com/eyJtYXhSZXN1bHRzUGVyUmVzb2x1dGlvbiI6MiwibWF4U2l6ZSI6MCwiY2FjaGVkT25seSI6ZmFsc2UsInNvcnRDYWNoZWRVbmNhY2hlZFRvZ2V0aGVyIjpmYWxzZSwicmVtb3ZlVHJhc2giOnRydWUsInJlc3VsdEZvcm1hdCI6WyJhbGwiXSwiZGVicmlkU2VydmljZXMiOltdLCJlbmFibGVUb3JyZW50IjpmYWxzZSwiZGVkdXBsaWNhdGVTdHJlYW1zIjpmYWxzZSwic2NyYXBlRGVicmlkQWNjb3VudFRvcnJlbnRzIjpmYWxzZSwiZGVicmlkU3RyZWFtUHJveHlQYXNzd29yZCI6IiIsImxhbmd1YWdlcyI6eyJyZXF1aXJlZCI6WyJhciJdLCJhbGxvd2VkIjpbImFyIl0sImV4Y2x1ZGUiOltdLCJwcmVmZXJyZWQiOlsiYXIiXX0sInJlc29sdXRpb25zIjp7InI1NzZwIjpmYWxzZSwicjQ4MHAiOmZhbHNlLCJyMzYwcCI6ZmFsc2UsInIyNDBwIjpmYWxzZSwidW5rbm93biI6ZmFsc2V9LCJvcHRpb25zIjp7InJlbW92ZV9yYW5rc191bmRlciI6LTEwMDAwMDAwMDAwLCJhbGxvd19lbmdsaXNoX2luX2xhbmd1YWdlcyI6ZmFsc2UsInJlbW92ZV91bmtub3duX2xhbmd1YWdlcyI6ZmFsc2V9fQ==/manifest.json"),
    DefaultAddonSource("https://top-streaming.stream/22914484-5df6-4c5e-9bdb-ef43be42513e/manifest.json"),
    DefaultAddonSource("https://froststream.cloutteam.com/usr_04883ba4646b680a0315/manifest.json"),
    DefaultAddonSource("https://94c8cb9f702d-brazuca-torrents.baby-beamup.club/manifest.json"),
    DefaultAddonSource("https://cncverse.dpdns.org/u/p_ek8k6v7epbmc/manifest.json"),
    DefaultAddonSource("https://hdhub.thevolecitor.qzz.io/eyJ0b3Jib3giOiJ1bnNldCIsInF1YWxpdGllcyI6IjIxNjBwLDEwODBwLDcyMHAiLCJzb3J0IjoiZGVzYyIsImNhdGFsb2dzIjoiIn0/manifest.json"),
    DefaultAddonSource("https://onepace.e6ip.com/eyJkZWJyaWRTZXJ2aWNlcyI6W3sic2VydmljZSI6InAycCJ9XX0=/manifest.json"),
    DefaultAddonSource("https://addon.peerflix.mov/enc_BD0t3qdlF5yb_eu68KcvhS6t2irF3_xCkzy1cy7AtLr2DRsPVaGaTa3FGsLXwC0s0YYQwBnU-AYAt2_gPjAGeJ3o-ExsD2BwoMmdWZXwOjHbC7eSGaBrbPk6LX77YTYcNgpLwXt8tsX09-djaaqjAi4QvNDl8BFgNdAXuMzS/manifest.json"),
    DefaultAddonSource("https://cinema.debrid.in/sort=qualityseeders|limit=200|subtitleLanguages=ar|salanguages=ta,te,ml,kn,hi,gu|asiandrama=0|extraanime=0|minseeders=0|maxsize=0|streamingservices=netflix,prime,disney,hulu,max,apple,peacock,paramount|sacatalogs=0|bollywood=0|marvelcatalog=1|dccatalog=1|animecatalogs=0|anilistcatalogs=0|airingweek=1|advisories=1|horrorcatalogs=0|seedrlinks=25|uncachedlimit=10|subf2m=1|yify=1|profile=970d9cd3af6706d88733fe7ff57d3dbf|mode=mixed|httpsources=4khdhub,moviebox,vixsrc,be-vidsrc,be-rgshows,archiveorg,archiveorgdub|top10=1|yearpickers=1|curated=hidden,short,digital,awards,tonight/manifest.json"),
    DefaultAddonSource("https://discussio.elfhosted.com/manifest.json"),
    DefaultAddonSource("https://jackettio.elfhosted.com/eyJtYXhUb3JyZW50cyI6OCwicHJpb3RpemVQYWNrVG9ycmVudHMiOjIsImV4Y2x1ZGVLZXl3b3JkcyI6W10sImRlYnJpZElkIjoicmVhbGRlYnJpZCIsImhpZGVVbmNhY2hlZCI6ZmFsc2UsInNvcnRDYWNoZWQiOltbInF1YWxpdHkiLHRydWVdLFsic2l6ZSIsdHJ1ZV1dLCJzb3J0VW5jYWNoZWQiOltbInNlZWRlcnMiLHRydWVdXSwiZm9yY2VDYWNoZU5leHRFcGlzb2RlIjpmYWxzZSwicHJpb3RpemVMYW5ndWFnZXMiOltdLCJpbmRleGVyVGltZW91dFNlYyI6NjAsIm1ldGFMYW5ndWFnZSI6IiIsImVuYWJsZU1lZGlhRmxvdyI6ZmFsc2UsIm1lZGlhZmxvd1Byb3h5VXJsIjoiIiwibWVkaWFmbG93QXBpUGFzc3dvcmQiOiIiLCJtZWRpYWZsb3dQdWJsaWNJcCI6IiIsInN0cmVtdGhydVVybCI6Imh0dHA6Ly9lbGZob3N0ZWQtaW50ZXJuYWwuc3RyZW10aHJ1IiwicXVhbGl0aWVzIjpbMCw3MjAsMTA4MF0sImluZGV4ZXJzIjpbInN1YnNwbGVhc2UiLCIxMzM3eCIsImJ0ZGlyZWN0b3J5IiwiZXp0diIsInRoZXBpcmF0ZWJheSIsInRoZXJhcmJnIiwidG9ycmVudGdhbGF4eWNsb25lIl0sImRlYnJpZEFwaUtleSI6IjQ1UE9MVDJBUk5STVhHT0tZWUFSRUNCRFlSWEI1T0Q1VldDRTYyUlk1VVFNQVBSWFNINFEifQ==/manifest.json"),
    DefaultAddonSource("https://collections.stremiolabar.com/api/addon/H4sIAAAAAAAAE51XW4_jJhj9L3kuUpKZaWf2cTVVV2q3UnekUS-qLALYZoyBBTy5VP3v_bAdxzjEZiuNJg45HA7f3f-syOrDXytncOV0sxOcZFYqXGXOMEm5LFCt3jmzq-_mMLZU-wUIlrxmSWQd8kxZU8BY1xiRSeZywQ_IKY02a3StMAGboLTi1M4J1Uo3ApsUSOQSO3VAKs85YehhHd3MAd3q3j6s5w4JcJGT2h0oQLn3GQOcRbc7Ei7X4iLnXvbXzDHTu2AdgmABKYmMco5Jp2rsFLOIqDqEEWYczzmjuWG2jDlaYMesQwMQtUi4adzJJfNa0M7v6ejAG8gvbzctjQTWI5JH5CAKb0TKN5IgwzAFQ1jEKHdcyfAGJS9KZLDzsTdykGBgGgPhQrPNl5KHK28fX__cT8RVzc5wUm3vslZYI-FMoAWTDHYbMWyfT3sVLunnutqHS4-f3j5PtDy__7gOl35Zb4qXcOnr4aPYTvRp-JFb3JiMNAKsJLC1nExNTLDguTKSY5k5UnfXKIyCz9loxMRbNiVue2QsN73dSsYDt2IhWjfOckLgMnpMOb1HxtIQZJT368fMOiwpavSA1YxwLKbpDWkFKa2xUcPJyXqpwTVOkdsBY7bCFiIL7KigEkiruYGvuyPCUGsbKN9OmeO1YGzBrx3p7LGlMkYlldgeebus1Q240zLfeyCSZL8hCt0-dDkdnB5Nbb9QKiGOe6Uo5LdWBqJ7kmGvv73-NEl1cI7aM3aO05anPTJ03Ijly-vPJt4nzhZwpeFCsCRrDdiIR2suad6QKs7zhk8nqFIMnJ1zyd2xyLQ9EjCCKjgk7VQGwoafPPWsHks4ynmK8h4Z0a0a32WsxtBSu__soIXyFXVcEEY2zV_Yp3gbKSki5jKotL2rq6G5UTXaPD1CE1XwR_EkurttEXndD1SRxkL9u7plST3QZm3WXLA1BBqOwM81IgqHUtEeAYYS9Xxaj_YkGD844vqK736aUYYXXEIgxEYRMOOY48Ycgp-2D3afeeSeVzxys5AWah-WytRwaGDfqF_rowX_H7_ds_Azlo6Trhz7vd3UgoaxZeCKbATL9GW86ureJBbvH3M60Uy5leyGX_wi9AetM80PvpnBsNwFOuylXE2pYPSo98pU_UQLWiBHST-EjIkLP_4yQUxjS2YySnrq8fgw01P_DzYSKBd3TRjjXhrPJW-fD0VIZmFmwNCRcsBIdaMWPP3w8utkxLn__Q_ZhFRXhRtGlPZF4TrO99iR8qK77x6wt7qxiUDhtA4CY854vWHAnQJqb8IUP4csMRwHLU5usx4WT8QCj0raTU3nV61l9WfkzEsYGDCBbwybeVGMX26nLa8HjI_6PhT9KEN8xkKKxIfN_h5zCRoDzt93mW2EWvQGVJgThLoedbFk-LzOVOYr7KLmcqdQjVMi6IycV5rAN4Yt6oNyK5hvWssCB-iCLRMYA9yiRt8La3hLcgkiL9h5lUmcIXBRZ9mIJkFiC1vw8gLTgIlUCM0wUTDqDkNLlAeeScVM-zHYrt8JTdicIM5JSAxpStS7nzMG6pH3xs0Jlvy0j5qv5ntX3JV31epvOH71YYX9iwQ9PxBxftLwYDTdrf79DwVrqfK2EwAA/manifest.json?lang=ar"),
    DefaultAddonSource("https://arabcity.fly.dev/YWxs/manifest.json"),
    DefaultAddonSource("https://submaker.elfhosted.com/addon/80108f395ff085c4db50b4cab0ec4021/manifest.json"),
    DefaultAddonSource("https://mediafusion.elfhosted.com/D-gDvLuf64LMYxtL1FuWsrFb_p5M2lL2j3IFLOAqMtyboaex7zmCRefPZNVulSFhmoaTt6bvTa4i5DurekhKmiT5Fky3a98OTRJhJBz5Dtg9IwGYGsIwijiVtrznQiCpz2kpSQu6qzeSrqzVSnbJcdLF3L7oV2MLmMKUzQj3K_66bBrV7Z8EcuxvV8kQbKhtJovROXpXVg_PfMkOcp49J6DB21kTAH1BYpGIn6fWaDPrWJtiPPmdyknsWO7KUpDHRl0sQntKky_vvGmz2haBOcS7rIQgvlQudTRYQL-tVnn1Wpy61B7vlUvHh-_-m907TJ9M0J4A-2TFfWrFmmWgVfMHMvWzv5BauByNOmsVCCQ4n8dAbqe9X56yEczI-ldqp9ufix2F9wATYD03aK9jKbJKr3_S3Ym01aFhLjZyifIzuzGttT2P55a8zeUKamXDpxWQ2YQGPJzmQea_ynPylnDlzJhNTbJFxILkXLwsP05bTMLLrsZZXbwIUF92lBaasQIyrvOH9SDl666N56LRhKYXHjp5qlBz9hDrpMt_1E8axzaVG39-2dfCnucmj94h3nFzGr62YfcK0QeyhnQPOD4_VHtRbqFMmd0qqPw13Hl9FpwFGWFiJwvQh4Cfp50en26A2qgr3CD6mEFy7ro56IsIvjr4dO_YJvG99d11rPQyoDWbWhAhgma8LGxSTTJ7BK/manifest.json"),
    DefaultAddonSource("https://meteorfortheweebs.midnightignite.me/eyJjYWNoZWRPbmx5IjpmYWxzZSwic2tpcFJlbGVhc2VGaWx0ZXIiOnRydWUsInJlbW92ZVRyYXNoIjpmYWxzZSwicmVtb3ZlU2FtcGxlcyI6ZmFsc2UsImFsbG93QWR1bHQiOnRydWUsImV4Y2x1ZGUzRCI6ZmFsc2UsImVuYWJsZVNlYURleCI6ZmFsc2UsInNob3dZb3VyTWVkaWEiOmZhbHNlLCJ5b3VyTWVkaWFMZWdhY3lNb2RlIjpmYWxzZSwibWluU2VlZGVycyI6MCwibWF4UmVzdWx0cyI6MCwibWF4UGVyUmVzb2x1dGlvbiI6MCwicmVzb2x1dGlvbnMiOltdLCJwcmVmZXJyZWRMYW5ncyI6WyJhciIsImVuIl0sImxhbmd1YWdlcyI6WyJhciJdLCJleGNsdWRlZExhbmdzIjpbXSwicmVzdWx0Rm9ybWF0IjpbInRpdGxlIiwicXVhbGl0eSIsInNpemUiLCJhdWRpbyIsInN1YmxhbmciLCJzb3VyY2UiLCJhdWRpb2xhbmciXSwibGFuZ3VhZ2VGb3JtYXQiOiJmbGFncyIsInNvcnRPcmRlciI6WyJ5b3VybWVkaWEiLCJjYWNoZWQiLCJzZWVkZXgiLCJyZXNvbHV0aW9uIiwicXVhbGl0eSIsInNjb3JlIiwibGFuZ3VhZ2UiLCJzZWVkZXJzIiwic2l6ZSIsInBhY2siLCJoZHIiLCJjb2RlYyJdLCJhbGxvd1AyUCI6dHJ1ZSwiZXhjbHVkZWRTb3VyY2VzIjpbXX0/manifest.json"),
    DefaultAddonSource("https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhbXAsYXRwLGhibTo6U0E6MTc5NDI1MDkzMjIyNTowOjA6U0E%3D/manifest.json"),
    DefaultAddonSource("https://watchhub.strem.io/manifest.json"),
    DefaultAddonSource("https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json"),
    DefaultAddonSource("https://opensubtitles-v3.strem.io/manifest.json"),
    DefaultAddonSource("https://150203dd784e-cinetorrent-addon.baby-beamup.club/manifest.json"),
    DefaultAddonSource("https://torrentsdb.com/eyJsYW5ndWFnZSI6WyJhcmFiaWMiXX0=/manifest.json"),
    DefaultAddonSource("https://addon.peerflix.mov/enc_BBgenvsYVV2lGfn3rpeDWFvjJ8mkO0KYlHAYmK5qdLC3ALE3D8PCkBJVkXM_959e1UkY5OBDAK0U2tNyHzRM_LIt2r-1dJqmdEpsbr6QnWj6kv1WgFAO_X1lgyfA8wZnJHaOhutr7eagWess36iuCKUJ-utP3NNpfHzAXHli/manifest.json"),
    DefaultAddonSource("https://cinema.debrid.in/sort=qualityseeders|limit=200|subtitleLanguages=ar|salanguages=ta,te,ml,kn,hi,gu|asiandrama=0|extraanime=0|minseeders=0|maxsize=0|streamingservices=netflix,prime,disney,hulu,max,apple,peacock,paramount|sacatalogs=0|bollywood=0|marvelcatalog=1|dccatalog=1|animecatalogs=0|anilistcatalogs=0|airingweek=1|advisories=1|horrorcatalogs=0|worldnews=1|abc=1|sports=1|livetv=1|seedrlinks=25|uncachedlimit=10|subf2m=1|yify=1|profile=65fef63344fdc714bd325089e7ea345c|mode=http|httpsources=4khdhub,moviebox,vixsrc,be-vidsrc,be-rgshows,archiveorg,archiveorgdub|top10=1|yearpickers=1|curated=hidden,short,digital,awards,tonight/manifest.json"),
    DefaultAddonSource("https://torrentclaw.com/api/stremio/manifest.json"),
    DefaultAddonSource("https://fedew04.github.io/OnePaceStremio/manifest.json"),
    DefaultAddonSource("https://stremio.yukistreams.xyz/p.YAWNo7KXUmEgwsxB/manifest.json"),
    DefaultAddonSource("https://hd.plavio.site/u/w6y5YbrH9fnV25qbziG8Vw/manifest.json"),
    DefaultAddonSource("https://stremio-addon.ehtudo.app/dd18a2a84427208753ebdf9153c7338c/manifest.json"),
    DefaultAddonSource("https://sup-nyp1.onrender.com/eyJwcm92aWRlcnMiOlsieXRzIiwiZXp0diIsInRwYiIsInRneCIsIjEzMzd4IiwibnlhYSIsInNvbGlkdG9ycmVudHMiLCJydXRvciIsImtpY2thc3MiLCJtYWduZXRkbCIsInRvcnJlbnRpbyIsImtuYWJlbiIsInRoZXJhcmJnIiwibGltZXRvcnJlbnRzIiwiYml0c2VhcmNoIiwiYnQ0ZyIsInRvcmxvY2siLCJ0b3JyZW50ZG93bmxvYWRzIiwiaWRvcGUiLCJ6b29xbGUiLCJ0b3JyZW50ejIiLCJ0b3JyZW50ZnVuayIsImJ0ZGlnIiwidG9ycmVudHNkYiIsInJ1dHJhY2tlciIsImFuaW1ldG9zaG8iLCJzdWJzcGxlYXNlIiwidG9ycmVudHNjc3YiXSwibWF4UXVhbGl0eSI6IjRrIiwic2l6ZUZpbHRlciI6dHJ1ZSwibWF4U2l6ZUJ5dGVzIjoyMTQ3NDgzNjQ4MCwibWluU2l6ZUJ5dGVzIjowLCJtaW5TZWVkZXJzIjowLCJzb3J0T3JkZXIiOiJxdWFsaXR5IiwicHJpb3JpdGl6ZSI6InNpemVfYXNjIiwiZGVkdXBlIjp0cnVlLCJleGNsdWRlQ2FtIjp0cnVlLCJhdWRpb0xhbmd1YWdlIjoicHJlZmVyX2VuIiwibWF4UmVzdWx0cyI6NTAsImNvZGVjcyI6W119/manifest.json"),
    DefaultAddonSource("https://cotonetnet-cotonet.hf.space/manifest.json"),
    DefaultAddonSource("https://stremio.alirostami.com/streams/manifest.json"),
    DefaultAddonSource("https://stremio-torrentfreak.krazykayaker.com/manifest.json"),
    DefaultAddonSource("https://dexter.q-1208.fun/manifest.json"),
    DefaultAddonSource("https://rophim.q-1208.fun/manifest.json"),
    DefaultAddonSource("https://marvel-timeline-stremio.onrender.com/manifest.json"),
    DefaultAddonSource("https://dc-timeline-stremio.onrender.com/manifest.json"),
    DefaultAddonSource("https://arabcinemeta.osamayousry.com/c/rAEM3___ef_fCv-P_58BPWA/manifest.json"),
    DefaultAddonSource("https://arabcinemeta.osamayousry.com/c/rAEMAAD_ef_fCv-P_58APGA/manifest.json"),
    DefaultAddonSource("https://raw.githubusercontent.com/yowmamasita/usa-tv-next/main/manifest.json"),
    DefaultAddonSource("https://pengu.uk/zZc9NT8MwDAbg_5LzJLKOscINARIXRMUGErtUbuqtbtN4irOPDvHfYSA2mh3fx3aU90MJr73BHLwJZNSNYqcGf5is0DQWI93gElreEEo0-MGCd_E-rCx06GOmcgESIm1IpKkiNOSw5u78gZi4wNrvuzpi-_vfSIUtWwjk-KzHwtKxhUfJk-GV_p-HOu3lSdKLl_3p6HRckkBhMS_Jozl2h3Wo8sANuoMYPWq3d0k6H2fVVKePPL9-X5Iev07vny23afEWMj3Ldk8P36eBWtyzO3S7FYKLF-qgrNTnFw/manifest.json"),
    DefaultAddonSource("https://filmwhisper.dev/ca7d6018-b2bb-454d-948d-2abb4f53a34a/manifest.json"),
    DefaultAddonSource("https://v3-cinemeta.strem.io/manifest.json"),
    DefaultAddonSource("https://disney-arabic-dub.onrender.com/manifest.json"),
    DefaultAddonSource("https://streamx.electron.al/u_3fa8eac141a8b51c32af/manifest.json"),
    DefaultAddonSource("https://peenoise.fkye.workers.dev/manifest.json"),
    DefaultAddonSource("https://sc.k-20.xyz/manifest.json"),
    DefaultAddonSource("https://2ecbbd610840-stremio-ar.baby-beamup.club/manifest.json"),
    DefaultAddonSource("http://manifest.desitvhub.eu.org/manifest.json"),
    DefaultAddonSource("https://muvibox.znzcloud.my.id/manifest.json"),
    DefaultAddonSource("https://stremio-movierulz-taupe.vercel.app/manifest.json"),
).filterNot { source -> isRetiredDefaultAddon(source.manifestUrl) }
    .filter { source -> isKeptDefaultAddon(source.manifestUrl) }
    // One entry per kept catalog host, so ArabCinemeta's two variants count once.
    .distinctBy { source ->
        KeptCatalogAddonHosts.firstOrNull { host ->
            source.manifestUrl.contains(host, ignoreCase = true)
        } ?: source.manifestUrl
    }
