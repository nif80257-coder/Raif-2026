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
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
