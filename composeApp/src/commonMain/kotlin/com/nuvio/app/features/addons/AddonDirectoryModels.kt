package com.nuvio.app.features.addons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** One add-on a source lists through its `addon_catalog` resource, ready to install as it is. */
data class AddonDirectoryEntry(
    val manifestUrl: String,
    val name: String,
    val description: String,
    val logoUrl: String?,
    val types: List<String>,
    val isP2p: Boolean,
    val isAdult: Boolean,
    val isInstalled: Boolean = false,
)

/** The directory of one source, as its screen draws it. */
data class AddonDirectoryUiState(
    val manifestUrl: String = "",
    val title: String = "",
    val isLoading: Boolean = false,
    val entries: List<AddonDirectoryEntry> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Reads the payload of an `addon_catalog` directory. Both shapes in the wild arrive as a `metas`
 * array (some servers use `addons`): an entry carries either a `manifestUrl`, a `transportUrl`
 * whose base gets `/manifest.json`, or the manifest itself.
 */
internal object AddonDirectoryParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        payload: String,
        installedManifestUrls: Set<String>,
    ): List<AddonDirectoryEntry> {
        val root = json.parseToJsonElement(payload)
        val listed = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["metas"] as? JsonArray)
                ?: (root["addons"] as? JsonArray)
                ?: JsonArray(emptyList())

            else -> JsonArray(emptyList())
        }
        val installed = installedManifestUrls.mapTo(mutableSetOf()) { url -> ensureAddonManifestUrl(url) }
        return listed
            .mapNotNull { element -> (element as? JsonObject)?.toDirectoryEntry(installed) }
            .distinctBy { entry -> entry.manifestUrl }
    }

    private fun JsonObject.toDirectoryEntry(installed: Set<String>): AddonDirectoryEntry? {
        val embedded = this["manifest"] as? JsonObject
        val manifestUrl = (text("manifestUrl") ?: text("transportUrl"))
            ?.takeIf { url -> url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//") }
            ?.let { url -> if (url.startsWith("//")) "https:$url" else url }
            ?.let(::ensureAddonManifestUrl)
            ?: return null
        // A directory is a way to find sources that answer with links. One served from this
        // device — the app's own 127.0.0.1 add-on server — only answers while the app runs that
        // server, and installing it from here is the request that failed and left the raw error on
        // screen, so it is not offered at all. One that describes no `stream` resource carries
        // catalogs, metadata or subtitles and has nothing to play, so it is not offered either.
        if (manifestUrl.isLocalDeviceUrl()) return null
        if (embedded?.carriesStreams() == false) return null
        val hints = (this["behaviorHints"] as? JsonObject)
            ?: (embedded?.get("behaviorHints") as? JsonObject)
        val types = strings("types").ifEmpty { embedded?.strings("types").orEmpty() }
        val name = (text("name") ?: embedded?.text("name"))
            .orEmpty()
            .ifBlank { manifestUrl.substringAfter("://").substringBefore('/') }
        return AddonDirectoryEntry(
            manifestUrl = manifestUrl,
            name = name,
            description = (text("description") ?: embedded?.text("description")).orEmpty(),
            logoUrl = text("logo") ?: embedded?.text("logo"),
            types = types,
            isP2p = hints?.flag("p2p") == true,
            isAdult = hints?.flag("adult") == true,
            isInstalled = manifestUrl in installed,
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

    private fun JsonObject.strings(key: String): List<String> {
        val elements = (this[key] as? JsonArray)?.toList().orEmpty()
        return elements.mapNotNull { element ->
            (element as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        }
    }

    private fun JsonObject.flag(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull == true

    /** The `resources` a manifest declares, by name: `stream`, `catalog`, `meta`, … */
    private fun JsonObject.resourceNames(): List<String> =
        (this["resources"] as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonObject)?.text("name") }
            .orEmpty()

    /**
     * True when the manifest is known to serve `stream`. A manifest that declares no resources at
     * all is not one this can judge, so it is kept rather than hidden on a guess.
     */
    private fun JsonObject.carriesStreams(): Boolean {
        val resources = resourceNames()
        return resources.isEmpty() || resources.any { name -> name.equals("stream", ignoreCase = true) }
    }
}

/**
 * The host a link points at, by itself: no scheme, port or path. An IPv6 host keeps its brackets,
 * so `[::1]:11470` reads as `[::1]`.
 */
private fun manifestHost(url: String): String {
    val authority = url.substringAfter("://").substringBefore('/').substringBefore('?')
    val host = if (authority.startsWith("[")) {
        authority.substringBefore(']') + "]"
    } else {
        authority.substringBefore(':')
    }
    return host.trim().lowercase()
}

/**
 * True when a link only resolves on this device or inside the local network: the add-on server the
 * app bundles answers on 127.0.0.1 and nowhere else, so a source pointing there is not a source a
 * person can install and watch with. A link that is not plain HTTP is not one mpv can play either.
 */
internal fun String.isLocalDeviceUrl(): Boolean {
    if (!startsWith("http://") && !startsWith("https://")) return true
    val host = manifestHost(this)
    return host.isEmpty() ||
        host == "localhost" ||
        host == "127.0.0.1" ||
        host == "0.0.0.0" ||
        host == "[::1]" ||
        host.endsWith(".local") ||
        host.startsWith("192.168.") ||
        host.startsWith("10.") ||
        host.startsWith("172.16.")
}

/** The manifest link of a directory entry, whether it was given as a base URL or a full one. */
internal fun ensureAddonManifestUrl(url: String): String {
    val path = url.substringBefore("?").trimEnd('/')
    val query = url.substringAfter("?", "")
    val withSuffix = if (path.endsWith("/manifest.json")) path else "$path/manifest.json"
    return if (query.isEmpty()) withSuffix else "$withSuffix?$query"
}
