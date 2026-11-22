package com.cinemax.app.addons

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createCinemaxHttpClient(): HttpClient = HttpClient {
    expectSuccess = true
    // Addon endpoints commonly redirect (CDNs, config URLs). Only validated HTTPS
    // URLs are requested, and iOS App Transport Security blocks plain HTTP hops.
    followRedirects = true
    install(HttpTimeout) {
        // A viewer waits on the sources list, so a dead host must fail quickly and
        // let the other addons through instead of holding the whole answer back.
        connectTimeoutMillis = 6_000
        requestTimeoutMillis = 20_000
        socketTimeoutMillis = 20_000
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
