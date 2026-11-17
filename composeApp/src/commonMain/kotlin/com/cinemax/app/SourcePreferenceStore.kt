package com.cinemax.app

import com.russhwolf.settings.Settings

/** What a viewer prefers for the sources of one addon. */
data class SourcePreference(
    val quality: String = "auto",
    val audio: String = "",
    val subtitle: String = ""
)

/**
 * Remembers a quality/audio/subtitle preference per addon id, so "play the best
 * source" can honour what the user tends to pick from that provider instead of
 * one global setting. Written when a source plays (the tier that worked) and
 * readable by the ranking.
 */
class SourcePreferenceStore(
    private val settings: Settings = Settings()
) {
    fun load(addonId: String): SourcePreference = parse(settings.getStringOrNull(key(addonId)))

    fun loadAll(): Map<String, SourcePreference> = settings.keys
        .filter { it.startsWith(PREFIX) }
        .mapNotNull { key -> parse(settings.getStringOrNull(key))?.let { key.removePrefix(PREFIX) to it } }
        .toMap()

    fun save(addonId: String, preference: SourcePreference) {
        if (addonId.isBlank()) return
        settings.putString(
            key(addonId),
            listOf(preference.quality, preference.audio, preference.subtitle).joinToString("|")
        )
    }

    /** Remembers the quality tier a link actually had when it played. */
    fun recordPlayedQuality(addonId: String, quality: String) {
        if (addonId.isBlank() || quality.isBlank() || quality == "auto") return
        val current = load(addonId)
        if (current.quality == quality) return
        save(addonId, current.copy(quality = quality))
    }

    fun clear() {
        settings.keys.filter { it.startsWith(PREFIX) }.forEach { settings.remove(it) }
    }

    private fun parse(raw: String?): SourcePreference {
        val parts = raw?.split('|') ?: return SourcePreference()
        return SourcePreference(
            quality = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "auto",
            audio = parts.getOrNull(1).orEmpty(),
            subtitle = parts.getOrNull(2).orEmpty()
        )
    }

    private fun key(addonId: String) = PREFIX + addonId

    private companion object {
        const val PREFIX = "cinemax.source.pref."
    }
}
