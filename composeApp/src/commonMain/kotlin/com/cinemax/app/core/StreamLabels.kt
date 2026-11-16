package com.cinemax.app.core

/**
 * Short quality/format badges read from a stream's name and title, e.g. 4K, 1080p,
 * HDR or HEVC. Most Stremio stream addons put those tokens in the description
 * rather than in structured fields.
 */
fun streamBadges(name: String?, title: String?): List<String> {
    val text = listOfNotNull(name, title).joinToString(" ").lowercase()
    if (text.isBlank()) return emptyList()
    val badges = mutableListOf<String>()
    when {
        text.contains("2160") || text.contains("4k") || text.contains("uhd") -> badges += "4K"
        text.contains("1080") -> badges.add("1080p")
        text.contains("720") -> badges.add("720p")
        text.contains("480") -> badges.add("480p")
    }
    if (text.contains("hdr") || text.contains("dolby vision") || text.contains("dovi") || text.contains(" dv ")) {
        badges.add("HDR")
    }
    if (text.contains("hevc") || text.contains("x265") || text.contains("h265")) badges.add("HEVC")
    if (text.contains("cam") || text.contains("hdcam") || text.contains("ts ")) badges.add("CAM")
    if (text.contains("remux")) badges.add("REMUX")
    return badges.distinct().take(3)
}

/**
 * The language a stream advertises in its own text — "مدبلج", "arabic", "multi" —
 * as an Arabic label, or null when the addon says nothing about it.
 */
fun streamLanguageLabel(name: String?, title: String?): String? {
    val text = listOfNotNull(name, title).joinToString(" ").lowercase()
    if (text.isBlank()) return null
    return when {
        text.contains("مدبلج") || text.contains("dub") -> "مدبلج"
        text.contains("arabic") || text.contains("عربي") || text.contains("عربية") -> "عربي"
        text.contains("multi") -> "متعدد اللغات"
        else -> null
    }
}
