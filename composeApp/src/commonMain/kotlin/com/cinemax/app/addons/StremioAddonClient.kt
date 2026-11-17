package com.cinemax.app.addons

import io.ktor.http.Url

/**
 * Protocol boundary for Stremio-compatible HTTP addons.
 * Remote addons must use HTTPS. Plain HTTP is allowed only for loopback development.
 * Addon responses are data and downloaded addon code is never executed.
 */
object StremioAddonClient {
    fun manifestUrl(input: String): String? {
        val value = input.trim().removeSuffix("/")
        val parsed = try {
            Url(value)
        } catch (_: Throwable) {
            return null
        }
        val secure = parsed.protocol.name == "https"
        val localDevelopment = parsed.protocol.name == "http" &&
            parsed.host in setOf("127.0.0.1", "localhost", "::1")
        if (!secure && !localDevelopment) return null
        if (!parsed.user.isNullOrEmpty() || !parsed.password.isNullOrEmpty()) return null
        val queryIndex = value.indexOf('?').takeIf { it >= 0 } ?: value.length
        val path = value.substring(0, queryIndex)
        val query = value.substring(queryIndex)
        return if (path.endsWith("/manifest.json")) value else "$path/manifest.json$query"
    }

    /**
     * The configure page of an addon that advertises `behaviorHints.configurable`:
     * the manifest's own base path plus "/configure".
     */
    fun configureUrl(input: String): String? {
        val manifest = manifestUrl(input) ?: return null
        val path = manifest.substringBefore('?').substringBefore('#')
        val base = path.removeSuffix("/manifest.json").removeSuffix("/")
        return "$base/configure"
    }

    /**
     * Builds a Stremio resource URL. The protocol takes all extra arguments in a
     * single path segment as `key=value&key=value`, and every key and value is
     * encoded independently to prevent path injection.
     */
    fun resourceUrl(
        baseManifestUrl: String,
        resource: String,
        type: String,
        id: String,
        extra: Map<String, String> = emptyMap()
    ): String {
        require(isSupportedResource(resource)) { "Unsupported Stremio resource" }
        val withoutFragment = baseManifestUrl.substringBefore('#')
        val queryIndex = withoutFragment.indexOf('?').takeIf { it >= 0 } ?: withoutFragment.length
        val manifestPath = withoutFragment.substring(0, queryIndex)
        val query = withoutFragment.substring(queryIndex)
        val base = manifestPath.removeSuffix("/manifest.json").removeSuffix("/")
        val path = buildString {
            append(base)
            append("/")
            append(encodeSegment(resource))
            append("/")
            append(encodeSegment(type))
            append("/")
            // Series and episode ids keep their colons ("tt0944947:1:2"), which
            // addons match literally.
            append(encodeSegment(id, keepColon = true))
            if (extra.isNotEmpty()) {
                append("/")
                append(extraPathSegment(extra))
            }
            append(".json")
        }
        return path + query
    }

    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val HEX = "0123456789ABCDEF"

    /**
     * Percent-encodes one path segment over its UTF-8 bytes. Done here instead of
     * through the HTTP client so the exact wire form Stremio addons match is
     * guaranteed: unreserved characters stay, spaces become %20, and colons can be
     * kept for episode ids.
     */
    private fun encodeSegment(value: String, keepColon: Boolean = false): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val symbol = code.toChar()
            if (symbol in UNRESERVED || (keepColon && symbol == ':')) {
                append(symbol)
            } else {
                append('%')
                append(HEX[code shr 4])
                append(HEX[code and 0x0F])
            }
        }
    }

    /**
     * Real addons only read one extra segment, e.g.
     * `/catalog/movie/top/genre=Action&skip=100.json`. Sending one segment per
     * argument, as this function used to, made search and pagination request a
     * path no addon parses.
     */
    private fun extraPathSegment(extra: Map<String, String>): String =
        extra.entries.map { (key, value) ->
            require(key.isNotBlank()) { "Extra argument key cannot be blank" }
            "${encodeSegment(key)}=${encodeSegment(value)}"
        }.joinToString("&")

    fun isSafeMediaUrl(input: String): Boolean {
        val parsed = try {
            Url(input.trim())
        } catch (_: Throwable) {
            return false
        }
        val secure = parsed.protocol.name == "https"
        val localDevelopment = parsed.protocol.name == "http" &&
            parsed.host in setOf("127.0.0.1", "localhost", "::1")
        return (secure || localDevelopment) &&
            parsed.user.isNullOrEmpty() && parsed.password.isNullOrEmpty()
    }

    fun isSupportedResource(resource: String): Boolean =
        resource in setOf("catalog", "meta", "stream", "subtitles")
}
