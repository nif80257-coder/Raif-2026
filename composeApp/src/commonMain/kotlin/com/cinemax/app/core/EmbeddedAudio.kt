package com.cinemax.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An audio track packed inside the video file itself, such as the Arabic and
 * English tracks of a dual-audio MKV. [id] is the player's track id used to
 * select it, [index] its position among the file's audio tracks (some players
 * only accept that position), and [selected] is what the player reports back.
 */
@Serializable
data class EmbeddedAudioTrack(
    val id: Long = -1,
    val index: Int = -1,
    val language: String = "",
    val title: String = "",
    val codec: String = "",
    val channels: String = "",
    val selected: Boolean = false
)

private val embeddedAudioJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Parses the audio track list the native player reports for the current file.
 * A player that cannot report tracks (or reports a broken list) yields nothing.
 */
fun parseEmbeddedAudioTracks(raw: String): List<EmbeddedAudioTrack> {
    if (raw.isBlank()) return emptyList()
    val tracks = runCatching {
        embeddedAudioJson.decodeFromString<List<EmbeddedAudioTrack>>(raw)
    }.getOrNull().orEmpty()
    return tracks.filter { it.id >= 0 }
}

/** Picker label: the track title, else its language, else its number. */
fun embeddedAudioLabel(track: EmbeddedAudioTrack): String {
    val title = track.title.trim()
    if (title.isNotEmpty()) return title
    subtitleLanguageName(track.language)?.let { return it }
    return "المسار ${track.index.coerceAtLeast(0) + 1}"
}

/** Secondary line for the picker: where the track lives and its format. */
fun embeddedAudioDetail(track: EmbeddedAudioTrack): String = buildList {
    add("مدمج في الفيديو")
    if (track.codec.isNotBlank()) add(track.codec.uppercase())
    if (track.channels.isNotBlank() && track.channels != "0") add("${track.channels} قنوات")
    if (track.index >= 0) add("المسار ${track.index + 1}")
}.joinToString(" • ")
