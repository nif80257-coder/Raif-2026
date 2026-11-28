package com.nuvio.app.features.addons

/** A destination read from a tunnel request line, before any address is chosen for it. */
internal data class AddonTunnelTarget(
    val host: String,
    val port: Int,
) {
    /** How the address is written back to the client: an IPv6 literal keeps its brackets. */
    val authority: String
        get() = if (':' in host) "[$host]:$port" else "$host:$port"
}

/**
 * Reads the destination of a `CONNECT host:port HTTP/1.1` request line.
 *
 * The tunnel carries TLS only, so anything else is refused here rather than forwarded: a plain
 * request would have to be rewritten and its destination re-read from the headers, and that is a
 * second parser to get wrong for a transport every add-on depends on.
 */
internal fun parseAddonTunnelTarget(requestLine: String): AddonTunnelTarget? {
    val parts = requestLine.trim().split(' ').filter(String::isNotEmpty)
    if (parts.size < 2 || !parts[0].equals("CONNECT", ignoreCase = true)) return null
    val authority = parts[1]
    if (authority.any { it.isWhitespace() || it.isISOControl() } || '\\' in authority) return null
    if ('@' in authority) return null

    val colon = authority.lastIndexOf(':')
    if (colon <= 0 || colon == authority.lastIndex) return null
    val port = authority.substring(colon + 1)
        .takeIf { text -> text.isNotEmpty() && text.all(Char::isDigit) }
        ?.toIntOrNull()
        ?.takeIf { it in 1..65_535 }
        ?: return null

    val host = authority.substring(0, colon).removePrefix("[").removeSuffix("]").trim().lowercase()
    if (host.isEmpty() || '%' in host) return null
    if (':' in host) {
        // A literal IPv6 destination is only usable when the address itself is public.
        return if (isPublicNumericAddress(host)) AddonTunnelTarget(host, port) else null
    }
    if (host.none { it == '.' }) return null
    if (host.any { !(it.isLetterOrDigit() || it == '-' || it == '.') }) return null
    return AddonTunnelTarget(host, port)
}

/**
 * The one address the tunnel may connect to for [addresses]: the first public one, and only when
 * every answer the resolver gave is public. The decision is taken at the moment of the connection
 * itself, so nothing can change between the check and the socket.
 */
internal fun chooseAddonTunnelAddress(addresses: List<String>): String? {
    if (addresses.isEmpty()) return null
    if (addresses.any { !isPublicNumericAddress(it) }) return null
    return addresses.first()
}
