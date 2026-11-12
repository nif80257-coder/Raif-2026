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

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        view.layer.masksToBounds = true
        metalLayer.frame = view.bounds
        view.layer.addSublayer(metalLayer)
        setupMPV()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        metalLayer.frame = view.bounds
        let scale = view.window?.screen.nativeScale ?? UIScreen.main.nativeScale
        metalLayer.drawableSize = CGSize(
            width: max(1, view.bounds.width * scale),
            height: max(1, view.bounds.height * scale)
        )
        CATransaction.commit()
        startPendingLoadIfReady()
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

    /// True when the given libmpv track is the one currently selected.
    private func isSubtitleSelected(_ ctx: OpaquePointer, trackId: Int64) -> Bool {
        let count = Int(getInt64(ctx, "track-list/count") ?? 0)
        for index in 0..<count {
            let prefix = "track-list/\(index)"
            guard getString(ctx, "\(prefix)/type") == "sub" else { continue }
            if getInt64(ctx, "\(prefix)/id") == trackId {
                return getFlag(ctx, "\(prefix)/selected")
            }
        }
        return false
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
        setOption(ctx, "hwdec", "videotoolbox")
        setOption(ctx, "hwdec-software-fallback", "yes")
        setOption(ctx, "ao", "audiounit")
        setOption(ctx, "audio-channels", "auto")
        setOption(ctx, "keep-open", "yes")
        setOption(ctx, "config", "no")
        setOption(ctx, "terminal", "no")
        setOption(ctx, "sub-auto", "no")
        // Embedded subtitle tracks stay off until the viewer picks one.
        setOption(ctx, "sid", "no")

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
        }
    }

    // MARK: - mpv helpers

    private func drainEvents(_ ctx: OpaquePointer) {
        while true {
            guard let event = mpv_wait_event(ctx, 0) else { return }
            if event.pointee.event_id == MPV_EVENT_NONE { return }
            if event.pointee.event_id == MPV_EVENT_END_FILE, let data = event.pointee.data {
                let endFile = UnsafePointer<mpv_event_end_file>(OpaquePointer(data)).pointee
                if endFile.reason == MPV_END_FILE_REASON_ERROR {
                    let message = String(cString: mpv_error_string(endFile.error))
                    errorText = "تعذر تشغيل المصدر (\(message))"
                }
            }
        }
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

    func setGestureListener(listener: CinemaxPlayerGestures?) {
        viewController.setGestureListener(listener)
    }

    func setSubtitleUrl(url: String?) { viewController.setSubtitle(url) }

    func getEmbeddedSubtitleTracksJson() -> String { viewController.embeddedSubtitleTracksJson() }

    func setSubtitleTrackId(trackId: Int64, ordinal: Int32) {
        viewController.setSubtitleTrack(trackId, ordinal: ordinal)
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


#endif
