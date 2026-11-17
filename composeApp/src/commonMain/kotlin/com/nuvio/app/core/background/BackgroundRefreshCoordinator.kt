package com.nuvio.app.core.background

import co.touchlab.kermit.Logger
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Work the iOS background refresh runs while the app is not in the foreground.
 *
 * It only reuses state that is already stored on the device: the episode-release notifications
 * reload their saved follow list and reschedule from it. It does not start a network sync or open
 * the library, so a cold background launch stays small and never waits on the account.
 */
object BackgroundRefreshCoordinator {
    private val log = Logger.withTag("BackgroundRefresh")

    /** The system stops a background task after about 30 seconds; stay well inside that. */
    private const val RefreshBudgetMs = 20_000L

    /**
     * Runs one refresh. Returns true when the work finished within the budget. Cancellation is
     * rethrown so the caller can end the task as the system requested.
     */
    suspend fun runOnce(): Boolean {
        return try {
            val finished = withTimeoutOrNull(RefreshBudgetMs) {
                EpisodeReleaseNotificationsRepository.refreshForBackground()
                true
            } ?: false
            if (!finished) log.w { "Background refresh ran out of time" }
            finished
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Background refresh failed: ${error.message}" }
            false
        }
    }
}
