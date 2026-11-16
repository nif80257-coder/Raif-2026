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
    /** Playback speed remembered between episodes and launches (1f is normal). */
    val preferredSpeed: Float = 1f,
    /** "mpv" (libmpv through MPVKit) or "system" (AVPlayer). */
    val preferredPlayer: String = "mpv",
    /** Hide links that failed in the last day instead of only demoting them. */
    val hideDeadSources: Boolean = false,
    /** "auto", "4K", "1080p" or "720p": the quality "play the best source" prefers. */
    val preferredQuality: String = "auto"
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
        preferredSpeed = settings.getFloat(SPEED, 1f),
        preferredPlayer = settings.getString(PLAYER, "mpv"),
        hideDeadSources = settings.getBoolean(HIDE_DEAD, false),
        preferredQuality = settings.getString(QUALITY, "auto")
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
        settings.putFloat(SPEED, value.preferredSpeed)
        settings.putString(PLAYER, value.preferredPlayer)
        settings.putBoolean(HIDE_DEAD, value.hideDeadSources)
        settings.putString(QUALITY, value.preferredQuality)
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
        const val SPEED = "cinemax.playback.speed"
        const val PLAYER = "cinemax.playback.player"
        const val HIDE_DEAD = "cinemax.sources.hidedead"
        const val QUALITY = "cinemax.sources.quality"
    }
}
