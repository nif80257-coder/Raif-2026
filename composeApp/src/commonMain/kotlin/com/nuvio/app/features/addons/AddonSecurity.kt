package com.nuvio.app.features.addons

import io.ktor.http.Url

/** Accepts only well-formed HTTP(S) URLs without user-info, controls, or malformed ports. */
internal fun isSafeWebUrl(value: String): Boolean {
    val candidate = value.trim()
    if (candidate.isEmpty() || candidate.length > MAX_ADDON_URL_LENGTH) return false
    if (candidate.any { it.isWhitespace() || it.isISOControl() } || '\\' in candidate) return false

    val parsed = runCatching { Url(candidate) }.getOrNull() ?: return false
    val scheme = parsed.protocol.name.lowercase()
    if (scheme != "http" && scheme != "https") return false

    val authority = candidate.substringAfter("://", "").substringBeforeAny('/', '?', '#')
    if (authority.isBlank() || '@' in authority || parsed.host.isBlank()) return false
    return hasValidAuthorityPort(authority)
}

/** Accepts only public HTTP(S) endpoints; private and special-use destinations are never fetched. */
internal fun isSafePublicWebUrl(value: String): Boolean {
    if (!isSafeWebUrl(value)) return false
    val parsed = runCatching { Url(value.trim()) }.getOrNull() ?: return false
    return isPublicHost(parsed.host)
}

/** Raised when a request destination is refused by the add-on URL policy. */
internal class AddonUnsafeDestinationException : IllegalStateException("Add-on destination refused")

/**
 * Checks [url] against the URL policy and, for a name, against the addresses it answers with now.
 *
 * The literal-address check alone cannot see a host that answers with a public address while it is
 * inspected and a private one when the connection is made, so the lookup is repeated twice right
 * before each request (and before every redirect hop) and every answer of both has to be public.
 * The second lookup is what catches the usual shape of a rebinding attack: a cache entry whose
 * entry lives for no time at all answers public once and private the next. The answers are not
 * required to be equal, because a load-balanced host legitimately rotates its addresses.
 *
 * This is as far as a check can go: it does not pin the address to the connection, because the
 * transport resolves the name again on its own, so a host that keeps both answers equal while it is
 * inspected and switches only at connect time is still not defeated here. Closing that needs the
 * connection itself, which Ktor's Darwin engine does not expose. Nothing is cached, which keeps the
 * window as small as this layer can make it.
 *
 * [resolve] exists so the decision can be tested without a network.
 */
internal suspend fun verifyAddonDestinationIsPublic(
    url: String,
    resolve: (suspend (String) -> List<String>)? = null,
) {
    val candidate = url.trim()
    if (!isSafePublicWebUrl(candidate)) throw AddonUnsafeDestinationException()
    val host = runCatching { Url(candidate).host }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: throw AddonUnsafeDestinationException()
    if (isLiteralAddressHost(host)) return

    val resolver = resolve ?: ::resolveHostAddresses
    repeat(ADDON_ADDRESS_LOOKUPS_PER_REQUEST) {
        val addresses = resolver(host)
        if (addresses.isEmpty() || addresses.any { !isPublicNumericAddress(it) }) {
            throw AddonUnsafeDestinationException()
        }
    }
}

/** Every request and every redirect hop asks twice, so one answer cannot be swapped after the check. */
private const val ADDON_ADDRESS_LOOKUPS_PER_REQUEST = 2

/** True only for a literal public address, as a resolver reports it (`93.184.216.34`, `2606:…`). */
internal fun isPublicNumericAddress(value: String): Boolean {
    val host = value.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
    if (host.isEmpty()) return false
    if (':' in host) return isPublicIpv6(host)
    val address = parseCanonicalIpv4(host) ?: return false
    return isPublicIpv4(address)
}

private fun isLiteralAddressHost(host: String): Boolean =
    ':' in host || host.all { it.isDigit() || it == '.' }

/** Normalizes a user or directory URL to a manifest endpoint after applying the URL policy. */
internal fun normalizeAddonManifestUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    require(trimmed.isNotEmpty() && trimmed.length <= MAX_ADDON_URL_LENGTH)

    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("stremio://", ignoreCase = true) -> "https://${trimmed.substringAfter("://")}"
        else -> "https://$trimmed"
    }
    val withoutFragment = withScheme.substringBefore('#')
    require(isSafePublicWebUrl(withoutFragment))

    val pathAndQuery = withoutFragment.substringBefore("?")
    val query = withoutFragment.substringAfter("?", "")
    val manifestPath = if (pathAndQuery.endsWith("/manifest.json", ignoreCase = true)) {
        pathAndQuery
    } else {
        "${pathAndQuery.trimEnd('/')}/manifest.json"
    }
    return if (query.isBlank()) manifestPath else "$manifestPath?$query"
}

/** True for path forms commonly used to embed a personal token or profile in a manifest link. */
internal fun hasSensitiveAddonPath(value: String): Boolean {
    val path = value.substringAfter("://", "").substringBefore('?').substringBefore('#')
        .substringAfter('/', "").lowercase()
    val segments = path.split('/').filterNot(::isProtectedAddonPathMarker)
    val unprotectedPath = segments.joinToString("/")
    if (SENSITIVE_ADDON_PATH_HINTS.any(unprotectedPath::contains)) return true

    return segments.any { segment ->
        segment.length >= LONG_OPAQUE_PATH_SEGMENT_LENGTH &&
            segment.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}

private fun isProtectedAddonPathMarker(segment: String): Boolean {
    val hash = segment.removePrefix(PROTECTED_ADDON_PATH_MARKER)
    val digits = hash.removePrefix("-")
    return segment.startsWith(PROTECTED_ADDON_PATH_MARKER) &&
        digits.isNotEmpty() &&
        digits.all { it in '0'..'9' || it in 'a'..'f' }
}

private fun hasValidAuthorityPort(authority: String): Boolean {
    val portText = when {
        authority.startsWith("[") -> {
            val closingBracket = authority.indexOf(']')
            if (closingBracket <= 1) return false
            val remainder = authority.substring(closingBracket + 1)
            if (remainder.isEmpty()) return true
            if (!remainder.startsWith(':')) return false
            remainder.substring(1)
        }
        authority.count { it == ':' } == 0 -> return true
        authority.count { it == ':' } == 1 -> authority.substringAfterLast(':')
        else -> return false
    }
    if (portText.isEmpty() || !portText.all(Char::isDigit)) return false
    return portText.toIntOrNull()?.let { it in 1..65_535 } == true
}

private fun isPublicHost(rawHost: String): Boolean {
    val host = rawHost.removePrefix("[").removeSuffix("]").trimEnd('.').lowercase()
    if (host.isBlank() || '%' in host) return false
    if (':' in host) return isPublicIpv6(host)

    if (host.all { it.isDigit() || it == '.' }) {
        val address = parseCanonicalIpv4(host) ?: return false
        return isPublicIpv4(address)
    }

    if (host.any { !(it.isLetterOrDigit() || it == '-' || it == '.') }) return false
    if (host.count { it == '.' } < 1) return false
    val labels = host.split('.')
    if (labels.any { label ->
            label.isEmpty() || label.length > 63 ||
                label.first() == '-' || label.last() == '-'
        }
    ) return false
    if (labels.last() in RESERVED_LOCAL_SUFFIXES) return false
    return !host.endsWith(".home.arpa") && !host.endsWith(".metadata.google.internal")
}

private fun parseCanonicalIpv4(value: String): List<Int>? {
    val parts = value.split('.')
    if (parts.size != 4) return null
    return parts.map { part ->
        if (part.isEmpty() || (part.length > 1 && part.startsWith('0'))) return null
        part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    }
}

private fun isPublicIpv4(address: List<Int>): Boolean {
    val (a, b, c) = address
    return when {
        a == 0 || a == 10 || a == 127 -> false
        a == 100 && b in 64..127 -> false
        a == 169 && b == 254 -> false
        a == 172 && b in 16..31 -> false
        a == 192 && (b == 0 || b == 168) -> false
        a == 192 && b == 88 && c == 99 -> false
        a == 198 && b in 18..19 -> false
        a == 198 && b == 51 && c == 100 -> false
        a == 203 && b == 0 && c == 113 -> false
        a >= 224 -> false
        else -> true
    }
}

private fun isPublicIpv6(value: String): Boolean {
    val groups = parseIpv6Groups(value) ?: return false
    if (groups.take(5).all { it == 0 } && groups[5] == 0xFFFF) {
        return isPublicIpv4(embeddedIpv4(groups))
    }
    // An iPhone on an IPv6-only carrier sees every IPv4 host as 64:ff9b::<address>, the well-known
    // NAT64 prefix. Judging the address inside that prefix keeps those networks usable; without
    // this every add-on install on such a carrier looked like a name that does not resolve.
    if (groups[0] == 0x0064 && groups[1] == 0xFF9B && groups[2] == 0 && groups[3] == 0 && groups[4] == 0 && groups[5] == 0) {
        return isPublicIpv4(embeddedIpv4(groups))
    }

    val first = groups.first()
    if (first !in 0x2000..0x3FFF) return false
    if (groups[0] == 0x2001 && groups[1] == 0x0DB8) return false
    return true
}

private fun embeddedIpv4(groups: List<Int>): List<Int> = listOf(
    groups[6] ushr 8,
    groups[6] and 0xFF,
    groups[7] ushr 8,
    groups[7] and 0xFF,
)

private fun parseIpv6Groups(value: String): List<Int>? {
    val compression = value.indexOf("::")
    if (compression >= 0 && value.indexOf("::", compression + 2) >= 0) return null
    val leftText = if (compression >= 0) value.substring(0, compression) else value
    val rightText = if (compression >= 0) value.substring(compression + 2) else ""

    fun parseSide(text: String): List<Int>? {
        if (text.isEmpty()) return emptyList()
        val parts = text.split(':').toMutableList()
        val last = parts.lastOrNull() ?: return null
        if ('.' in last) {
            val ipv4 = parseCanonicalIpv4(last) ?: return null
            parts.removeAt(parts.lastIndex)
            val address = listOf(
                (ipv4[0] shl 8) or ipv4[1],
                (ipv4[2] shl 8) or ipv4[3],
            )
            return parts.map { it.toIntOrNull(16)?.takeIf { group -> group in 0..0xFFFF } ?: return null } + address
        }
        return parts.map { part ->
            if (part.isEmpty() || part.length > 4) return null
            part.toIntOrNull(16)?.takeIf { it in 0..0xFFFF } ?: return null
        }
    }

    val left = parseSide(leftText) ?: return null
    val right = parseSide(rightText) ?: return null
    if (compression < 0) return (left + right).takeIf { it.size == 8 }
    val zeroCount = 8 - left.size - right.size
    if (zeroCount < 1) return null
    return left + List(zeroCount) { 0 } + right
}

private fun String.substringBeforeAny(vararg delimiters: Char): String {
    val end = indexOfAny(delimiters).takeIf { it >= 0 } ?: length
    return substring(0, end)
}

private const val MAX_ADDON_URL_LENGTH = 8_192
private const val LONG_OPAQUE_PATH_SEGMENT_LENGTH = 32
private const val PROTECTED_ADDON_PATH_MARKER = "__raif_secret_path_"
private val RESERVED_LOCAL_SUFFIXES = setOf(
    "localhost",
    "local",
    "internal",
    "lan",
    "home",
    "test",
    "invalid",
    "example",
    "onion",
    "arpa",
)
private val SENSITIVE_ADDON_PATH_HINTS = listOf(
    "/user/",
    "/users/",
    "/u/",
    "enc_",
    "apikey",
    "api-key",
    "token",
    "secret",
    "profile",
)
