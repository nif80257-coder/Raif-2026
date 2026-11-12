package com.cinemax.app.nuvio

import com.cinemax.app.plugins.PluginSecurityPolicy
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

/** Client for provider scripts: `fetch` must see every status instead of throwing. */
fun createNuvioHttpClient(): HttpClient = HttpClient {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = PluginSecurityPolicy.NETWORK_TIMEOUT_MILLIS
        socketTimeoutMillis = PluginSecurityPolicy.NETWORK_TIMEOUT_MILLIS
    }
}
