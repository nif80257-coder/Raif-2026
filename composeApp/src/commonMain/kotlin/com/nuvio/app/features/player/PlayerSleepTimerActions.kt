package com.nuvio.app.features.player

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun PlayerScreenRuntime.openSleepTimerModal() {
    showSleepTimerModal = true
    controlsVisible = true
}

internal fun PlayerScreenRuntime.startSleepTimer(option: PlayerSleepTimerOption) {
    sleepTimerJob?.cancel()
    sleepTimerJob = null
    PlayerSleepTimerSession.lastOption = option
    sleepTimerOption = option
    sleepTimerLocked = false
    showSleepTimerModal = false

    val minutes = option.minutes
    if (minutes == null) {
        sleepTimerRemainingMs = null
        return
    }

    val totalMs = minutes * 60_000L
    sleepTimerRemainingMs = totalMs
    sleepTimerJob = scope.launch {
        var remaining = totalMs
        while (remaining > 0L) {
            delay(SleepTimerTickMs)
            remaining = (remaining - SleepTimerTickMs).coerceAtLeast(0L)
            sleepTimerRemainingMs = remaining
        }
        sleepTimerJob = null
        expireSleepTimer()
    }
}

internal fun PlayerScreenRuntime.cancelSleepTimer() {
    sleepTimerJob?.cancel()
    sleepTimerJob = null
    sleepTimerOption = null
    sleepTimerRemainingMs = null
    showSleepTimerModal = false
}

/** Stops playback for the timer and leaves the clean sleep screen up. */
internal fun PlayerScreenRuntime.expireSleepTimer() {
    sleepTimerOption = null
    sleepTimerRemainingMs = null
    showSleepTimerModal = false
    shouldPlay = false
    playerController?.pause()
    controlsVisible = false
    pausedOverlayVisible = false
    sleepTimerLocked = true
}

internal fun PlayerScreenRuntime.resumeFromSleepTimer() {
    sleepTimerLocked = false
    shouldPlay = true
    playerController?.play()
    controlsVisible = true
}
