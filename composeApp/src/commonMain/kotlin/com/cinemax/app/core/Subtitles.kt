package com.cinemax.app.core

/** One subtitle cue in milliseconds. */
data class SubtitleCue(
    val startMillis: Long,
    val endMillis: Long,
    val text: String
)

/**
 * Parses SRT and WebVTT subtitle files. WebVTT headers, cue identifiers, cue
 * settings and simple inline tags are dropped; ASS/SSA files are not supported.
 */
fun parseSubtitles(content: String): List<SubtitleCue> {
    val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
    val cues = mutableListOf<SubtitleCue>()
    val blocks = normalized.split(Regex("\n\\s*\n"))
    for (block in blocks) {
        val lines = block.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) continue
        val timingIndex = lines.indexOfFirst { it.contains("-->") }
        if (timingIndex < 0) continue
        val timing = lines[timingIndex]
        val start = parseTimestamp(timing.substringBefore("-->").trim()) ?: continue
        val endPart = timing.substringAfter("-->").trim().split(Regex("\\s+")).firstOrNull() ?: continue
        val end = parseTimestamp(endPart) ?: continue
        val text = lines.drop(timingIndex + 1)
            .joinToString("\n")
            .let(::cleanCueText)
        if (text.isNotBlank()) cues.add(SubtitleCue(start, end, text))
    }
    return cues.sortedBy { it.startMillis }
}

/** The cue that should currently be shown, or null between cues. */
fun activeCue(cues: List<SubtitleCue>, positionMillis: Long): String? =
    cues.lastOrNull { positionMillis >= it.startMillis && positionMillis <= it.endMillis }?.text

private fun parseTimestamp(value: String): Long? {
    val cleaned = value.replace(',', '.').trim()
    if (!cleaned.contains(':')) return null
    val parts = cleaned.split(':')
    if (parts.size < 2) return null
    val seconds = parts.last().toDoubleOrNull() ?: return null
    val minutes = parts[parts.size - 2].toDoubleOrNull() ?: return null
    val hours = if (parts.size >= 3) parts[parts.size - 3].toDoubleOrNull() ?: 0.0 else 0.0
    if (seconds < 0 || minutes < 0 || hours < 0) return null
    return ((hours * 3600 + minutes * 60 + seconds) * 1000).toLong()
}

private fun cleanCueText(text: String): String = text
    .replace(Regex("\\{\\\\[^}]*}"), "")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()
