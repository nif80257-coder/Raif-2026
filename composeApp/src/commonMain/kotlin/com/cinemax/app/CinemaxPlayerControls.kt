package com.cinemax.app

/**
 * Imperative handle the native player hands back to the shared UI, so the
 * overlay (seek bar, speed chip, play/pause) can drive playback without knowing
 * whether libmpv or AVPlayer is behind it.
 */
class CinemaxPlayerControls(
    val seekTo: (positionMillis: Long) -> Unit,
    val setSpeed: (speed: Float) -> Unit,
    val setPlaying: (playing: Boolean) -> Unit
)

/** "1:02:03" or "2:03" for a position in milliseconds. */
fun formatClock(positionMillis: Long): String {
    val total = (positionMillis.coerceAtLeast(0L)) / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    val two = { value: Long -> if (value < 10) "0$value" else value.toString() }
    return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
}
