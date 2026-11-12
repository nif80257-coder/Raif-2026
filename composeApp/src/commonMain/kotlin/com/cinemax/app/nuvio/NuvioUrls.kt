package com.cinemax.app.nuvio

import io.ktor.http.Url

/**
 * Nuvio repositories and their scripts must be served over HTTPS; plain HTTP is
 * allowed only for loopback development servers.
 */
object NuvioUrls {
    fun isSecure(input: String): Boolean {
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

    /** Accepts a repository root or its manifest and returns the manifest URL. */
    fun manifestUrl(input: String): String? {
        val value = input.trim().removeSuffix("/")
        if (value.isEmpty() || !isSecure(value)) return null
        return if (value.endsWith(".json")) value else "$value/manifest.json"
    }

    /** Resolves a provider `filename` against its repository base URL. */
    fun resolve(baseUrl: String, filename: String): String? {
        val value = filename.trim()
        if (value.isEmpty()) return null
        val base = baseUrl.removeSuffix("/manifest.json").removeSuffix("/")
        val target = when {
            value.startsWith("https://") || value.startsWith("http://") -> value
            else -> "$base/${value.trimStart('/')}"
        }
        return target.takeIf { isSecure(it) }
    }
}
