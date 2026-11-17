package com.nuvio.app.features.player

/**
 * Orders the subtitles an add-on returned so the ones in the user's language come first.
 *
 * The five bundled subtitle providers differ wildly: SubDL and SubSource answer with dozens of
 * Arabic tracks, Subtito often with one, and OpenSubtitles is English-first. Without ordering, the
 * list is the order the answers arrived in, so an English track from a fast add-on can sit above
 * the Arabic ones. Inside the preferred language, a stable add-on name order keeps the list from
 * shuffling between loads.
 */
internal fun orderedAddonSubtitles(
    subtitles: List<AddonSubtitle>,
    preferredLanguagePrefixes: List<String> = listOf("ar"),
): List<AddonSubtitle> {
    if (subtitles.size < 2) return subtitles
    fun AddonSubtitle.matchesPreferred(): Boolean {
        val language = language.trim().lowercase()
        return preferredLanguagePrefixes.any { prefix -> language.startsWith(prefix) }
    }
    return subtitles.sortedWith(
        compareBy<AddonSubtitle> { subtitle -> if (subtitle.matchesPreferred()) 0 else 1 }
            .thenBy { subtitle -> subtitle.display.lowercase() },
    )
}
