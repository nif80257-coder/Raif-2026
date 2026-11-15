package com.cinemax.app

import com.russhwolf.settings.Settings
import com.cinemax.app.core.BADGE_PACKS

/**
 * Which Fusion badge pack is selected and the rules JSON last downloaded for it,
 * so badges still draw on a cold start with no network.
 */
class BadgePackStore(
    private val settings: Settings = Settings()
) {
    fun loadPackUrl(): String =
        settings.getStringOrNull(PACK)?.takeIf { it.isNotBlank() } ?: BADGE_PACKS.first().second

    fun savePackUrl(url: String) {
        settings.putString(PACK, url)
    }

    fun loadRawPack(): String? = settings.getStringOrNull(RAW)?.takeIf { it.isNotBlank() }

    fun saveRawPack(raw: String) {
        settings.putString(RAW, raw)
    }

    fun clear() {
        settings.remove(PACK)
        settings.remove(RAW)
    }

    private companion object {
        const val PACK = "cinemax.badges.pack"
        const val RAW = "cinemax.badges.raw"
    }
}
