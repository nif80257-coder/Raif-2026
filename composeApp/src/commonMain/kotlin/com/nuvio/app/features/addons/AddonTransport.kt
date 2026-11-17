package com.nuvio.app.features.addons

/**
 * How a source delivers what it finds, as far as the installed manifest can tell.
 *
 * The app has no debrid subscription behind it, so the torrent bucket is what plays through the
 * built-in p2p engine and the direct bucket is what plays over plain HTTP.
 */
internal enum class AddonTransport {
    /** Torrent results; played by the p2p engine. */
    Torrent,

    /** Direct HTTP links. */
    Direct,

    /** No stream resource: catalogs, metadata or subtitles only. */
    Catalog,
}

/**
 * Sources known to return torrents rather than playable HTTP links.
 *
 * A manifest does not say which it is, so this is the list of the hosts that were checked and
 * serve torrent results. Anything else with a stream resource is treated as direct, which is the
 * safer default: a direct link plays even when p2p is unavailable.
 */
internal val TorrentSourceHosts: List<String> = listOf(
    "torrentio.strem.fun",
    "mediafusion.elfhosted.com",
    "meteorfortheweebs.midnightignite.me",
    "torrentsdb.com",
    "torrentclaw.com",
    "brazuca-torrents",
    "addon.peerflix.mov",
    "hdhub.thevolecitor.qzz.io",
    "cinetorrent-addon",
    "stremio.yukistreams.xyz",
)

internal fun ManagedAddon.transport(): AddonTransport {
    val resourceNames = manifest?.resources?.map { resource -> resource.name }?.toSet()
    if (resourceNames != null && "stream" !in resourceNames) {
        return AddonTransport.Catalog
    }
    val host = manifestUrl.substringAfter("://", manifestUrl).substringBefore('/')
    return if (TorrentSourceHosts.any { host.contains(it, ignoreCase = true) }) {
        AddonTransport.Torrent
    } else {
        AddonTransport.Direct
    }
}
