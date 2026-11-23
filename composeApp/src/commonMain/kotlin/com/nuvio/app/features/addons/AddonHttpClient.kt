package com.nuvio.app.features.addons

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.backendRetryDelayMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val addonFetchLog = Logger.withTag("AddonHttpClient")

/** The first request plus two retries: a flaky add-on gets another chance without stalling a refresh. */
internal const val ADDON_FETCH_MAX_ATTEMPTS = 3

/** Caps a server's Retry-After so one add-on cannot hold up the launch for minutes. */
internal const val ADDON_FETCH_MAX_RETRY_DELAY_MS = 10_000L

/** A failed add-on request that carries the HTTP status and Retry-After header of its response. */
internal class AddonHttpStatusException(
    val statusCode: Int,
    val retryAfterHeader: String?,
    message: String,
) : IllegalStateException(message)

internal fun isRetryableAddonHttpStatus(statusCode: Int): Boolean =
    statusCode == 408 ||
        statusCode == 429 ||
        statusCode == 500 ||
        statusCode == 502 ||
        statusCode == 503 ||
        statusCode == 504

/**
 * Client errors such as 404 are final, a cancelled call is never retried, and a timeout is final
 * too: it has already waited out the full request timeout, so another attempt would only stall.
 */
internal fun isRetryableAddonFetchFailure(failure: Throwable): Boolean = when (failure) {
    is CancellationException -> false
    is AddonHttpStatusException -> isRetryableAddonHttpStatus(failure.statusCode)
    else -> !isTimeoutFailure(failure)
}

/** Matched by name because the platform timeout types differ between iOS (Ktor) and Android (OkHttp). */
internal fun isTimeoutFailure(failure: Throwable): Boolean =
    failure::class.simpleName?.contains("Timeout", ignoreCase = true) == true

/**
 * Runs [fetch] and retries transient failures with the backend's exponential backoff, honouring
 * Retry-After. The last failure is rethrown once the attempts run out.
 */
internal suspend fun retryAddonFetch(
    maxAttempts: Int = ADDON_FETCH_MAX_ATTEMPTS,
    pause: suspend (Long) -> Unit = { delay(it) },
    fetch: suspend () -> String,
): String {
    var attempt = 0
    while (true) {
        try {
            return fetch()
        } catch (failure: Throwable) {
            attempt += 1
            if (attempt >= maxAttempts || !isRetryableAddonFetchFailure(failure)) throw failure
            val waitMs = backendRetryDelayMillis(
                retryCount = attempt - 1,
                retryAfterHeader = (failure as? AddonHttpStatusException)?.retryAfterHeader,
            ).coerceAtMost(ADDON_FETCH_MAX_RETRY_DELAY_MS)
            addonFetchLog.w { "Add-on request failed (attempt $attempt of $maxAttempts), retrying in $waitMs ms" }
            pause(waitMs)
        }
    }
}

/**
 * Fetches an add-on payload with retries. When [gate] is given, each attempt takes a permit only
 * for its own request, so a backoff never holds a slot other add-ons are waiting for.
 *
 * [maxAttempts] is 1 for a source answering a stream request: the caller already bounds that
 * request with its own timeout, and a retry there spends the slot the next source is waiting for.
 */
internal suspend fun fetchAddonResponseText(
    url: String,
    forceRefresh: Boolean = false,
    gate: Semaphore? = null,
    maxAttempts: Int = ADDON_FETCH_MAX_ATTEMPTS,
): String = retryAddonFetch(maxAttempts = maxAttempts) {
    val attempt: suspend () -> String = {
        if (forceRefresh) {
            httpGetTextWithHeaders(
                url = url,
                headers = mapOf("Cache-Control" to "no-cache"),
            )
        } else {
            httpGetText(url)
        }
    }
    if (gate != null) gate.withPermit { attempt() } else attempt()
}
