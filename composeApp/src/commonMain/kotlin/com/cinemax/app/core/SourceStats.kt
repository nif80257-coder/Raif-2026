package com.cinemax.app.core

/** What the app remembers about one playable link's track record. */
data class SourceStats(
    val lastSuccessAtEpochMillis: Long = 0L,
    val lastFailureAtEpochMillis: Long = 0L,
    val failures: Int = 0,
    val timeToFirstFrameMs: Long = 0L
) {
    val hasPlayed: Boolean get() = lastSuccessAtEpochMillis > 0L
}

/** Six hours, a day and three days in milliseconds, for the failure decay. */
private const val SIX_HOURS = 6L * 60L * 60L * 1000L
private const val ONE_DAY = 24L * 60L * 60L * 1000L
private const val THREE_DAYS = 3L * ONE_DAY

/**
 * How much a link's track record moves it in the list. A recent failure drops it
 * below the rest without hiding it, older failures fade, a link that played
 * before rises, and a very slow start is a warning rather than a failure.
 */
fun SourceStats.penalty(nowEpochMillis: Long): Int {
    var score = 0
    if (lastFailureAtEpochMillis > 0L) {
        val sinceFailure = (nowEpochMillis - lastFailureAtEpochMillis).coerceAtLeast(0L)
        score -= when {
            sinceFailure < SIX_HOURS -> 220
            sinceFailure < ONE_DAY -> 120
            sinceFailure < THREE_DAYS -> 60
            else -> 0
        }
    }
    if (hasPlayed) {
        score += 60
        if (timeToFirstFrameMs > 12_000L) score -= 40
    }
    return score
}
