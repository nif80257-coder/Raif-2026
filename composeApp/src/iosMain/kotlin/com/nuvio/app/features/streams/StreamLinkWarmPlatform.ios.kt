package com.nuvio.app.features.streams

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable

/**
 * One client for warm-ups, apart from the one that reads add-on payloads.
 *
 * Two connections per host is all a warm-up needs, and it is deliberately *not* the eight the
 * source list uses: a warm-up must never take a connection a source list is waiting for. Offline
 * fails at once (`waitsForConnectivity` off) because a warm-up is a head start, not a request
 * anyone is waiting on.
 */
private val warmHttpClient = HttpClient(Darwin) {
    engine {
        configureSession {
            HTTPMaximumConnectionsPerHost = 2
            waitsForConnectivity = false
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 12_000
        connectTimeoutMillis = 8_000
        socketTimeoutMillis = 12_000
    }
    expectSuccess = false
    followRedirects = true
}

/**
 * Reads the first bytes and returns, which is the whole point: by then the host has been found,
 * the connection opened, the redirects followed and the answer started. The range request keeps a
 * host that honours it from sending more than those bytes, and the ones that ignore it are cut off
 * after the same amount. `HttpStatement.execute` frees the response when the block returns, so no
 * connection is held open while the viewer is still reading the list.
 */
internal actual suspend fun warmStreamLinkConnection(
    url: String,
    headers: Map<String, String>,
    maxBytes: Int,
): Boolean {
    if (maxBytes <= 0) return false
    return warmHttpClient
        .prepareGet(url) {
            headers.forEach { (name, value) ->
                // The range is ours: a source that already answered with one would only narrow
                // the warm-up to a slice of the file.
                if (!name.equals(HttpHeaders.Range, ignoreCase = true)) {
                    header(name, value)
                }
            }
            header(HttpHeaders.Range, "bytes=0-${maxBytes - 1}")
            header(HttpHeaders.Accept, "*/*")
        }
        .execute { response ->
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(4_096)
            var received = 0
            while (received < maxBytes) {
                val count = channel.readAvailable(buffer, 0, minOf(buffer.size, maxBytes - received))
                if (count <= 0) break
                received += count
            }
            received > 0
        }
}
