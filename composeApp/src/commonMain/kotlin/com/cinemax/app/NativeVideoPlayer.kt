package com.cinemax.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cinemax.app.core.EmbeddedSubtitleTrack

/** True when the platform has a full-format native player (libmpv on iOS). */
expect fun isNativeFullFormatPlayerAvailable(): Boolean

@Composable
expect fun NativeVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    initialPositionMillis: Long = 0L,
    preferredSubtitleLanguage: String? = null,
    preferredAudioLanguage: String? = null,
    embeddedSubtitleTrackId: Long? = null,
    /** Use the system AVPlayer even when libmpv is available. */
    useSystemPlayer: Boolean = false,
    httpHeaders: Map<String, String> = emptyMap(),
    onEmbeddedSubtitleTracks: (List<EmbeddedSubtitleTrack>) -> Unit = {},
    onProgress: (positionMillis: Long, durationMillis: Long) -> Unit = { _, _ -> },
    onPosition: (positionMillis: Long) -> Unit = {},
    onEnded: () -> Unit = {},
    onError: (message: String) -> Unit = {},
    /** Seek/speed/play controls for the overlay, once the player exists. */
    onControlsReady: (CinemaxPlayerControls) -> Unit = {},
    /** A tap on the video: the overlay uses it to show or hide itself. */
    onGestureTap: () -> Unit = {},
    /** Short feedback text for a native gesture (seek, volume, brightness). */
    onGestureHud: (message: String) -> Unit = {}
)
