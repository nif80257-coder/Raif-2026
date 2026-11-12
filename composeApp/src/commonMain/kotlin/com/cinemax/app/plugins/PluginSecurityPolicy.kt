package com.cinemax.app.plugins

/**
 * Policy for running remote provider scripts.
 *
 * Nuvio providers are executable JavaScript. They run only after the user enables
 * them, inside JavaScriptCore with no filesystem, keychain or native API access,
 * HTTPS-only `fetch`, bounded timers, a hard timeout and a response size cap.
 * Anything else must stay rejected rather than being evaluated by accident.
 */
object PluginSecurityPolicy {
    const val EXECUTION_ENABLED = true
    const val REQUIRE_USER_CONSENT = true
    const val REQUIRE_SANDBOX = true

    /** Wall clock limit for one provider run. */
    const val NETWORK_TIMEOUT_MILLIS = 25_000L

    /** Largest provider response body kept in memory. */
    const val MAX_RESPONSE_BYTES = 4_000_000L

    fun mayExecute(isSandboxed: Boolean, userConsented: Boolean): Boolean =
        EXECUTION_ENABLED && isSandboxed && userConsented
}
