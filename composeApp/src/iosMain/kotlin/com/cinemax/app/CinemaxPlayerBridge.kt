package com.cinemax.app

import platform.UIKit.UIViewController

/**
 * Gestures the native player recognises on the video surface itself. Compose
 * cannot receive touches over a UIKit-hosted view, so the player reports them
 * back for the overlay and the heads-up text.
 */
interface CinemaxPlayerGestures {
    fun onTap()
    fun onSeek(targetMs: Long, deltaMs: Long)
    fun onScrub(positionMs: Long, durationMs: Long)
    fun onScrubEnd(positionMs: Long)
    fun onVolume(percent: Int)
    fun onBrightness(percent: Int)
}

/**
 * Optional native player (libmpv through MPVKit) registered by the Swift app at
 * launch. It plays every container, including MKV; when it is not registered the
 * Compose player falls back to AVPlayer.
 */
interface CinemaxNativePlayer {
    fun createViewController(): UIViewController
    fun load(url: String, headersJson: String, startPositionMs: Long)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun setSubtitleUrl(url: String?)
    /** Embedded (in-file) subtitle tracks as JSON: see `parseEmbeddedSubtitleTracks`. */
    fun getEmbeddedSubtitleTracksJson(): String
    /**
     * Selects an embedded subtitle track by its player track id, falling back to
     * its ordinal [ordinal] when the player only accepts positions. A negative id
     * turns embedded subtitles off.
     */
    fun setSubtitleTrackId(trackId: Long, ordinal: Int)
    /** Embedded (in-file) audio tracks as JSON: see `parseEmbeddedAudioTracks`. */
    fun getEmbeddedAudioTracksJson(): String
    /**
     * Selects an embedded audio track by its player track id, falling back to its
     * ordinal [ordinal] when the player only accepts positions.
     */
    fun setAudioTrackId(trackId: Long, ordinal: Int)
    /** Tap, double-tap and drag gestures on the video surface. */
    fun setGestureListener(listener: CinemaxPlayerGestures?)
    fun refresh()
    fun getPositionMs(): Long
    fun getDurationMs(): Long
    fun isEnded(): Boolean
    fun getErrorMessage(): String
    fun destroy()
}

object CinemaxPlayerBridge {
    private var factory: (() -> CinemaxNativePlayer)? = null

    fun register(factory: () -> CinemaxNativePlayer) {
        this.factory = factory
    }

    fun create(): CinemaxNativePlayer? = factory?.invoke()

    val isRegistered: Boolean get() = factory != null
}
