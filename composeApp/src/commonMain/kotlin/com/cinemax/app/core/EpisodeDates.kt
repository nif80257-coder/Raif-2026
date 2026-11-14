package com.cinemax.app.core

import kotlinx.datetime.LocalDate

/** An episode's release line: the text to show and whether it is still to come. */
data class EpisodeRelease(val text: String, val upcoming: Boolean)

private val ARABIC_MONTHS = listOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
)

/**
 * Reads the date of a Stremio `released` value. Addons send a full ISO
 * timestamp ("2020-01-05T00:00:00.000Z") or a plain date, so only the first ten
 * characters are read. Anything else yields null rather than a wrong date.
 */
fun parseReleaseDate(raw: String?): LocalDate? {
    val text = raw?.trim()?.take(10) ?: return null
    if (text.length < 10) return null
    return runCatching { LocalDate.parse(text) }.getOrNull()
}

/**
 * The line shown under an episode: "5 يناير 2020", or "قريبًا • 5 يناير 2020"
 * when the date is after [today], so an episode with no sources yet explains why.
 * Null when the addon sent no usable date.
 */
fun episodeReleaseLabel(raw: String?, today: LocalDate): EpisodeRelease? {
    val date = parseReleaseDate(raw) ?: return null
    val written = "${date.dayOfMonth} ${ARABIC_MONTHS[date.monthNumber - 1]} ${date.year}"
    val upcoming = date > today
    return EpisodeRelease(text = if (upcoming) "قريبًا • $written" else written, upcoming = upcoming)
}
