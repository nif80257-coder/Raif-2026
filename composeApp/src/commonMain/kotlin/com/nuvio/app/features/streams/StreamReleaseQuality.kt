package com.nuvio.app.features.streams

/**
 * How a release was made, ranked the way the Sootio add-on ranks its results: a remux keeps the
 * disc's quality, and every step below it gives something away — first the disc, then the bitrate.
 *
 * A Stremio source hands a client nothing but the text it displays, so the tier is read from that
 * text (name and description) once per stream, never inside a comparator.
 */
internal enum class ReleaseTier(val rank: Int) {
    CAM(0),
    HDTV(1),
    WEBRIP(2),
    WEBDL(3),
    BLURAY(4),
    REMUX(5),
    ;

    companion object {
        fun of(text: String): ReleaseTier? {
            val value = text.lowercase()
            return when {
                CAM_PATTERN.containsMatchIn(value) -> CAM
                REMUX_PATTERN.containsMatchIn(value) -> REMUX
                BLURAY_PATTERN.containsMatchIn(value) -> BLURAY
                WEBDL_PATTERN.containsMatchIn(value) -> WEBDL
                WEBRIP_PATTERN.containsMatchIn(value) -> WEBRIP
                HDTV_PATTERN.containsMatchIn(value) -> HDTV
                else -> null
            }
        }
    }
}

private val CAM_PATTERN = Regex(
    "\\b(hd)?cam(rip)?\\b|\\btelesync\\b|\\btelecine\\b|\\bhdts\\b|\\bts\\b|\\btc\\b|\\bscreener\\b|\\bdvdscr\\b|\\bscr\\b",
)
private val REMUX_PATTERN = Regex("\\bremux\\b")
private val BLURAY_PATTERN = Regex("\\bblu-?ray\\b|\\bbd-?rip\\b|\\bbr-?rip\\b|\\bbdremux\\b")
private val WEBDL_PATTERN = Regex("\\bweb-?dl\\b")
private val WEBRIP_PATTERN = Regex("\\bweb-?rip\\b")
private val HDTV_PATTERN = Regex("\\bhd-?tv\\b|\\bhdrip\\b|\\bdvd-?rip\\b")

/** Re-encode groups whose only selling point is the file size, in the Sootio sense of junk. */
private val JUNK_GROUP_PATTERN = Regex("\\b(yify|yts|axxo)\\b")

/** The text a source shows for this stream, which is all it tells a client about the release. */
internal fun StreamItem.releaseQualityText(): String =
    listOfNotNull(streamLabel, streamSubtitle).joinToString(separator = " ")

internal fun StreamItem.releaseTier(): ReleaseTier? = ReleaseTier.of(releaseQualityText())

/**
 * A release worth leaving out when the user asked for a decent copy: a camera or screener
 * recording, or a size-only re-encode. Playable, but never the copy to pick on purpose.
 */
internal fun StreamItem.isJunkRelease(): Boolean {
    val text = releaseQualityText().lowercase()
    return ReleaseTier.of(text) == ReleaseTier.CAM || JUNK_GROUP_PATTERN.containsMatchIn(text)
}
