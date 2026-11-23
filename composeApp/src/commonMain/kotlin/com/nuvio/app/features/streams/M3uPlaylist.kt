package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.fetchAddonResponseText
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** One playable line of an M3U list, with the headers its own options asked for. */
internal data class M3uEntry(
    val url: String,
    val title: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

/** How long a playlist may take to answer before the original link is kept as it is. */
private const val PLAYLIST_FETCH_TIMEOUT_MS = 8_000L

/** How many entries of a playlist become stream rows; a 10 000-channel list is not a stream list. */
private const val MAX_PLAYLIST_ENTRIES = 8

private val playlistJson = Json { ignoreUnknownKeys = true }

/**
 * True for a URL that points at an M3U *list* rather than an HLS media playlist.
 *
 * `.m3u8` is a manifest the player reads itself, so it is left alone. A plain `.m3u` (or a link
 * marked `format=m3u`, which is how several add-ons expose IPTV lists) holds one line per channel
 * with its own options, and handing that to the player only produces a playback error.
 */
internal fun String.looksLikeM3uListUrl(): Boolean {
    val withoutQuery = substringBefore('?').substringBefore('#').trim().lowercase()
    if (withoutQuery.endsWith(".m3u")) return true
    if (withoutQuery.endsWith(".m3u8")) return false
    val query = substringAfter('?', missingDelimiterValue = "").lowercase()
    return query.split('&', ';').any { parameter ->
        val name = parameter.substringBefore('=')
        val value = parameter.substringAfter('=', "")
        name == "format" && value == "m3u"
    }
}

/**
 * Reads an M3U list into its playable entries.
 *
 * The parser keeps what a player needs and drops the rest: `#EXTINF` gives the name after its last
 * comma, `#EXTVLCOPT` gives `http-user-agent` / `http-referrer` / `http-origin` / `http-cookie` in
 * the player's own words, and `#EXTHTTP` (the JSON form Stremio add-ons use) gives a whole header
 * map. Anything that is not an `http(s)` line is ignored, so a comment, an empty line or a
 * relative path never becomes a stream row.
 */
internal fun parseM3uPlaylist(payload: String): List<M3uEntry> {
    if (!payload.contains("#EXTM3U", ignoreCase = true) && !payload.contains("#EXTINF", ignoreCase = true)) {
        return emptyList()
    }
    val entries = mutableListOf<M3uEntry>()
    var pendingTitle: String? = null
    var pendingHeaders = mutableMapOf<String, String>()

    payload.lineSequence().forEach { rawLine ->
        val line = rawLine.trim().removePrefix("\uFEFF")
        when {
            line.isEmpty() -> Unit

            line.startsWith("#EXTINF", ignoreCase = true) -> {
                pendingTitle = line.substringAfter(',', missingDelimiterValue = "")
                    .trim()
                    .takeIf { it.isNotEmpty() }
            }

            line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                val option = line.substringAfter(':').trim()
                val value = option.substringAfter('=', "").trim()
                if (value.isNotEmpty()) {
                    when (option.substringBefore('=').trim().lowercase()) {
                        "http-user-agent" -> pendingHeaders["User-Agent"] = value
                        "http-referrer", "http-referer" -> pendingHeaders["Referer"] = value
                        "http-origin" -> pendingHeaders["Origin"] = value
                        "http-cookie" -> pendingHeaders["Cookie"] = value
                    }
                }
            }

            line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                pendingHeaders.putAll(parseInlineHeaders(line.substringAfter(':')))
            }

            line.startsWith("#") -> Unit

            line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true) -> {
                entries += M3uEntry(
                    url = line,
                    title = pendingTitle,
                    headers = pendingHeaders.toMap(),
                )
                pendingTitle = null
                pendingHeaders = mutableMapOf()
            }
        }
    }
    return entries
}

private fun parseInlineHeaders(encoded: String): Map<String, String> {
    val json = runCatching { playlistJson.parseToJsonElement(encoded.trim()).jsonObject }.getOrNull()
        ?: return emptyMap()
    return json.mapNotNull { (key, value) ->
        val text = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (key.isBlank() || text.isEmpty()) null else key.trim() to text
    }.toMap()
}

/**
 * Expands stream rows whose URL is actually a playlist into the playable entries it holds.
 *
 * Several sources return one link to a whole M3U list instead of a stream: played as they are they
 * fail with no explanation. The entries inherit the source's own headers and add the ones their
 * options declared, so a channel that needs its own `User-Agent` still plays. A link that does not
 * parse, does not answer in time or is not a list is left exactly as it was, so nothing is lost.
 */
internal suspend fun List<StreamItem>.expandingPlaylistLinks(): List<StreamItem> {
    if (none { stream -> stream.url?.looksLikeM3uListUrl() == true }) return this
    return flatMap { stream -> stream.expandPlaylistLink() }
}

private suspend fun StreamItem.expandPlaylistLink(): List<StreamItem> {
    val link = url?.takeIf { it.looksLikeM3uListUrl() } ?: return listOf(this)
    val payload = withTimeoutOrNull(PLAYLIST_FETCH_TIMEOUT_MS) {
        runCatching { fetchAddonResponseText(url = link, forceRefresh = false) }.getOrNull()
    } ?: return listOf(this)
    val entries = parseM3uPlaylist(payload).take(MAX_PLAYLIST_ENTRIES)
    if (entries.isEmpty()) return listOf(this)

    val inheritedHeaders = behaviorHints.proxyHeaders?.request.orEmpty()
    return entries.map { entry ->
        val headers = inheritedHeaders + entry.headers
        copy(
            name = entry.title ?: name,
            title = entry.title ?: title,
            url = entry.url,
            behaviorHints = behaviorHints.copy(
                proxyHeaders = if (headers.isEmpty()) {
                    null
                } else {
                    StreamProxyHeaders(request = headers)
                },
            ),
        )
    }
}
