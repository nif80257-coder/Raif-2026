// Compiled only when the MPVKit package is present. Without it the app falls back
// to AVPlayer, so a build can ship without the libmpv/ffmpeg/MoltenVK stack.
#if canImport(Libmpv)

import Foundation
import UIKit
import Libmpv
import CinemaxKit

/// Minimal libmpv player. Kotlin polls state through the CinemaxNativePlayer
/// bridge every 250 ms, so no extra threads or event loops are needed here; the
/// mpv event queue is drained inside the polling calls to catch stream errors.
final class MPVViewController: UIViewController {

    private var mpv: OpaquePointer?
    private let metalLayer = MPVMetalLayer()

    private var pendingURL: String?
    private var pendingHeaders: [String: String] = [:]
    private var pendingStartMs: Int64 = 0
    private var pendingSeekWorkItem: DispatchWorkItem?

    private var errorText: String = ""
    private var isLoaded = false
    private var cachedDurationMs: Int64 = 0
    private var cachedPositionMs: Int64 = 0
    private var cachedEnded = false
    /// Whether the viewer was watching when the app went to the background, so a
    /// deliberate pause is not undone on the way back.
    private var wasPlayingBeforeBackground = false
    /// The last warnings libmpv wrote, kept so a black picture can be explained.
    private var recentLog: [String] = []

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        view.layer.masksToBounds = true
        metalLayer.frame = view.bounds
        metalLayer.contentsScale = UIScreen.main.nativeScale
        view.layer.addSublayer(metalLayer)
        setupMPV()
        installLifecycleObservers()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateSurfaceSize()
        startPendingLoadIfReady()
    }

    /// Turns with the phone. mpv draws into this layer, so its drawable has to
    /// follow the window the moment the window changes shape: left alone it keeps
    /// the old size and the picture comes out stretched or half black.
    override func viewWillTransition(
        to size: CGSize,
        with coordinator: UIViewControllerTransitionCoordinator
    ) {
        super.viewWillTransition(to: size, with: coordinator)
        coordinator.animate(alongsideTransition: { _ in
            self.updateSurfaceSize()
        }, completion: { _ in
            self.updateSurfaceSize()
        })
    }

    /// Keeps the drawable the same shape as the window, at the screen's own
    /// scale, which is what makes the picture fill the screen after a rotation.
    private func updateSurfaceSize() {
        let bounds = view.bounds
        guard bounds.width > 1, bounds.height > 1 else { return }
        let scale = view.window?.screen.nativeScale ?? UIScreen.main.nativeScale
        let drawable = CGSize(
            width: (bounds.width * scale).rounded(),
            height: (bounds.height * scale).rounded()
        )
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        metalLayer.frame = bounds
        metalLayer.contentsScale = scale
        if metalLayer.drawableSize != drawable {
            metalLayer.drawableSize = drawable
        }
        CATransaction.commit()
    }

    /// A player that came back from the background is the classic black screen:
    /// the video output is dropped on the way out and asked for again on the way in.
    private func installLifecycleObservers() {
        let center = NotificationCenter.default
        center.addObserver(
            self,
            selector: #selector(appDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
        center.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }

    @objc private func appDidEnterBackground() {
        guard let mpv, isLoaded else { return }
        wasPlayingBeforeBackground = !getFlag(mpv, "pause")
        pausePlayback()
        setString(mpv, "vid", "no")
    }

    @objc private func appWillEnterForeground() {
        guard let mpv, isLoaded else { return }
        setString(mpv, "vid", "auto")
        if wasPlayingBeforeBackground {
            playPlayback()
        }
    }

    deinit {
        destroyPlayer()
    }

    // MARK: - Public API

    func load(urlString: String, headersJson: String, startMs: Int64) {
        pendingURL = urlString
        pendingHeaders = Self.parseHeaders(headersJson)
        pendingStartMs = max(0, startMs)
        errorText = ""
        recentLog.removeAll()
        isLoaded = false
        cachedPositionMs = 0
        cachedDurationMs = 0
        cachedEnded = false
        startLoadIfPossible()
    }

    func playPlayback() {
        guard let mpv else { return }
        setFlag(mpv, "pause", false)
    }

    func pausePlayback() {
        guard let mpv else { return }
        setFlag(mpv, "pause", true)
    }

    func seekTo(_ ms: Int64) {
        guard let mpv, isLoaded else { return }
        command(mpv, "seek", [String(format: "%.3f", Double(ms) / 1000.0), "absolute"])
    }

    func setSubtitle(_ urlString: String?) {
        guard let mpv, isLoaded else { return }
        guard let urlString, !urlString.isEmpty else {
            setString(mpv, "sid", "no")
            return
        }
        command(mpv, "sub-add", [urlString, "select"])
    }

    /// Selects a subtitle track that is packed inside the file, or turns them off
    /// with a negative id. Some libmpv builds address tracks by id and others by
    /// their position, so the ordinal is tried when the id does not stick.
    func setSubtitleTrack(_ trackId: Int64, ordinal: Int32) {
        guard let mpv else { return }
        guard trackId >= 0 else {
            setString(mpv, "sid", "no")
            return
        }
        setString(mpv, "sid", String(trackId))
        guard !isSubtitleSelected(mpv, trackId: trackId) else { return }
        let fallback = Int64(ordinal) + 1
        if fallback > 0 {
            setString(mpv, "sid", String(fallback))
        }
    }

    /// The file's embedded subtitle tracks as JSON, read from libmpv's track list.
    func embeddedSubtitleTracksJson() -> String {
        guard let mpv else { return "[]" }
        var tracks: [[String: Any]] = []
        var subtitleOrdinal: Int32 = 0
        let count = Int(getInt64(mpv, "track-list/count") ?? 0)
        for index in 0..<count {
            let prefix = "track-list/\(index)"
            guard getString(mpv, "\(prefix)/type") == "sub" else { continue }
            let ordinal = subtitleOrdinal
            subtitleOrdinal += 1
            // Tracks added from an addon are external; the picker lists those
            // separately, so only the file's own tracks are reported here.
            if getFlag(mpv, "\(prefix)/external") { continue }
            let entry: [String: Any] = [
                "id": getInt64(mpv, "\(prefix)/id") ?? -1,
                "index": Int(ordinal),
                "language": getString(mpv, "\(prefix)/lang") ?? "",
                "title": getString(mpv, "\(prefix)/title") ?? "",
                "codec": getString(mpv, "\(prefix)/codec") ?? "",
                "forced": getFlag(mpv, "\(prefix)/forced"),
                "selected": getFlag(mpv, "\(prefix)/selected")
            ]
            tracks.append(entry)
        }
        guard let data = try? JSONSerialization.data(withJSONObject: tracks, options: []),
              let text = String(data: data, encoding: .utf8) else {
            return "[]"
        }
        return text
    }

    /// The file's embedded audio tracks as JSON, read from libmpv's track list.
    func embeddedAudioTracksJson() -> String {
        guard let mpv else { return "[]" }
        var tracks: [[String: Any]] = []
        var audioOrdinal: Int32 = 0
        let count = Int(getInt64(mpv, "track-list/count") ?? 0)
        for index in 0..<count {
            let prefix = "track-list/\(index)"
            guard getString(mpv, "\(prefix)/type") == "audio" else { continue }
            let ordinal = audioOrdinal
            audioOrdinal += 1
            if getFlag(mpv, "\(prefix)/external") { continue }
            let entry: [String: Any] = [
                "id": getInt64(mpv, "\(prefix)/id") ?? -1,
                "index": Int(ordinal),
                "language": getString(mpv, "\(prefix)/lang") ?? "",
                "title": getString(mpv, "\(prefix)/title") ?? "",
                "codec": getString(mpv, "\(prefix)/codec") ?? "",
                "channels": getString(mpv, "\(prefix)/demux-channel-count") ?? "",
                "selected": getFlag(mpv, "\(prefix)/selected")
            ]
            tracks.append(entry)
        }
        guard let data = try? JSONSerialization.data(withJSONObject: tracks, options: []),
              let text = String(data: data, encoding: .utf8) else {
            return "[]"
        }
        return text
    }

    /// Selects an audio track packed inside the file. A negative id is ignored:
    /// a film must never be left without sound.
    func setAudioTrack(_ trackId: Int64, ordinal: Int32) {
        guard let mpv, trackId >= 0 else { return }
        setString(mpv, "aid", String(trackId))
        guard !isTrackSelected(mpv, type: "audio", trackId: trackId) else { return }
        let fallback = Int64(ordinal) + 1
        if fallback > 0 {
            setString(mpv, "aid", String(fallback))
        }
    }

    /// True when the given libmpv track of `type` is the one currently selected.
    private func isTrackSelected(_ ctx: OpaquePointer, type: String, trackId: Int64) -> Bool {
        let count = Int(getInt64(ctx, "track-list/count") ?? 0)
        for index in 0..<count {
            let prefix = "track-list/\(index)"
            guard getString(ctx, "\(prefix)/type") == type else { continue }
            if getInt64(ctx, "\(prefix)/id") == trackId {
                return getFlag(ctx, "\(prefix)/selected")
            }
        }
        return false
    }

    /// True when the given subtitle track is the one currently selected.
    private func isSubtitleSelected(_ ctx: OpaquePointer, trackId: Int64) -> Bool {
        isTrackSelected(ctx, type: "sub", trackId: trackId)
    }

    func refreshState() {
        guard let mpv else { return }
        drainEvents(mpv)
        cachedPositionMs = Int64(getDouble(mpv, "time-pos") * 1000)
        cachedDurationMs = Int64(getDouble(mpv, "duration") * 1000)
        cachedEnded = getFlag(mpv, "eof-reached")
    }

    var positionMs: Int64 { cachedPositionMs }
    var durationMs: Int64 { cachedDurationMs }
    var ended: Bool { cachedEnded }
    var errorMessage: String { errorText }
    /// True while libmpv has paused itself to fill the cache.
    var buffering: Bool {
        guard let mpv else { return false }
        return getFlag(mpv, "paused-for-cache")
    }

    func destroyPlayer() {
        pendingSeekWorkItem?.cancel()
        pendingSeekWorkItem = nil
        pendingURL = nil
        isLoaded = false
        guard let ctx = mpv else { return }
        mpv = nil
        mpv_terminate_destroy(ctx)
    }

    // MARK: - Speed, volume and gestures

    func setSpeed(_ speed: Double) {
        guard let mpv else { return }
        setString(mpv, "speed", String(format: "%.2f", max(0.25, min(4.0, speed))))
    }

    /// Repeats the scene between two points, in milliseconds. A negative side is
    /// cleared, and two negative values remove the loop.
    func setABLoop(startMs: Int64, endMs: Int64) {
        guard let mpv else { return }
        setString(
            mpv,
            "ab-loop-a",
            startMs >= 0 ? String(format: "%.3f", Double(startMs) / 1000.0) : "no"
        )
        setString(
            mpv,
            "ab-loop-b",
            endMs >= 0 ? String(format: "%.3f", Double(endMs) / 1000.0) : "no"
        )
    }

    /// Asks libmpv to write the frame it is showing, and answers where it will land.
    /// The write is asynchronous, so the caller waits for the file to appear.
    func captureFramePng() -> String? {
        guard let mpv, isLoaded else { return nil }
        let name = "cinemax-frame-\(UUID().uuidString).png"
        let path = FileManager.default.temporaryDirectory.appendingPathComponent(name).path
        command(mpv, "screenshot-to-file", [path, "video"])
        return path
    }

    /// Shifts subtitle timing in seconds; 0 puts it back in sync.
    func setSubtitleDelay(_ seconds: Double) {
        guard let mpv else { return }
        setString(mpv, "sub-delay", String(format: "%.2f", seconds))
    }

    /// Forces an aspect ratio ("16:9", "4:3", "2.35:1"), fits the picture with
    /// bars ("ملاءمة" / empty), or crops it to fill the screen ("fill").
    func setVideoAspect(_ aspect: String) {
        guard let mpv else { return }
        let value = aspect.trimmingCharacters(in: .whitespaces)
        let fits = value.isEmpty || value == "auto" || value == "fit"
        let fills = value == "fill"
        setString(mpv, "panscan", fills ? "1.0" : "0.0")
        // mpv's own word for "no override" is `no`, not an empty value: an empty
        // one leaves the last ratio in place, which is the stretched picture
        // the viewer saw after turning the phone.
        setString(mpv, "video-aspect-override", fits || fills ? "no" : value)
    }

    /// Subtitle text size, as libmpv's font-size property (default is 55).
    func setSubtitleFontSize(_ size: Int) {
        guard let mpv else { return }
        setString(mpv, "sub-font-size", String(max(20, min(120, size))))
    }

    /// Raises the subtitle line from the bottom, 100 being libmpv's own place.
    func setSubtitlePosition(_ value: Double) {
        guard let mpv else { return }
        setString(mpv, "sub-pos", String(Int(max(0, min(150, value)))))
    }

    /// Zooms the picture inside the window; 0 fits, positive values crop in.
    func setVideoZoom(_ value: Double) {
        guard let mpv else { return }
        setString(mpv, "video-zoom", String(format: "%.2f", max(-1.0, min(1.0, value))))
    }

    func setVolumeLevel(_ volume: Double) {
        guard let mpv else { return }
        setString(mpv, "volume", String(format: "%.0f", max(0.0, min(100.0, volume))))
    }

    private var gestureListener: CinemaxPlayerGestures?
    private var gesturesInstalled = false

    private var panStartVolume: Double = 100
    private var panStartBrightness: CGFloat = 0
    private var panSeekStartMs: Int64 = 0
    private var panSeekTargetMs: Int64 = 0
    private var isScrubbing = false

    func setGestureListener(_ listener: CinemaxPlayerGestures?) {
        gestureListener = listener
        installGesturesIfNeeded()
    }

    private func installGesturesIfNeeded() {
        guard !gesturesInstalled else { return }
        gesturesInstalled = true
        let doubleTap = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2
        view.addGestureRecognizer(doubleTap)
        let singleTap = UITapGestureRecognizer(target: self, action: #selector(handleSingleTap(_:)))
        singleTap.numberOfTapsRequired = 1
        singleTap.require(toFail: doubleTap)
        view.addGestureRecognizer(singleTap)
        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        view.addGestureRecognizer(pan)
    }

    @objc private func handleSingleTap(_ recognizer: UITapGestureRecognizer) {
        gestureListener?.onTap()
    }

    /// Double tap on the left half seeks back 10 s, on the right half forward 10 s.
    @objc private func handleDoubleTap(_ recognizer: UITapGestureRecognizer) {
        guard isLoaded else { return }
        let forward = recognizer.location(in: view).x >= view.bounds.width / 2
        let delta: Int64 = forward ? 10_000 : -10_000
        var target = cachedPositionMs + delta
        if cachedDurationMs > 0 { target = min(target, cachedDurationMs - 500) }
        target = max(0, target)
        seekTo(target)
        gestureListener?.onSeek(targetMs: target, deltaMs: delta)
    }

    /// Horizontal drag scrubs, vertical drag on the right half sets the volume and
    /// on the left half the screen brightness, the way the Nuvio player behaves.
    @objc private func handlePan(_ recognizer: UIPanGestureRecognizer) {
        guard let mpv, isLoaded, view.bounds.width > 1, view.bounds.height > 1 else { return }
        let location = recognizer.location(in: view)
        let translation = recognizer.translation(in: view)
        switch recognizer.state {
        case .began:
            if abs(translation.x) > abs(translation.y) {
                isScrubbing = true
                panSeekStartMs = cachedPositionMs
                panSeekTargetMs = cachedPositionMs
            } else if location.x >= view.bounds.width / 2 {
                panStartVolume = getDouble(mpv, "volume")
            } else {
                panStartBrightness = UIScreen.main.brightness
            }
        case .changed:
            if isScrubbing {
                let span = max(cachedDurationMs, 1)
                let deltaMs = Int64(Double(translation.x) / Double(view.bounds.width) * Double(span) * 0.5)
                panSeekTargetMs = max(0, min(panSeekStartMs + deltaMs, span - 500))
                gestureListener?.onScrub(positionMs: panSeekTargetMs, durationMs: span)
            } else if location.x >= view.bounds.width / 2 {
                let delta = Double(-translation.y) / Double(view.bounds.height) * 100.0
                let value = max(0.0, min(100.0, panStartVolume + delta))
                setString(mpv, "volume", String(format: "%.0f", value))
                gestureListener?.onVolume(percent: Int32(value))
            } else {
                let delta = (-translation.y) / view.bounds.height
                let value = max(0.0, min(1.0, panStartBrightness + delta))
                UIScreen.main.brightness = value
                gestureListener?.onBrightness(percent: Int32(value * 100))
            }
        case .ended, .cancelled, .failed:
            if isScrubbing {
                isScrubbing = false
                seekTo(panSeekTargetMs)
                gestureListener?.onScrubEnd(positionMs: panSeekTargetMs)
            }
        default:
            break
        }
    }

    // MARK: - Setup

    private func setupMPV() {
        guard mpv == nil else { return }
        guard let ctx = mpv_create() else {
            errorText = "تعذر تهيئة مشغل MPV"
            return
        }
        mpv = ctx

        var layerPointer = Int64(Int(bitPattern: Unmanaged.passUnretained(metalLayer).toOpaque()))
        mpv_set_option(ctx, "wid", MPV_FORMAT_INT64, &layerPointer)
        setOption(ctx, "vo", "gpu-next")
        setOption(ctx, "gpu-api", "vulkan")
        setOption(ctx, "gpu-context", "moltenvk")
        // Settings can trade hardware decoding and cache size for compatibility,
        // which is the only way to tell a bad source from a bad decoder.
        let defaults = UserDefaults.standard
        let hardwareDecoding = (defaults.object(forKey: "cinemax.hwdec") as? Bool) ?? true
        setOption(ctx, "hwdec", hardwareDecoding ? "videotoolbox" : "no")
        setOption(ctx, "hwdec-software-fallback", "yes")
        if (defaults.object(forKey: "cinemax.mpvFast") as? Bool) ?? false {
            setOption(ctx, "profile", "fast")
        }
        let storedCache = defaults.integer(forKey: "cinemax.cacheMB")
        let cacheBytes = (storedCache == 0 ? 256 : storedCache) * 1024 * 1024
        setOption(ctx, "ao", "audiounit")
        setOption(ctx, "audio-channels", "auto")
        // A quiet mix can be pushed past 100% from the panel.
        setOption(ctx, "volume-max", "200")
        setOption(ctx, "keep-open", "yes")
        setOption(ctx, "config", "no")
        setOption(ctx, "terminal", "no")
        setOption(ctx, "sub-auto", "no")
        // Arabic subtitle files are often still Windows-1256; auto lets libmpv
        // guess, and the settings can force that encoding when the guess fails.
        let arabicEncoding = (defaults.object(forKey: "cinemax.subCp1256") as? Bool) ?? false
        setOption(ctx, "sub-codepage", arabicEncoding ? "cp1256" : "auto")
        // Embedded subtitle tracks stay off until the viewer picks one.
        setOption(ctx, "sid", "no")

        // Streaming tuning, all free and inside the player itself: a generous
        // read-ahead cache rides out a slow CDN, a short probe keeps the first
        // frame quick, four decoder threads keep high-quality H.264 smooth, and
        // libavformat reconnects a dropped socket instead of failing the title.
        setOption(ctx, "cache", "yes")
        setOption(ctx, "cache-size", String(cacheBytes))
        setOption(ctx, "demuxer-max-bytes", String(cacheBytes / 2))
        setOption(ctx, "demuxer-max-back-bytes", String(cacheBytes / 4))
        setOption(ctx, "demuxer-readahead-secs", "40")
        setOption(ctx, "cache-secs", "120")
        setOption(ctx, "stream-buffer-size", "4194304")
        setOption(ctx, "network-timeout", "15")
        setOption(ctx, "demuxer-lavf-o", "analyzeduration=1500000,probesize=1500000")
        setOption(ctx, "stream-lavf-o", "reconnect=1,reconnect_streamed=1,reconnect_delay_max=5")
        setOption(ctx, "vd-lavc-threads", "4")
        setOption(ctx, "audio-buffer", "0.3")
        // Pausing until the cache is healthy stops the picture from stalling on a
        // source that starts fast and then crawls, and a seekable cache lets the
        // viewer scrub a file served over plain HTTP.
        setOption(ctx, "cache-pause", "yes")
        setOption(ctx, "cache-pause-wait", "1")
        setOption(ctx, "force-seekable", "yes")
        setOption(ctx, "demuxer-seekable-cache", "yes")

        mpv_request_log_messages(ctx, "warn")
        if mpv_initialize(ctx) < 0 {
            errorText = "تعذر تشغيل مشغل MPV"
        }
    }

    private func startPendingLoadIfReady() {
        guard pendingURL != nil else { return }
        startLoadIfPossible()
    }

    private func startLoadIfPossible() {
        guard let mpv, let url = pendingURL else { return }
        guard isViewLoaded, view.window != nil, view.bounds.width > 1, view.bounds.height > 1 else { return }
        pendingURL = nil
        applyHeaders(mpv, pendingHeaders)
        setFlag(mpv, "pause", false)
        command(mpv, "loadfile", [url, "replace"])
        isLoaded = true
        if pendingStartMs > 0 {
            let seekMs = pendingStartMs
            let work = DispatchWorkItem { [weak self] in
                self?.seekTo(seekMs)
            }
            pendingSeekWorkItem = work
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7, execute: work)
            // A slow source may not accept the first seek; one more attempt after
            // the file has had time to open puts the viewer back where they were.
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.4) { [weak self] in
                guard let self, let ctx = self.mpv, self.isLoaded else { return }
                if self.getDouble(ctx, "time-pos") > 0.5 { return }
                self.seekTo(seekMs)
            }
        }
    }

    // MARK: - mpv helpers

    private func drainEvents(_ ctx: OpaquePointer) {
        while true {
            guard let event = mpv_wait_event(ctx, 0) else { return }
            if event.pointee.event_id == MPV_EVENT_NONE { return }
            if event.pointee.event_id == MPV_EVENT_FILE_LOADED {
                // A file that opened clears whatever the last link complained about.
                errorText = ""
            }
            if event.pointee.event_id == MPV_EVENT_END_FILE, let data = event.pointee.data {
                let endFile = UnsafePointer<mpv_event_end_file>(OpaquePointer(data)).pointee
                if endFile.reason == MPV_END_FILE_REASON_ERROR {
                    let message = String(cString: mpv_error_string(endFile.error))
                    errorText = "تعذر تشغيل المصدر (\(message))"
                }
            }
            if event.pointee.event_id == MPV_EVENT_LOG_MESSAGE, let data = event.pointee.data {
                let log = UnsafePointer<mpv_event_log_message>(OpaquePointer(data)).pointee
                if let text = log.text {
                    let line = String(cString: log.prefix.map { String(cString: $0) } ?? "") +
                        ": " + String(cString: text).trimmingCharacters(in: .whitespacesAndNewlines)
                    recentLog.append(line)
                    if recentLog.count > 30 { recentLog.removeFirst(recentLog.count - 30) }
                }
            }
        }
    }

    /// The last warnings libmpv wrote, so a black picture can be explained.
    func diagnostics() -> String {
        let tail = recentLog.suffix(18).joined(separator: "\n")
        if errorText.isEmpty { return tail }
        return tail.isEmpty ? errorText : errorText + "\n" + tail
    }

    private func setOption(_ ctx: OpaquePointer, _ name: String, _ value: String) {
        mpv_set_option_string(ctx, name, value)
    }

    private func setFlag(_ ctx: OpaquePointer, _ name: String, _ flag: Bool) {
        var data: Int32 = flag ? 1 : 0
        mpv_set_property(ctx, name, MPV_FORMAT_FLAG, &data)
    }

    private func setString(_ ctx: OpaquePointer, _ name: String, _ value: String) {
        mpv_set_property_string(ctx, name, value)
    }

    private func getDouble(_ ctx: OpaquePointer, _ name: String) -> Double {
        var value = Double()
        mpv_get_property(ctx, name, MPV_FORMAT_DOUBLE, &value)
        return value
    }

    private func getInt64(_ ctx: OpaquePointer, _ name: String) -> Int64? {
        var value: Int64 = 0
        let result = mpv_get_property(ctx, name, MPV_FORMAT_INT64, &value)
        return result >= 0 ? value : nil
    }

    private func getString(_ ctx: OpaquePointer, _ name: String) -> String? {
        guard let raw = mpv_get_property_string(ctx, name) else { return nil }
        let value = String(cString: raw)
        mpv_free(UnsafeMutableRawPointer(raw))
        return value
    }

    private func getFlag(_ ctx: OpaquePointer, _ name: String) -> Bool {
        var value: Int32 = 0
        mpv_get_property(ctx, name, MPV_FORMAT_FLAG, &value)
        return value > 0
    }

    private func command(_ ctx: OpaquePointer, _ name: String, _ args: [String]) {
        var owned: [UnsafeMutablePointer<CChar>?] = [strdup(name)]
        for arg in args { owned.append(strdup(arg)) }
        owned.append(nil)
        var pointers: [UnsafePointer<CChar>?] = owned.map { pointer in
            pointer.map { UnsafePointer($0) }
        }
        defer {
            for pointer in owned where pointer != nil {
                free(pointer)
            }
        }
        pointers.withUnsafeMutableBufferPointer { buffer in
            _ = mpv_command(ctx, buffer.baseAddress)
        }
    }

    private func applyHeaders(_ ctx: OpaquePointer, _ headers: [String: String]) {
        let sanitized = headers.filter { key, value in
            !key.isEmpty && !value.isEmpty && key.caseInsensitiveCompare("Range") != .orderedSame
        }
        guard !sanitized.isEmpty else {
            mpv_set_property_string(ctx, "http-header-fields", "")
            return
        }
        let serialized = sanitized
            .sorted { $0.key < $1.key }
            .map { "\($0.key): \($0.value.replacingOccurrences(of: ",", with: "\\,"))" }
            .joined(separator: ",")
        mpv_set_property_string(ctx, "http-header-fields", serialized)
    }

    private static func parseHeaders(_ json: String) -> [String: String] {
        guard !json.isEmpty, let data = json.data(using: .utf8),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return [:]
        }
        var headers: [String: String] = [:]
        for (key, value) in raw {
            if let text = value as? String { headers[key] = text }
        }
        return headers
    }
}

/// Kotlin `CinemaxNativePlayer` implementation.
final class MPVCinemaxPlayer: NSObject, CinemaxNativePlayer {
    private let viewController = MPVViewController()

    func createViewController() -> UIViewController {
        viewController
    }

    func load(url: String, headersJson: String, startPositionMs: Int64) {
        viewController.load(urlString: url, headersJson: headersJson, startMs: startPositionMs)
    }

    func play() { viewController.playPlayback() }

    func pause() { viewController.pausePlayback() }

    func seekTo(positionMs: Int64) { viewController.seekTo(positionMs) }

    func setSpeed(speed: Float) { viewController.setSpeed(Double(speed)) }

    func setVolume(volume: Float) { viewController.setVolumeLevel(Double(volume)) }

    func setABLoop(startMs: Int64, endMs: Int64) {
        viewController.setABLoop(startMs: startMs, endMs: endMs)
    }

    func captureFramePng() -> String? { viewController.captureFramePng() }

    func setGestureListener(listener: CinemaxPlayerGestures?) {
        viewController.setGestureListener(listener)
    }

    func setSubtitleUrl(url: String?) { viewController.setSubtitle(url) }

    func getEmbeddedSubtitleTracksJson() -> String { viewController.embeddedSubtitleTracksJson() }

    func setSubtitleTrackId(trackId: Int64, ordinal: Int32) {
        viewController.setSubtitleTrack(trackId, ordinal: ordinal)
    }

    func getEmbeddedAudioTracksJson() -> String { viewController.embeddedAudioTracksJson() }

    func setAudioTrackId(trackId: Int64, ordinal: Int32) {
        viewController.setAudioTrack(trackId, ordinal: ordinal)
    }

    func refresh() { viewController.refreshState() }

    func getPositionMs() -> Int64 { viewController.positionMs }

    func getDurationMs() -> Int64 { viewController.durationMs }

    func isEnded() -> Bool { viewController.ended }

    func getErrorMessage() -> String { viewController.errorMessage }

    func destroy() {
        viewController.destroyPlayer()
    }
}

enum CinemaxMPVRegistration {
    static func register() {
        CinemaxPlayerBridge.shared.register(factory: { MPVCinemaxPlayer() })
    }
}

/// Reads an HLS master playlist and picks the rendition worth playing, so an
/// adaptive link opens on a sensible variant instead of leaving the choice to a
/// player that never adapts. A media playlist, a broken answer or a timeout all
/// answer nil, and the caller plays the original link untouched.
final class HLSParser {
    static let shared = HLSParser()

    struct Variant {
        let url: URL
        let bandwidth: Int
        let height: Int?
    }

    private let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 4
        configuration.httpMaximumConnectionsPerHost = 2
        return URLSession(configuration: configuration)
    }()

    private var cache: [String: [Variant]] = [:]
    private var cachedAt: [String: Date] = [:]

    func isHLS(_ url: String) -> Bool {
        let lowered = url.lowercased()
        return lowered.contains(".m3u8") || lowered.contains("format=m3u8")
    }

    /// The variant to hand the player, or nil when this is not HLS or nothing came
    /// back in time.
    func bestStream(
        for urlString: String,
        headers: [String: String],
        preferredQuality: String?
    ) async -> (url: String, label: String)? {
        guard isHLS(urlString), let url = URL(string: urlString) else { return nil }
        guard let variants = try? await variants(for: url, headers: headers), !variants.isEmpty else {
            return nil
        }
        guard let chosen = pick(from: variants, preferredQuality: preferredQuality) else { return nil }
        let megabits = Double(chosen.bandwidth) / 1_000_000
        let label = chosen.height.map { "\($0)p" } ?? String(format: "%.1f Mbps", megabits)
        return (chosen.url.absoluteString, label)
    }

    private func variants(for url: URL, headers: [String: String]) async throws -> [Variant] {
        let key = url.absoluteString + "|" + headers.keys.sorted().joined(separator: ",")
        if let cached = cache[key], let at = cachedAt[key], Date().timeIntervalSince(at) < 600 {
            return cached
        }
        var request = URLRequest(url: url)
        // The host wants whatever the addon asked for, exactly as playback does.
        for (name, value) in headers {
            request.setValue(value, forHTTPHeaderField: name)
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode < 400,
              let text = String(data: data, encoding: .utf8) else {
            throw URLError(.badServerResponse)
        }
        let parsed = Self.parse(text, base: url)
        cache[key] = parsed
        cachedAt[key] = Date()
        if cache.count > 40 {
            // Keep the small map small; an old playlist is worthless anyway.
            cache.removeAll()
            cachedAt.removeAll()
        }
        return parsed
    }

    /// Pulls the renditions out of a master playlist. A media playlist has none, and
    /// an empty answer is what tells the caller to play the link as it is.
    static func parse(_ text: String, base: URL) -> [Variant] {
        guard text.contains("#EXTM3U") else { return [] }
        var variants: [Variant] = []
        var bandwidth = 0
        var height: Int?

        for rawLine in text.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }

            if line.hasPrefix("#EXT-X-STREAM-INF:") {
                bandwidth = 0
                height = nil
                let attributes = line.dropFirst("#EXT-X-STREAM-INF:".count)
                for pair in attributes.split(separator: ",") {
                    let parts = pair.split(separator: "=", maxSplits: 1).map(String.init)
                    guard parts.count == 2 else { continue }
                    let name = parts[0].trimmingCharacters(in: .whitespaces).uppercased()
                    let value = parts[1].trimmingCharacters(in: CharacterSet(charactersIn: "\" "))
                    switch name {
                    case "BANDWIDTH", "AVERAGE-BANDWIDTH":
                        if let number = Int(value) { bandwidth = max(bandwidth, number) }
                    case "RESOLUTION":
                        let pieces = value.lowercased().split(separator: "x")
                        if pieces.count == 2 { height = Int(pieces[1]) }
                    default:
                        break
                    }
                }
                continue
            }

            if line.hasPrefix("#") { continue }

            // A URI line only counts when the tag above it described a rendition.
            guard bandwidth > 0 || height != nil,
                  let resolved = URL(string: line, relativeTo: base) else {
                bandwidth = 0
                height = nil
                continue
            }
            variants.append(Variant(url: resolved, bandwidth: bandwidth, height: height))
            bandwidth = 0
            height = nil
        }
        return variants
    }

    /// A quality the viewer picked wins; otherwise the highest rendition a medium
    /// connection carries comfortably, so the first frame arrives quickly.
    func pick(from variants: [Variant], preferredQuality: String?) -> Variant? {
        guard !variants.isEmpty else { return nil }
        let heightOrdered = variants.sorted {
            let left = $0.height ?? 0
            let right = $1.height ?? 0
            if left == right { return $0.bandwidth < $1.bandwidth }
            return left < right
        }
        let targetHeight: Int?
        switch preferredQuality ?? "auto" {
        case "4K": targetHeight = 2160
        case "1080p": targetHeight = 1080
        case "720p": targetHeight = 720
        case "480p": targetHeight = 480
        default: targetHeight = nil
        }

        if let targetHeight {
            let atOrBelow = heightOrdered.last { variant in
                guard let height = variant.height else { return false }
                return height <= targetHeight
            }
            return atOrBelow ?? heightOrdered.first
        }

        // Auto: 3 Mbps is the point where a medium network stops stuttering.
        let ceiling = 3_000_000
        let withinBudget = heightOrdered.last { $0.bandwidth > 0 && $0.bandwidth <= ceiling }
        return withinBudget ?? heightOrdered.first
    }
}

/// Races a handful of candidate links with a small range request and answers the
/// first that really serves bytes, so pressing play does not land on a dead mirror.
final class StreamRacer {
    static let shared = StreamRacer()

    private let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 4
        configuration.httpMaximumConnectionsPerHost = 6
        return URLSession(configuration: configuration)
    }()

    /// The winner is the first candidate that answers, not the first in the list: a
    /// mirror that responds in 200 ms beats a faster-looking one that never answers.
    func firstPlayable(_ candidates: [StreamChoice]) async -> StreamChoice? {
        let playable = candidates.filter { $0.externalUrl == nil }
        guard !playable.isEmpty else { return nil }
        if playable.count == 1 { return playable.first }
        return await withTaskGroup(of: StreamChoice?.self) { group in
            for stream in playable {
                group.addTask { await self.probe(stream) }
            }
            for await winner in group {
                if let winner {
                    group.cancelAll()
                    return winner
                }
            }
            return nil
        }
    }

    private func probe(_ stream: StreamChoice) async -> StreamChoice? {
        guard let url = URL(string: stream.url) else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = 4
        // A kilobyte is enough to tell a working host from a dead one, and it costs
        // the connection almost nothing.
        request.setValue("bytes=0-1023", forHTTPHeaderField: "Range")
        for (name, value) in stream.headers ?? [:] {
            request.setValue(value, forHTTPHeaderField: name)
        }
        do {
            let (_, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { return nil }
            guard (200...399).contains(http.statusCode) else { return nil }
            return stream
        } catch {
            return nil
        }
    }
}

/// Reads the expiry a signed link carries in its query, so a cached URL can be
/// recognised as dead before the viewer presses play on it.
enum StreamExpiry {
    static func date(in urlString: String) -> Date? {
        guard let components = URLComponents(string: urlString), let items = components.queryItems else {
            return nil
        }
        for name in ["exp", "expires", "expiry", "token_expires", "valid_until"] {
            guard let raw = items.first(where: { $0.name.lowercased() == name })?.value,
                  let value = Double(raw), value > 0 else { continue }
            if value > 10_000_000_000 {
                return Date(timeIntervalSince1970: value / 1000)
            }
            if value > 1_000_000_000 {
                return Date(timeIntervalSince1970: value)
            }
            // A small number is a duration, not a moment.
            return Date().addingTimeInterval(value)
        }
        return nil
    }

    static func isExpiring(_ urlString: String, within seconds: TimeInterval = 300) -> Bool {
        guard let date = date(in: urlString) else { return false }
        return date.timeIntervalSinceNow < seconds
    }
}


#endif
