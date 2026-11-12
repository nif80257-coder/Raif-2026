package com.cinemax.app

import com.russhwolf.settings.Settings

/** User-facing appearance and playback preferences for the iOS app. */
data class CinemaxPreferences(
    val accent: String = "Violet",
    val oledBlack: Boolean = true,
    val compactCards: Boolean = false,
    val rememberPlaybackPosition: Boolean = true,
    val autoplayNextEpisode: Boolean = false,
    val preferredSubtitleLanguage: String = "ar",
    val preferredAudioLanguage: String = "device",
    /** "small", "medium" or "large": the size of addon subtitles over the video. */
    val subtitleSize: String = "medium",
    /** "mpv" (libmpv through MPVKit) or "system" (AVPlayer). */
    val preferredPlayer: String = "mpv"
)

class CinemaxPreferencesStore(
    private val settings: Settings = Settings()
) {
    fun load() = CinemaxPreferences(
        accent = settings.getString(ACCENT, "Violet"),
        oledBlack = settings.getBoolean(OLED, true),
        compactCards = settings.getBoolean(COMPACT, false),
        rememberPlaybackPosition = settings.getBoolean(RESUME, true),
        autoplayNextEpisode = settings.getBoolean(AUTOPLAY, false),
        preferredSubtitleLanguage = settings.getString(SUBTITLE, "ar"),
        preferredAudioLanguage = settings.getString(AUDIO, "device"),
        subtitleSize = settings.getString(SUBTITLE_SIZE, "medium"),
        preferredPlayer = settings.getString(PLAYER, "mpv")
    )

    fun save(value: CinemaxPreferences) {
        settings.putString(ACCENT, value.accent)
        settings.putBoolean(OLED, value.oledBlack)
        settings.putBoolean(COMPACT, value.compactCards)
        settings.putBoolean(RESUME, value.rememberPlaybackPosition)
        settings.putBoolean(AUTOPLAY, value.autoplayNextEpisode)
        settings.putString(SUBTITLE, value.preferredSubtitleLanguage)
        settings.putString(AUDIO, value.preferredAudioLanguage)
        settings.putString(SUBTITLE_SIZE, value.subtitleSize)
        settings.putString(PLAYER, value.preferredPlayer)
    }

    private companion object {
        const val ACCENT = "cinemax.ui.accent"
        const val OLED = "cinemax.ui.oled"
        const val COMPACT = "cinemax.ui.compact"
        const val RESUME = "cinemax.playback.resume"
        const val AUTOPLAY = "cinemax.playback.autoplay"
        const val SUBTITLE = "cinemax.subtitle.language"
        const val AUDIO = "cinemax.audio.language"
        const val SUBTITLE_SIZE = "cinemax.subtitle.size"
        const val PLAYER = "cinemax.playback.player"
    }
}
