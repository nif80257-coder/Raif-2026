package com.nuvio.app.features.settings

import com.nuvio.app.features.addons.AddonManifest

internal const val AddonHealthCheckTimeoutMs = 10_000L
internal const val AddonHealthCheckSlowThresholdMs = 2_500L
internal const val AddonHealthCheckKnownVideoId = "tt0111601"

internal enum class AddonHealthStatus {
    OK,
    SLOW,
    DOWN,
    DISABLED,
}

internal enum class AddonSupportedType {
    CATALOGS,
    META,
    STREAMS,
    SUBTITLES,
}

internal data class AddonKnownContentTest(
    val videoId: String,
    val streamCount: Int? = null,
    val subtitleCount: Int? = null,
    val streamsTimedOut: Boolean = false,
    val subtitlesTimedOut: Boolean = false,
    val streamsError: String? = null,
    val subtitlesError: String? = null,
)

internal data class AddonCheckResult(
    val manifestUrl: String,
    val addonId: String,
    val name: String,
    val enabled: Boolean,
    val status: AddonHealthStatus,
    val responseTimeMs: Long?,
    val manifestParsed: Boolean,
    val manifestError: String?,
    val manifestVersion: String?,
    val supportedTypes: Set<AddonSupportedType>,
    val catalogsCount: Int,
    val knownTest: AddonKnownContentTest,
    val changedSinceLastCheck: Boolean?,
    val checkedAtEpochMs: Long,
)

internal fun AddonManifest.addonSupportedTypes(): Set<AddonSupportedType> = buildSet {
    if (catalogs.isNotEmpty() || resources.any { it.name.equals("catalog", true) }) {
        add(AddonSupportedType.CATALOGS)
    }
    if (resources.any { it.name.equals("meta", true) }) {
        add(AddonSupportedType.META)
    }
    if (resources.any { it.name.equals("stream", true) || it.name.equals("streams", true) }) {
        add(AddonSupportedType.STREAMS)
    }
    if (resources.any { it.name.equals("subtitles", true) || it.name.equals("subtitle", true) }) {
        add(AddonSupportedType.SUBTITLES)
    }
}

internal fun AddonManifest.supportsAddonType(type: AddonSupportedType): Boolean =
    type in addonSupportedTypes()

/** Stable descriptor used to detect manifest changes between checks. */
internal fun AddonManifest.addonFingerprint(): String = buildString {
    append(id)
    append('|')
    append(version)
    append('|')
    append(types.joinToString(","))
    append('|')
    append(
        resources.joinToString(",") { resource ->
            resource.name + ":" + resource.types.joinToString("+")
        },
    )
    append('|')
    append(catalogs.joinToString(",") { catalog -> catalog.type + "/" + catalog.id + "/" + catalog.name })
}
