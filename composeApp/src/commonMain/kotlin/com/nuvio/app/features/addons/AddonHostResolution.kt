package com.nuvio.app.features.addons

/**
 * The numeric addresses [host] answers with right now.
 *
 * An empty list means the lookup failed or answered nothing, and the caller treats that as a
 * refusal. The answer is deliberately not cached: a cached address is exactly what a host that
 * rebinds between lookups needs in order to pass a check and then be reached at a private address.
 */
internal expect suspend fun resolveHostAddresses(host: String): List<String>
