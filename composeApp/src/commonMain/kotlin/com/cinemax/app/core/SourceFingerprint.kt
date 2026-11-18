package com.cinemax.app.core

import com.cinemax.app.addons.StreamOption
import io.ktor.http.Url

/** Query parameters that change between requests for the very same file. */
private val volatileQueryParams = setOf(
    "tr", "token", "sig", "signature", "expires", "expiry", "policy",
    "key-pair-id", "auth", "apikey", "api_key", "authorization", "noredirect"
)

/**
 * The identity of a playable file, independent of the addon that returned it: a
 * torrent by its info hash (plus the file inside a season pack), a direct link by
 * its normalised URL. Two addons that offer the same file therefore share one
 * fingerprint and the source is shown once.
 */
fun streamFingerprint(option: StreamOption): String {
    val stream = option.stream
    val hash = stream.infoHash?.trim()?.lowercase()
    if (!hash.isNullOrEmpty()) return "torrent:$hash:${stream.fileIdx ?: -1}"
    val url = stream.url?.trim()
    if (!url.isNullOrEmpty()) return "url:${normalizeStreamUrl(url)}"
    val external = stream.externalUrl?.trim()
    if (!external.isNullOrEmpty()) return "external:${external.lowercase()}"
    return "other:${option.addonId}:${stream.name ?: ""}:${stream.title ?: ""}"
}

/**
 * A URL with its tracking, signature and expiry parameters removed and the rest
 * sorted, so the same file behind two different tokens compares equal. Falls back
 * to the trimmed value when the address cannot be parsed.
 */
fun normalizeStreamUrl(url: String): String {
    val trimmed = url.trim()
    val parsed = runCatching { Url(trimmed) }.getOrNull() ?: return trimmed.lowercase()
    val protocol = parsed.protocol.name.lowercase()
    val host = parsed.host.lowercase().removePrefix("www.")
    val port = parsed.port
    val defaultPort = (protocol == "https" && port == 443) || (protocol == "http" && port == 80)
    val authority = if (defaultPort) host else if (port > 0) "$host:$port" else host
    val path = parsed.encodedPath.ifEmpty { "/" }
    val query = parsed.parameters.names()
        .filterNot { name ->
            val key = name.lowercase()
            key.startsWith("utm_") || key in volatileQueryParams
        }
        .sorted()
        .joinToString("&") { name ->
            "$name=${parsed.parameters.getAll(name).orEmpty().joinToString(",")}"
        }
    return buildString {
        append(protocol)
        append("://")
        append(authority)
        append(path)
        if (query.isNotEmpty()) {
            append('?')
            append(query)
        }
    }
}

/** One playable file together with every addon that offers it. */
data class MergedSource(
    val option: StreamOption,
    val providers: List<String>,
    val fingerprint: String
)

/**
 * Groups the sources of every addon by file identity, so a duplicate appears once
 * with the full list of addons that carry it. Within a group the best described
 * entry wins, because addons often label the same file differently.
 */
fun mergeSources(options: List<StreamOption>): List<MergedSource> {
    val groups = LinkedHashMap<String, MutableList<StreamOption>>()
    for (option in options) {
        groups.getOrPut(streamFingerprint(option)) { mutableListOf() }.add(option)
    }
    return groups.map { (fingerprint, group) ->
        MergedSource(
            option = group.maxByOrNull { sourceScore(it) } ?: group.first(),
            providers = group.map { it.addonName }.distinct(),
            fingerprint = fingerprint
        )
    }
}
