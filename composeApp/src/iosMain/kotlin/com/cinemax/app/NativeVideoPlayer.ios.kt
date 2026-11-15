package com.cinemax.app

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitViewController
import com.cinemax.app.core.EmbeddedAudioTrack
import com.cinemax.app.core.EmbeddedSubtitleTrack
import com.cinemax.app.core.languageIndex
import com.cinemax.app.core.parseEmbeddedAudioTracks
import com.cinemax.app.core.parseEmbeddedSubtitleTracks
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVMediaCharacteristicAudible
import platform.AVFoundation.AVMediaCharacteristicLegible
import platform.AVFoundation.AVMediaSelectionOption
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerStatusFailed
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.asset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setRate
import platform.AVFoundation.selectMediaOption
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun isNativeFullFormatPlayerAvailable(): Boolean = CinemaxPlayerBridge.isRegistered

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeVideoPlayer(
    url: String,
    modifier: Modifier,
    initialPositionMillis: Long,
    preferredSubtitleLanguage: String?,
    preferredAudioLanguage: String?,
    embeddedSubtitleTrackId: Long?,
    selectedAudioTrackId: Long?,
    useSystemPlayer: Boolean,
    httpHeaders: Map<String, String>,
    onEmbeddedSubtitleTracks: (List<EmbeddedSubtitleTrack>) -> Unit,
    onEmbeddedAudioTracks: (List<EmbeddedAudioTrack>) -> Unit,
    onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
    onPosition: (positionMillis: Long) -> Unit,
    onEnded: () -> Unit,
    onError: (message: String) -> Unit,
    onControlsReady: (CinemaxPlayerControls) -> Unit,
    onGestureTap: () -> Unit,
    onGestureHud: (message: String) -> Unit
) {
    // The iPhone must not dim or lock itself while a video is playing.
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
    val nativePlayer = remember { CinemaxPlayerBridge.create() }
    if (nativePlayer != null && !useSystemPlayer) {
        MpvPlayerSurface(
            player = nativePlayer,
            url = url,
            httpHeaders = httpHeaders,
            initialPositionMillis = initialPositionMillis,
            embeddedSubtitleTrackId = embeddedSubtitleTrackId,
            selectedAudioTrackId = selectedAudioTrackId,
            onEmbeddedSubtitleTracks = onEmbeddedSubtitleTracks,
            onEmbeddedAudioTracks = onEmbeddedAudioTracks,
            modifier = modifier,
            onProgress = onProgress,
            onPosition = onPosition,
            onEnded = onEnded,
            onError = onError,
            onControlsReady = onControlsReady,
            onGestureTap = onGestureTap,
            onGestureHud = onGestureHud
        )
        return
    }
    val player = remember(url, httpHeaders) { buildPlayer(url, httpHeaders, initialPositionMillis) }
    if (player == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("تعذر تجهيز رابط التشغيل", color = Color.White)
        }
        return
    }
    LaunchedEffect(player) {
        // Playback audio should keep going with the ring/silent switch set to silent.
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        }
        var ticks = 0
        while (isActive) {
            val item = player.currentItem
            if (item?.status == AVPlayerItemStatusFailed || player.status == AVPlayerStatusFailed) {
                onError(item?.error?.localizedDescription ?: "تعذر تشغيل هذا المصدر")
                break
            }
            val positionSeconds = CMTimeGetSeconds(player.currentTime())
            val durationSeconds = item?.duration?.let { CMTimeGetSeconds(it) } ?: 0.0
            if (positionSeconds.isFinite() && durationSeconds.isFinite() &&
                positionSeconds > 0.0 && durationSeconds > 0.0
            ) {
                val positionMillis = (positionSeconds * 1000.0).toLong()
                onPosition(positionMillis)
                ticks += 1
                if (ticks % 20 == 0) {
                    onProgress(positionMillis, (durationSeconds * 1000.0).toLong())
                }
            }
            delay(250)
        }
    }
    LaunchedEffect(player, preferredSubtitleLanguage, preferredAudioLanguage) {
        selectTrack(player, AVMediaCharacteristicAudible, preferredAudioLanguage)
        selectTrack(player, AVMediaCharacteristicLegible, preferredSubtitleLanguage ?: "off")
    }
    LaunchedEffect(player) {
        // The system player exposes its own scrub bar, speed menu and gestures,
        // so the shared overlay only needs a handle for pause/speed.
        onControlsReady(
            CinemaxPlayerControls(
                seekTo = { millis ->
                    player.seekToTime(CMTimeMakeWithSeconds(millis.toDouble() / 1000.0, 1000))
                },
                setSpeed = { speed -> player.setRate(speed) },
                setPlaying = { playing -> if (playing) player.play() else player.pause() }
            )
        )
    }
    // AVPlayer exposes the file's own subtitle tracks as legible media options;
    // they are listed in the picker next to the addon-provided subtitles.
    LaunchedEffect(player) {
        val options = avPlayerSubtitleOptions(player)
        if (options.isNotEmpty()) {
            onEmbeddedSubtitleTracks(
                options.mapIndexed { index, option ->
                    EmbeddedSubtitleTrack(
                        id = index.toLong(),
                        index = index,
                        language = option.extendedLanguageTag() ?: "",
                        title = option.displayName,
                        codec = ""
                    )
                }
            )
        }
    }
    LaunchedEffect(player, embeddedSubtitleTrackId) {
        val wanted = embeddedSubtitleTrackId ?: return@LaunchedEffect
        val item = player.currentItem ?: return@LaunchedEffect
        val group = item.asset?.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
            ?: return@LaunchedEffect
        val options = group.options.filterIsInstance<AVMediaSelectionOption>()
        options.getOrNull(wanted.toInt())?.let { option ->
            item.selectMediaOption(option, inMediaSelectionGroup = group)
        }
    }
    DisposableEffect(player) {
        val item = player.currentItem
        val observer = item?.let { current ->
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = current,
                queue = NSOperationQueue.mainQueue
            ) { _ -> onEnded() }
        }
        onDispose {
            observer?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
        }
    }
    UIKitViewController(
        factory = {
            AVPlayerViewController().apply {
                this.player = player
                showsPlaybackControls = true
                allowsPictureInPicturePlayback = true
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun buildPlayer(url: String, httpHeaders: Map<String, String>, initialPositionMillis: Long): AVPlayer? {
    val nsUrl = NSURL(string = url) ?: return null
    val asset = AVURLAsset(
        uRL = nsUrl,
        options = if (httpHeaders.isEmpty()) null
        else mapOf<Any?, Any?>("AVURLAssetHTTPHeaderFieldsKey" to httpHeaders)
    )
    val item = AVPlayerItem(asset = asset)
    val player = AVPlayer(playerItem = item)
    if (initialPositionMillis > 0L) {
        item.seekToTime(CMTimeMakeWithSeconds(initialPositionMillis.toDouble() / 1000.0, 1000))
    }
    player.play()
    return player
}

/**
 * Waits for the item's media selection group to load, then selects the preferred
 * language. "off" clears the subtitle selection instead of selecting a track.
 */
private suspend fun selectTrack(player: AVPlayer, characteristic: String?, wanted: String?) {
    repeat(20) {
        val item = player.currentItem ?: return
        val group = item.asset?.mediaSelectionGroupForMediaCharacteristic(characteristic)
        if (group == null) {
            delay(250)
            return@repeat
        }
        val options = group.options.filterIsInstance<AVMediaSelectionOption>()
        if (wanted == null || wanted == "off") {
            item.selectMediaOption(null, inMediaSelectionGroup = group)
            return
        }
        val index = languageIndex(options.map { option -> option.extendedLanguageTag() }, wanted)
        if (index != null) item.selectMediaOption(options[index], inMediaSelectionGroup = group)
        return
    }
}

/** The file's embedded subtitle tracks, once AVPlayer has loaded them. */
private suspend fun avPlayerSubtitleOptions(player: AVPlayer): List<AVMediaSelectionOption> {
    repeat(20) {
        val group = player.currentItem?.asset
            ?.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
        if (group != null) return group.options.filterIsInstance<AVMediaSelectionOption>()
        delay(250)
    }
    return emptyList()
}


/** libmpv surface: plays every container (MKV included) through the Swift bridge. */
@Composable
private fun MpvPlayerSurface(
    player: CinemaxNativePlayer,
    url: String,
    httpHeaders: Map<String, String>,
    initialPositionMillis: Long,
    embeddedSubtitleTrackId: Long?,
    selectedAudioTrackId: Long?,
    onEmbeddedSubtitleTracks: (List<EmbeddedSubtitleTrack>) -> Unit,
    onEmbeddedAudioTracks: (List<EmbeddedAudioTrack>) -> Unit,
    modifier: Modifier,
    onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
    onPosition: (positionMillis: Long) -> Unit,
    onEnded: () -> Unit,
    onError: (message: String) -> Unit,
    onControlsReady: (CinemaxPlayerControls) -> Unit,
    onGestureTap: () -> Unit,
    onGestureHud: (message: String) -> Unit
) {
    val controller = remember(player) { player.createViewController() }
    val controlsCallback by rememberUpdatedState(onControlsReady)
    val gestureTap by rememberUpdatedState(onGestureTap)
    val gestureHud by rememberUpdatedState(onGestureHud)
    LaunchedEffect(player) {
        controlsCallback(
            CinemaxPlayerControls(
                seekTo = { millis -> player.seekTo(millis.coerceAtLeast(0L)) },
                setSpeed = { speed -> player.setSpeed(speed) },
                setPlaying = { playing -> if (playing) player.play() else player.pause() }
            )
        )
        player.setGestureListener(object : CinemaxPlayerGestures {
            override fun onTap() {
                gestureTap()
            }

            override fun onSeek(targetMs: Long, deltaMs: Long) {
                val seconds = (deltaMs / 1000L).toInt()
                gestureHud(if (seconds >= 0) "＋$seconds ث" else "$seconds ث")
            }

            override fun onScrub(positionMs: Long, durationMs: Long) {
                gestureHud("${formatClock(positionMs)} / ${formatClock(durationMs)}")
            }

            override fun onScrubEnd(positionMs: Long) {
                gestureHud(formatClock(positionMs))
            }

            override fun onVolume(percent: Int) {
                gestureHud("الصوت $percent%")
            }

            override fun onBrightness(percent: Int) {
                gestureHud("الإضاءة $percent%")
            }
        })
    }
    // The subtitle picker changes the wanted track while playback keeps running,
    // so the polling loop reads these instead of being restarted.
    val wantedTrackId by rememberUpdatedState(embeddedSubtitleTrackId)
    val reportTracks by rememberUpdatedState(onEmbeddedSubtitleTracks)
    val wantedAudioTrackId by rememberUpdatedState(selectedAudioTrackId)
    val reportAudioTracks by rememberUpdatedState(onEmbeddedAudioTracks)
    LaunchedEffect(player, url) {
        val headersJson = buildJsonObject {
            httpHeaders.forEach { (name, value) -> put(name, value) }
        }.toString()
        player.load(url, headersJson, initialPositionMillis)
    }
    LaunchedEffect(player, url) {
        var poll = 0
        var reported: List<EmbeddedSubtitleTrack>? = null
        // No track has this id, so the first pass always applies the choice.
        var appliedTrackId: Long? = Long.MIN_VALUE
        var attempts = 0
        var reportedAudio: List<EmbeddedAudioTrack>? = null
        var appliedAudioId: Long? = Long.MIN_VALUE
        var audioAttempts = 0
        while (isActive) {
            player.refresh()
            val position = player.getPositionMs()
            val duration = player.getDurationMs()
            if (position > 0L) {
                onPosition(position)
                if (poll % 20 == 0 && duration > 0L) onProgress(position, duration)
            }
            if (poll % 4 == 0) {
                // The file's own subtitle tracks, listed once the stream is open,
                // and the one the user picked in the picker.
                val tracks = parseEmbeddedSubtitleTracks(player.getEmbeddedSubtitleTracksJson())
                if (tracks != reported) {
                    reported = tracks
                    reportTracks(tracks)
                }
                val wanted = wantedTrackId
                if (wanted != appliedTrackId) {
                    appliedTrackId = wanted
                    attempts = 0
                }
                val settled = if (wanted == null) {
                    tracks.none { it.selected }
                } else {
                    tracks.any { it.id == wanted && it.selected }
                }
                if (!settled && attempts < 8) {
                    val ordinal = tracks.firstOrNull { it.id == wanted }?.index ?: -1
                    player.setSubtitleTrackId(wanted ?: -1L, ordinal)
                    attempts += 1
                }
                // The file's own audio tracks, listed once the stream is open, and
                // the one the user picked. Audio is never turned off, so a null
                // choice leaves the player's default track alone.
                val audioTracks = parseEmbeddedAudioTracks(player.getEmbeddedAudioTracksJson())
                if (audioTracks != reportedAudio) {
                    reportedAudio = audioTracks
                    reportAudioTracks(audioTracks)
                }
                val wantedAudio = wantedAudioTrackId
                if (wantedAudio != null) {
                    if (wantedAudio != appliedAudioId) {
                        appliedAudioId = wantedAudio
                        audioAttempts = 0
                    }
                    val settledAudio = audioTracks.any { it.id == wantedAudio && it.selected }
                    if (!settledAudio && audioAttempts < 8) {
                        val ordinal = audioTracks.firstOrNull { it.id == wantedAudio }?.index ?: -1
                        player.setAudioTrackId(wantedAudio, ordinal)
                        audioAttempts += 1
                    }
                }
            }
            if (player.isEnded()) {
                onEnded()
                break
            }
            val error = player.getErrorMessage()
            if (error.isNotEmpty()) {
                onError(error)
                break
            }
            delay(250)
            poll += 1
        }
    }
    DisposableEffect(player) {
        onDispose { player.destroy() }
    }
    UIKitViewController(factory = { controller }, modifier = modifier)
}
