package com.nuvio.app.features.addons

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.backendRetryDelayMillis
import com.nuvio.app.features.profiles.ProfileRepository
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
    is CancellationException,
    is AddonRedirectRejectedException,
    is AddonUnsafeDestinationException,
    -> false
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
private const val MAX_VALIDATED_ADDON_REDIRECTS = 5
private const val ADDON_RESPONSE_MAX_BYTES = 8 * 1024 * 1024
private val addonRedirectStatuses = setOf(301, 302, 303, 307, 308)
private val redirectSchemePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

internal class AddonRedirectRejectedException : IllegalStateException("Add-on redirect rejected")

/** Resolves a redirect only when its final HTTP(S) destination remains a public web URL. */
internal fun resolveSafeAddonRedirect(currentUrl: String, location: String): String? {
    val current = currentUrl.substringBefore('#')
    val target = location.trim().substringBefore('#')
    if (target.isEmpty() || target.any { it.isWhitespace() || it.isISOControl() } || '\\' in target) return null
    if (!isSafePublicWebUrl(current)) return null

    val scheme = current.substringBefore("://", "").lowercase()
    val authorityStart = current.indexOf("://")
    if (authorityStart < 0) return null
    val authorityText = current.substring(authorityStart + 3)
    val authorityEnd = authorityText.indexOfAny(charArrayOf('/', '?', '#')).let { index ->
        if (index < 0) authorityText.length else index
    }
    val authority = authorityText.substring(0, authorityEnd)
    if (authority.isBlank()) return null
    val origin = "$scheme://$authority"

    val resolved = when {
        target.startsWith("//") -> "$scheme:$target"
        redirectSchemePattern.containsMatchIn(target) -> target
        target.startsWith("/") -> origin + target
        target.startsWith("?") -> current.substringBefore('?') + target
        else -> {
            val currentPath = authorityText.substringAfter('/', "").substringBefore('?')
            val directory = currentPath.substringBeforeLast('/', "")
            origin + "/" + listOf(directory, target).filter(String::isNotEmpty).joinToString("/")
        }
    }
    val targetScheme = resolved.substringBefore("://", "").lowercase()
    if (scheme == "https" && targetScheme != "https") return null
    return resolved.takeIf(::isSafePublicWebUrl)
}

private suspend fun fetchAddonResponseTextWithValidatedRedirects(
    initialUrl: String,
    forceRefresh: Boolean,
): String {
    var requestUrl = initialUrl
    for (redirectCount in 0..MAX_VALIDATED_ADDON_REDIRECTS) {
        verifyAddonDestinationIsPublic(requestUrl)
        val response = httpRequestRaw(
            method = "GET",
            url = requestUrl,
            headers = if (forceRefresh) mapOf("Cache-Control" to "no-cache") else emptyMap(),
            body = "",
            followRedirects = false,
            maxResponseBodyBytes = ADDON_RESPONSE_MAX_BYTES,
        )
        if (response.status in addonRedirectStatuses) {
            if (redirectCount >= MAX_VALIDATED_ADDON_REDIRECTS) throw AddonRedirectRejectedException()
            val location = response.headers.entries
                .firstOrNull { (name, _) -> name.equals("location", ignoreCase = true) }
                ?.value
                ?: throw AddonRedirectRejectedException()
            requestUrl = resolveSafeAddonRedirect(requestUrl, location)
                ?: throw AddonRedirectRejectedException()
            continue
        }
        if (response.status !in 200..299) {
            throw AddonHttpStatusException(
                statusCode = response.status,
                retryAfterHeader = response.headers.entries
                    .firstOrNull { (name, _) -> name.equals("retry-after", ignoreCase = true) }
                    ?.value,
                message = "The add-on request failed.",
            )
        }
        if (response.bodyTruncated) throw IllegalStateException("Add-on response exceeded the size limit.")
        if (response.body.isBlank()) throw IllegalStateException("Add-on response was empty.")
        return response.body
    }
    throw AddonRedirectRejectedException()
}

internal suspend fun fetchAddonResponseText(
    url: String,
    forceRefresh: Boolean = false,
    gate: Semaphore? = null,
    maxAttempts: Int = ADDON_FETCH_MAX_ATTEMPTS,
): String {
    val requestUrl = AddonConfigSecretStore.appendStoredSecrets(
        profileId = ProfileRepository.activeProfileId,
        url = url,
    )
    require(isSafePublicWebUrl(requestUrl)) { "Unsafe add-on request URL." }
    return retryAddonFetch(maxAttempts = maxAttempts) {
        val attempt: suspend () -> String = {
            fetchAddonResponseTextWithValidatedRedirects(requestUrl, forceRefresh)
        }
        if (gate != null) gate.withPermit { attempt() } else attempt()
    }
}
