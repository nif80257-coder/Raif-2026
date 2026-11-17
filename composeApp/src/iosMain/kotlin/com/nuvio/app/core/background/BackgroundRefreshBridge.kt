package com.nuvio.app.core.background

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val backgroundRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Entry point for the iOS background refresh task. Runs [BackgroundRefreshCoordinator.runOnce] and
 * calls [onFinished] exactly once, including when the system cancels the work, so the Swift side
 * can always complete its BGAppRefreshTask.
 */
fun startBackgroundRefresh(onFinished: (Boolean) -> Unit): Job =
    backgroundRefreshScope.launch {
        var finished = false
        try {
            finished = BackgroundRefreshCoordinator.runOnce()
        } finally {
            onFinished(finished)
        }
    }

/** Cancels a refresh started by [startBackgroundRefresh] when the system ends the task early. */
fun cancelBackgroundRefresh(job: Job) {
    job.cancel()
}
