package com.nuvio.app.features.addons

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.network_empty_response_body
import nuvio.composeapp.generated.resources.network_request_failed_http
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSUserDefaults

actual object AddonStorage {
    private const val addonUrlsKey = "installed_manifest_urls"
    private const val addonEnabledStatesKey = "installed_manifest_enabled_states"
    private const val defaultAddonsSeededKey = "default_addons_seeded"

    actual fun loadInstalledAddonUrls(profileId: Int): List<String> =
        NSUserDefaults.standardUserDefaults
            .stringForKey("${addonUrlsKey}_$profileId")
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    actual fun saveInstalledAddonUrls(profileId: Int, urls: List<String>) {
        NSUserDefaults.standardUserDefaults.setObject(
            urls.joinToString(separator = "\n"),
            forKey = "${addonUrlsKey}_$profileId",
        )
    }

    actual fun loadAddonEnabledStates(profileId: Int): Map<String, Boolean> =
        NSUserDefaults.standardUserDefaults
            .stringForKey("${addonEnabledStatesKey}_$profileId")
            .orEmpty()
            .lineSequence()
            .mapNotNull(::parseEnabledStateLine)
            .toMap()

    actual fun saveAddonEnabledStates(profileId: Int, states: Map<String, Boolean>) {
        val payload = states.entries.joinToString(separator = "\n") { (url, enabled) ->
            "$url\t$enabled"
        }
        NSUserDefaults.standardUserDefaults.setObject(
            payload,
            forKey = "${addonEnabledStatesKey}_$profileId",
        )
    }

    actual fun areDefaultAddonsSeeded(profileId: Int): Boolean =
        NSUserDefaults.standardUserDefaults.boolForKey("${defaultAddonsSeededKey}_$profileId")

    actual fun markDefaultAddonsSeeded(profileId: Int) {
        NSUserDefaults.standardUserDefaults.setBool(
            true,
            forKey = "${defaultAddonsSeededKey}_$profileId",
        )
    }

    actual fun loadPendingSyncPayload(accountId: String, profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(pendingSyncKey(accountId, profileId))

    actual fun savePendingSyncPayload(accountId: String, profileId: Int, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = pendingSyncKey(accountId, profileId))
    }

    actual fun clearPendingSyncPayload(accountId: String, profileId: Int) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(pendingSyncKey(accountId, profileId))
    }

    actual fun loadStartupCache(profileId: Int, name: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(startupCacheKey(name, profileId))

    actual fun saveStartupCache(profileId: Int, name: String, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(
            payload,
            forKey = startupCacheKey(name, profileId),
        )
    }

    private fun pendingSyncKey(accountId: String, profileId: Int): String =
        "pending_user_addons_${profileId}_$accountId"

    private fun startupCacheKey(name: String, profileId: Int): String =
        "startup_cache_${name}_$profileId"
}

private fun parseEnabledStateLine(line: String): Pair<String, Boolean>? {
    val url = line.substringBefore("\t").trim().takeIf { it.isNotEmpty() } ?: return null
    val rawEnabled = line.substringAfter("\t", "true").trim().lowercase()
    val enabled = when (rawEnabled) {
        "false" -> false
        else -> true
    }
    return url to enabled
}

/**
 * One client for every add-on request.
 *
 * A source sheet asks thirty-three add-ons at once and several of them live on the same host.
 * NSURLSession's default of six connections per host leaves the rest of those answers queued, so
 * the sheet waits for work that could already be in flight; eight keeps a phone's radio busy
 * without opening a door per add-on. `waitsForConnectivity` is off so an offline phone fails at
 * once instead of parking the request until iOS finds a link again.
 *
 * The connect timeout is the one that matters when a host is simply gone. Sixty seconds was the
 * default here, and with retries a dead manifest could hold the add-on screen for minutes while
 * the sources behind it never started.
 */
private val addonHttpClient = HttpClient(Darwin) {
    engine {
        configureSession {
            HTTPMaximumConnectionsPerHost = 8
            waitsForConnectivity = false
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
    expectSuccess = false
}

/**
 * Ceiling for a manifest, catalog or source list. A Stremio answer is JSON in the hundreds of
 * kilobytes; anything past this comes from a wrong or hostile host, and reading it whole is what
 * turns one bad add-on into a memory spike on a phone.
 */
private const val ADDON_RESPONSE_MAX_BYTES = 8 * 1024 * 1024

private fun HttpResponse.rejectOversizedBody() {
    val declared = contentLength()
    if (declared != null && declared > ADDON_RESPONSE_MAX_BYTES) {
        throw IllegalStateException("Add-on response of $declared bytes exceeds the limit")
    }
}

actual suspend fun httpGetText(url: String): String =
    addonHttpClient
        .get(url) {
            accept(ContentType.Application.Json)
        }
        .let { response ->
            response.rejectOversizedBody()
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw AddonHttpStatusException(
                    statusCode = response.status.value,
                    retryAfterHeader = response.headers[HttpHeaders.RetryAfter],
                    message = runBlocking { getString(Res.string.network_request_failed_http, response.status.value) },
                )
            }
            if (payload.isBlank()) {
                throw IllegalStateException(runBlocking { getString(Res.string.network_empty_response_body) })
            }
            payload
        }

actual suspend fun httpPostJson(url: String, body: String): String =
    addonHttpClient
        .post(url) {
            accept(ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(body)
        }
        .let { response ->
            response.rejectOversizedBody()
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error(runBlocking { getString(Res.string.network_request_failed_http, response.status.value) })
            }
            if (payload.isBlank()) {
                throw IllegalStateException(runBlocking { getString(Res.string.network_empty_response_body) })
            }
            payload
        }

actual suspend fun httpGetTextWithHeaders(
    url: String,
    headers: Map<String, String>,
): String =
    addonHttpClient
        .get(url) {
            accept(ContentType.Application.Json)
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        .let { response ->
            response.rejectOversizedBody()
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw AddonHttpStatusException(
                    statusCode = response.status.value,
                    retryAfterHeader = response.headers[HttpHeaders.RetryAfter],
                    message = runBlocking { getString(Res.string.network_request_failed_http, response.status.value) },
                )
            }
            if (payload.isBlank()) {
                throw IllegalStateException(runBlocking { getString(Res.string.network_empty_response_body) })
            }
            payload
        }

actual suspend fun httpPostJsonWithHeaders(
    url: String,
    body: String,
    headers: Map<String, String>,
): String =
    addonHttpClient
        .post(url) {
            accept(ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            headers.forEach { (key, value) ->
                header(key, value)
            }
            setBody(body)
        }
        .let { response ->
            response.rejectOversizedBody()
            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error(runBlocking { getString(Res.string.network_request_failed_http, response.status.value) })
            }
            if (payload.isBlank()) {
                throw IllegalStateException(runBlocking { getString(Res.string.network_empty_response_body) })
            }
            payload
        }

actual suspend fun httpRequestRaw(
    method: String,
    url: String,
    headers: Map<String, String>,
    body: String,
    followRedirects: Boolean,
    maxResponseBodyBytes: Int,
    bodyBytes: ByteArray?,
): RawHttpResponse =
    addonHttpClient
        .request {
            url(url)
            this.method = HttpMethod.parse(method.uppercase())
            headers.forEach { (key, value) ->
                header(key, value)
            }
            if (this.method == HttpMethod.Post || this.method == HttpMethod.Put || this.method == HttpMethod.Patch) {
                setBody(bodyBytes ?: body)
            }
        }
        .let { response ->
            val receivedBytes = response.body<ByteArray>()
            val limitedBytes = receivedBytes.copyOf(minOf(receivedBytes.size, maxResponseBodyBytes.coerceAtLeast(0)))
            val decoded = limitedBytes.decodeToString()
            RawHttpResponse(
                status = response.status.value,
                statusText = response.status.description,
                url = response.call.request.url.toString(),
                body = if (receivedBytes.size > limitedBytes.size) "$decoded\n...[truncated]" else decoded,
                bodyBytes = limitedBytes,
                headers = response.headers.entries().associate { (name, values) ->
                    name.lowercase() to values.joinToString(",")
                },
            )
        }
