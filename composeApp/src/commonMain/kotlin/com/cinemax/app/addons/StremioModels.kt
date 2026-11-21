package com.cinemax.app.addons

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class StremioManifest(
    val id: String,
    val version: String,
    val name: String,
    val description: String? = null,
    val resources: List<JsonElement> = emptyList(),
    val types: List<String> = emptyList(),
    val catalogs: List<StremioCatalog> = emptyList(),
    @SerialName("idPrefixes") val idPrefixes: List<String> = emptyList(),
    /** Manifest hints, including whether the addon has a configure page. */
    @SerialName("behaviorHints") val behaviorHints: StremioManifestBehaviorHints = StremioManifestBehaviorHints()
) {
    /**
     * Resource names declared by the manifest. The protocol lists them either as
     * plain strings ("stream") or as objects ({"name":"stream","types":[...]}).
     */
    fun resourceNames(): Set<String> = resources.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> (element["name"] as? JsonPrimitive)?.contentOrNull
            else -> null
        }
    }.toSet()
}

/** Protocol manifest hints: the configure page flag and the artwork slots. */
@Serializable
data class StremioManifestBehaviorHints(
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
    val adult: Boolean = false,
    val logo: String? = null,
    val background: String? = null
)

@Serializable
data class StremioCatalog(
    val type: String,
    val id: String,
    val name: String? = null,
    val extra: List<StremioExtra> = emptyList(),
    /** Legacy manifest form still used by many real addons. */
    @SerialName("extraSupported") val extraSupported: List<String> = emptyList(),
    /** Legacy manifest form that lists the required extras. */
    @SerialName("extraRequired") val extraRequired: List<String> = emptyList()
) {
    /**
     * Every extra this catalog accepts, from the current `extra` list and from the
     * legacy `extraSupported`/`extraRequired` fields. Without both forms, search
     * and pagination stay hidden for addons that only declare the old one.
     */
    fun extraNames(): Set<String> =
        (extra.map { it.name } + extraSupported + extraRequired).toSet()

    fun supportsExtra(name: String): Boolean = name in extraNames()
}

@Serializable
data class StremioExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList()
)

@Serializable
data class StremioMetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null
)

@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMetaPreview> = emptyList()
)

@Serializable
data class StremioMeta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val videos: List<StremioVideo> = emptyList()
)

/**
 * A playable entry of a series: its id ("tt1234:1:2") is what stream addons
 * expect instead of the series id, which returns nothing for series.
 */
@Serializable
data class StremioVideo(
    val id: String,
    val name: String? = null,
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val released: String? = null,
    val thumbnail: String? = null,
    val overview: String? = null
)

@Serializable
data class StremioMetaResponse(
    val meta: StremioMeta? = null
)

@Serializable
data class StremioStream(
    val name: String? = null,
    val title: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    /** A page or app to open instead of playing anything (official providers). */
    val externalUrl: String? = null,
    /** A YouTube video, which is opened as its official watch page. */
    val ytId: String? = null,
    val subtitles: List<StremioSubtitle> = emptyList(),
    val behaviorHints: StremioBehaviorHints = StremioBehaviorHints()
)

/** Protocol `behaviorHints` used for playback (headers, filename, hash, size). */
@Serializable
data class StremioBehaviorHints(
    val notWebReady: Boolean = false,
    val filename: String? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val bingeGroup: String? = null,
    val proxyHeaders: StremioProxyHeaders? = null,
    /** A ready-made size label, for providers that return one as text. */
    val videoSizeLabel: String? = null
)

@Serializable
data class StremioProxyHeaders(
    val request: Map<String, String> = emptyMap(),
    val response: Map<String, String> = emptyMap()
)

@Serializable
data class StremioSubtitle(
    val id: String? = null,
    val url: String,
    val lang: String,
    val label: String? = null
)

@Serializable
data class StremioSubtitlesResponse(
    val subtitles: List<StremioSubtitle> = emptyList()
)

@Serializable
data class StremioStreamResponse(
    val streams: List<StremioStream> = emptyList()
)

/** A stream keeps the addon it came from, so the shared UI can label its origin. */
data class StreamOption(
    val addonId: String,
    val addonName: String,
    val stream: StremioStream
)
