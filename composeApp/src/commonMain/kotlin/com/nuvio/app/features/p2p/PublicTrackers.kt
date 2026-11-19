package com.nuvio.app.features.p2p

/**
 * The public announce URLs a torrent opened by the app is registered with.
 *
 * A stream usually advertises only a handful of trackers, and some advertise none at all, so a
 * magnet built from it alone reaches a fraction of the peers that already hold the file. The app
 * therefore announces to this list as well: the whole set is tried on every torrent, and a tracker
 * that is down or blocked by the network costs a request, not the stream.
 *
 * The list is shared by both platforms so the peer set no longer depends on which one the app runs
 * on. Entries are kept as written by their operators and de-duplicated, since announce URLs are
 * matched exactly by the engine.
 */
internal object PublicTrackers {
    val All: List<String> = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://tracker.openbittorrent.com:80/announce",
        "udp://explodie.org:6969/announce",
        "udp://opentracker.i2p.rocks:6969/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.qu.ax:6969/announce",
        "udp://tracker.plx.im:6969/announce",
        "udp://tracker.nyaa.vc:6969/announce",
        "udp://tracker.iperson.xyz:6969/announce",
        "udp://tracker.gmi.gd:6969/announce",
        "udp://tracker.fnix.net:6969/announce",
        "udp://tracker.flatuslifir.is:6969/announce",
        "udp://tracker.ducks.party:1984/announce",
        "udp://tracker.bluefrog.pw:2710/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker.startwork.cv:1337/announce",
        "udp://tracker.tryhackx.org:6969/announce",
        "udp://udp.tracker.projectk.org:23333/announce",
        "udp://tracker.publictracker.xyz:6969/announce",
        "udp://zer0day.ch:1337/announce",
        "udp://tracker.0x7c0.com:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://tracker.dgweb.moe:6969/announce",
        "udp://tracker.leech.ie:1337/announce",
        "udp://tracker.zhuqiy.com:443/announce",
        "udp://tamas3.ynh.fr:6969/announce",
        "udp://tracker.auctor.tv:6969/announce",
        "http://tracker.renfei.net:8080/announce",
    ).distinct()

    /**
     * The trackers to announce for one torrent: the public set first, then whatever the stream
     * named itself, with each announce URL kept once.
     */
    fun withStreamTrackers(streamTrackers: List<String>): List<String> =
        (All + streamTrackers)
            .map { tracker -> tracker.trim() }
            .filter { tracker -> tracker.isNotEmpty() }
            .distinct()
}
