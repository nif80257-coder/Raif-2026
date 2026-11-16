package com.cinemax.app.core

import com.cinemax.app.addons.StreamOption

/**
 * Ranks a stream for the source list: a direct link always beats a torrent-only
 * entry, then resolution, then the format details viewers care about.
 */
fun sourceScore(option: StreamOption): Int {
    val badges = streamBadges(option.stream.name, option.stream.title)
    var score = 0
    if ("4K" in badges) score += 400
    if ("1080p" in badges) score += 300
    if ("720p" in badges) score += 200
    if ("480p" in badges) score += 100
    if ("REMUX" in badges) score += 40
    if ("HDR" in badges) score += 30
    if ("HEVC" in badges) score += 10
    if ("CAM" in badges) score -= 50
    if (option.stream.url == null) score -= 1000
    return score
}

/**
 * Sorts sources the way the source sheet shows them: direct links first, best
 * quality first, and the link that worked last time pinned to the top.
 */
fun rankedSources(
    options: List<StreamOption>,
    onlyDirect: Boolean = false,
    lastPlayedUrl: String? = null
): List<StreamOption> {
    val filtered = if (onlyDirect) options.filter { it.stream.url != null } else options
    val ranked = filtered.sortedByDescending { sourceScore(it) }
    if (lastPlayedUrl == null) return ranked
    return ranked.sortedByDescending { it.stream.url == lastPlayedUrl }
}

/**
 * The next direct link to try when a source fails: the best ranked link that has
 * not been attempted yet, or null once [maxAttempts] links have been tried, so a
 * title whose sources are all dead cannot bounce through the whole list.
 */
fun nextUntriedSource(
    options: List<StreamOption>,
    attemptedUrls: Set<String>,
    lastPlayedUrl: String? = null,
    maxAttempts: Int = 3
): StreamOption? {
    if (attemptedUrls.size >= maxAttempts) return null
    return rankedSources(options, onlyDirect = true, lastPlayedUrl = lastPlayedUrl)
        .firstOrNull { option ->
            val url = option.stream.url
            url != null && url !in attemptedUrls
        }
}

/**
 * The source list as the detail screen shows it: the static ranking plus the
 * remembered track record of each link, so a link that recently failed drops
 * below the rest (without disappearing) and one that played before rises. The
 * last-played link stays pinned on top.
 */
fun rankSources(
    options: List<StreamOption>,
    stats: Map<String, SourceStats> = emptyMap(),
    onlyDirect: Boolean = false,
    lastPlayedUrl: String? = null,
    pinnedUrl: String? = null,
    nowEpochMillis: Long = 0L
): List<StreamOption> {
    val filtered = if (onlyDirect) options.filter { it.stream.url != null } else options
    val scored = filtered.sortedByDescending { option ->
        sourceScore(option) + (stats[streamFingerprint(option)]?.penalty(nowEpochMillis) ?: 0)
    }
    val withLastPlayed = if (lastPlayedUrl == null) {
        scored
    } else {
        scored.sortedByDescending { it.stream.url == lastPlayedUrl }
    }
    if (pinnedUrl == null) return withLastPlayed
    return withLastPlayed.sortedByDescending { it.stream.url == pinnedUrl }
}
