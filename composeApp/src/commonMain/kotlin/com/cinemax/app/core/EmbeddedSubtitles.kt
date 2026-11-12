package com.cinemax.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A subtitle track packed inside the video file itself — the "internal" tracks
 * of an MKV, MP4 or TS file. [id] is the player's track id used to select it,
 * [index] its position among the file's subtitle tracks (some players only
 * accept that position), and [selected] is what the player reports back.
 */
@Serializable
data class EmbeddedSubtitleTrack(
    val id: Long = -1,
    val index: Int = -1,
    val language: String = "",
    val title: String = "",
    val codec: String = "",
    val forced: Boolean = false,
    val selected: Boolean = false
)

private val embeddedTracksJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Parses the JSON track list the native player reports for the current file.
 * A player that cannot report tracks (or reports a broken list) yields nothing.
 */
fun parseEmbeddedSubtitleTracks(raw: String): List<EmbeddedSubtitleTrack> {
    if (raw.isBlank()) return emptyList()
    val tracks = runCatching {
        embeddedTracksJson.decodeFromString<List<EmbeddedSubtitleTrack>>(raw)
    }.getOrNull().orEmpty()
    return tracks.filter { it.id >= 0 }
}

/** Arabic name of an ISO language code ("ar-SA" included), or the code itself. */
fun subtitleLanguageName(code: String?): String? {
    val value = code?.trim()?.lowercase().orEmpty()
    if (value.isEmpty() || value == "und") return null
    val base = value.substringBefore('-').substringBefore('_')
    if (base.isEmpty()) return null
    return subtitleLanguageNames[base] ?: base.uppercase()
}

/** Picker label: the track title, else its language, else its number. */
fun embeddedSubtitleLabel(track: EmbeddedSubtitleTrack): String {
    val title = track.title.trim()
    if (title.isNotEmpty()) return title
    subtitleLanguageName(track.language)?.let { return it }
    return "المسار ${track.index.coerceAtLeast(0) + 1}"
}

/** Secondary line for the picker: where the track lives and its format. */
fun embeddedSubtitleDetail(track: EmbeddedSubtitleTrack): String = buildList {
    add("مدمجة في الفيديو")
    if (track.forced) add("إجبارية")
    if (track.codec.isNotBlank()) add(track.codec.uppercase())
    if (track.index >= 0) add("المسار ${track.index + 1}")
}.joinToString(" • ")

private val subtitleLanguageNames = mapOf(
    "ar" to "العربية",
    "en" to "الإنجليزية",
    "fr" to "الفرنسية",
    "es" to "الإسبانية",
    "de" to "الألمانية",
    "it" to "الإيطالية",
    "pt" to "البرتغالية",
    "ru" to "الروسية",
    "tr" to "التركية",
    "ur" to "الأردية",
    "hi" to "الهندية",
    "fa" to "الفارسية",
    "id" to "الإندونيسية",
    "ms" to "الماليزية",
    "ja" to "اليابانية",
    "ko" to "الكورية",
    "zh" to "الصينية",
    "nl" to "الهولندية",
    "pl" to "البولندية",
    "sv" to "السويدية",
    "th" to "التايلاندية",
    "he" to "العبرية"
)
