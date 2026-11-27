import SwiftUI
import UIKit
import AVKit
import AVFoundation
import AppIntents
import Charts
import CoreSpotlight
import LocalAuthentication
import Photos
import ImageIO
import Network
import SafariServices
import UniformTypeIdentifiers
import UserNotifications
import CinemaxKit

// MARK: - Theme

/// The accent colours the viewer can pick from, kept next to the theme so the
/// settings screen and the whole interface agree on one list.
enum AccentPalette {
    static let entries: [(id: String, name: String, hex: UInt32)] = [
        ("hype", "Hype", 0xFF7C5C),
        ("raif", "Raif", 0xE8A24A),
        ("white", "الأبيض", 0xE5E5EA),
        ("violet", "البنفسجي", 0xC173E6),
        ("ocean", "المحيطي", 0x3B82F6),
        ("crimson", "القرمزي", 0xE64545),
        ("pink", "الوردي", 0xEC4899),
        ("amber", "الكهرماني", 0xF59E0B),
        ("emerald", "الزمردي", 0x22C55E),
        ("indigo", "الأرجواني", 0x8B5CF6)
    ]

    static func color(_ id: String) -> Color {
        let hex = entries.first { $0.id == id }?.hex ?? 0xE8A24A
        return Color(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// The house palette: Hype's near-black violet field, a raised sheet, a coral
/// accent and a gold star. The accent and the canvas still come from settings, so
/// a viewer can recolour the app or go OLED-deep.
enum CinemaTheme {
    static var accent: Color {
        let stored = UserDefaults.standard.string(forKey: "cinemax.accent") ?? "hype"
        // The coral is the house accent now, so an install that never picked one
        // has no old default left to answer with.
        return AccentPalette.color(stored == "raif" ? "hype" : stored)
    }

    static var canvas: Color {
        UserDefaults.standard.bool(forKey: "cinemax.oled")
            ? .black
            : Color(red: 0.047, green: 0.039, blue: 0.063)
    }

    static var reduceMotion: Bool {
        UserDefaults.standard.bool(forKey: "cinemax.reduceMotion")
    }

    /// The raised panel the rows sit on, above the page's own base.
    static let sheet = Color(red: 0.071, green: 0.063, blue: 0.09)
    static let surface = Color(red: 0.071, green: 0.063, blue: 0.09)
    static let elevated = Color(red: 0.09, green: 0.075, blue: 0.106)
    static let favourite = Color(red: 1.0, green: 0.357, blue: 0.498)
    static let gold = Color(red: 0.851, green: 0.635, blue: 0.31)
    static let muted = Color(red: 0.67, green: 0.64, blue: 0.706)
    static let tertiary = Color(red: 0.545, green: 0.518, blue: 0.592)
    static let hairline = Color.white.opacity(0.07)

    /// The poster's corner comes from settings: sharp, soft or round.
    static var posterRadius: CGFloat {
        switch UserDefaults.standard.string(forKey: "cinemax.corner") ?? "soft" {
        case "sharp": return 6
        case "round": return 24
        default: return 14
        }
    }

    static let cardRadius: CGFloat = 22
}

/// Touch-down response shared by every custom card: a soft spring and one haptic.
struct CinemaPressStyle: ButtonStyle {
    var scale: CGFloat = 0.96
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(response: 0.28, dampingFraction: 0.7), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { pressed in
                if pressed { Haptics.tap() }
            }
    }
}

enum Haptics {
    /// Honours the "الاهتزاز" switch in settings.
    private static var enabled: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.haptics") as? Bool) ?? true
    }

    /// How firm the tap feels; the settings offer three levels.
    private static var style: UIImpactFeedbackGenerator.FeedbackStyle {
        switch UserDefaults.standard.string(forKey: "cinemax.hapticsLevel") ?? "light" {
        case "medium": return .medium
        case "heavy": return .heavy
        default: return .light
        }
    }

    static func tap() {
        guard enabled else { return }
        UIImpactFeedbackGenerator(style: style).impactOccurred()
    }

    static func select() {
        guard enabled else { return }
        UISelectionFeedbackGenerator().selectionChanged()
    }
}

/// Watches the network so the interface can say plainly when it is offline.
@MainActor
final class ConnectivityMonitor: ObservableObject {
    static let shared = ConnectivityMonitor()

    @Published private(set) var online = true

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "cinemax.connectivity")

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let reachable = path.status == .satisfied
            Task { @MainActor in
                self?.online = reachable
            }
        }
        monitor.start(queue: queue)
    }
}

// MARK: - Models

struct CinemaItem: Identifiable, Codable, Hashable {
    let id: String
    let type: String
    let name: String
    let poster: String?
    let background: String?
    let releaseInfo: String?
    let rating: String?
    let overview: String?

    var posterURL: URL? { poster.flatMap { URL(string: $0) } }
    var backdropURL: URL? { (background ?? poster).flatMap { URL(string: $0) } }
}

struct Shelf: Identifiable, Codable {
    let id: String
    let title: String
    let items: [CinemaItem]
}

struct ShelvesPayload: Codable { let shelves: [Shelf] }
struct StreamsPayload: Codable {
    let streams: [StreamChoice]
    let failures: [String]?
}

struct ProgressItem: Identifiable, Codable {
    let item: CinemaItem
    let positionMillis: Int64
    let durationMillis: Int64
    let episodeLabel: String?
    let fraction: Double

    var id: String { item.id + (episodeLabel ?? "") }
    var remainingMinutes: Int {
        max(0, Int((durationMillis - positionMillis) / 60_000))
    }
}

struct DetailPayload: Codable {
    let item: CinemaItem
    let genres: [String]
    let runtime: String?
    let director: [String]
    let cast: [String]
    let seasons: [Int]
    let episodes: [CinemaItem]
    /// Trailer links the meta addon offers.
    let trailers: [String]?
}

struct StreamChoice: Identifiable, Codable, Hashable {
    /// Replaced by the chosen rendition when the link is an adaptive HLS playlist.
    var url: String
    let addon: String
    let title: String
    let quality: String?
    /// Referer / User-Agent the host demands; nil for an open link.
    var headers: [String: String]? = nil
    /// A file size label, when the source reports one.
    var size: String? = nil
    /// A page the addon wants opened instead of a file, when there is no direct link.
    var externalUrl: String? = nil
    /// Why this row cannot be played here, shown instead of quietly dropping it.
    var note: String? = nil

    var id: String { url }
}

/// One page of a catalogue, with the genres the addon offers for it.
struct CatalogPagePayload: Codable {
    let items: [CinemaItem]
    let hasMore: Bool
    let genres: [String]?
}

/// A subtitle one of the installed subtitle addons offers.
struct SubtitleChoice: Identifiable, Codable, Hashable {
    let id: String
    let url: String
    let lang: String
    let label: String
    let addon: String
}

struct InstalledAddonRow: Identifiable, Codable {
    let id: String
    let name: String
    let url: String
    let version: String
}

/// One catalogue an installed addon brings, as that addon's own page lists it.
struct AddonCatalogInfo: Codable, Hashable {
    let id: String
    let type: String
    let name: String
    var genres: [String]? = nil

    /// The same catalogue id can exist for several types, so both make the key.
    var key: String { id + "|" + type }
}

/// What one addon page shows: its capabilities, catalogues, version and age.
struct AddonProfile: Identifiable, Codable {
    let id: String
    let name: String
    let url: String
    let version: String
    let about: String
    let installedAt: Int64
    let resources: [String]
    let types: [String]
    let catalogs: [AddonCatalogInfo]
    let configurable: Bool

    private enum CodingKeys: String, CodingKey {
        case id, name, url, version
        case about = "description"
        case installedAt, resources, types, catalogs, configurable
    }
}

extension AddonProfile {
    /// A short list of what the addon answers, for the manager's rows.
    var capabilitySummary: String {
        let names: [String: String] = [
            "catalog": "كتالوجات", "stream": "مصادر",
            "subtitles": "ترجمة", "meta": "تفاصيل"
        ]
        var seen = Set<String>()
        let unique = resources.compactMap { names[$0] }.filter { seen.insert($0).inserted }
        return unique.isEmpty ? "بلا قدرات معلنة" : unique.joined(separator: " · ")
    }
}

/// A manifest read before installing, so a pasted link is checked first.
struct AddonPreview: Codable {
    let name: String
    let id: String
    let version: String
    let about: String
    let resources: [String]
    let types: [String]
    let catalogs: Int
    let configurable: Bool
    let installed: Bool

    private enum CodingKeys: String, CodingKey {
        case name, id, version
        case about = "description"
        case resources, types, catalogs, configurable, installed
    }
}

/// What the app measured about one source: its average answer time and its run of
/// failures, which is what decides whether it is asked at all right now.
struct SourceHealthRow: Identifiable, Codable {
    let name: String
    let ms: Int64
    let failures: Int
    let skipped: Bool

    var id: String { name }
}

/// One provider inside an installed Nuvio repository.
struct NuvioProviderRow: Identifiable, Codable {
    let key: String
    let name: String
    let description: String?
    let version: String
    let types: [String]
    let enabled: Bool
    let hasSettings: Bool?
    let disabledOnIos: Bool?

    var id: String { key }
}

/// A Nuvio provider repository, with every provider it offers.
struct NuvioRepositoryRow: Identifiable, Codable {
    let url: String
    let name: String
    let version: String
    let description: String?
    let providers: [NuvioProviderRow]

    var id: String { url }
    var enabledCount: Int { providers.filter(\.enabled).count }
}

struct AddonCheckRow: Identifiable, Codable {
    let id: String
    let name: String
    let url: String
    let ok: Bool
    let ms: Int64
    let version: String
    let storedVersion: String

    var hasUpdate: Bool { ok && !version.isEmpty && version != storedVersion }
}

struct SuggestedAddonRow: Identifiable, Codable {
    let name: String
    let url: String

    var id: String { url }
}

struct CinemaError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

// MARK: - Bridge

/// Thin async wrapper over the Kotlin data layer. Everything the interface shows
/// comes from the same addon store, caches and history the classic UI uses.
@MainActor
final class CinemaAPI {
    static let shared = CinemaAPI()
    private let bridge = CinemaxAppleBridge()

    var versionLabel: String { bridge.versionLabel() }

    var tmdbKey: String { bridge.tmdbKey() }

    func saveTmdbKey(_ value: String) {
        bridge.saveTmdbKey(value: value)
    }

    private func decode<T: Decodable>(_ type: T.Type, _ json: String?) throws -> T {
        guard let json, let data = json.data(using: .utf8) else {
            throw CinemaError(message: "لم تصل بيانات")
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    func shelves() async throws -> [Shelf] {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[Shelf], Error>) in
            bridge.loadShelves(onDone: { json in
                do {
                    continuation.resume(returning: try self.decode(ShelvesPayload.self, json).shelves)
                } catch {
                    continuation.resume(throwing: error)
                }
            }, onError: { message in
                continuation.resume(throwing: CinemaError(message: message))
            })
        }
    }

    /// The same cross-addon search, live: every source's hits arrive the moment
    /// it answers, and a newer token cancels the run before it, so an older
    /// answer never replaces a newer one.
    func searchStreaming(
        _ query: String,
        token: String,
        onPartial: @escaping ([CinemaItem]) -> Void
    ) async -> [CinemaItem] {
        await withCheckedContinuation { (continuation: CheckedContinuation<[CinemaItem], Never>) in
            bridge.searchStreaming(
                query: query,
                token: token,
                onUpdate: { json in
                    let shelves = (try? self.decode(ShelvesPayload.self, json).shelves) ?? []
                    onPartial(shelves.flatMap(\.items))
                },
                onDone: { json in
                    let shelves = (try? self.decode(ShelvesPayload.self, json).shelves) ?? []
                    continuation.resume(returning: shelves.flatMap(\.items))
                },
                onError: { _ in
                    continuation.resume(returning: [])
                }
            )
        }
    }

    /// Cancels a live search, so the sources behind the old query stop working.
    func cancelSearch(_ token: String) {
        bridge.cancelSearch(token: token)
    }

    func search(_ query: String) async throws -> [Shelf] {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[Shelf], Error>) in
            bridge.search(query: query, onDone: { json in
                do {
                    continuation.resume(returning: try self.decode(ShelvesPayload.self, json).shelves)
                } catch {
                    continuation.resume(throwing: error)
                }
            }, onError: { message in
                continuation.resume(throwing: CinemaError(message: message))
            })
        }
    }

    func detail(for item: CinemaItem) async throws -> DetailPayload {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<DetailPayload, Error>) in
            bridge.loadDetail(itemId: item.id, type: item.type, onDone: { json in
                do {
                    continuation.resume(returning: try self.decode(DetailPayload.self, json))
                } catch {
                    continuation.resume(throwing: error)
                }
            }, onError: { message in
                continuation.resume(throwing: CinemaError(message: message))
            })
        }
    }

    func streams(for id: String, type: String) async throws -> [StreamChoice] {
        try await streamsReport(for: id, type: type).streams
    }

    /// The links and, separately, which addon or provider failed to answer.
    /// A page opened again within a minute reuses the answer instead of asking
    /// every addon and provider all over again.
    func streamsReport(for id: String, type: String) async throws -> (streams: [StreamChoice], failures: [String]) {
        let key = id + "|" + type
        if let cached = CinemaAPI.streamCache[key],
           Date().timeIntervalSince(cached.at) < 60 {
            return (cached.streams, cached.failures)
        }
        let report = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<([StreamChoice], [String]), Error>) in
            bridge.loadStreams(itemId: id, type: type, onDone: { json in
                do {
                    let payload = try self.decode(StreamsPayload.self, json)
                    continuation.resume(returning: (payload.streams, payload.failures ?? []))
                } catch {
                    continuation.resume(throwing: error)
                }
            }, onError: { message in
                continuation.resume(throwing: CinemaError(message: message))
            })
        }
        if !report.0.isEmpty {
            CinemaAPI.streamCache[key] = (report.0, report.1, Date())
        }
        return report
    }

    /// Answers kept for a minute, keyed by "id|type".
    nonisolated(unsafe) static var streamCache: [String: (streams: [StreamChoice], failures: [String], at: Date)] = [:]

    func progress() async -> [ProgressItem] {
        await withCheckedContinuation { (continuation: CheckedContinuation<[ProgressItem], Never>) in
            bridge.loadProgress(onDone: { json in
                let items = (try? self.decode([ProgressItem].self, json)) ?? []
                continuation.resume(returning: items)
            })
        }
    }

    func favorites() async -> [CinemaItem] {
        await withCheckedContinuation { (continuation: CheckedContinuation<[CinemaItem], Never>) in
            bridge.loadFavorites(onDone: { json in
                let items = (try? self.decode([CinemaItem].self, json)) ?? []
                continuation.resume(returning: items)
            })
        }
    }

    func isFavorite(_ id: String) -> Bool { bridge.isFavorite(itemId: id) }

    func libraryCounts() async -> (progress: Int, favorites: Int) {
        await withCheckedContinuation { (continuation: CheckedContinuation<(Int, Int), Never>) in
            bridge.libraryCounts(onDone: { json in
                let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]
                continuation.resume(returning: (
                    (object?["progress"] as? NSNumber)?.intValue ?? 0,
                    (object?["favorites"] as? NSNumber)?.intValue ?? 0
                ))
            })
        }
    }

    func clearProgress() async -> (progress: Int, favorites: Int) {
        await withCheckedContinuation { (continuation: CheckedContinuation<(Int, Int), Never>) in
            bridge.clearWatchProgress(onDone: { json in
                let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]
                continuation.resume(returning: (
                    (object?["progress"] as? NSNumber)?.intValue ?? 0,
                    (object?["favorites"] as? NSNumber)?.intValue ?? 0
                ))
            })
        }
    }

    func clearFavorites() async -> (progress: Int, favorites: Int) {
        await withCheckedContinuation { (continuation: CheckedContinuation<(Int, Int), Never>) in
            bridge.clearFavorites(onDone: { json in
                let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]
                continuation.resume(returning: (
                    (object?["progress"] as? NSNumber)?.intValue ?? 0,
                    (object?["favorites"] as? NSNumber)?.intValue ?? 0
                ))
            })
        }
    }

    func removeProgress(itemId: String, videoLabel: String?) async -> (progress: Int, favorites: Int) {
        await withCheckedContinuation { (continuation: CheckedContinuation<(Int, Int), Never>) in
            bridge.removeProgress(itemId: itemId, videoLabel: videoLabel, onDone: { json in
                let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]
                continuation.resume(returning: (
                    (object?["progress"] as? NSNumber)?.intValue ?? 0,
                    (object?["favorites"] as? NSNumber)?.intValue ?? 0
                ))
            })
        }
    }

    func exportBackup() async -> String {
        await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            bridge.exportBackup(onDone: { text in
                continuation.resume(returning: text)
            })
        }
    }

    func importBackup(_ raw: String) async throws -> String {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            bridge.importBackup(raw: raw, onDone: { summary in
                continuation.resume(returning: summary)
            }, onError: { message in
                continuation.resume(throwing: CinemaError(message: message))
            })
        }
    }

    /// How many episodes are marked watched and how many addons are installed.
    func extraCounts() async -> (watched: Int, addons: Int) {
        await withCheckedContinuation { (continuation: CheckedContinuation<(Int, Int), Never>) in
            bridge.extraCounts(onDone: { json in
                let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]
                continuation.resume(returning: (
                    object?["watched"] as? Int ?? 0,
                    object?["addons"] as? Int ?? 0
                ))
            })
        }
    }

    /// Moves an addon in the priority order and answers the new list.
    func moveAddon(url: String, delta: Int) async -> [InstalledAddonRow] {
        let payload = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            bridge.moveAddon(url: url, delta: Int32(delta), onDone: { text in
                continuation.resume(returning: text)
            })
        }
        return (try? JSONDecoder().decode([InstalledAddonRow].self, from: Data(payload.utf8))) ?? []
    }

    /// Titles TMDB lists as close to this one, for the bottom of the detail page.
    func similar(item: CinemaItem) async -> [CinemaItem] {
        let payload = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            bridge.loadSimilar(itemId: item.id, type: item.type, name: item.name, onDone: { text in
                continuation.resume(returning: text)
            })
        }
        let decoded = try? JSONDecoder().decode(ShelvesPayload.self, from: Data(payload.utf8))
        return (decoded?.shelves.first?.items ?? []).filter { $0.id != item.id }
    }

    func saveProgress(item: CinemaItem, positionMs: Int64, durationMs: Int64, videoId: String?, videoLabel: String?) {
        guard let data = try? JSONEncoder().encode(item), let json = String(data: data, encoding: .utf8) else { return }
        bridge.saveProgress(
            itemJson: json,
            positionMillis: positionMs,
            durationMillis: durationMs,
            videoId: videoId,
            videoLabel: videoLabel
        )
    }

    func resume(itemId: String, videoId: String?) async -> Int64 {
        await withCheckedContinuation { (continuation: CheckedContinuation<Int64, Never>) in
            bridge.resumeJson(itemId: itemId, videoId: videoId, onDone: { json in
                let data = Data(json.utf8)
                let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
                let value = (object?["positionMillis"] as? NSNumber)?.int64Value ?? 0
                continuation.resume(returning: value)
            })
        }
    }

    /// One page of a catalogue, with `skip` for the next one.
    func catalogPage(
        addonId: String,
        catalogId: String,
        type: String,
        skip: Int,
        genre: String?
    ) async throws -> CatalogPagePayload {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<CatalogPagePayload, Error>) in
            bridge.loadCatalogPage(
                addonId: addonId,
                catalogId: catalogId,
                type: type,
                skip: Int32(skip),
                genre: genre,
                onDone: { text in
                    do {
                        continuation.resume(returning: try self.decode(CatalogPagePayload.self, text))
                    } catch {
                        continuation.resume(throwing: error)
                    }
                },
                onError: { message in
                    continuation.resume(throwing: CinemaError(message: message))
                }
            )
        }
    }

    /// A live source fetch: `onUpdate` fires each time an addon or provider answers,
    /// so the list fills while the slow ones are still thinking. The token it returns
    /// cancels the fetch when the viewer moves on to another title.
    @discardableResult
    func streamsFast(
        for id: String,
        type: String,
        onUpdate: @escaping (StreamsPayload) -> Void,
        onDone: @escaping (StreamsPayload) -> Void
    ) -> String {
        let token = UUID().uuidString
        bridge.loadStreamsFast(
            itemId: id,
            type: type,
            token: token,
            onUpdate: { json in
                guard let payload = try? self.decode(StreamsPayload.self, json) else { return }
                onUpdate(payload)
            },
            onDone: { json in
                let payload = (try? self.decode(StreamsPayload.self, json))
                    ?? StreamsPayload(streams: [], failures: [])
                onDone(payload)
            },
            onError: { message in
                onDone(StreamsPayload(streams: [], failures: [message]))
            }
        )
        return token
    }

    func cancelStreams(_ token: String) {
        bridge.cancelStreams(token: token)
    }

    /// A source list with the cache deliberately skipped, for renewing a link whose
    /// signature is about to expire.
    func freshStreams(for id: String, type: String) async -> [StreamChoice] {
        await withCheckedContinuation { (continuation: CheckedContinuation<[StreamChoice], Never>) in
            streamsFast(
                for: id,
                type: type,
                onUpdate: { _ in },
                onDone: { payload in
                    continuation.resume(returning: payload.streams)
                }
            )
        }
    }

    /// Every subtitle the installed subtitle addons offer for this id.
    func subtitles(itemId: String, type: String) async -> [SubtitleChoice] {
        let payload = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            bridge.loadSubtitles(itemId: itemId, type: type, onDone: { text in
                continuation.resume(returning: text)
            })
        }
        return (try? JSONDecoder().decode([SubtitleChoice].self, from: Data(payload.utf8))) ?? []
    }

    // MARK: - Nuvio providers

    func nuvioRepositories() async -> [NuvioRepositoryRow] {
        let payload = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            bridge.nuvioRepositories(onDone: { text in
                continuation.resume(returning: text)
            })
        }
        return (try? JSONDecoder().decode([NuvioRepositoryRow].self, from: Data(payload.utf8))) ?? []
    }

    func addNuvioRepository(_ url: String) async throws -> [NuvioRepositoryRow] {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[NuvioRepositoryRow], Error>) in
            bridge.addNuvioRepository(url: url, onDone: { text in
                let rows = (try? JSONDecoder().decode([NuvioRepositoryRow].self, from: Data(text.utf8))) ?? []
                continuation.resume(returning: rows)
            }, onError: { message in
                continuation.resume(throwing: CinemaError(message: message))
            })
        }
    }

    func removeNuvioRepository(_ url: String) async -> [NuvioRepositoryRow] {
        let payload = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            bridge.removeNuvioRepository(url: url, onDone: { text in
                continuation.resume(returning: text)
            })
        }
        return (try? JSONDecoder().decode([NuvioRepositoryRow].self, from: Data(payload.utf8))) ?? []
    }

    func setNuvioProvider(_ key: String, enabled: Bool) async -> [NuvioRepositoryRow] {
        let payload = await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            bridge.setNuvioProvider(key: key, enabled: enabled, onDone: { text in
                continuation.resume(returning: text)
            })
        }
        return (try? JSONDecoder().decode([NuvioRepositoryRow].self, from: Data(payload.utf8))) ?? []
    }

    func installedAddons() async -> [InstalledAddonRow] {        await withCheckedContinuation { (continuation: CheckedContinuation<[InstalledAddonRow], Never>) in
            bridge.installedAddons(onDone: { json in
                continuation.resume(returning: (try? self.decode([InstalledAddonRow].self, json)) ?? [])
            })
        }
    }

    func suggestedAddons() async -> [SuggestedAddonRow] {
        await withCheckedContinuation { (continuation: CheckedContinuation<[SuggestedAddonRow], Never>) in
            bridge.suggestedAddons(onDone: { json in
                continuation.resume(returning: (try? self.decode([SuggestedAddonRow].self, json)) ?? [])
            })
        }
    }

    func installAddon(_ url: String) async throws -> String {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            bridge.installAddon(url: url, onDone: { name in
                continuation.resume(returning: name)
            }, onError: { message in
                continuation.resume(throwing: CinemaError(message: message))
            })
        }
    }

    func removeAddon(_ url: String) {
        bridge.removeAddon(url: url)
    }

    func installAllSuggested() async -> String {
        await withCheckedContinuation { (continuation: CheckedContinuation<String, Never>) in
            bridge.installAllSuggested(onDone: { json in
                continuation.resume(returning: json)
            })
        }
    }

    func checkAddons() async -> [AddonCheckRow] {
        await withCheckedContinuation { (continuation: CheckedContinuation<[AddonCheckRow], Never>) in
            bridge.checkAddons(onDone: { json in
                continuation.resume(returning: (try? self.decode([AddonCheckRow].self, json)) ?? [])
            })
        }
    }

    func updateAddon(_ url: String) async throws -> String {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            bridge.updateAddon(url: url, onDone: { summary in
                continuation.resume(returning: summary)
            }, onError: { message in
                continuation.resume(throwing: CinemaError(message: message))
            })
        }
    }

    func updateAllAddons() async -> Int {
        await withCheckedContinuation { (continuation: CheckedContinuation<Int, Never>) in
            bridge.updateAllAddons(onDone: { json in
                let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]
                continuation.resume(returning: (object?["updated"] as? NSNumber)?.intValue ?? 0)
            })
        }
    }

    /// How each source has been behaving: average answer time and failures in a row.
    func sourceHealth() async -> [SourceHealthRow] {
        await withCheckedContinuation { (continuation: CheckedContinuation<[SourceHealthRow], Never>) in
            bridge.sourceHealth(onDone: { json in
                continuation.resume(returning: (try? self.decode([SourceHealthRow].self, json)) ?? [])
            })
        }
    }

    func resetSourceHealth() {
        bridge.resetSourceHealth()
    }

    /// Every installed addon with what it can answer and its catalogues.
    func addonProfiles() async -> [AddonProfile] {
        await withCheckedContinuation { (continuation: CheckedContinuation<[AddonProfile], Never>) in
            bridge.addonProfiles(onDone: { json in
                continuation.resume(returning: (try? self.decode([AddonProfile].self, json)) ?? [])
            })
        }
    }

    /// Reads a manifest without installing it, so the link can be seen first.
    func previewAddon(_ url: String) async throws -> AddonPreview {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<AddonPreview, Error>) in
            bridge.previewAddon(url: url, onDone: { json in
                if let preview = try? self.decode(AddonPreview.self, json) {
                    continuation.resume(returning: preview)
                } else {
                    continuation.resume(throwing: CinemaError(message: "تعذر قراءة الإضافة"))
                }
            }, onError: { message in
                continuation.resume(throwing: CinemaError(message: message))
            })
        }
    }

    func toggleFavorite(_ item: CinemaItem) -> Bool {
        guard let data = try? JSONEncoder().encode(item), let json = String(data: data, encoding: .utf8) else {
            return false
        }
        // The bridge answers on this same call stack; waiting on a semaphore would
        // block the main thread and deadlock if that ever changed, so the result is
        // simply read after the call returns.
        var result = false
        var answered = false
        bridge.toggleFavorite(itemJson: json, onDone: { state in
            result = state.boolValue
            answered = true
        })
        return answered ? result : false
    }

    func useClassicInterface() {
        bridge.switchInterface(classic: true)
    }
}

// MARK: - The free set

/// What CINEMAX offers on its own: free Stremio addons and Nuvio provider
/// repositories that answer with a link over plain http or peer-to-peer, with no
/// debrid and no proxy in the middle. Every URL was checked live before it was
/// listed, so a dead, deprecated or paywalled one is not here: WatchHub only
/// hands out links to legal services, Nuvio Streams answers 404 from its own
/// manifest host, and WebStreamr answers with a "deprecated" page. All three are
/// gone on purpose, and a source installed by an earlier build is removed by
/// `removeUnfree()` when it sits behind a debrid account or a proxy.
enum CuratedSources {
    struct Source: Identifiable {
        let id: String
        let name: String
        let url: String
        let note: String
        let isRepository: Bool
    }

    static let addons: [Source] = [
        Source(
            id: "cinemeta",
            name: "Cinemeta",
            url: "https://v3-cinemeta.strem.io/manifest.json",
            note: "كتالوج وبوسترات وأوصاف وبحث",
            isRepository: false
        ),
        Source(
            id: "streamar",
            name: "StreamAR",
            url: "https://2ecbbd610840-stremio-ar.baby-beamup.club/manifest.json",
            note: "روابط عربية مباشرة: أكوام و wecima",
            isRepository: false
        ),
        Source(
            id: "opensubtitles",
            name: "OpenSubtitles v3",
            url: "https://opensubtitles-v3.strem.io/manifest.json",
            note: "ترجمات مجانية بلا حساب",
            isRepository: false
        ),
        Source(
            id: "torrentsdb",
            name: "TorrentsDB",
            url: "https://torrentsdb.com/eyJsYW5ndWFnZSI6WyJhcmFiaWMiXX0=/manifest.json",
            note: "P2P مجاني — تورنتات عربية أولًا",
            isRepository: false
        ),
        Source(
            id: "mediafusion",
            name: "MediaFusion",
            url: "https://mediafusion.elfhosted.com/manifest.json",
            note: "HTTP وP2P بلا وسيط",
            isRepository: false
        ),
        Source(
            id: "torrentio",
            name: "Torrentio",
            url: "https://torrentio.strem.fun/manifest.json",
            note: "P2P بلا وسيط — يحتاج جلسة نظير",
            isRepository: false
        ),
        Source(
            // Comet answered "client request invalid" for every title until it is
            // configured with a debrid account, so it is not offered any more.
            id: "anime-kitsu",
            name: "Anime Kitsu",
            url: "https://anime-kitsu.strem.fun/manifest.json",
            note: "أنمي مجاني بلا وسيط",
            isRepository: false
        ),
        Source(
            id: "cinetorrent",
            name: "CineTorrent",
            url: "https://150203dd784e-cinetorrent-addon.baby-beamup.club/manifest.json",
            note: "P2P مجاني — تورنتات متعددة اللغات",
            isRepository: false
        ),
        Source(
            id: "plavio",
            name: "PlavioHD",
            url: "https://hd.plavio.site/u/w6y5YbrH9fnV25qbziG8Vw/manifest.json",
            note: "HTTP مجاني حتى 4K — العربية مُفعّلة افتراضيًا",
            isRepository: false
        ),
        Source(
            id: "yukistreams",
            name: "Yukistreams",
            url: "https://stremio.yukistreams.xyz/p.YAWNo7KXUmEgwsxB/manifest.json",
            note: "HTTP مجاني — آسيوي وأنمي وفيلم عالمي",
            isRepository: false
        ),
        Source(
            id: "muvibox",
            name: "Muvibox",
            url: "https://muvibox.znzcloud.my.id/manifest.json",
            note: "HTTP مجاني من Netflix وDisney بلا تورنت",
            isRepository: false
        ),
        Source(
            id: "torrentclaw",
            name: "TorrentClaw",
            url: "https://torrentclaw.com/api/stremio/manifest.json",
            note: "P2P مجاني + كتالوجات وبيانات وأدق الروابط",
            isRepository: false
        ),
        Source(
            id: "publicdomain",
            name: "Public Domain Movies",
            url: "https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json",
            note: "أفلام ملكية عامة تبثّ قانونيًا وبلا حساب",
            isRepository: false
        ),
        Source(
            id: "pencarimovie",
            name: "PencariMovie",
            url: "https://stream.pencarimovie.com/66ff097863dedb7ccd8401d5ffe616f3/manifest.json",
            note: "HTTP مجاني من Telegram — أفلام ومسلسلات وكتالوجات",
            isRepository: false
        )
    ]

    static let repositories: [Source] = [
        Source(
            id: "yoru",
            name: "Yoru's Repo",
            url: "https://raw.githubusercontent.com/tapframe/nuvio-providers/refs/heads/main/manifest.json",
            note: "مزوّدات سكربتات مجانية",
            isRepository: true
        ),
        Source(
            id: "allinone",
            name: "All-in-One-Nuvio",
            url: "https://raw.githubusercontent.com/NuvioPlugin/All-in-One-Nuvio/main/manifest.json",
            note: "مزوّدات مجانية متعددة — فيها مزوّدات عربية",
            isRepository: true
        ),
        Source(
            id: "tuktuk",
            name: "TukTuk Cinema",
            url: "https://raw.githubusercontent.com/flavasava2022/Nuvio-providers/main/manifest.json",
            note: "مزوّد عربي وإنجليزي (عربي أولًا)",
            isRepository: true
        )
    ]

    static var all: [Source] { addons + repositories }

    /// Every id ever offered. A source is offered once and never again, so one
    /// the viewer removed on purpose is not put back, and a source added to the
    /// set later still reaches an install that already has the old ones.
    private static let offeredKey = "cinemax.curated.offered"

    /// Offers what is new in the set once, first launch and after an update.
    static func offerIfNeeded() async {
        let offered = Set(UserDefaults.standard.stringArray(forKey: offeredKey) ?? [])
        let installed = await installedIDs()
        let fresh = all.filter { !offered.contains($0.id) && !installed.contains($0.id) }
        // Marked as offered before anything is fetched: a failed source is not
        // asked again on every launch.
        UserDefaults.standard.set(all.map(\.id), forKey: offeredKey)
        guard !fresh.isEmpty else { return }
        let added = await install(fresh)
        if added > 0 {
            await EventLog.shared.add("addons", "المجموعة المجانية: \(added) جديدة")
        }
    }

    /// Installs whatever of the set is not installed yet, for the settings
    /// button: an explicit tap may put a removed source back.
    @discardableResult
    static func installMissing() async -> Int {
        let installedAddons = Set((await CinemaAPI.shared.installedAddons()).map(\.url))
        let installedRepos = Set((await CinemaAPI.shared.nuvioRepositories()).map(\.url))
        let missing = all.filter { source in
            source.isRepository
                ? !installedRepos.contains(source.url)
                : !installedAddons.contains(source.url)
        }
        let added = await install(missing)
        if added > 0 {
            await EventLog.shared.add("addons", "المجموعة المجانية: \(added) جديدة")
        }
        return added
    }

    /// Installs the given sources and answers how many landed. One URL that
    /// fails never stops the rest.
    @discardableResult
    static func install(_ sources: [Source]) async -> Int {
        var added = 0
        for source in sources {
            if source.isRepository {
                if (try? await CinemaAPI.shared.addNuvioRepository(source.url)) != nil {
                    added += 1
                }
            } else if (try? await CinemaAPI.shared.installAddon(source.url)) != nil {
                added += 1
            }
        }
        return added
    }

    /// Which of the set is already installed, for the settings rows.
    static func installedIDs() async -> Set<String> {
        let installedAddons = Set((await CinemaAPI.shared.installedAddons()).map(\.url))
        let installedRepos = Set((await CinemaAPI.shared.nuvioRepositories()).map(\.url))
        var ids = Set<String>()
        for source in all where installedAddons.contains(source.url) || installedRepos.contains(source.url) {
            ids.insert(source.id)
        }
        return ids
    }

    /// Every marker that names a paid service, a debrid account or a middleman
    /// in a source URL. A link behind one of these is not free: it answers only
    /// to a paid key or a private proxy, and it sat in the ready-made list of an
    /// earlier build in exactly that shape.
    private static let unfreeMarkers = [
        "realdebrid", "real-debrid", "alldebrid", "premiumize", "torbox", "debridlink",
        "easydebrid", "debrid", "offcloud", "put.io", "putio", "easynews", "seedr",
        "jackettio", "aiostreams", "autostream", "orion", "stremthru", "mediaflow"
    ]

    /// Removes an installed source that sits behind a paid service or a proxy, so
    /// the list holds only what plays for free over http or peer-to-peer. It runs
    /// once, before the free set is offered, and a source it removes is never put
    /// back: the ids offered are written after it.
    @discardableResult
    static func removeUnfree() async -> Int {
        let installed = await CinemaAPI.shared.installedAddons()
        var removed = 0
        for addon in installed {
            let lowered = addon.url.lowercased()
            guard unfreeMarkers.contains(where: { lowered.contains($0) }) else { continue }
            await CinemaAPI.shared.removeAddon(addon.url)
            removed += 1
            await EventLog.shared.add("addons", "حُذف \(addon.name): ليس مجانيًا")
        }
        return removed
    }
}

// MARK: - Images

/// A small memory cache in front of URLSession: no third-party loader, and the
/// same image is never downloaded twice while scrolling. A poster is decoded to
/// the size it is drawn at rather than its own, and the cache is capped in bytes
/// as well as in images, because a handful of 4K stills is otherwise enough to
/// push the app into a memory warning halfway down a grid.
final class CinemaImageStore {
    static let shared = CinemaImageStore()
    private let cache = NSCache<NSURL, UIImage>()
    private let session = URLSession(configuration: .default)
    /// The largest side a poster is decoded to: a hero fills the width at the
    /// screen's own scale, and nothing is drawn larger than that.
    private let maxPixel = 1_200

    init() {
        cache.countLimit = 160
        cache.totalCostLimit = 96 * 1024 * 1024
    }

    func cached(_ url: URL) -> UIImage? {
        cache.object(forKey: url as NSURL)
    }

    func image(for url: URL) async -> UIImage? {
        if let cached = cached(url) { return cached }
        do {
            let (data, _) = try await session.data(from: url)
            guard let image = Self.decode(data, maxPixel: maxPixel) else { return nil }
            let cost = image.cgImage.map { $0.bytesPerRow * $0.height } ?? data.count
            cache.setObject(image, forKey: url as NSURL, cost: cost)
            return image
        } catch {
            return nil
        }
    }

    /// Decodes a poster straight to the size it is drawn at. Skipping this is
    /// what makes a grid decode a full-resolution still for every card.
    private static func decode(_ data: Data, maxPixel: Int) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else {
            return UIImage(data: data)
        }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return UIImage(data: data)
        }
        return UIImage(cgImage: image)
    }

    /// Frees every poster held in memory, for the data screen's action.
    func clear() {
        cache.removeAllObjects()
    }
}

struct RemoteImage: View {
    let url: URL?
    var contentMode: ContentMode = .fill

    @State private var image: UIImage?

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                CinemaTheme.elevated
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: contentMode)
                        .frame(width: proxy.size.width, height: proxy.size.height)
                        .clipped()
                }
            }
        }
        .task(id: url) {
            guard let url else { return }
            let loaded = await CinemaImageStore.shared.image(for: url)
            withAnimation(.easeInOut(duration: 0.25)) { image = loaded }
        }
    }
}

/// Two colours sampled from a poster, used to light the room behind the hero.
enum PaletteSampler {
    private static let cache = NSCache<NSURL, NSArray>()

    static func colors(for url: URL?) async -> [Color] {
        guard let url else { return [] }
        if let cached = cache.object(forKey: url as NSURL) as? [UIColor] {
            return cached.map(Color.init)
        }
        let image = await CinemaImageStore.shared.image(for: url)
        guard let image, let cgImage = image.cgImage else { return [] }
        let ciImage = CIImage(cgImage: cgImage)
        guard let filter = CIFilter(name: "CIAreaAverage") else { return [] }
        filter.setValue(ciImage, forKey: kCIInputImageKey)
        filter.setValue(CIVector(cgRect: ciImage.extent), forKey: kCIInputExtentKey)
        guard let output = filter.outputImage else { return [] }
        var bitmap = [UInt8](repeating: 0, count: 4)
        let context = CIContext(options: [.workingColorSpace: NSNull()])
        context.render(
            output,
            toBitmap: &bitmap,
            rowBytes: 4,
            bounds: CGRect(x: 0, y: 0, width: 1, height: 1),
            format: .RGBA8,
            colorSpace: nil
        )
        let base = UIColor(
            red: CGFloat(bitmap[0]) / 255,
            green: CGFloat(bitmap[1]) / 255,
            blue: CGFloat(bitmap[2]) / 255,
            alpha: 1
        )
        let lighter = base.withAlphaComponent(1).brightened(by: 0.35)
        let colors = [base, lighter]
        cache.setObject(colors as NSArray, forKey: url as NSURL)
        return colors.map(Color.init)
    }
}

private extension UIColor {
    func brightened(by amount: CGFloat) -> UIColor {
        var hue: CGFloat = 0, saturation: CGFloat = 0, brightness: CGFloat = 0, alpha: CGFloat = 0
        guard getHue(&hue, saturation: &saturation, brightness: &brightness, alpha: &alpha) else { return self }
        return UIColor(
            hue: hue,
            saturation: max(0.2, saturation * 0.9),
            brightness: min(1, brightness + amount),
            alpha: alpha
        )
    }
}

// MARK: - Ambient glow

/// The room light: two soft blobs that take their colour from the poster in view
/// and cross-fade when that poster changes.
struct AmbientGlow: View {
    let colors: [Color]

    @State private var appear = false

    private var palette: [Color] {
        colors.isEmpty ? [CinemaTheme.accent, Color.blue] : colors
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                ForEach(Array(palette.prefix(2).enumerated()), id: \.offset) { index, color in
                    Circle()
                        .fill(color.opacity(0.38))
                        .frame(width: proxy.size.width * 1.1)
                        .blur(radius: 130)
                        .offset(
                            x: index == 0 ? -proxy.size.width * 0.32 : proxy.size.width * 0.34,
                            y: index == 0 ? -proxy.size.height * 0.34 : -proxy.size.height * 0.02
                        )
                }
            }
            .opacity(appear ? 1 : 0)
            .animation(CinemaxPrefs.reduceMotion ? nil : .easeInOut(duration: 1.1), value: palette)
            .onAppear {
                if CinemaxPrefs.reduceMotion {
                    appear = true
                } else {
                    withAnimation(.easeInOut(duration: 1.6)) { appear = true }
                }
            }
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Stream speed probe

/// Measures how fast a link really answers by pulling the first half-megabyte.
/// The result is used to put the fastest mirror first and to label each source,
/// which is the free alternative to a paid resolver.
final class StreamProber {
    static let shared = StreamProber()

    private let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 6
        configuration.httpMaximumConnectionsPerHost = 4
        return URLSession(configuration: configuration)
    }()

    func speed(for url: URL) async -> Double? {
        var request = URLRequest(url: url)
        request.setValue("bytes=0-524287", forHTTPHeaderField: "Range")
        request.timeoutInterval = 6
        let start = Date()
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode < 400 else { return nil }
            let seconds = Date().timeIntervalSince(start)
            guard seconds > 0.05, !data.isEmpty else { return nil }
            return Double(data.count) / 1_048_576.0 / seconds
        } catch {
            return nil
        }
    }

    /// Probes the first links in parallel and remembers each answer for ten
    /// minutes, so reopening a page does not measure the same mirrors again.
    func speeds(for streams: [StreamChoice], limit: Int = 8) async -> [String: Double] {
        var result: [String: Double] = [:]
        var pending: [String] = []
        for stream in streams.prefix(limit) {
            if let entry = cache[stream.url], Date().timeIntervalSince(entry.at) < 600 {
                result[stream.url] = entry.speed
            } else if URL(string: stream.url) != nil {
                pending.append(stream.url)
            }
        }
        guard !pending.isEmpty else { return result }
        await withTaskGroup(of: (String, Double?).self) { group in
            for link in pending {
                guard let url = URL(string: link) else { continue }
                group.addTask { (link, await StreamProber.shared.speed(for: url)) }
            }
            for await outcome in group {
                if let speed = outcome.1 {
                    result[outcome.0] = speed
                    cache[outcome.0] = (speed, Date())
                }
            }
        }
        return result
    }

    private var cache: [String: (speed: Double, at: Date)] = [:]
}

// MARK: - Small components

struct CinemaPill: View {
    let text: String
    var tint: Color = .white

    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .lineLimit(1)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(.ultraThinMaterial, in: Capsule())
            .overlay(Capsule().strokeBorder(CinemaTheme.hairline))
            .foregroundStyle(tint)
    }
}

struct SectionHeader: View {
    let title: String
    var action: (() -> Void)?

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(.white)
            Spacer()
            if let action {
                Button("المزيد", action: action)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(CinemaTheme.accent)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 18)
        .padding(.bottom, 10)
    }
}

/// A poster with the marks the viewer's own history adds: a thin progress bar and
/// a pink heart for a saved title.
struct PosterCard: View {
    let item: CinemaItem
    var width: CGFloat = 132
    var progress: Double = 0
    var saved: Bool = false
    var onLongClick: (() -> Void)? = nil
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 8) {
                ZStack(alignment: .bottom) {
                    RemoteImage(url: item.posterURL)
                        .frame(width: width, height: width * 1.5)
                        .clipShape(RoundedRectangle(cornerRadius: CinemaTheme.posterRadius, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: CinemaTheme.posterRadius, style: .continuous)
                                .strokeBorder(Color.white.opacity(0.07), lineWidth: 1)
                        )
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.55)],
                        startPoint: .center,
                        endPoint: .bottom
                    )
                    .frame(height: 54)
                    .clipShape(RoundedRectangle(cornerRadius: CinemaTheme.posterRadius, style: .continuous))

                    if progress > 0.01 {
                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color.white.opacity(0.22))
                                Capsule().fill(CinemaTheme.accent)
                                    .frame(width: max(3, proxy.size.width * progress))
                            }
                        }
                        .frame(height: 3)
                        .padding(.horizontal, 8)
                        .padding(.bottom, 7)
                    }
                    if let rating = item.rating, !rating.isEmpty {
                        Text("★ \(rating)")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(Color.black.opacity(0.55), in: Capsule())
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                            .padding(7)
                    }
                    if item.type == "movie" || item.type == "series" {
                        Text(item.type == "movie" ? "فيلم" : "مسلسل")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundStyle(.white.opacity(0.85))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(Color.black.opacity(0.55), in: Capsule())
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                            .padding(7)
                    }
                    if saved {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(6)
                            .background(CinemaTheme.favourite, in: Circle())
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                            .padding(7)
                    }
                }
                Text(item.name)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            .frame(width: width)
        }
        .buttonStyle(CinemaPressStyle())
        .simultaneousGesture(
            LongPressGesture(minimumDuration: 0.45).onEnded { _ in
                onLongClick?()
            }
        )
    }
}

/// Landscape card used for a wide row: artwork, title and one meta line.
struct WideCard: View {
    let item: CinemaItem
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 8) {
                RemoteImage(url: item.backdropURL ?? item.posterURL)
                    .frame(width: 248, height: 140)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(
                        LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .center, endPoint: .bottom)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    )
                    .overlay(alignment: .bottomLeading) {
                        Text(item.name)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(2)
                            .padding(10)
                    }
            }
        }
        .buttonStyle(CinemaPressStyle())
    }
}

struct ContinueCard: View {
    let entry: ProgressItem
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 8) {
                ZStack(alignment: .bottom) {
                    RemoteImage(url: entry.item.backdropURL ?? entry.item.posterURL)
                        .frame(width: 260, height: 146)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    VStack(spacing: 6) {
                        HStack {
                            if let label = entry.episodeLabel {
                                CinemaPill(text: label)
                            }
                            Spacer()
                            CinemaPill(text: "بقي \(entry.remainingMinutes) د")
                            CinemaPill(text: "\(Int(entry.fraction * 100))٪", tint: CinemaTheme.accent)
                        }
                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color.white.opacity(0.25))
                                Capsule().fill(CinemaTheme.accent)
                                    .frame(width: max(4, proxy.size.width * entry.fraction))
                            }
                        }
                        .frame(height: 4)
                    }
                    .padding(10)
                }
                Text(entry.item.name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
            }
            .frame(width: 260)
        }
        .buttonStyle(CinemaPressStyle())
    }
}

struct ShimmerBlock: View {
    var height: CGFloat
    var width: CGFloat?
    @State private var offset: CGFloat = -1

    var body: some View {
        RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(CinemaTheme.elevated)
            .frame(width: width, height: height)
            .overlay(
                LinearGradient(
                    colors: [.clear, Color.white.opacity(0.09), .clear],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .scaleEffect(x: 0.5, anchor: .center)
                .offset(x: offset * (width ?? 200))
            )
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .onAppear {
                withAnimation(.linear(duration: 1.3).repeatForever(autoreverses: false)) {
                    offset = 1
                }
            }
    }
}

// MARK: - Hero

struct HeroCarousel: View {
    let items: [CinemaItem]
    let onOpen: (CinemaItem) -> Void
    let onPalette: ([Color]) -> Void
    var isSaved: (CinemaItem) -> Bool = { _ in false }
    var onToggleFavorite: (CinemaItem) -> Void = { _ in }

    @State private var index = 0
    @State private var autoTimer: Timer?

    var body: some View {
        TabView(selection: $index) {
            ForEach(Array(items.prefix(6).enumerated()), id: \.element.id) { offset, item in
                HeroSlide(
                    item: item,
                    saved: isSaved(item),
                    onOpen: { onOpen(item) },
                    onFavorite: { onToggleFavorite(item) },
                    onPalette: onPalette
                )
                .tag(offset)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .frame(height: 440)
        .overlay(alignment: .bottom) {
            if items.count > 1 {
                HStack(spacing: 5) {
                    ForEach(0..<min(items.count, 6), id: \.self) { dot in
                        Button {
                            Haptics.select()
                            withAnimation(.easeInOut(duration: 0.3)) { index = dot }
                        } label: {
                            Capsule()
                                .fill(dot == index ? CinemaTheme.accent : Color.white.opacity(0.28))
                                .frame(width: dot == index ? 16 : 6, height: 4)
                                .animation(.easeInOut(duration: 0.2), value: index)
                                .padding(.vertical, 8)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.bottom, 12)
            }
        }
        .onChange(of: index) { value in
            guard items.indices.contains(value) else { return }
            Task {
                let colors = await PaletteSampler.colors(for: items[value].posterURL)
                if !colors.isEmpty { onPalette(colors) }
            }
            scheduleAdvance()
        }
        .task { scheduleAdvance() }
    }

    /// Slides the hero onward by itself, so the top of the home screen is never
    /// static; any swipe restarts the countdown.
    private func scheduleAdvance() {
        autoTimer?.invalidate()
        guard items.count > 1, CinemaxPrefs.heroAutoRotate, !CinemaxPrefs.reduceMotion else { return }
        autoTimer = Timer.scheduledTimer(withTimeInterval: 8, repeats: false) { _ in
            Task { @MainActor in
                let limit = min(items.count, 6)
                guard limit > 1 else { return }
                withAnimation(.easeInOut(duration: 0.55)) {
                    index = (index + 1) % limit
                }
            }
        }
    }
}

private struct HeroSlide: View {
    let item: CinemaItem
    var saved: Bool = false
    let onOpen: () -> Void
    var onFavorite: () -> Void = {}
    let onPalette: ([Color]) -> Void

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            RemoteImage(url: item.backdropURL ?? item.posterURL)
                .overlay(
                    LinearGradient(
                        stops: [
                            .init(color: .clear, location: 0.35),
                            .init(color: CinemaTheme.canvas.opacity(0.75), location: 0.78),
                            .init(color: CinemaTheme.canvas, location: 1)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                .padding(.horizontal, 16)

            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    if let rating = item.rating, !rating.isEmpty {
                        CinemaPill(text: "★ \(rating)")
                    }
                    if let year = item.releaseInfo, !year.isEmpty {
                        CinemaPill(text: year, tint: CinemaTheme.muted)
                    }
                }
                Text(item.name)
                    .font(.system(size: 30, weight: .heavy))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .shadow(color: .black.opacity(0.45), radius: 12, y: 4)

                if let overview = item.overview, !overview.isEmpty {
                    Text(overview)
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.78))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .shadow(color: .black.opacity(0.4), radius: 6, y: 2)
                }

                HStack(spacing: 10) {
                    Button(action: onOpen) {
                        Label("مشاهدة", systemImage: "play.fill")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 22)
                            .padding(.vertical, 12)
                            .background(.white, in: Capsule())
                    }
                    .buttonStyle(CinemaPressStyle())

                    Button(action: onFavorite) {
                        Image(systemName: saved ? "heart.fill" : "heart")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(saved ? CinemaTheme.favourite : .white)
                            .frame(width: 44, height: 44)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                    .buttonStyle(CinemaPressStyle())
                }
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 26)
        }
        .task(id: item.id) {
            let colors = await PaletteSampler.colors(for: item.posterURL)
            if !colors.isEmpty { onPalette(colors) }
        }
    }
}

// MARK: - Floating tab bar

struct FloatingTabBar: View {
    @Binding var selection: Int
    var libraryBadge: Int = 0

    private let tabs: [(icon: String, title: String)] = [
        ("house.fill", "الرئيسية"),
        ("magnifyingglass", "البحث"),
        ("square.stack.fill", "مكتبتي"),
        ("slider.horizontal.3", "الإعدادات")
    ]

    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(tabs.enumerated()), id: \.offset) { index, tab in
                Button {
                    Haptics.select()
                    withAnimation(.spring(response: 0.32, dampingFraction: 0.8)) {
                        selection = index
                    }
                } label: {
                    VStack(spacing: 3) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: tab.icon)
                                .font(.system(size: 17, weight: .semibold))
                            if index == 2 && libraryBadge > 0 {
                                Text("\(min(libraryBadge, 99))")
                                    .font(.system(size: 8, weight: .bold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 4)
                                    .padding(.vertical, 1)
                                    .background(CinemaTheme.favourite, in: Capsule())
                                    .offset(x: 12, y: -6)
                            }
                        }
                        Text(tab.title)
                            .font(.system(size: 10, weight: .medium))
                    }
                    .foregroundStyle(selection == index ? Color.black : CinemaTheme.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background {
                        if selection == index {
                            Capsule().fill(CinemaTheme.accent)
                        }
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(tab.title))
            }
        }
        .padding(6)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(CinemaTheme.hairline, lineWidth: 1))
        .shadow(color: .black.opacity(0.4), radius: 18, y: 8)
        .padding(.horizontal, 20)
        .padding(.bottom, 6)
    }
}

// MARK: - Home

@MainActor
final class HomeModel: ObservableObject {
    /// One model for the whole session: switching tabs must not throw the
    /// catalogues away and fetch them again.
    static let shared = HomeModel()

    @Published var shelves: [Shelf] = []
    @Published var continueWatching: [ProgressItem] = []
    @Published var favorites: Set<String> = []
    /// A row the app builds itself from what the viewer last opened.
    @Published var smartShelf: Shelf?
    @Published var loading = true
    @Published var error: String?
    /// The chips row's choice, kept across launches so the home opens as left.
    @Published var filter = "all" {
        didSet { UserDefaults.standard.set(filter, forKey: "cinemax.homeFilter") }
    }

    /// Rows the viewer put away from the home screen, by shelf id.
    @Published var hiddenShelves: Set<String> = Set(
        UserDefaults.standard.stringArray(forKey: "cinemax.hiddenShelves") ?? []
    ) {
        didSet { UserDefaults.standard.set(Array(hiddenShelves), forKey: "cinemax.hiddenShelves") }
    }

    private var lastLoaded: Date?
    private var lastPersonal: Date?
    private var smartSeed: String?
    private let cacheKey = "cinemax.shelves.cache.v1"

    init() {
        filter = UserDefaults.standard.string(forKey: "cinemax.homeFilter") ?? "all"
        // The last catalogues are painted immediately on launch, then refreshed.
        if let data = UserDefaults.standard.data(forKey: cacheKey),
           let cached = try? JSONDecoder().decode([Shelf].self, from: data),
           !cached.isEmpty {
            shelves = cached
            loading = false
        }
    }

    /// Refetches at most once a minute unless forced; the personal rows (resume
    /// points, watchlist) refresh on every visit since they change while watching.
    func load(force: Bool = false) async {
        // The rows are kept for five minutes instead of one: coming back to the
        // app after a film refetched every catalogue, which is the reload the
        // viewer sees. Pull-to-refresh still asks at once.
        if !force, CinemaxPrefs.autoRefresh, let last = lastLoaded, Date().timeIntervalSince(last) < 300 {
            // The personal rows are the only thing that changes while watching,
            // and they are asked at most every twenty seconds: coming back to the
            // home screen asked for them again on every single appearance.
            if let asked = lastPersonal, Date().timeIntervalSince(asked) < 20 { return }
            lastPersonal = Date()
            await refreshPersonal()
            return
        }
        loading = shelves.isEmpty
        error = nil
        do {
            async let shelvesTask = CinemaAPI.shared.shelves()
            async let progressTask = CinemaAPI.shared.progress()
            async let favoritesTask = CinemaAPI.shared.favorites()
            let loaded = try await shelvesTask
            continueWatching = await progressTask
            favorites = Set(await favoritesTask.map(\.id))
            shelves = loaded
            if let data = try? JSONEncoder().encode(loaded) {
                UserDefaults.standard.set(data, forKey: cacheKey)
            }
            prefetchPosters(loaded)
            await loadSmartRow()
            lastLoaded = Date()
            CinemaxPrefs.markRefreshed()
            if loaded.isEmpty {
                error = "لا كتالوجات بعد. ثبّت المصادر الجاهزة من الإعدادات ← الإضافات."
            }
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    /// Warms the poster cache for the rows a viewer sees first, so the home paints
    /// its artwork instead of grey blocks.
    private func prefetchPosters(_ shelves: [Shelf]) {
        let urls = shelves.prefix(3).flatMap { $0.items.prefix(6) }.compactMap(\.posterURL)
        guard !urls.isEmpty else { return }
        Task.detached(priority: .utility) {
            for url in urls {
                _ = await CinemaImageStore.shared.image(for: url)
            }
        }
    }

    /// Forgets the cached catalogues and forces the next visit to refetch, used
    /// after an addon is installed or removed. The rows on screen stay while the
    /// new ones load: emptying them here turned the home back into skeletons
    /// every time a title was saved or marked watched.
    func resetCache() {
        UserDefaults.standard.removeObject(forKey: cacheKey)
        lastLoaded = nil
        smartShelf = nil
        smartSeed = nil
        Task { await load(force: true) }
    }

    private func refreshPersonal() async {
        async let progressTask = CinemaAPI.shared.progress()
        async let favoritesTask = CinemaAPI.shared.favorites()
        continueWatching = await progressTask
        favorites = Set(await favoritesTask.map(\.id))
        await loadSmartRow()
    }

    /// Builds the "لأنك تشاهد" row from the title the viewer last opened. It only
    /// asks again when that title changes, so later visits cost nothing.
    private func loadSmartRow() async {
        guard let seed = continueWatching.first?.item, seed.type != "channel" else {
            smartShelf = nil
            smartSeed = nil
            return
        }
        if smartSeed == seed.id, smartShelf != nil { return }
        smartSeed = seed.id
        let titles = await CinemaAPI.shared.similar(item: seed)
        guard !titles.isEmpty else {
            smartShelf = nil
            return
        }
        smartShelf = Shelf(
            id: "smart:similar",
            title: "لأنك تشاهد \(seed.name)",
            items: Array(titles.prefix(18))
        )
    }

    func toggle(_ item: CinemaItem) {
        if CinemaAPI.shared.toggleFavorite(item) {
            favorites.insert(item.id)
        } else {
            favorites.remove(item.id)
        }
        // The library tab reads the same store through its own model, so it is
        // reloaded here instead of on the next visit to that tab, where the row
        // the viewer just saved was missing.
        Task { await LibraryModel.shared.load() }
    }

    /// Forgets one stop position, called from the "أكمل المشاهدة" row.
    func removeResume(_ entry: ProgressItem) {
        continueWatching.removeAll { $0.id == entry.id }
        Task {
            _ = await CinemaAPI.shared.removeProgress(
                itemId: entry.item.id,
                videoLabel: entry.episodeLabel
            )
        }
    }

    var heroItems: [CinemaItem] {
        let all = shelves.flatMap(\.items)
        switch filter {
        case "movie": return all.filter { $0.type == "movie" }
        case "series": return all.filter { $0.type == "series" }
        case "channel": return all.filter { $0.type == "channel" || $0.type == "tv" }
        default: return all
        }
    }

    var visibleShelves: [Shelf] {
        let base = shelves.filter { !hiddenShelves.contains($0.id) }
        // A viewer's own order wins; anything new follows in the addon's order.
        let order = rowOrder
        let ordered: [Shelf]
        if order.isEmpty {
            ordered = base
        } else {
            let ranked = base.enumerated().sorted { lhs, rhs in
                let left = order.firstIndex(of: lhs.element.id) ?? Int.max
                let right = order.firstIndex(of: rhs.element.id) ?? Int.max
                if left == right { return lhs.offset < rhs.offset }
                return left < right
            }
            ordered = ranked.map(\.element)
        }
        // The app's own row sits above the addon rows, since it is the most personal.
        var result = ordered
        if let smart = smartShelf, !smart.items.isEmpty, !hiddenShelves.contains(smart.id) {
            result.removeAll { $0.id == smart.id }
            result.insert(smart, at: 0)
        }
        switch filter {
        case "movie": return result.filter { $0.items.contains { $0.type == "movie" } }
        case "series": return result.filter { $0.items.contains { $0.type == "series" } }
        case "channel": return result.filter { $0.items.contains { $0.type == "channel" || $0.type == "tv" } }
        default: return result
        }
    }

    /// Moves a row up or down the home screen and remembers the order.
    func moveRow(_ shelf: Shelf, up: Bool) {
        var ids = visibleShelves.map(\.id)
        guard let index = ids.firstIndex(of: shelf.id) else { return }
        let target = up ? index - 1 : index + 1
        guard ids.indices.contains(target) else { return }
        ids.swapAt(index, target)
        rowOrder = ids
    }

    /// The viewer's own row order, by shelf id.
    var rowOrder: [String] {
        get { UserDefaults.standard.stringArray(forKey: "cinemax.rowOrder") ?? [] }
        set { UserDefaults.standard.set(newValue, forKey: "cinemax.rowOrder") }
    }

    /// Puts a row away; settings can bring every row back.
    func hide(_ shelf: Shelf) {
        hiddenShelves.insert(shelf.id)
    }

    func unhideAll() {
        hiddenShelves = []
    }
}

/// Design 1 — the cinematic, glass, ambient-glow home.
struct CinematicHomeView: View {
    @Binding var selection: Int
    @ObservedObject private var model = HomeModel.shared
    @ObservedObject private var lists = ListsStore.shared
    @AppStorage("cinemax.showContinue") private var showContinue = true
    @AppStorage("cinemax.showHero") private var showHero = true
    @State private var glow: [Color] = [CinemaTheme.accent]
    @State private var detailItem: CinemaItem?
    @State private var gridShelf: Shelf?
    @State private var listItem: CinemaItem?

    private let filters: [(id: String, title: String)] = [
        ("all", "الكل"), ("movie", "أفلام"), ("series", "مسلسلات"), ("channel", "قنوات")
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                header
                filterBar
                if model.loading {
                    skeleton
                } else if let error = model.error, model.heroItems.isEmpty {
                    emptyState(error)
                } else if model.heroItems.isEmpty {
                    emptyState("لا عناوين من النوع المختار. جرّب «الكل» أو ثبّت مصدرًا آخر.")
                } else {
                    if showHero {
                        HeroCarousel(
                            items: model.heroItems,
                            onOpen: { detailItem = $0 },
                            onPalette: { glow = $0 },
                            isSaved: { model.favorites.contains($0.id) },
                            onToggleFavorite: { item in
                                Haptics.tap()
                                model.toggle(item)
                            }
                        )
                        .frame(height: 440)
                    }
                    if showContinue, !model.continueWatching.isEmpty {
                        SectionHeader(title: "أكمل المشاهدة")
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(spacing: 12) {
                                ForEach(model.continueWatching) { entry in
                                    ContinueCard(entry: entry) { detailItem = entry.item }
                                        .contextMenu {
                                            Button(role: .destructive) {
                                                Haptics.tap()
                                                model.removeResume(entry)
                                            } label: {
                                                Label("إزالة من قيد المشاهدة", systemImage: "trash")
                                            }
                                        }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                    if let list = lists.lists.first(where: { !$0.items.isEmpty }) {
                        SectionHeader(title: "قوائمي · \(list.name) · \(list.items.count)")
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(alignment: .top, spacing: 12) {
                                ForEach(list.items.prefix(20)) { item in
                                    PosterCard(
                                        item: item,
                                        progress: model.continueWatching.first { $0.item.id == item.id }?.fraction ?? 0,
                                        saved: model.favorites.contains(item.id),
                                        onOpen: { detailItem = item }
                                    )
                                    .contextMenu {
                                        Button(role: .destructive) {
                                            Haptics.tap()
                                            ListsStore.shared.remove(item, from: list.id)
                                        } label: {
                                            Label("إزالة من القائمة", systemImage: "minus.circle")
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                    ForEach(model.visibleShelves) { shelf in
                        SectionHeader(title: "\(shelf.title) · \(shelf.items.count)") {
                            Haptics.tap()
                            gridShelf = shelf
                        }
                        .contextMenu {
                            Button {
                                Haptics.tap()
                                withAnimation(.easeInOut(duration: 0.2)) { model.moveRow(shelf, up: true) }
                            } label: {
                                Label("نقل للأعلى", systemImage: "arrow.up")
                            }
                            Button {
                                Haptics.tap()
                                withAnimation(.easeInOut(duration: 0.2)) { model.moveRow(shelf, up: false) }
                            } label: {
                                Label("نقل للأسفل", systemImage: "arrow.down")
                            }
                            Button(role: .destructive) {
                                Haptics.tap()
                                withAnimation(.easeInOut(duration: 0.2)) { model.hide(shelf) }
                            } label: {
                                Label("إخفاء هذا الصف", systemImage: "eye.slash")
                            }
                        }
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(alignment: .top, spacing: 12) {
                                ForEach(shelf.items) { item in
                                    PosterCard(
                                        item: item,
                                        progress: model.continueWatching.first { $0.item.id == item.id }?.fraction ?? 0,
                                        saved: model.favorites.contains(item.id),
                                        onOpen: { detailItem = item }
                                    )
                                    .contextMenu {
                                        Button {
                                            Haptics.tap()
                                            listItem = item
                                        } label: {
                                            Label("أضف إلى قائمة", systemImage: "text.badge.plus")
                                        }
                                        Button {
                                            Haptics.tap()
                                            model.toggle(item)
                                        } label: {
                                            Label(
                                                model.favorites.contains(item.id)
                                                    ? "إزالة من المحفوظة"
                                                    : "أضف إلى المحفوظة",
                                                systemImage: "heart"
                                            )
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                }
                Spacer(minLength: 120)
            }
        }
        .background(AmbientGlow(colors: glow).ignoresSafeArea())
        .refreshable { await model.load(force: true) }
        .task { await model.load() }
        .fullScreenCover(item: $detailItem) { item in
            DetailView(item: item, onFavorite: { model.toggle(item) })
        }
        .sheet(item: $gridShelf) { shelf in
            ShelfGridScreen(shelf: shelf, onOpen: { item in
                gridShelf = nil
                detailItem = item
            })
        }
        .sheet(item: $listItem) { ListsPickerSheet(item: $0) }
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(Brand.name.uppercased())
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(3)
                    .foregroundStyle(CinemaTheme.muted)
                Text("الرئيسية")
                    .font(.system(size: 34, weight: .heavy))
                    .foregroundStyle(.white)
                Text(CinemaxPrefs.lastRefreshLabel + " · " + "\(model.shelves.reduce(0) { $0 + $1.items.count }) عنوانًا")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
            }
            Spacer()
            Text(CinemaAPI.shared.versionLabel)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(CinemaTheme.muted)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(.ultraThinMaterial, in: Capsule())
            Button {
                Haptics.tap()
                Task { await model.load(force: true) }
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)
                    .frame(width: 34, height: 34)
                    .background(.ultraThinMaterial, in: Circle())
            }
            .buttonStyle(.plain)
            Button {
                surprise()
            } label: {
                Image(systemName: "dice.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(CinemaTheme.accent)
                    .frame(width: 34, height: 34)
                    .background(CinemaTheme.accent.opacity(0.14), in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(model.visibleShelves.isEmpty)
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 14)
    }

    /// A title the viewer has not started yet, taken from the rows already on screen:
    /// instant, free, and always something they can actually play.
    private func surprise() {
        Haptics.select()
        let started = Set(model.continueWatching.map(\.item.id))
        let all = model.visibleShelves.flatMap(\.items)
        let fresh = all.filter { !started.contains($0.id) }
        let pool = fresh.isEmpty ? all : fresh
        guard let pick = pool.randomElement() else { return }
        EventLog.shared.add("discover", "فاجئني: \(pick.name)")
        detailItem = pick
    }

    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(filters, id: \.id) { filter in
                    Button {
                        Haptics.select()
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { model.filter = filter.id }
                    } label: {
                        Text(filter.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(model.filter == filter.id ? .black : .white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 9)
                            .background {
                                Capsule()
                                    .fill(model.filter == filter.id ? CinemaTheme.accent : Color.white.opacity(0.08))
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 20)
        }
        .padding(.bottom, 8)
    }

    private var skeleton: some View {
        VStack(alignment: .leading, spacing: 16) {
            ShimmerBlock(height: 400, width: UIScreen.main.bounds.width - 32)
                .padding(.horizontal, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(0..<4, id: \.self) { _ in ShimmerBlock(height: 200, width: 132) }
                }
                .padding(.horizontal, 20)
            }
        }
        .padding(.top, 8)
    }

    private func emptyState(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: "film.stack")
                .font(.system(size: 34, weight: .light))
                .foregroundStyle(CinemaTheme.accent)
            Text("لا شيء لعرضه بعد")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(.white)
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(CinemaTheme.muted)
            Button("افتح الإعدادات") { selection = 3 }
                .buttonStyle(.borderedProminent)
                .tint(CinemaTheme.accent)
        }
        .padding(24)
    }
}


// MARK: - Round icon button

struct CircleButton: View {
    let system: String
    var tint: Color = .white
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: system)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 40, height: 40)
                .background(.ultraThinMaterial, in: Circle())
                .overlay(Circle().strokeBorder(CinemaTheme.hairline, lineWidth: 1))
        }
        .buttonStyle(CinemaPressStyle())
        .accessibilityLabel(Text(CircleButton.label(for: system)))
    }

    /// VoiceOver needs words, not SF Symbol names.
    static func label(for system: String) -> String {
        switch system {
        case "xmark": return "إغلاق"
        case "chevron.down": return "رجوع"
        case "list.bullet": return "الحلقات"
        case "speedometer": return "سرعة التشغيل"
        case "captions.bubble": return "الترجمة والصوت"
        case "square.and.arrow.up": return "مشاركة"
        case "heart", "heart.fill": return "الحفظ"
        case "textformat": return "نسخ الاسم"
        case "rectangle.landscape.rotate": return "أفقي"
        case "rectangle.portrait.rotate": return "رأسي"
        default: return system
        }
    }
}

// MARK: - Player

/// The libmpv surface as a SwiftUI view. The same controller the classic player
/// uses, so MKV, HEVC, AVI and embedded subtitle tracks all work here too.
struct MPVSurface: UIViewControllerRepresentable {
    let url: String
    var headers: [String: String] = [:]
    var startMs: Int64 = 0
    @Binding var controller: MPVViewController?

    func makeUIViewController(context: Context) -> MPVViewController {
        let player = MPVViewController()
        player.load(urlString: url, headersJson: Self.headersJson(headers), startMs: startMs)
        DispatchQueue.main.async { controller = player }
        return player
    }

    /// libmpv takes its request headers as a JSON object string.
    static func headersJson(_ headers: [String: String]) -> String {
        guard !headers.isEmpty,
              let data = try? JSONSerialization.data(withJSONObject: headers),
              let text = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return text
    }

    func updateUIViewController(_ uiViewController: MPVViewController, context: Context) {}
}

struct TrackInfo: Decodable, Identifiable {
    let id: Int64
    let index: Int
    let language: String
    let title: String
    let codec: String
    let selected: Bool

    var label: String {
        let name = title.isEmpty ? (language.isEmpty ? codec : language) : title
        return name.isEmpty ? "مسار \(index + 1)" : name
    }
}

struct PlayerScreen: View {
    let title: String
    let url: String
    var item: CinemaItem? = nil
    var videoId: String? = nil
    var videoLabel: String? = nil
    /// The addon and quality this link came from, shown under the title.
    var sourceNote: String? = nil
    /// Referer / User-Agent a host demands before it serves the file.
    var headers: [String: String] = [:]
    /// Every source the detail page found, so a dead link can be replaced in place.
    var sources: [StreamChoice] = []
    /// The whole episode list, so the player can roll into the next one.
    var episodes: [CinemaItem] = []
    var type: String = "series"

    @Environment(\.dismiss) private var dismiss
    @State private var player: MPVViewController?
    @State private var playing = true
    @State private var position: Double = 0
    @State private var duration: Double = 0
    @State private var ticker: Timer?
    @State private var startMs: Int64 = 0
    @State private var ready = false
    @State private var showOptions = false
    /// True while the viewer drags the scrubber, so the poller leaves the thumb alone.
    @State private var scrubbing = false
    @State private var scrubValue: Double = 0
    @State private var subtitleTracks: [TrackInfo] = []
    @State private var audioTracks: [TrackInfo] = []
    @State private var speed: Double = 1
    @State private var loopA: Double?
    @State private var loopB: Double?
    @State private var shotNote: String?
    @State private var subtitlesOff = false
    @State private var subtitleDelay: Double = 0
    @State private var subtitleSize = UserDefaults.standard.string(forKey: "cinemax.subtitleSize") ?? "medium"
    @State private var externalSubtitle: String?
    @State private var subtitleError = false
    @State private var addonSubtitles: [SubtitleChoice] = []
    @State private var loadingSubtitles = false
    @State private var volume: Double = (UserDefaults.standard.object(forKey: "cinemax.volume") as? Double) ?? 100
    /// The picture's shape, which every title now opens fitted with bars. It is
    /// kept for the screen alone and never stored: a crop left behind by an
    /// earlier film is exactly what made the next one larger than the page.
    @State private var aspect = ""
    @State private var zoom: Double = 0
    @State private var subtitlePosition: Double = (UserDefaults.standard.object(forKey: "cinemax.subPos") as? Double) ?? 100
    @State private var buffering = false
    @State private var appliedInitial = false
    @State private var lastDoubleTap = Date.distantPast
    @State private var locked = false
    @State private var bufferTicks = 0
    /// Counts the half seconds between two resume points written while playing.
    @State private var saveTicks = 0
    @State private var didAutoSkipIntro = false
    @State private var resumeHint: String?
    @AppStorage("cinemax.playerHints") private var hintsShown = false
    @State private var brightness: Double = 0.6
    @State private var savedBrightness: Double = 0.6
    @State private var showEpisodes = false
    @State private var episodeError = false
    @State private var sleepMinutes: Int?
    /// The moment the sleep timer runs out, kept apart from the minutes so the
    /// countdown keeps running while the options panel is closed.
    @State private var sleepDeadline: Date?
    @State private var sleepRemaining: Int = 0
    /// Tapping the picture hides every control, for a clean full-screen frame.
    @State private var controlsVisible = true
    @State private var showRemaining = false
    @State private var pool: [StreamChoice] = []
    @State private var poolIndex = 0
    @State private var playToken = 0
    /// True once this screen has started, so a return from the background never
    /// starts the film again.
    @State private var started = false
    /// The seconds left before the next episode rolls in, or nil when it is not
    /// offered. The screen asks instead of taking the film away while it is being
    /// watched, which is what an unannounced roll-over felt like.
    @State private var upNextCountdown: Int?
    /// True once the viewer said not now, so the end of the file closes the
    /// screen instead of rolling into the next episode.
    @State private var autoplayCancelled = false
    @State private var autoTries = 0
    @State private var loadTicks = 0
    @State private var playerError: String?
    @State private var diagnosticsText: String?
    @State private var currentURL = ""
    @State private var currentHeaders: [String: String] = [:]
    @State private var currentTitle = ""
    @State private var currentVideoId: String?
    @State private var currentVideoLabel: String?
    @State private var switching = false
    /// The link handed to the system player, and whether this one was playing so
    /// it is taken back up when the viewer comes back.
    @State private var systemTakeover: SystemTakeover?
    @State private var pausedForSystem = false

    private var upNext: CinemaItem? {
        guard let currentVideoId,
              let index = episodes.firstIndex(where: { $0.id == currentVideoId }),
              index + 1 < episodes.count else { return nil }
        return episodes[index + 1]
    }

    private var autoplayNext: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.autoplayNext") as? Bool) ?? true
    }

    private var skipIntroEnabled: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.skipIntro") as? Bool) ?? true
    }

    /// The step the ±buttons take, chosen in Settings → التشغيل.
    private var seekStep: Double { Double(CinemaxPrefs.seekSeconds) }

    /// Episodes of this title already watched to the end, for the sheet's ticks.
    private var watchedEpisodeLabels: Set<String> {
        guard let item else { return [] }
        return Set(
            LibraryModel.shared.progress
                .filter { $0.item.id == item.id && $0.fraction >= 0.9 }
                .compactMap(\.episodeLabel)
        )
    }

    /// The speed this title was last watched at, kept per id so a series keeps it
    /// across episodes and a film keeps it the next evening.
    private func rememberedSpeed(for id: String?) -> Double? {
        guard let id else { return nil }
        let value = UserDefaults.standard.double(forKey: "cinemax.speed." + id)
        return value > 0 ? value : nil
    }

    private func saveSpeed(_ value: Double) {
        guard let id = videoId ?? item?.id else { return }
        UserDefaults.standard.set(value, forKey: "cinemax.speed." + id)
    }

    /// The surface builds its player a moment after the screen appears, so the
    /// remembered speed is pushed once that player exists, not before it.
    private func applyRememberedSpeed() {
        Task {
            for _ in 0..<24 {
                if player != nil { break }
                try? await Task.sleep(nanoseconds: 50_000_000)
            }
            player?.setSpeed(speed)
        }
    }

    // MARK: - Controls

    /// The top row: the way out, what is playing, and the four actions a viewer
    /// reaches for while watching. Everything else folds into the ⋯ menu, so the
    /// row holds five targets instead of ten cramped ones.
    private var topControls: some View {
        HStack(spacing: 10) {
            CircleButton(system: "xmark") { dismiss() }
            VStack(alignment: .leading, spacing: 2) {
                Text(currentTitle.isEmpty ? title : currentTitle)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(playbackCaption)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            if switching {
                ProgressView().tint(CinemaTheme.accent)
            }
            if episodes.count > 1 {
                CircleButton(system: "rectangle.stack") {
                    Haptics.tap()
                    showEpisodes = true
                }
            }
            CircleButton(system: "slider.horizontal.3") {
                Haptics.tap()
                loadTracks()
                if addonSubtitles.isEmpty { loadAddonSubtitles() }
                showOptions = true
            }
            Menu {
                moreActions
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(.ultraThinMaterial, in: Circle())
                    .overlay(Circle().strokeBorder(CinemaTheme.hairline, lineWidth: 1))
            }
        }
    }

    /// What is playing and with which engine, under the title.
    private var playbackCaption: String {
        if let sourceNote, !sourceNote.isEmpty { return sourceNote + " · libmpv" }
        if let label = currentVideoLabel, !label.isEmpty { return label + " · libmpv" }
        return "libmpv"
    }

    @ViewBuilder
    private var moreActions: some View {
        if upNext != nil {
            Button {
                Haptics.tap()
                playNext()
            } label: {
                Label("الحلقة التالية", systemImage: "forward.end.fill")
            }
            Divider()
        }
        Button {
            Haptics.tap()
            let options = ["", "16:9", "4:3", "2.35:1"]
            let index = options.firstIndex(of: aspect) ?? 0
            let next = options[(index + 1) % options.count]
            aspect = next
            player?.setVideoAspect(next)
        } label: {
            Label("شكل الصورة: \(aspect.isEmpty ? "ملاءمة" : aspect)", systemImage: "aspectratio")
        }
        Menu {
            ForEach([0.5, 0.75, 1, 1.25, 1.5, 2], id: \.self) { value in
                Button(value == 1 ? "عادي" : String(format: "%g×", value)) {
                    speed = value
                    player?.setSpeed(value)
                    saveSpeed(value)
                }
            }
        } label: {
            Label("السرعة: \(String(format: "%g×", speed))", systemImage: "speedometer")
        }
        Button {
            handleLoop()
        } label: {
            Label(
                loopA == nil ? "تكرار مقطع" : (loopB == nil ? "تعيين نقطة ب" : "إلغاء التكرار"),
                systemImage: "repeat"
            )
        }
        Button {
            takeScreenshot()
        } label: {
            Label("لقطة من الفيلم", systemImage: "camera")
        }
        Button {
            Haptics.select()
            locked = true
            withAnimation(.easeInOut(duration: 0.2)) { controlsVisible = false }
        } label: {
            Label("قفل الأدوات", systemImage: "lock.fill")
        }
        Button {
            Haptics.tap()
            loadTracks()
            if addonSubtitles.isEmpty { loadAddonSubtitles() }
            showOptions = true
        } label: {
            Label("مصدر آخر وترجمة", systemImage: "captions.bubble")
        }
    }

    /// The transport in the middle of the picture, where a thumb reaches it:
    /// one white control and two glass ones around it.
    private var transportControls: some View {
        HStack(spacing: 26) {
            transportButton(system: "gobackward.\(CinemaxPrefs.seekSeconds)", size: 22) {
                seek(by: -seekStep)
            }
            Button {
                Haptics.tap()
                playing.toggle()
                if playing { player?.playPlayback() } else { player?.pausePlayback() }
            } label: {
                Image(systemName: playing ? "pause.fill" : "play.fill")
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(.black)
                    .frame(width: 66, height: 66)
                    .background(.white, in: Circle())
                    .shadow(color: .black.opacity(0.35), radius: 14, y: 6)
            }
            .buttonStyle(CinemaPressStyle())
            transportButton(system: "goforward.\(CinemaxPrefs.seekSeconds)", size: 22) {
                seek(by: seekStep)
            }
        }
    }

    private func transportButton(
        system: String,
        size: CGFloat,
        action: @escaping () -> Void
    ) -> some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            Image(systemName: system)
                .font(.system(size: size, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 52, height: 52)
                .background(.ultraThinMaterial, in: Circle())
                .overlay(Circle().strokeBorder(CinemaTheme.hairline, lineWidth: 1))
        }
        .buttonStyle(CinemaPressStyle())
        .simultaneousGesture(
            LongPressGesture(minimumDuration: 0.45).onEnded { _ in
                seek(by: system.contains("backward") ? -seekStep * 3 : seekStep * 3)
            }
        )
    }

    /// The timeline and the chips that belong to it, in one glass card.
    private var timelineControls: some View {
        VStack(spacing: 12) {
            HStack(spacing: 8) {
                Text(clock(scrubbing ? scrubValue : position))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white)
                    .monospacedDigit()
                Spacer()
                if let shotNote {
                    Text(shotNote)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                        .lineLimit(1)
                        .task {
                            try? await Task.sleep(nanoseconds: 2_200_000_000)
                            withAnimation(.easeInOut(duration: 0.2)) { self.shotNote = nil }
                        }
                }
                if sleepDeadline != nil {
                    Label(clock(Double(sleepRemaining)), systemImage: "moon.zzz.fill")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                        .monospacedDigit()
                }
                Text(
                    showRemaining
                        ? "−\(clock(max(0, duration - position)))"
                        : clock(duration)
                )
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CinemaTheme.muted)
                .monospacedDigit()
            }
            Slider(
                value: Binding(
                    get: { scrubbing ? scrubValue : position },
                    set: { scrubValue = $0 }
                ),
                in: 0...max(1, duration),
                onEditingChanged: { editing in
                    if editing {
                        scrubValue = position
                        scrubbing = true
                    } else {
                        position = scrubValue
                        player?.seekTo(Int64(scrubValue * 1000))
                        // Held a moment longer while libmpv catches up.
                        Task {
                            try? await Task.sleep(nanoseconds: 700_000_000)
                            scrubbing = false
                        }
                    }
                }
            )
            .tint(CinemaTheme.accent)
            HStack(spacing: 8) {
                if startMs > 30_000, position < 120 {
                    chip("من البداية", system: "backward.end.fill") {
                        Haptics.tap()
                        position = 0
                        player?.seekTo(0)
                    }
                }
                if skipIntroEnabled, duration > 300, position > 5, position < 90 {
                    chip("تخطي المقدمة", system: "forward.fill") {
                        Haptics.tap()
                        seek(by: 85)
                    }
                }
                if upNext != nil, duration > 0, position > duration - 60 {
                    chip("الحلقة التالية", system: "forward.end.fill") {
                        Haptics.tap()
                        playNext()
                    }
                }
                Spacer(minLength: 0)
                Button {
                    Haptics.select()
                    showRemaining.toggle()
                } label: {
                    Text(showRemaining ? "الوقت الكامل" : "الوقت المتبقي")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
        )
    }

    /// Three taps of one button: point A, point B, then off again.
    private func handleLoop() {
        Haptics.select()
        if loopA == nil {
            loopA = position
            shotNote = "نقطة أ عند \(clock(position))"
            return
        }
        if loopB == nil {
            guard position > (loopA ?? 0) + 3 else {
                shotNote = "حرّك الشريط ثم اختر نقطة ب"
                return
            }
            loopB = position
            player?.setABLoop(startMs: Int64((loopA ?? 0) * 1000), endMs: Int64(position * 1000))
            shotNote = "تكرار المقطع من أ إلى ب"
            return
        }
        loopA = nil
        loopB = nil
        player?.setABLoop(startMs: -1, endMs: -1)
        shotNote = "أُلغي التكرار"
    }

    /// Grabs the frame on screen, waits for libmpv to write it, then puts it in Photos.
    private func takeScreenshot() {
        Haptics.tap()
        guard let path = player?.captureFramePng() else {
            shotNote = "لا يوجد إطار الآن"
            return
        }
        Task {
            var exists = false
            for _ in 0..<30 {
                if FileManager.default.fileExists(atPath: path) {
                    exists = true
                    break
                }
                try? await Task.sleep(nanoseconds: 60_000_000)
            }
            guard exists, let image = UIImage(contentsOfFile: path) else {
                await MainActor.run { shotNote = "تعذّر أخذ اللقطة" }
                return
            }
            let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
            guard status == .authorized || status == .limited else {
                await MainActor.run { shotNote = "السماح بالصور مطلوب لحفظ اللقطة" }
                return
            }
            do {
                try await PHPhotoLibrary.shared().performChanges {
                    PHAssetChangeRequest.creationRequestForAsset(from: image)
                }
                await MainActor.run {
                    shotNote = "حُفظت اللقطة في تطبيق الصور"
                    EventLog.shared.add("player", "لقطة شاشة من \(title)")
                }
            } catch {
                await MainActor.run { shotNote = "تعذّر الحفظ" }
            }
            try? FileManager.default.removeItem(atPath: path)
        }
    }

    /// Jumps past an opening sequence without a tap, when the viewer asked for it.
    private var autoSkipIntro: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.autoSkipIntro") as? Bool) ?? false
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if ready {
                MPVSurface(url: currentURL, headers: currentHeaders, startMs: startMs, controller: $player)
                    .id(currentURL + "-" + String(startMs) + "-" + String(playToken))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .ignoresSafeArea()
                    .clipped()
                    .contentShape(Rectangle())
                    .highPriorityGesture(
                        SpatialTapGesture(count: 2)
                            .onEnded { value in
                                guard !locked else { return }
                                let width = UIScreen.main.bounds.width
                                lastDoubleTap = Date()
                                if value.location.x < width * 0.33 {
                                    seek(by: -seekStep * 2)
                                    Haptics.tap()
                                } else if value.location.x > width * 0.67 {
                                    seek(by: seekStep * 2)
                                    Haptics.tap()
                                } else {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        controlsVisible.toggle()
                                    }
                                }
                            }
                    )
                    .onTapGesture {
                        // A double tap must not also flip the controls.
                        guard !locked else { return }
                        guard Date().timeIntervalSince(lastDoubleTap) > 0.4 else { return }
                        withAnimation(.easeInOut(duration: 0.2)) {
                            controlsVisible.toggle()
                            if !controlsVisible { showOptions = false }
                        }
                    }
            } else {
                ProgressView().tint(CinemaTheme.accent)
            }
            // A quiet scrim over the picture instead of a hard edge, so the
            // top row and the timeline sit on the film rather than on the black.
            VStack(spacing: 0) {
                LinearGradient(
                    colors: [.black.opacity(0.62), .black.opacity(0)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 150)
                .allowsHitTesting(false)
                Spacer(minLength: 0)
                LinearGradient(
                    colors: [.black.opacity(0), .black.opacity(0.72)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 230)
                .allowsHitTesting(false)
            }
            .ignoresSafeArea()
            .opacity(controlsVisible ? 1 : 0)
            VStack(spacing: 0) {
                topControls
                Spacer(minLength: 0)
                transportControls
                Spacer(minLength: 0)
                timelineControls
            }
            .padding(.horizontal, 18)
            .padding(.top, 6)
            .padding(.bottom, 12)
            .opacity(controlsVisible ? 1 : 0)
            .allowsHitTesting(controlsVisible)
            if let countdown = upNextCountdown, let next = upNext, controlsVisible {
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        HStack(spacing: 10) {
                            RemoteImage(url: next.posterURL)
                                .frame(width: 34, height: 50)
                                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                            VStack(alignment: .leading, spacing: 2) {
                                Text("الحلقة التالية")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                                Text(next.name)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                                Text(countdown > 0 ? "تبدأ خلال \(countdown) ث" : "تبدأ الآن…")
                                    .font(.system(size: 10))
                                    .foregroundStyle(CinemaTheme.muted)
                                    .monospacedDigit()
                            }
                            Button {
                                Haptics.tap()
                                upNextCountdown = nil
                                playNext()
                            } label: {
                                Text("شغّل الآن")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(.black)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 7)
                                    .background(.white, in: Capsule())
                            }
                            .buttonStyle(CinemaPressStyle())
                            Button {
                                Haptics.select()
                                autoplayCancelled = true
                                upNextCountdown = nil
                            } label: {
                                Image(systemName: "xmark")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(CinemaTheme.muted)
                                    .frame(width: 26, height: 26)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(10)
                        .background(
                            .ultraThinMaterial,
                            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
                        )
                        .shadow(color: .black.opacity(0.3), radius: 16, y: 8)
                    }
                    .padding(.horizontal, 18)
                    .padding(.bottom, 132)
                }
                .transition(.move(edge: .trailing).combined(with: .opacity))
            }
            if let message = playerError, duration <= 1 {
                VStack(spacing: 14) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 26, weight: .semibold))
                        .foregroundStyle(CinemaTheme.favourite)
                    Text("تعذّر تشغيل هذا المصدر")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                    Text(message)
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                    HStack(spacing: 10) {
                        Button("أعد المحاولة") { retryPlayback() }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(CinemaTheme.accent, in: Capsule())
                            .buttonStyle(.plain)
                        if pool.count > 1 {
                            Button("مصدر آخر") { playNextSource() }
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(Color.white.opacity(0.12), in: Capsule())
                                .buttonStyle(.plain)
                        }
                        Button("إغلاق") { dismiss() }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                            .buttonStyle(.plain)
                    }
                    Button("تشخيص المشغل") {
                        diagnosticsText = player?.diagnostics() ?? "لا رسائل من المشغّل"
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(CinemaTheme.accent)
                    .buttonStyle(.plain)
                }
                .padding(22)
                .frame(maxWidth: 330)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
                )
            }
            if locked {
                VStack {
                    HStack {
                        Spacer()
                        CircleButton(system: "lock.open.fill") {
                            Haptics.select()
                            locked = false
                            withAnimation(.easeInOut(duration: 0.2)) { controlsVisible = true }
                        }
                        .padding(.trailing, 20)
                        .padding(.top, 8)
                    }
                    Spacer()
                    Text("الأدوات مقفلة — اضغط القفل للعودة")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.7))
                        .padding(.bottom, 26)
                }
            }
            if buffering, duration > 1, playerError == nil {
                HStack(spacing: 8) {
                    ProgressView().tint(.white)
                    Text("جارٍ التحميل…")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .background(.ultraThinMaterial, in: Capsule())
                .transition(.opacity)
            }
            if let resumeHint {
                VStack {
                    Text(resumeHint)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding(.top, 56)
                    Spacer()
                }
                .task {
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                    withAnimation(.easeInOut(duration: 0.3)) { self.resumeHint = nil }
                }
            }
            if !hintsShown {
                VStack(spacing: 12) {
                    Text("إيماءات المشغّل")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                    ForEach([
                        "نقرة مزدوجة على يمين الصورة: تقديم",
                        "نقرة مزدوجة على يسارها: تأخير",
                        "ضغط مطوّل على ±: قفزة ثلاثة أضعاف",
                        "نقرة واحدة: إخفاء الأدوات وإظهارها"
                    ], id: \.self) { line in
                        Text("• " + line)
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Button {
                        Haptics.tap()
                        withAnimation(.easeInOut(duration: 0.2)) { hintsShown = true }
                    } label: {
                        Text("فهمت")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 9)
                            .background(CinemaTheme.accent, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
                .frame(maxWidth: 320)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
                )
            }
            Color.clear
                .frame(width: 0, height: 0)
                // A sheet of its own, not an overlay: the player's own gestures
                // would otherwise swallow the panel's scroll, and in landscape
                // the bottom of the list sat under the home bar.
                .sheet(isPresented: $showOptions) {
                    PlayerOptionsPanel(
                    subtitleTracks: subtitleTracks,                    audioTracks: audioTracks,
                    speed: speed,
                    subtitlesOff: subtitlesOff,
                    subtitleDelay: subtitleDelay,
                    subtitleSize: subtitleSize,
                    externalSubtitle: externalSubtitle,
                    sourceLabel: currentSourceLabel,
                    canSwitchSource: pool.count > 1,
                    addonSubtitles: addonSubtitles,
                    loadingSubtitles: loadingSubtitles,
                    volume: volume,
                    brightness: brightness,
                    aspect: aspect,
                    zoom: zoom,
                    subtitlePosition: subtitlePosition,
                    rotationAllowed: CinemaxPrefs.allowRotation,
                    sleepMinutes: sleepMinutes,
                    sleepRemaining: sleepRemaining,
                    onClose: { showOptions = false },
                    onSubtitle: { track in
                        subtitlesOff = track == nil
                        if let track {
                            player?.setSubtitleTrack(track.id, ordinal: Int32(track.index))
                        } else {
                            player?.setSubtitleTrack(-1, ordinal: 0)
                        }
                        Haptics.select()
                        showOptions = false
                    },
                    onAudio: { track in
                        player?.setAudioTrack(track.id, ordinal: Int32(track.index))
                        Haptics.select()
                        showOptions = false
                    },
                    onSpeed: { value in
                        speed = value
                        player?.setSpeed(value)
                        saveSpeed(value)
                        Haptics.select()
                    },
                    onSubtitleDelay: { value in
                        subtitleDelay = value
                        player?.setSubtitleDelay(value)
                        Haptics.select()
                    },
                    onSubtitleSize: { value in
                        subtitleSize = value
                        UserDefaults.standard.set(value, forKey: "cinemax.subtitleSize")
                        let points = value == "small" ? 42 : (value == "large" ? 66 : 54)
                        player?.setSubtitleFontSize(points)
                        Haptics.select()
                    },
                    onVolume: { value in
                        volume = value
                        UserDefaults.standard.set(value, forKey: "cinemax.volume")
                        player?.setVolumeLevel(value)
                    },
                    onBrightness: { value in
                        brightness = value
                        applyBrightness(value)
                    },
                    onAspect: { value in
                        aspect = value
                        player?.setVideoAspect(value)
                        Haptics.select()
                    },
                    onZoom: { value in
                        zoom = value
                        player?.setVideoZoom(value)
                        Haptics.select()
                    },
                    onSubtitlePosition: { value in
                        subtitlePosition = value
                        UserDefaults.standard.set(value, forKey: "cinemax.subPos")
                        player?.setSubtitlePosition(value)
                    },
                    onRotate: { landscape in
                        Haptics.select()
                        // The picture is what the viewer asked for, so the panel
                        // steps aside instead of covering the turned film.
                        showOptions = false
                        OrientationManager.shared.rotate(toLandscape: landscape)
                    },
                    onSleep: { minutes in
                        if let minutes {
                            sleepMinutes = minutes
                            sleepRemaining = minutes * 60
                            sleepDeadline = Date().addingTimeInterval(Double(minutes) * 60)
                        } else {
                            sleepMinutes = nil
                            sleepDeadline = nil
                            sleepRemaining = 0
                        }
                        Haptics.select()
                    },
                    onSubtitleLink: { value in
                        applySubtitleLink(value)
                    },
                    onNextSource: { playNextSource() },
                    canSwitchToSystem: CinemaxPrefs.systemReadable(currentURL),
                    onSystemPlayer: {
                        Haptics.select()
                        showOptions = false
                        pausedForSystem = playing
                        if playing {
                            playing = false
                            player?.pausePlayback()
                        }
                        // The system player takes the link from here, and this one
                        // waits underneath at the same second.
                        player?.refreshState()
                        let takeover = SystemTakeover(
                            id: currentURL,
                            title: currentTitle,
                            url: currentURL,
                            headers: currentHeaders,
                            videoId: currentVideoId,
                            videoLabel: currentVideoLabel,
                            item: item,
                            episodes: episodes,
                            type: type,
                            sources: pool
                        )
                        // This panel is a sheet on its way out: the player waits for
                        // it rather than losing the presentation to it.
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
                            systemTakeover = takeover
                        }
                    },
                    onAddonSubtitle: { choice in
                        applySubtitleLink(choice.url)
                    },
                    onDownload: {
                        Haptics.tap()
                        DownloadManager.shared.start(
                            title: (item?.name ?? title) + " — " + currentURL.suffix(6),
                            urlString: currentURL,
                            headers: currentHeaders
                        )
                        showOptions = false
                    },
                    onCopyLink: {
                        Haptics.select()
                        UIPasteboard.general.string = currentURL
                    },
                    onMarkWatched: {
                        Haptics.tap()
                        if let item {
                            let minutes: Int64 = 100 * 60 * 1000
                            CinemaAPI.shared.saveProgress(
                                item: item,
                                positionMs: minutes,
                                durationMs: minutes,
                                videoId: currentVideoId,
                                videoLabel: currentVideoLabel
                            )
                            Task { await LibraryModel.shared.load() }
                        }
                        resumeHint = "عُلم كمكتمل"
                        showOptions = false
                    }
                )
                .presentationDetents([.medium, .large])
                }
        }
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showEpisodes) {
            PlayerEpisodeSheet(
                episodes: episodes,
                currentVideoId: currentVideoId,
                watchedLabels: watchedEpisodeLabels,
                onPick: { episode in
                    showEpisodes = false
                    play(episode: episode)
                },
                onClose: { showEpisodes = false }
            )
            .presentationDetents([.medium, .large])
        }
        .alert("تعذّر جلب مصدر لهذه الحلقة", isPresented: $episodeError) {
            Button("حسنًا", role: .cancel) {}
        }
        .alert("رابط الترجمة غير صالح", isPresented: $subtitleError) {
            Button("حسنًا", role: .cancel) {}
        } message: {
            Text("الصق رابطًا كاملًا يبدأ بـ https و ينتهي بملف .srt أو .vtt")
        }
        .alert("تشخيص المشغل", isPresented: Binding(
            get: { diagnosticsText != nil },
            set: { if !$0 { diagnosticsText = nil } }
        )) {
            Button("نسخ") {
                UIPasteboard.general.string = diagnosticsText ?? ""
                diagnosticsText = nil
            }
            Button("إغلاق", role: .cancel) { diagnosticsText = nil }
        } message: {
            Text(diagnosticsText ?? "")
        }
        .fullScreenCover(item: $systemTakeover, onDismiss: {
            systemTakeover = nil
            // Only a film that was still running goes on; a finished one or a
            // deliberate pause stays where it is.
            if pausedForSystem {
                pausedForSystem = false
                if duration > 0, position < duration - 2 {
                    playing = true
                    player?.playPlayback()
                }
            }
        }) { takeover in
            SystemPlayerScreen(
                title: takeover.title,
                url: takeover.url,
                headers: takeover.headers,
                item: takeover.item,
                videoId: takeover.videoId,
                videoLabel: takeover.videoLabel,
                episodes: takeover.episodes,
                type: takeover.type,
                sources: takeover.sources
            )
        }
        .task {
            // The task runs again when the app comes back from the background,
            // and starting again reopened the film from its resume point; the
            // screen starts once.
            guard !started else { return }
            started = true
            UIApplication.shared.isIdleTimerDisabled = true
            try? AVAudioSession.sharedInstance().setActive(true)
            OrientationManager.shared.setPlayerRotation(CinemaxPrefs.allowRotation)
            if sources.isEmpty {
                currentURL = url
                currentHeaders = headers
            } else {
                pool = sources
                poolIndex = sources.firstIndex { $0.url == url } ?? 0
                currentURL = sources[poolIndex].url
                currentHeaders = sources[poolIndex].headers ?? headers
            }
            currentTitle = title
            currentVideoId = videoId
            currentVideoLabel = videoLabel
            speed = rememberedSpeed(for: videoId ?? item?.id) ?? CinemaxPrefs.defaultSpeed
            savedBrightness = screenBrightness
            brightness = savedBrightness
            if let item, CinemaxPrefs.autoResume {
                startMs = await CinemaAPI.shared.resume(itemId: item.id, videoId: videoId)
                if startMs > 30_000 {
                    resumeHint = "استؤنف من \(clock(Double(startMs) / 1000))"
                }
            }
            ready = true
            startTicker()
            applyRememberedSpeed()
        }
        .task(id: currentVideoId) {
            // Warms the next episode's links so the roll-over is instant.
            guard let next = upNext else { return }
            _ = try? await CinemaAPI.shared.streamsReport(for: next.id, type: type)
        }
        .onDisappear {
            ticker?.invalidate()
            saveProgress()
            applyBrightness(savedBrightness)
            UIApplication.shared.isIdleTimerDisabled = false
            OrientationManager.shared.setPlayerRotation(false)
            try? AVAudioSession.sharedInstance().setActive(
                false,
                options: .notifyOthersOnDeactivation
            )
        }
        .onChange(of: duration) { value in
            // The track list is only reliable once the file is demuxed.
            if value > 0 {
                if subtitleTracks.isEmpty, audioTracks.isEmpty { loadTracks() }
                if speed != 1 { player?.setSpeed(speed) }
            }
        }
        .task(id: controlsVisible) {
            // The controls step aside after a few quiet seconds while playing.
            guard controlsVisible, CinemaxPrefs.autoHideControls else { return }
            try? await Task.sleep(nanoseconds: 4_500_000_000)
            guard !Task.isCancelled, playing, !showOptions else { return }
            withAnimation(.easeInOut(duration: 0.2)) { controlsVisible = false }
        }
    }

    private func loadTracks() {
        guard let player else { return }
        let decoder = JSONDecoder()
        subtitleTracks = (try? decoder.decode(
            [TrackInfo].self,
            from: Data(player.embeddedSubtitleTracksJson().utf8)
        )) ?? []
        audioTracks = (try? decoder.decode(
            [TrackInfo].self,
            from: Data(player.embeddedAudioTracksJson().utf8)
        )) ?? []
        applySubtitlePreferences()
    }

    /// Applies the subtitle language and size the viewer picked in settings.
    private func applySubtitlePreferences() {
        guard let player else { return }
        let defaults = UserDefaults.standard
        let language = defaults.string(forKey: "cinemax.subtitleLang") ?? "ar"
        let size = defaults.string(forKey: "cinemax.subtitleSize") ?? "medium"
        let auto = (defaults.object(forKey: "cinemax.subtitleAuto") as? Bool) ?? true

        let fontSize: Int
        switch size {
        case "small": fontSize = 42
        case "large": fontSize = 66
        default: fontSize = 54
        }
        player.setSubtitleFontSize(fontSize)

        guard auto, language != "off", !subtitlesOff else { return }
        guard let match = subtitleTracks.first(where: {
            $0.language.lowercased().hasPrefix(language)
        }) else { return }
        player.setSubtitleTrack(match.id, ordinal: Int32(match.index))
    }

    /// Writes the stop position back, so "أكمل المشاهدة" and resume stay accurate
    /// even when playback happened in this interface rather than the classic one.
    private func saveProgress() {
        guard let item, duration > 5 else { return }
        CinemaAPI.shared.saveProgress(
            item: item,
            positionMs: Int64(position * 1000),
            durationMs: Int64(duration * 1000),
            videoId: currentVideoId,
            videoLabel: currentVideoLabel
        )
    }

    /// Rolls into the next episode: asks the addons for its best link and rebuilds
    /// the surface with it. Returns false when there is nothing after this one.
    @discardableResult
    private func playNext() -> Bool {
        guard let next = upNext else { return false }
        return play(episode: next, closeOnFailure: true)
    }

    /// Switches to any episode of this series, whether the viewer picked it from
    /// the list or the player rolled on by itself.
    @discardableResult
    private func play(episode next: CinemaItem, closeOnFailure: Bool = false) -> Bool {
        saveProgress()
        switching = true
        ticker?.invalidate()
        Task {
            let streams = (try? await CinemaAPI.shared.streams(for: next.id, type: type)) ?? []
            // A link that belongs to another app cannot play here, so the roll
            // opens the first link this player can read instead of whatever the
            // addons answered with first.
            guard let best = streams.first(where: { $0.externalUrl == nil }) else {
                switching = false
                if closeOnFailure { dismiss() } else { episodeError = true }
                return
            }
            position = 0
            duration = 0
            startMs = 0
            upNextCountdown = nil
            autoplayCancelled = false
            currentVideoId = next.id
            currentVideoLabel = next.name
            currentTitle = next.name
            currentURL = best.url
            currentHeaders = best.headers ?? [:]
            ready = true
            switching = false
            player = nil
            // The next episode is another file with its own tracks, so the
            // subtitle choice and the panel's volume are applied to it again.
            subtitleTracks = []
            audioTracks = []
            appliedInitial = false
            startTicker()
            applyRememberedSpeed()
        }
        return true
    }

    /// The small action chips that float above the controls.
    private func chip(_ title: String, system: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: system)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .background(.ultraThinMaterial, in: Capsule())
                .overlay(Capsule().strokeBorder(CinemaTheme.hairline, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .transition(.opacity)
    }

    private var screenBrightness: Double {
        guard let screen = (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.screen else { return 0.6 }
        return Double(screen.brightness)
    }

    /// Dims the whole screen for a dark room, and puts it back when the player closes.
    private func applyBrightness(_ value: Double) {
        (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.screen.brightness = CGFloat(value)
    }

    private func startTicker() {
        ticker?.invalidate()
        ticker = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
            Task { @MainActor in
                tickSleepTimer()
                guard let player else { return }
                player.refreshState()
                // A link that never produced a duration is the one case worth
                // reporting: the next source is tried on its own a couple of times
                // first, then the viewer gets the error and the choice.
                if player.durationMs == 0, player.errorMessage.isEmpty {
                    // A source that says nothing at all still has to be stopped:
                    // twenty seconds without a duration means it is not coming.
                    loadTicks += 1
                    if loadTicks > 40, playerError == nil {
                        playerError = "لم يبدأ هذا المصدر بالبث خلال ٢٠ ثانية"
                    }
                } else {
                    loadTicks = 0
                }
                if player.durationMs == 0, !player.errorMessage.isEmpty {
                    if pool.count > 1, autoTries < min(pool.count - 1, 3) {
                        autoTries += 1
                        playNextSource()
                        return
                    }
                    playerError = player.errorMessage
                }
                if !scrubbing {
                    position = Double(player.positionMs) / 1000
                }
                // The place the viewer reached is written every half minute while
                // the film plays, so a call, a crash or the app being closed never
                // loses it: before this it was only written on the way out.
                saveTicks += 1
                if saveTicks >= 60 {
                    saveTicks = 0
                    saveProgress()
                }
                if !appliedInitial {
                    // The panel's choices come back the way they were left.
                    player.setVolumeLevel(volume)
                    player.setVideoAspect(aspect)
                    player.setVideoZoom(zoom)
                    player.setSubtitlePosition(subtitlePosition)
                    appliedInitial = true
                }
                buffering = player.buffering
                // A source that keeps starving is swapped for the next one instead
                // of leaving the viewer watching a spinner.
                if buffering, duration > 1, pool.count > 1, !scrubbing {
                    bufferTicks += 1
                    if bufferTicks > 24, autoTries < 3 {
                        autoTries += 1
                        bufferTicks = 0
                        playNextSource()
                        return
                    }
                } else {
                    bufferTicks = 0
                }
                duration = max(1, Double(player.durationMs) / 1000)
                if autoSkipIntro, !didAutoSkipIntro, duration > 300, position > 25, position < 80 {
                    didAutoSkipIntro = true
                    position = min(85, duration - 2)
                    player.seekTo(Int64(position * 1000))
                }
                if player.ended {
                    saveProgress()
                    if autoplayNext, !autoplayCancelled, playNext() { return }
                    dismiss()
                }
                // The next episode is offered before this one ends, with a count
                // the viewer can stop: a roll-over that happens while they are
                // still watching must ask, never take the picture.
                if upNext != nil, duration > 60, position > duration - 32, !autoplayCancelled {
                    upNextCountdown = min(20, max(0, Int((duration - position).rounded())))
                } else if upNextCountdown != nil {
                    upNextCountdown = nil
                }
            }
        }
    }

    private var currentSourceLabel: String? {
        guard pool.indices.contains(poolIndex) else { return sourceNote }
        let choice = pool[poolIndex]
        let parts = [choice.addon, choice.quality].compactMap { $0 }
        return parts.isEmpty ? sourceNote : parts.joined(separator: " · ")
    }

    /// Asks the installed subtitle addons for this exact video.
    private func loadAddonSubtitles() {
        guard let id = currentVideoId ?? item?.id else { return }
        loadingSubtitles = true
        let kind = type
        Task {
            addonSubtitles = await CinemaAPI.shared.subtitles(itemId: id, type: kind)
            loadingSubtitles = false
        }
    }

    /// Rebuilds the surface for the same link, for a stream that stalled on open.
    private func retryPlayback() {
        playerError = nil
        autoTries = 0
        loadTicks = 0
        playToken += 1
        Haptics.tap()
    }

    /// Steps to the next source in the same list, the quickest cure for a dead link.
    private func playNextSource() {
        guard pool.count > 1 else { return }
        saveProgress()
        poolIndex = (poolIndex + 1) % pool.count
        playerError = nil
        autoTries = 0
        loadTicks = 0
        didAutoSkipIntro = false
        // The new file has tracks of its own, and the surface behind it is a new
        // player: the subtitle choice, the volume and the picture's shape are
        // applied to it again instead of being carried over from the dead link.
        subtitleTracks = []
        audioTracks = []
        appliedInitial = false
        // The new link opens where the dead one stopped, a second before it, so a
        // swap in the middle of a film never sends the viewer back to the start.
        startMs = position > 5 ? Int64((position - 1) * 1000) : 0
        position = 0
        duration = 0
        upNextCountdown = nil
        autoplayCancelled = false
        currentURL = pool[poolIndex].url
        currentHeaders = pool[poolIndex].headers ?? [:]
        playToken += 1
        ticker?.invalidate()
        startTicker()
        Haptics.tap()
    }

    /// Loads a subtitle the viewer found elsewhere. libmpv fetches http links itself,
    /// so nothing has to sit in memory first.
    private func applySubtitleLink(_ raw: String) {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else {
            player?.setSubtitle(nil)
            externalSubtitle = nil
            Haptics.select()
            return
        }
        guard let url = URL(string: text), url.scheme != nil else {
            subtitleError = true
            return
        }
        player?.setSubtitle(text)
        externalSubtitle = url.lastPathComponent.isEmpty ? text : url.lastPathComponent
        Haptics.select()
        showOptions = false
    }

    /// Counts the sleep timer down and stops playback the moment it runs out, so
    /// the viewer can doze off without the episode rolling on all night.
    private func tickSleepTimer() {
        guard let deadline = sleepDeadline else { return }
        let left = deadline.timeIntervalSinceNow
        guard left > 0 else {
            sleepDeadline = nil
            sleepMinutes = nil
            sleepRemaining = 0
            playing = false
            player?.pausePlayback()
            Haptics.tap()
            return
        }
        sleepRemaining = Int(left.rounded(.up))
    }

    private func seek(by seconds: Double) {
        guard duration > 0 else { return }
        position = min(max(0, position + seconds), max(0, duration - 0.5))
        player?.seekTo(Int64(position * 1000))
    }

    /// The wall clock time this file will end at, shown beside the remaining time.
    private var finishClock: String {
        guard duration > 0 else { return "--:--" }
        let end = Date().addingTimeInterval(max(0, duration - position))
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: end)
    }

    private func clock(_ seconds: Double) -> String {
        let total = Int(seconds.rounded())
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let secs = total % 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, secs)
            : String(format: "%d:%02d", minutes, secs)
    }
}

/// Picture in Picture for the system player. Apple's own control bar is turned
/// off, so the button that starts it belongs to the app, and it is built from an
/// invisible layer of the very same player: that is what gives the promised PiP
/// back without bringing back the bar that covered the title.
@MainActor
final class CinemaxPictureInPicture: NSObject, AVPictureInPictureControllerDelegate {
    static let shared = CinemaxPictureInPicture()

    private var controller: AVPictureInPictureController?
    private var layer: AVPlayerLayer?

    var isPossible: Bool { controller?.isPictureInPicturePossible ?? false }

    /// Attaches a layer of this player to the tree, which is what PiP draws from.
    func attach(_ player: AVPlayer) {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        let layer = AVPlayerLayer(player: player)
        layer.frame = CGRect(x: 0, y: 0, width: 2, height: 2)
        layer.opacity = 0
        self.layer = layer
        let pip = AVPictureInPictureController(playerLayer: layer)
        pip?.delegate = self
        pip?.canStartPictureInPictureAutomaticallyFromInline = true
        controller = pip
        // The layer lives in the key window, far from the controls but in a view
        // that is on screen, since PiP ignores a layer outside a window.
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }?
            .keyWindow?
            .addSubview(Self.host(layer))
    }

    /// Puts the layer into a view so it is a visible part of the window.
    private static func host(_ layer: AVPlayerLayer) -> UIView {
        let holder = UIView(frame: CGRect(x: 0, y: 0, width: 2, height: 2))
        holder.isUserInteractionEnabled = false
        holder.addSubview(layerView(layer))
        return holder
    }

    private static func layerView(_ layer: AVPlayerLayer) -> UIView {
        let view = UIView(frame: layer.frame)
        view.layer.addSublayer(layer)
        return view
    }

    /// Starts PiP, only when the viewer asked for it and it is possible now.
    func start() {
        guard let controller, controller.isPictureInPicturePossible else { return }
        controller.startPictureInPicture()
    }

    func pictureInPictureController(
        _ pictureInPictureController: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
    ) {
        // The player is still on screen underneath, so the restore finishes at
        // once instead of leaving the system animation waiting.
        completionHandler(true)
    }
}

/// A link handed from the libmpv player to the system player, so Picture in
/// Picture and AirPlay work without leaving the film behind.
struct SystemTakeover: Identifiable {
    let id: String
    let title: String
    let url: String
    let headers: [String: String]
    let videoId: String?
    let videoLabel: String?
    let item: CinemaItem?
    let episodes: [CinemaItem]
    let type: String
    /// The whole source list, so the system player replaces a dead link instead
    /// of giving up on the film when the first host fails.
    let sources: [StreamChoice]
}

/// The system player: AVPlayer with Apple's own controls, AirPlay and Picture in
/// Picture. It plays HLS and MP4 natively and leaves odd containers to libmpv.
struct SystemPlayerScreen: View {
    let title: String
    let url: String
    var headers: [String: String] = [:]
    var item: CinemaItem? = nil
    var videoId: String? = nil
    var videoLabel: String? = nil
    /// The whole episode list, so the player rolls into the next one by itself
    /// instead of stopping and leaving the viewer to find it.
    var episodes: [CinemaItem] = []
    var type: String = "series"
    /// Every link the detail page found, so a dead host is replaced here instead
    /// of the film stopping on the first failure.
    var sources: [StreamChoice] = []

    @Environment(\.dismiss) private var dismiss
    @State private var player = AVPlayer()
    @State private var startMs: Int64 = 0
    @State private var ready = false
    @State private var position: Double = 0
    @State private var duration: Double = 0
    @State private var errorText: String?
    @State private var ticker: Timer?
    @State private var finished = false
    /// What is really on screen now: a roll-over replaces it without closing.
    @State private var stageTitle = ""
    @State private var stageURL = ""
    @State private var stageHeaders: [String: String] = [:]
    @State private var stageVideoId: String?
    @State private var stageVideoLabel: String?
    @State private var switching = false
    /// True once this screen has started, so a return from the background never
    /// starts the film again.
    @State private var started = false
    @State private var nextNote: String?
    /// The links already tried, so a failure never sends the player back to the
    /// host that just died.
    @State private var attempts: Set<String> = []
    /// The links of the episode on screen, replaced on every roll-over so a
    /// failure never falls back to a source of another episode.
    @State private var pool: [StreamChoice] = []
    @State private var showOptions = false
    @State private var speed = 1.0
    /// Counts the half seconds between two resume points written while playing.
    @State private var saveTicks = 0
    /// Counts the half seconds a link has gone without a duration, so a source
    /// that never opens is swapped instead of leaving a black screen.
    @State private var stalledTicks = 0
    @State private var sleepMinutes: Int?
    @State private var sleepDeadline: Date?
    @State private var sleepRemaining = 0
    /// One sheet for the episode list and the card: two `.sheet` modifiers on the
    /// same screen compete for the presentation, and one never appears.
    @State private var sheet: SystemSheet?
    /// Whether the film is running, for the transport's own button, which is what
    /// the screen has now that Apple's control bar is turned off.
    @State private var isPlaying = false
    /// True while the viewer drags the timeline, so the poller leaves it alone.
    @State private var scrubbing = false
    @State private var scrubValue: Double = 0

    private enum SystemSheet: Identifiable {
        case episodes
        case card(ShareImage)

        var id: String {
            switch self {
            case .episodes: return "episodes"
            case .card(let entry): return "card:" + entry.id.uuidString
            }
        }
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if ready {
                SystemPlayerSurface(player: player)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .ignoresSafeArea()
                    .clipped()
            } else {
                ProgressView().tint(CinemaTheme.accent)
            }

            VStack {
                HStack(spacing: 12) {
                    CircleButton(system: "xmark") { dismiss() }
                    VStack(alignment: .leading, spacing: 1) {
                        Text(stageTitle.isEmpty ? title : stageTitle)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Text(attempts.isEmpty
                             ? "مشغل النظام · PiP وAirPlay"
                             : "مشغل النظام · المحاولة \(attempts.count)")
                            .font(.system(size: 10))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    Spacer()
                    if AVPictureInPictureController.isPictureInPictureSupported() {
                        // Apple's own bar is turned off, so Picture in Picture
                        // starts from this button on a layer of the same player.
                        CircleButton(system: "pip.enter") {
                            Haptics.tap()
                            CinemaxPictureInPicture.shared.start()
                        }
                    }
                    Button {
                        Haptics.tap()
                        showOptions = true
                    } label: {
                        Image(systemName: "slider.horizontal.3")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                    .buttonStyle(.plain)
                    if !episodes.isEmpty {
                        CircleButton(system: "list.bullet") {
                            Haptics.tap()
                            sheet = .episodes
                        }
                    }
                    if let next = upNext {
                        Button {
                            Haptics.tap()
                            roll(to: next)
                        } label: {
                            HStack(spacing: 5) {
                                Image(systemName: "forward.end.fill")
                                    .font(.system(size: 10, weight: .bold))
                                Text("التالي")
                                    .font(.system(size: 11, weight: .bold))
                            }
                            .foregroundStyle(.black)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(CinemaTheme.accent, in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                Spacer()
                if let nextNote {
                    Text(nextNote)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding(.bottom, 76)
                }
                if let errorText {
                    VStack(spacing: 10) {
                        Text("تعذّر تشغيل هذا المصدر في مشغل النظام")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                        Text(errorText)
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                            .multilineTextAlignment(.center)
                            .lineLimit(3)
                        Text("بدّل إلى مشغل libmpv من الإعدادات ← التشغيل.")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                        HStack(spacing: 10) {
                            Button("أعد المحاولة") {
                                Haptics.tap()
                                attempts.removeAll()
                                start(
                                    title: stageTitle,
                                    url: stageURL,
                                    headers: stageHeaders,
                                    videoId: stageVideoId,
                                    videoLabel: stageVideoLabel,
                                    startAt: 0
                                )
                            }
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 9)
                            .background(CinemaTheme.accent, in: Capsule())
                            .buttonStyle(.plain)
                            Button("إغلاق") { dismiss() }
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(CinemaTheme.muted)
                                .buttonStyle(.plain)
                        }
                    }
                    .padding(20)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .padding(.bottom, 60)
                }
            }
            // The transport and the timeline, in one glass card: Apple's own bar
            // is turned off, so these are the controls the screen has.
            VStack(spacing: 14) {
                Button {
                    Haptics.tap()
                    if player.rate > 0 {
                        player.pause()
                    } else {
                        player.play()
                    }
                } label: {
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 26, weight: .semibold))
                        .foregroundStyle(.black)
                        .frame(width: 66, height: 66)
                        .background(.white, in: Circle())
                        .shadow(color: .black.opacity(0.35), radius: 14, y: 6)
                }
                .buttonStyle(CinemaPressStyle())

                Slider(
                    value: Binding(
                        get: { scrubbing ? scrubValue : position },
                        set: { scrubValue = $0 }
                    ),
                    in: 0...max(1, duration),
                    onEditingChanged: { editing in
                        if editing {
                            scrubValue = position
                            scrubbing = true
                        } else {
                            position = scrubValue
                            player.seek(
                                to: CMTime(
                                    value: CMTimeValue(scrubValue * 1000),
                                    timescale: 1000
                                )
                            )
                            Task {
                                try? await Task.sleep(nanoseconds: 700_000_000)
                                scrubbing = false
                            }
                        }
                    }
                )
                .tint(CinemaTheme.accent)

                HStack {
                    Text(clockLabel(scrubbing ? scrubValue : position))
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.white)
                        .monospacedDigit()
                    Spacer()
                    if sleepDeadline != nil {
                        Label(clockLabel(Double(sleepRemaining)), systemImage: "moon.zzz.fill")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .monospacedDigit()
                    }
                    Text(clockLabel(duration))
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(CinemaTheme.muted)
                        .monospacedDigit()
                }
            }
            .padding(16)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
            )
            .padding(.horizontal, 18)
            .padding(.bottom, 14)
            if showOptions {
                SystemOptionsSheet(
                    title: stageTitle,
                    speed: speed,
                    sleepMinutes: sleepMinutes,
                    sleepRemaining: sleepRemaining,
                    canShareCard: item != nil,
                    onSpeed: { value in
                        speed = value
                        saveSpeed(value)
                        player.defaultRate = Float(value)
                        if player.rate > 0 { player.rate = Float(value) }
                        Haptics.select()
                    },
                    onSleep: { minutes in setSleep(minutes) },
                    onCard: {
                        showOptions = false
                        buildCard()
                    },
                    onClose: { showOptions = false }
                )
            }
        }
        .preferredColorScheme(.dark)
        .task {
            // The task runs again when the app comes back from the background,
            // and starting again reopened the film from its resume point; the
            // screen starts once.
            guard !started else { return }
            started = true
            UIApplication.shared.isIdleTimerDisabled = true
            try? AVAudioSession.sharedInstance().setActive(true)
            OrientationManager.shared.setPlayerRotation(CinemaxPrefs.allowRotation)
            var beginAt: Int64 = 0
            if let item, CinemaxPrefs.autoResume {
                beginAt = await CinemaAPI.shared.resume(itemId: item.id, videoId: videoId)
            }
            pool = sources
            start(
                title: title,
                url: url,
                headers: headers,
                videoId: videoId,
                videoLabel: videoLabel,
                startAt: beginAt
            )
        }
        .onDisappear {
            ticker?.invalidate()
            UIApplication.shared.isIdleTimerDisabled = false
            OrientationManager.shared.setPlayerRotation(false)
            try? AVAudioSession.sharedInstance().setActive(
                false,
                options: .notifyOthersOnDeactivation
            )
            saveProgress()
        }
        .sheet(item: $sheet) { value in
            switch value {
            case .episodes:
                PlayerEpisodeSheet(
                    episodes: episodes,
                    currentVideoId: stageVideoId,
                    watchedLabels: watchedLabels,
                    onPick: { entry in
                        sheet = nil
                        roll(to: entry)
                    },
                    onClose: { sheet = nil }
                )
                .presentationDetents([.medium, .large])
            case .card(let entry):
                ShareSheet(items: [entry.image])
            }
        }
    }

    /// A time as a viewer reads it, for the timeline in the system player.
    private func clockLabel(_ seconds: Double) -> String {
        let total = Int(max(0, seconds).rounded())
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let secs = total % 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, secs)
            : String(format: "%d:%02d", minutes, secs)
    }

    /// Points the screen at one episode and plays it from the start, whether it is
    /// the first one or the one that follows it.
    private func start(
        title: String,
        url: String,
        headers: [String: String],
        videoId: String?,
        videoLabel: String?,
        startAt: Int64
    ) {
        stageTitle = title
        stageURL = url
        stageHeaders = headers
        stageVideoId = videoId
        stageVideoLabel = videoLabel
        startMs = max(0, startAt)
        finished = false
        errorText = nil
        duration = 0
        position = 0
        ready = false
        speed = rememberedSpeed(for: videoId ?? item?.id) ?? CinemaxPrefs.defaultSpeed
        prepare()
        startTicker()
    }

    /// The episode after the one on screen, when this title has one.
    private var upNext: CinemaItem? {
        guard let stageVideoId,
              let index = episodes.firstIndex(where: { $0.id == stageVideoId }),
              index + 1 < episodes.count else { return nil }
        return episodes[index + 1]
    }

    private var autoplayNext: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.autoplayNext") as? Bool) ?? true
    }

    /// The next link this engine can read, skipping the ones already tried so a
    /// failure never sends the player back to the host that just died.
    private var nextSource: StreamChoice? {
        let candidates = pool.filter {
            $0.externalUrl == nil && !attempts.contains($0.url) && $0.url != stageURL
        }
        return candidates.first { CinemaxPrefs.systemReadable($0.url) }
    }

    /// Replaces a dead link with the next source, and only shows the error when
    /// nothing is left to try.
    private func tryFailover() {
        guard !switching else { return }
        attempts.insert(stageURL)
        guard let next = nextSource else {
            errorText = player.currentItem?.error?.localizedDescription ?? "فشل تحميل المصدر"
            EventLog.shared.add("player", "لا مصدر آخر يعمل لهذا العنوان")
            return
        }
        attempts.insert(next.url)
        EventLog.shared.add("player", "فشل المصدر · تجربة \(next.addon)")
        // The replacement opens where the dead link stopped instead of starting
        // the episode again, which is what makes a swap usable mid-film.
        let keep = position > 5 ? Int64((position - 1) * 1000) : 0
        start(
            title: stageTitle,
            url: next.url,
            headers: next.headers ?? [:],
            videoId: stageVideoId,
            videoLabel: stageVideoLabel,
            startAt: keep
        )
    }

    /// Episodes already watched to the end, for the list's ticks.
    private var watchedLabels: Set<String> {
        guard let item else { return [] }
        return Set(
            LibraryModel.shared.progress
                .filter { $0.item.id == item.id && $0.fraction >= 0.9 }
                .compactMap(\.episodeLabel)
        )
    }

    /// Writes the sleep timer's end, or clears it.
    private func setSleep(_ minutes: Int?) {
        Haptics.select()
        sleepMinutes = minutes
        if let minutes {
            sleepDeadline = Date().addingTimeInterval(Double(minutes) * 60)
            sleepRemaining = minutes * 60
        } else {
            sleepDeadline = nil
            sleepRemaining = 0
        }
    }

    /// The speed this title was last watched at, kept per id the way libmpv's
    /// player keeps it, so a series holds one speed across its episodes.
    private func rememberedSpeed(for id: String?) -> Double? {
        guard let id else { return nil }
        let value = UserDefaults.standard.double(forKey: "cinemax.speed." + id)
        return value > 0 ? value : nil
    }

    private func saveSpeed(_ value: Double) {
        guard let id = stageVideoId ?? item?.id else { return }
        UserDefaults.standard.set(value, forKey: "cinemax.speed." + id)
    }

    /// Draws this title as a card and hands it to the share sheet without
    /// leaving the film.
    private func buildCard() {
        guard let target = item else { return }
        Task { @MainActor in
            guard let image = await CinemaCards.title(target) else {
                nextNote = "تعذّر إنشاء البطاقة"
                return
            }
            sheet = .card(ShareImage(image: image))
            EventLog.shared.add("card", "بطاقة \(target.name)")
        }
    }

    /// Rolls into the next episode: asks the addons for a link, picks the one this
    /// engine can read, and swaps the player without closing the screen.
    private func roll(to next: CinemaItem) {
        guard !switching else { return }
        switching = true
        nextNote = "جارٍ جلب مصدر \(next.name)…"
        let list = episodes
        Task {
            let streams = await CinemaAPI.shared.freshStreams(for: next.id, type: type)
            let playable = streams.filter { $0.externalUrl == nil }
            // The next file opens best in the engine that reads its container, and
            // the system player only takes what it plays natively.
            // The next file must be one this engine reads: a container only
            // libmpv understands would open a player that fails at once.
            let chosen = playable.first { CinemaxPrefs.systemReadable($0.url) }
            await MainActor.run {
                switching = false
                nextNote = nil
                guard let chosen else {
                    // The next episode is a container this engine cannot read: the
                    // note says so instead of opening a player that will fail.
                    nextNote = "الحلقة التالية بصيغة لا يقرأها هذا المشغل — افتحها في libmpv"
                    return
                }
                EventLog.shared.add("next", "\(next.name) · \(chosen.addon)")
                // The new episode's own links replace the old list, so a failure
                // here never falls back to a source of the episode before it.
                pool = playable
                attempts = [chosen.url]
                start(
                    title: next.name,
                    url: chosen.url,
                    headers: chosen.headers ?? [:],
                    videoId: next.id,
                    videoLabel: next.name,
                    startAt: 0
                )
                if list.count > 1 { Haptics.select() }
            }
        }
    }

    private func prepare() {
        let link: URL
        if stageURL.hasPrefix("/") {
            link = URL(fileURLWithPath: stageURL)
        } else if let remote = URL(string: stageURL) {
            link = remote
        } else {
            errorText = "الرابط غير صالح"
            return
        }
        let asset = stageHeaders.isEmpty
            ? AVURLAsset(url: link)
            : AVURLAsset(url: link, options: ["AVURLAssetHTTPHeaderFieldsKey": stageHeaders])
        let fresh = AVPlayer(playerItem: AVPlayerItem(asset: asset))
        fresh.allowsExternalPlayback = true
        if #available(iOS 15.0, *) {
            fresh.audiovisualBackgroundPlaybackPolicy = .continuesIfPossible
        }
        player = fresh
        // Picture in Picture draws from a layer of this very player, and Apple's
        // own bar is turned off here, so the layer is kept ready for the app's
        // button: without it the promised PiP had nothing to start from.
        CinemaxPictureInPicture.shared.attach(fresh)
        if startMs > 0 {
            fresh.seek(to: CMTime(value: startMs, timescale: 1000))
        }
        fresh.play()
        // The speed this title was last watched at follows the film here too, and
        // a paused player keeps it as its default for the next press of play.
        fresh.defaultRate = Float(speed)
        if abs(speed - 1) > 0.01 { fresh.rate = Float(speed) }
        ready = true
    }

    private func startTicker() {
        ticker?.invalidate()
        ticker = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
            Task { @MainActor in
                guard let current = player.currentItem else { return }
                let total = current.duration.seconds
                if total.isFinite, total > 0 { duration = total }
                let now = player.currentTime().seconds
                if now.isFinite { position = now }
                isPlaying = player.rate > 0
                if player.currentItem?.duration.seconds.isFinite == true { duration = player.currentItem?.duration.seconds ?? duration }
                // A link that never produces a duration is the black screen the
                // viewer stared at: after twenty-four seconds it is swapped for
                // the next source instead of being waited on forever.
                if duration <= 1, errorText == nil, !switching {
                    stalledTicks += 1
                    if stalledTicks >= 48 {
                        stalledTicks = 0
                        EventLog.shared.add("player", "مصدر بلا صورة · تجربة التالي")
                        tryFailover()
                    }
                } else {
                    stalledTicks = 0
                }
                // The resume point is written while the film plays as well as on
                // the way out, so a call or a crash mid-episode keeps the place.
                saveTicks += 1
                if saveTicks >= 60 {
                    saveTicks = 0
                    saveProgress()
                }
                if current.status == .failed, errorText == nil {
                    // A host that died is not the end of the film: the next link
                    // that this engine can read takes its place.
                    tryFailover()
                }
                // The sleep timer counts down on the same tick, so it needs no
                // timer of its own and stops the film while the screen is open.
                if let deadline = sleepDeadline {
                    let left = deadline.timeIntervalSinceNow
                    sleepRemaining = max(0, Int(left.rounded(.up)))
                    if left <= 0 {
                        sleepDeadline = nil
                        sleepMinutes = nil
                        sleepRemaining = 0
                        player.pause()
                        nextNote = "توقف بمؤقت النوم"
                    }
                }
                if duration > 1, position >= duration - 1.5, !finished {
                    finished = true
                    saveProgress()
                    // A series rolls into the next episode by itself; a film, and a
                    // series the viewer turned off, closes the screen.
                    if autoplayNext, let next = upNext {
                        roll(to: next)
                    } else {
                        dismiss()
                    }
                }
            }
        }
    }

    private func saveProgress() {
        guard let item, duration > 0 else { return }
        CinemaAPI.shared.saveProgress(
            item: item,
            positionMs: Int64(position * 1000),
            durationMs: Int64(duration * 1000),
            videoId: stageVideoId,
            videoLabel: stageVideoLabel
        )
    }
}

struct SystemPlayerSurface: UIViewControllerRepresentable {
    let player: AVPlayer

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        // The app draws its own header, scrubber and panels, so Apple's bar is
        // turned off: with both on, the title and the AirPlay control sat on top
        // of each other and half the words were unreadable.
        controller.showsPlaybackControls = false
        // The picture is letterboxed inside the screen in both orientations. It
        // is set here rather than left to the default so a player kept from an
        // earlier screen can never come back filling the page.
        controller.videoGravity = .resizeAspect
        controller.allowsPictureInPicturePlayback = true
        controller.canStartPictureInPictureAutomaticallyFromInline = true
        controller.entersFullScreenWhenPlaybackBegins = false
        return controller
    }

    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {
        if controller.player !== player {
            controller.player = player
        }
        if controller.videoGravity != .resizeAspect {
            controller.videoGravity = .resizeAspect
        }
    }
}

/// Hands the link to another installed app through the system share sheet.
struct ExternalPlayerScreen: View {
    let title: String
    let url: String

    @Environment(\.dismiss) private var dismiss
    @State private var sharing = false

    var body: some View {
        ZStack {
            CinemaTheme.canvas.ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "play.rectangle.on.rectangle")
                    .font(.system(size: 34, weight: .light))
                    .foregroundStyle(CinemaTheme.accent)
                Text(title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                Text("المشغل الخارجي: تُمرَّر هذه الرابط إلى تطبيق آخر (VLC أو Infuse أو أي مشغل مثبّت) عبر ورقة المشاركة.")
                    .font(.system(size: 12))
                    .foregroundStyle(CinemaTheme.muted)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                Button {
                    Haptics.tap()
                    sharing = true
                } label: {
                    Text("اختر تطبيقًا")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.black)
                        .padding(.horizontal, 22)
                        .padding(.vertical, 12)
                        .background(CinemaTheme.accent, in: Capsule())
                }
                .buttonStyle(CinemaPressStyle())
                Button("إغلاق") { dismiss() }
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)
                    .buttonStyle(.plain)
            }
            .padding(24)
        }
        .preferredColorScheme(.dark)
        .task { OrientationManager.shared.setPlayerRotation(CinemaxPrefs.allowRotation) }
        .onDisappear { OrientationManager.shared.setPlayerRotation(false) }
        .sheet(isPresented: $sharing) {
            if let link = URL(string: url) {
                ShareSheet(items: [link])
            }
        }
    }
}

/// The player's own control panel: subtitle and audio tracks packed in the file,
/// plus playback speed. Everything here talks straight to libmpv.
struct PlayerOptionsPanel: View {
    let subtitleTracks: [TrackInfo]
    let audioTracks: [TrackInfo]
    let speed: Double
    let subtitlesOff: Bool
    let subtitleDelay: Double
    let subtitleSize: String
    let externalSubtitle: String?
    let sourceLabel: String?
    let canSwitchSource: Bool
    let addonSubtitles: [SubtitleChoice]
    let loadingSubtitles: Bool
    let volume: Double
    let brightness: Double
    let aspect: String
    let zoom: Double
    let subtitlePosition: Double
    let rotationAllowed: Bool
    let sleepMinutes: Int?
    let sleepRemaining: Int

    let onClose: () -> Void
    let onSubtitle: (TrackInfo?) -> Void
    let onAudio: (TrackInfo) -> Void
    let onSpeed: (Double) -> Void
    let onSubtitleDelay: (Double) -> Void
    let onSubtitleSize: (String) -> Void
    let onVolume: (Double) -> Void
    let onBrightness: (Double) -> Void
    let onAspect: (String) -> Void
    let onZoom: (Double) -> Void
    let onSubtitlePosition: (Double) -> Void
    let onRotate: (Bool) -> Void
    let onSleep: (Int?) -> Void
    let onSubtitleLink: (String) -> Void
    let onNextSource: () -> Void
    /// Only offered when the link is one the system player can read, where it
    /// brings Picture in Picture and AirPlay.
    let canSwitchToSystem: Bool
    let onSystemPlayer: () -> Void
    let onAddonSubtitle: (SubtitleChoice) -> Void
    let onDownload: () -> Void
    let onCopyLink: () -> Void
    let onMarkWatched: () -> Void

    private let speeds: [Double] = [0.5, 0.75, 1, 1.25, 1.5, 2]
    @State private var subtitleLink = ""
    private let subtitleSizes: [(String, String)] = [
        ("small", "صغير"), ("medium", "متوسط"), ("large", "كبير")
    ]
    private let aspects: [(String, String)] = [
        ("", "ملاءمة"), ("fill", "قص الحواف"), ("16:9", "16:9"), ("4:3", "4:3"), ("2.35:1", "2.35:1")
    ]

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture(perform: onClose)
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Text("خيارات التشغيل")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(.white)
                        Spacer()
                        CircleButton(system: "xmark") { onClose() }
                    }

                    Button {
                        onMarkWatched()
                    } label: {
                        Label("علّم هذا التشغيل كمكتمل", systemImage: "checkmark.circle")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(CinemaTheme.accent.opacity(0.12), in: Capsule())
                    }
                    .buttonStyle(.plain)

                    if !subtitleTracks.isEmpty {
                        label("الترجمة")
                        Button { onSubtitle(nil) } label: {
                            optionRow("بدون", selected: subtitlesOff)
                        }
                        .buttonStyle(.plain)
                        ForEach(subtitleTracks) { track in
                            Button { onSubtitle(track) } label: {
                                optionRow(track.label, selected: track.selected && !subtitlesOff)
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if !audioTracks.isEmpty {
                        label("الصوت")
                        ForEach(audioTracks) { track in
                            Button { onAudio(track) } label: {
                                optionRow(track.label, selected: track.selected)
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if loadingSubtitles {
                        HStack(spacing: 8) {
                            ProgressView().tint(CinemaTheme.accent)
                            Text("جارٍ جلب الترجمات من إضافاتك…")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    } else if !addonSubtitles.isEmpty {
                        label("ترجمات من إضافاتك (\(addonSubtitles.count))")
                        ForEach(addonSubtitles.prefix(12)) { choice in
                            Button { onAddonSubtitle(choice) } label: {
                                optionRow(
                                    "\(choice.label) · \(choice.addon)",
                                    selected: externalSubtitle == choice.label
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    label("سرعة التشغيل")
                    HStack(spacing: 8) {
                        ForEach(speeds, id: \.self) { value in
                            Button {
                                onSpeed(value)
                            } label: {
                                Text(value == 1 ? "عادي" : String(format: "%g×", value))
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(abs(speed - value) < 0.01 ? .black : .white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 7)
                                    .background {
                                        Capsule().fill(
                                            abs(speed - value) < 0.01
                                                ? CinemaTheme.accent
                                                : Color.white.opacity(0.1)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if subtitleTracks.isEmpty && audioTracks.isEmpty {
                        Text("هذا الملف لا يحتوي مسارات مدمجة.")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                    }

                    label("تأخير الترجمة")
                    HStack(spacing: 10) {
                        Button {
                            onSubtitleDelay(subtitleDelay - 0.5)
                        } label: {
                            Text("− 0.5")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(Color.white.opacity(0.1), in: Capsule())
                        }
                        .buttonStyle(.plain)
                        Text(String(format: "%+.1f ث", subtitleDelay))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .frame(minWidth: 62)
                        Button {
                            onSubtitleDelay(subtitleDelay + 0.5)
                        } label: {
                            Text("+ 0.5")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(Color.white.opacity(0.1), in: Capsule())
                        }
                        .buttonStyle(.plain)
                        Spacer()
                        Button("تصفير") { onSubtitleDelay(0) }
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                    }

                    label("حجم الترجمة")
                    HStack(spacing: 8) {
                        ForEach(subtitleSizes, id: \.0) { entry in
                            Button {
                                onSubtitleSize(entry.0)
                            } label: {
                                Text(entry.1)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(subtitleSize == entry.0 ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background {
                                        Capsule().fill(
                                            subtitleSize == entry.0
                                                ? CinemaTheme.accent
                                                : Color.white.opacity(0.1)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    label("ترجمة من رابط")
                    HStack(spacing: 8) {
                        Image(systemName: "link")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                        TextField("رابط ملف .srt أو .vtt", text: $subtitleLink)
                            .textFieldStyle(.plain)
                            .font(.system(size: 12))
                            .foregroundStyle(.white)
                            .keyboardType(.URL)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                        Button("تحميل") {
                            let text = subtitleLink.trimmingCharacters(in: .whitespacesAndNewlines)
                            subtitleLink = ""
                            onSubtitleLink(text)
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                        .disabled(subtitleLink.trimmingCharacters(in: .whitespaces).isEmpty)
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(CinemaTheme.elevated.opacity(0.7), in: Capsule())

                    if let externalSubtitle {
                        HStack(spacing: 8) {
                            Image(systemName: "captions.bubble.fill")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.accent)
                            Text(externalSubtitle)
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                                .lineLimit(1)
                            Spacer()
                            Button("إزالة") { onSubtitleLink("") }
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(CinemaTheme.favourite)
                                .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 12)
                    }

                    label("نسبة العرض")
                    HStack(spacing: 8) {
                        ForEach(aspects, id: \.0) { entry in
                            Button {
                                onAspect(entry.0)
                            } label: {
                                Text(entry.1)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(aspect == entry.0 ? .black : .white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 7)
                                    .background {
                                        Capsule().fill(aspect == entry.0 ? CinemaTheme.accent : Color.white.opacity(0.1))
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    // One tap puts the picture back: fit, no crop, no zoom, which
                    // is what a stretched frame after a rotation needs.
                    Button {
                        Haptics.select()
                        onAspect("")
                        onZoom(0)
                    } label: {
                        Label("إعادة ضبط الصورة", systemImage: "arrow.counterclockwise")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                    }
                    .buttonStyle(.plain)

                    label("تكبير الصورة")
                    HStack(spacing: 10) {
                        Button {
                            onZoom(max(-0.5, zoom - 0.05))
                        } label: {
                            Text("−")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 38, height: 32)
                                .background(Color.white.opacity(0.1), in: Capsule())
                        }
                        .buttonStyle(.plain)
                        Text("\(Int((1 + zoom) * 100))%")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .frame(minWidth: 54)
                            .monospacedDigit()
                        Button {
                            onZoom(min(0.5, zoom + 0.05))
                        } label: {
                            Text("+")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 38, height: 32)
                                .background(Color.white.opacity(0.1), in: Capsule())
                        }
                        .buttonStyle(.plain)
                        Spacer()
                        Button("ملاءمة") { onZoom(0) }
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                            .buttonStyle(.plain)
                    }

                    label("موضع الترجمة")
                    HStack(spacing: 10) {
                        Image(systemName: "arrow.up.and.down")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                        Slider(
                            value: Binding(get: { subtitlePosition }, set: { onSubtitlePosition($0) }),
                            in: 0...150
                        )
                        .tint(CinemaTheme.accent)
                        Text("\(Int(subtitlePosition))")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                            .frame(width: 34)
                            .monospacedDigit()
                    }

                    label("الصوت")
                    HStack(spacing: 10) {
                        Button {
                            onVolume(volume > 0 ? 0 : 100)
                        } label: {
                            Image(systemName: volume <= 0
                                  ? "speaker.slash.fill"
                                  : (volume > 120 ? "speaker.wave.3.fill" : "speaker.wave.1.fill"))
                                .font(.system(size: 13))
                                .foregroundStyle(volume <= 0 ? CinemaTheme.favourite : CinemaTheme.muted)
                                .frame(width: 28, height: 28)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        Slider(
                            value: Binding(get: { volume }, set: { onVolume($0) }),
                            in: 0...200
                        )
                        .tint(CinemaTheme.accent)
                        Text("\(Int(volume))%")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                            .frame(width: 40)
                    }

                    label("سطوع الشاشة")
                    HStack(spacing: 10) {
                        Image(systemName: "sun.max.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                        Slider(
                            value: Binding(get: { brightness }, set: { onBrightness($0) }),
                            in: 0.1...1
                        )
                        .tint(CinemaTheme.accent)
                        Text("\(Int(brightness * 100))%")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                            .frame(width: 40)
                    }

                    if let sourceLabel {
                        HStack(spacing: 8) {
                            Image(systemName: "antenna.radiowaves.left.and.right")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                            Text(sourceLabel)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(CinemaTheme.muted)
                                .lineLimit(1)
                            Spacer()
                            Button("تنزيل") { onDownload() }
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(CinemaTheme.muted)
                                .buttonStyle(.plain)
                            if canSwitchToSystem {
                                Button("PiP") { onSystemPlayer() }
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                                    .buttonStyle(.plain)
                            }
                            Button("نسخ") { onCopyLink() }
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(CinemaTheme.muted)
                                .buttonStyle(.plain)
                            if canSwitchSource {
                                Button("مصدر آخر") { onNextSource() }
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                                    .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                    }

                    if rotationAllowed {
                        label("اتجاه الشاشة")
                        HStack(spacing: 8) {
                            Button {
                                onRotate(true)
                            } label: {
                                Label("أفقي", systemImage: "rectangle.landscape.rotate")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(Color.white.opacity(0.1), in: Capsule())
                            }
                            .buttonStyle(.plain)
                            Button {
                                onRotate(false)
                            } label: {
                                Label("رأسي", systemImage: "rectangle.portrait.rotate")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(Color.white.opacity(0.1), in: Capsule())
                            }
                            .buttonStyle(.plain)
                            Spacer()
                            Text("أو دوّر الجهاز")
                                .font(.system(size: 10))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    }

                    HStack(spacing: 6) {
                        label("مؤقت النوم")
                        Spacer()
                        if sleepMinutes != nil {
                            Text("يتبقى \(remainingClock)")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(CinemaTheme.accent)
                                .monospacedDigit()
                        }
                    }
                    HStack(spacing: 8) {
                        ForEach([15, 30, 45, 60], id: \.self) { minutes in
                            Button {
                                onSleep(minutes)
                            } label: {
                                Text("\(minutes) د")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(sleepMinutes == minutes ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background {
                                        Capsule().fill(
                                            sleepMinutes == minutes ? CinemaTheme.accent : Color.white.opacity(0.1)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                        Button {
                            onSleep(nil)
                        } label: {
                            Text("إيقاف")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(sleepMinutes == nil ? CinemaTheme.accent : CinemaTheme.muted)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(20)
            }
            .frame(maxHeight: min(460, UIScreen.main.bounds.height * 0.82))
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
            )
            .padding(16)
        }
    }

    /// The time left on the sleep timer, as m:ss or h:mm:ss.
    private var remainingClock: String {
        let total = max(0, sleepRemaining)
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let secs = total % 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, secs)
            : String(format: "%d:%02d", minutes, secs)
    }

    private func label(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(CinemaTheme.muted)
    }

    private func optionRow(_ text: String, selected: Bool) -> some View {
        HStack {
            Text(text)
                .font(.system(size: 14, weight: selected ? .semibold : .regular))
                .foregroundStyle(selected ? CinemaTheme.accent : .white)
            Spacer()
            if selected {
                Image(systemName: "checkmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(CinemaTheme.accent)
            }
        }
        .padding(.vertical, 9)
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(CinemaTheme.elevated.opacity(0.6), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

/// The series' episodes inside the player, so a viewer can jump to another one
/// without leaving playback for the detail page.
struct PlayerEpisodeSheet: View {
    let episodes: [CinemaItem]
    let currentVideoId: String?
    var watchedLabels: Set<String> = []
    let onPick: (CinemaItem) -> Void
    let onClose: () -> Void

    @State private var query = ""

    private var visible: [CinemaItem] {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return episodes }
        return episodes.filter { $0.name.localizedCaseInsensitiveContains(text) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Text("الحلقات")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                Text("\(episodes.count)")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.white.opacity(0.08), in: Capsule())
                Spacer()
                CircleButton(system: "xmark") { onClose() }
            }
            .padding(.horizontal, 20)
            .padding(.top, 18)
            .padding(.bottom, 10)

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12))
                    .foregroundStyle(CinemaTheme.muted)
                TextField("ابحث عن حلقة", text: $query)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12))
                    .foregroundStyle(.white)
                    .autocorrectionDisabled()
                if !query.isEmpty {
                    Button {
                        query = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(CinemaTheme.surface, in: Capsule())
            .padding(.horizontal, 20)
            .padding(.bottom, 10)

            ScrollViewReader { proxy in
                ScrollView(showsIndicators: false) {
                    LazyVStack(spacing: 8) {
                        ForEach(visible) { episode in
                            Button {
                                Haptics.select()
                                onPick(episode)
                            } label: {
                                HStack(spacing: 10) {
                                    Text(episode.name)
                                        .font(.system(
                                            size: 14,
                                            weight: episode.id == currentVideoId ? .bold : .regular
                                        ))
                                        .foregroundStyle(episode.id == currentVideoId ? CinemaTheme.accent : .white)
                                        .lineLimit(2)
                                        .multilineTextAlignment(.leading)
                                    if watchedLabels.contains(episode.name) {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.system(size: 12))
                                            .foregroundStyle(CinemaTheme.accent.opacity(0.8))
                                    }
                                    Spacer(minLength: 8)
                                    if episode.id == currentVideoId {
                                        Image(systemName: "play.fill")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundStyle(CinemaTheme.accent)
                                    }
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(
                                    CinemaTheme.elevated.opacity(0.6),
                                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                                )
                            }
                            .buttonStyle(.plain)
                            .id(episode.id)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
                .onAppear {
                    // Opening on the episode that is playing saves a long scroll.
                    if let current = currentVideoId {
                        proxy.scrollTo(current, anchor: .center)
                    }
                }
            }
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }
}

// MARK: - Detail

@MainActor
final class DetailModel: ObservableObject {
    @Published var detail: DetailPayload?
    @Published var loading = true
    @Published var streams: [StreamChoice] = []
    @Published var loadingStreams = false
    @Published var failures: [String] = []
    @Published var error: String?

    /// The fetch currently filling the list, so a new title cancels the old one.
    private var streamToken: String?
    /// How long the first source took to appear, which is the number a viewer feels.
    @Published var firstResultSeconds: Double?
    private var streamStartedAt: Date?

    func load(item: CinemaItem) async {
        loading = true
        // The details and the sources do not depend on each other, so both go out at
        // once instead of the sources waiting for the metadata to come back.
        async let detailTask = CinemaAPI.shared.detail(for: item)
        startStreams(id: item.id, type: item.type)
        do {
            detail = try await detailTask
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    /// What the list on screen belongs to, so opening the same title again within
    /// a minute shows the links it already had instead of fetching every source
    /// once more.
    private var loadedFor: (id: String, type: String, at: Date)?

    /// Starts a live fetch that fills the list while the addons are still answering.
    func startStreams(id: String, type: String, force: Bool = false) {
        // The same title reopened within a minute keeps its list: coming back
        // from the player refetched every source for nothing.
        if !force, let loadedFor, loadedFor.id == id, loadedFor.type == type,
           Date().timeIntervalSince(loadedFor.at) < 60, !streams.isEmpty {
            loadingStreams = false
            return
        }
        if let token = streamToken { CinemaAPI.shared.cancelStreams(token) }
        loadedFor = (id, type, Date())
        // A new title starts from an empty list, so the rows on screen never
        // belong to the title before it.
        streams = []
        failures = []
        loadingStreams = true
        firstResultSeconds = nil
        streamStartedAt = Date()
        EventLog.shared.add("sources", "جلب \(type) \(id)")
        streamToken = CinemaAPI.shared.streamsFast(
            for: id,
            type: type,
            onUpdate: { [weak self] payload in
                Task { @MainActor in self?.apply(payload) }
            },
            onDone: { [weak self] payload in
                Task { @MainActor in
                    self?.apply(payload)
                    self?.loadingStreams = false
                    self?.streamToken = nil
                    EventLog.shared.add(
                        "sources",
                        "انتهى: \(payload.streams.count) رابط، \(payload.failures?.count ?? 0) فشل"
                    )
                    if let first = payload.failures?.first {
                        // The page shows one Arabic word per failure; the whole
                        // message, URL included, is kept here for a report.
                        EventLog.shared.add("sources", "تفصيل: " + first)
                    }
                }
            }
        )
    }

    /// Kept for pull-to-refresh, which asks every source again and waits for the
    /// last addon before it stops.
    func loadStreams(id: String, type: String) async {
        startStreams(id: id, type: type, force: true)
        for _ in 0..<120 where streamToken != nil {
            try? await Task.sleep(nanoseconds: 150_000_000)
        }
        loadingStreams = false
    }

    /// The bridge always sends the whole ordered list, so this replaces it wholesale
    /// and keeps the order the scoring already chose.
    private func apply(_ payload: StreamsPayload) {
        streams = payload.streams
        failures = payload.failures ?? []
        if !streams.isEmpty {
            loadingStreams = false
            if firstResultSeconds == nil, let started = streamStartedAt {
                let seconds = Date().timeIntervalSince(started)
                firstResultSeconds = seconds
                EventLog.shared.add(
                    "sources",
                    String(format: "أول مصدر بعد %.1f ث (%d رابط)", seconds, payload.streams.count)
                )
            }
        }
    }
}

/// What the viewer decided about sources once, kept for every title: links they hid,
/// addons they switched off and groups they folded. An addon switched off here is
/// not even asked next time, which is the fastest source of all.
enum SourcePrefs {
    private static let hiddenKey = "cinemax.hiddenSources"
    private static let offKey = "cinemax.offSourceAddons"
    private static let foldedKey = "cinemax.foldedSourceGroups"

    static var hidden: Set<String> {
        Set(UserDefaults.standard.stringArray(forKey: hiddenKey) ?? [])
    }

    static func hide(_ url: String, _ value: Bool) {
        var current = hidden
        if value { current.insert(url) } else { current.remove(url) }
        UserDefaults.standard.set(Array(current), forKey: hiddenKey)
    }

    static func resetHiding() {
        UserDefaults.standard.removeObject(forKey: hiddenKey)
    }

    static var offAddons: Set<String> {
        Set(UserDefaults.standard.stringArray(forKey: offKey) ?? [])
    }

    /// The native side reads the same key, so a switched-off addon is skipped at fetch.
    static func setAddon(_ name: String, on: Bool) {
        var current = offAddons
        if on { current.remove(name) } else { current.insert(name) }
        UserDefaults.standard.set(Array(current), forKey: offKey)
    }

    static func resetAddons() {
        UserDefaults.standard.removeObject(forKey: offKey)
    }

    static var folded: Set<String> {
        Set(UserDefaults.standard.stringArray(forKey: foldedKey) ?? [])
    }

    static func fold(_ group: String, _ value: Bool) {
        var current = folded
        if value { current.insert(group) } else { current.remove(group) }
        UserDefaults.standard.set(Array(current), forKey: foldedKey)
    }

    /// What the native side measured for each addon on this device: its average
    /// answer time. The sources list uses it to prefer what actually works here.
    static func addonLatency() -> [String: Int64] {
        guard let raw = UserDefaults.standard.string(forKey: "cinemax.sourceHealth.v1"),
              let data = raw.data(using: .utf8),
              let decoded = try? JSONDecoder().decode([String: HealthEntry].self, from: data) else {
            return [:]
        }
        return decoded.mapValues { $0.ms }
    }

    private struct HealthEntry: Codable {
        var ms: Int64 = 0
        var failures: Int = 0
        var last: Int64 = 0
    }
}

/// One sheet for both the trailer page and the lists picker. Two `.sheet`
/// modifiers on the same view compete for the presentation and one of them loses,
/// so the page keeps a single sheet whose content is chosen by the case.
enum DetailSheet: Identifiable {
    case trailer(URL)
    case lists(CinemaItem)
    case web(URL)
    /// A card drawn in the app, shown in the share sheet.
    case card(UIImage)

    var id: String {
        switch self {
        case .trailer(let url): return "trailer:" + url.absoluteString
        case .lists(let item): return "lists:" + item.id
        case .web(let url): return "web:" + url.absoluteString
        case .card: return "card"
        }
    }
}

struct DetailView: View {
    let item: CinemaItem
    var onFavorite: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = DetailModel()
    @State private var saved = false
    @State private var season: Int?
    @State private var episode: CinemaItem?
    @State private var playing: StreamChoice?
    /// The engine the chosen link opens with, decided the moment it is picked so
    /// an adaptive link goes to the system player and a file stays with libmpv.
    @State private var playbackEngine = "mpv"
    @State private var qualityFilter: String?
    @State private var addonFilter: String?
    @AppStorage("cinemax.sourcesCollapsed") private var sourcesCollapsed = false
    @State private var speeds: [String: Double] = [:]
    @State private var probeTask: Task<Void, Never>?
    @State private var shared = false
    @State private var buildingCard = false
    @State private var marked = false
    @State private var episodeQuery = ""
    @State private var similar: [CinemaItem] = []
    @State private var similarPick: CinemaItem?
    @State private var copied: String?
    @State private var detailSheet: DetailSheet?
    /// True while an adaptive link's rendition is being chosen, so the page says
    /// it is opening instead of looking frozen.
    @State private var opening = false
    /// The title this page has already loaded. A full-screen player closes back
    /// onto the page and SwiftUI runs its task again, which refetched every
    /// source and reset the chosen season and episode; this keeps what was there.
    @State private var loadedItemID: String?
    @State private var hiddenSources: Set<String> = SourcePrefs.hidden
    @State private var offAddons: Set<String> = SourcePrefs.offAddons
    @State private var foldedGroups: Set<String> = SourcePrefs.folded
    @State private var expandedGroups: Set<String> = []
    @State private var addonLatency: [String: Int64] = [:]
    @AppStorage("cinemax.sourcesCompact") private var compactSources = false
    @AppStorage("cinemax.prefetchNext") private var prefetchNext = true
    /// Races the top links before the film opens. Off by default: the race waits
    /// for one of three range requests before any picture, and a dead link is now
    /// replaced in place from the same second, so waiting for it is wasted.
    @AppStorage("cinemax.raceSources2") private var raceSources = false
    @State private var prefetchedEpisode: String?
    /// True once the page picked the episode to open on by itself, so a page with
    /// no source for that episode never asks again in a loop.
    @State private var autoPicked = false

    private var playlist: [CinemaItem] {
        guard let detail = model.detail, !detail.episodes.isEmpty else { return [] }
        let text = episodeQuery.trimmingCharacters(in: .whitespaces)
        let base = detail.episodes.filter { entry in
            guard !text.isEmpty else { return true }
            return entry.name.localizedCaseInsensitiveContains(text)
        }
        // The season on screen is the one chosen or, before anything is chosen,
        // the first one the page shows. Without this a series listed every
        // episode of every season at once while its first chip looked selected.
        guard let onScreen = season ?? model.detail?.seasons.first else { return base }
        // Episode ids end with ":season:episode" both for addons ("tt1:1:2") and for
        // TMDB ("tmdb:123:series:1:2"), so the season is the second-to-last piece.
        return base.filter { episode in
            let parts = episode.id.split(separator: ":")
            guard parts.count >= 2, let value = Int(parts[parts.count - 2]) else { return false }
            return value == onScreen
        }
    }

    /// The episode a series opens on: the first of this season not watched yet,
    /// or the first of the season when every one of them is watched.
    private func episodeToOpenOn() -> CinemaItem? {
        guard item.type == "series", let detail = model.detail else { return nil }
        let number = currentSeason
        let inSeason = detail.episodes.filter { entry in
            guard let number else { return true }
            let parts = entry.id.split(separator: ":")
            guard parts.count >= 2, let value = Int(parts[parts.count - 2]) else { return false }
            return value == number
        }
        guard !inSeason.isEmpty else { return nil }
        return inSeason.first { !watched($0) } ?? inSeason.first
    }

    var body: some View {
        ZStack(alignment: .top) {
            CinemaTheme.canvas.ignoresSafeArea()
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    backdrop
                    if model.loading {
                        ShimmerBlock(height: 110, width: nil).padding(.horizontal, 20)
                    } else {
                        meta
                        if !playlist.isEmpty { episodes }
                    }
                    sources
                    if !similar.isEmpty { similarRow }
                    Spacer(minLength: 60)
                }
            }
            topBar
            if shared {
                Text("تم نسخ الرابط")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.ultraThinMaterial, in: Capsule())
                    .padding(.top, 54)
            }
            if marked {
                Text("عُلم كمشاهد")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(CinemaTheme.accent.opacity(0.85), in: Capsule())
                    .padding(.top, 100)
            }
            if opening {
                HStack(spacing: 8) {
                    ProgressView().tint(CinemaTheme.accent)
                    Text("جارٍ فتح المصدر…")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(.ultraThinMaterial, in: Capsule())
                .padding(.top, 54)
            }
            if let copied {
                Text(copied)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.ultraThinMaterial, in: Capsule())
                    .padding(.top, 100)
                    .task {
                        try? await Task.sleep(nanoseconds: 1_600_000_000)
                        withAnimation(.easeInOut(duration: 0.2)) { self.copied = nil }
                    }
            }
        }
        .task {
            // The page is loaded once per title: coming back from the player
            // ran this again, which fetched every source a second time and
            // reset the season and the episode the viewer had picked.
            if loadedItemID != item.id {
                loadedItemID = item.id
                await model.load(item: item)
                // The heart shows what the library really holds: before this it
                // always opened empty, so a saved title looked unsaved and a tap
                // on it removed it while the page said it was never saved.
                saved = HomeModel.shared.favorites.contains(item.id)
                // A season the viewer was on last time comes back with the page.
                let remembered = UserDefaults.standard.integer(forKey: "cinemax.season." + item.id)
                if remembered > 0, let seasons = model.detail?.seasons,
                   seasons.contains(remembered) {
                    season = remembered
                }
                // A series opens on the episode that follows the last one
                // watched, and asks for its links: a series id alone answers no
                // source, which left the watch button greyed out until an
                // episode was tapped by hand.
                if episode == nil, let next = episodeToOpenOn() {
                    episode = next
                    await model.loadStreams(id: next.id, type: item.type)
                }
            }
            prefetchNextEpisode()
        }
        .refreshable {
            await model.loadStreams(id: episode?.id ?? item.id, type: item.type)
        }
        .onAppear {
            // The source decisions are shared by every title, so they are re-read
            // whenever the page opens instead of only once.
            hiddenSources = SourcePrefs.hidden
            offAddons = SourcePrefs.offAddons
            foldedGroups = SourcePrefs.folded
            addonLatency = SourcePrefs.addonLatency()
        }
        .task(id: item.id) {
            similar = await CinemaAPI.shared.similar(item: item)
        }
        .onChange(of: model.streams) { streams in
            // The list grows with every addon's answer, so each answer used to
            // start another round of measuring: a dozen rounds of half-megabyte
            // downloads froze the screen along with the link. The measuring waits
            // for a full second of quiet and then reads the top six rows only.
            prefetchNextEpisode()
            probeTask?.cancel()
            probeTask = Task {
                try? await Task.sleep(nanoseconds: 1_200_000_000)
                guard !Task.isCancelled else { return }
                let pending = Array(streams.filter { speeds[$0.url] == nil }.prefix(6))
                guard !pending.isEmpty else { return }
                let measured = await StreamProber.shared.speeds(for: pending)
                guard !Task.isCancelled else { return }
                await MainActor.run { speeds.merge(measured) { _, fresh in fresh } }
            }
        }
        .fullScreenCover(item: $playing, onDismiss: { playing = nil }) { choice in
            Group {
                switch playbackEngine {
                case "system":
                    SystemPlayerScreen(
                        title: item.name,
                        url: choice.url,
                        headers: choice.headers ?? [:],
                        item: item,
                        videoId: episode?.id,
                        videoLabel: episode?.name,
                        episodes: model.detail?.episodes ?? [],
                        type: item.type,
                        sources: playableStreams
                    )
                case "external":
                    ExternalPlayerScreen(title: item.name, url: choice.url)
                default:
                    PlayerScreen(
                        title: item.name,
                        url: choice.url,
                        item: item,
                        videoId: episode?.id,
                        videoLabel: episode?.name,
                        sourceNote: [choice.addon, choice.quality].compactMap { $0 }.joined(separator: " · "),
                        headers: choice.headers ?? [:],
                        sources: playableStreams,
                        episodes: model.detail?.episodes ?? [],
                        type: item.type
                    )
                }
            }
        }
        .sheet(item: $detailSheet) { sheet in
            switch sheet {
            case .trailer(let url):
                SafariView(url: url)
                    .ignoresSafeArea()
            case .lists(let entry):
                ListsPickerSheet(item: entry)
            case .web(let url):
                SafariView(url: url)
                    .ignoresSafeArea()
            case .card(let image):
                ShareSheet(items: [image])
            }
        }
    }

    /// Titles TMDB puts next to this one, tapping through to their own page.
    private var similarRow: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHeader(title: "أعمال مشابهة")
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(similar) { entry in
                        PosterCard(
                            item: entry,
                            width: CinemaxPrefs.posterMinWidth,
                            onOpen: { similarPick = entry }
                        )
                    }
                }
                .padding(.horizontal, 20)
            }
        }
        .fullScreenCover(item: $similarPick) { entry in
            DetailView(item: entry)
        }
    }

    /// Records the title as fully watched, for what was seen somewhere else.
    private func markWatched() {
        let minutes: Int64 = 100 * 60 * 1000
        CinemaAPI.shared.saveProgress(
            item: item,
            positionMs: minutes,
            durationMs: minutes,
            videoId: episode?.id,
            videoLabel: episode?.name
        )
        Haptics.tap()
        withAnimation(.easeInOut(duration: 0.2)) { marked = true }
        Task {
            await LibraryModel.shared.load()
            HomeModel.shared.resetCache()
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            withAnimation(.easeInOut(duration: 0.2)) { marked = false }
        }
    }

    /// The stop position this title already has, so the primary button says أكمل.
    private var resumeEntry: ProgressItem? {
        LibraryModel.shared.progress.first { $0.item.id == item.id }
    }

    private var topBar: some View {
        HStack(spacing: 10) {
            CircleButton(system: "chevron.down") { dismiss() }
            Spacer()
            CircleButton(system: "textformat") {
                Haptics.tap()
                UIPasteboard.general.string = model.detail?.item.name ?? item.name
                shared = true
                Task {
                    try? await Task.sleep(nanoseconds: 1_600_000_000)
                    shared = false
                }
            }
            CircleButton(system: "photo.on.rectangle") {
                Haptics.tap()
                buildCard()
            }
            CircleButton(system: "square.and.arrow.up") {
                Haptics.tap()
                share()
            }
            CircleButton(system: "text.badge.plus") {
                Haptics.tap()
                detailSheet = .lists(model.detail?.item ?? item)
            }
            CircleButton(system: saved ? "heart.fill" : "heart", tint: saved ? CinemaTheme.favourite : .white) {
                onFavorite()
                saved.toggle()
                Haptics.tap()
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }

    private var backdrop: some View {
        ZStack(alignment: .bottomLeading) {
            RemoteImage(url: item.backdropURL ?? item.posterURL)
                .frame(height: 300)
                .overlay(
                    LinearGradient(
                        stops: [
                            .init(color: .clear, location: 0.3),
                            .init(color: CinemaTheme.canvas, location: 1)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            VStack(alignment: .leading, spacing: 10) {
                Text(item.name)
                    .font(.system(size: 26, weight: .heavy))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                HStack(spacing: 8) {
                    if let rating = item.rating, !rating.isEmpty {
                        CinemaPill(text: "★ \(rating)")
                    }
                    if let year = item.releaseInfo, !year.isEmpty {
                        CinemaPill(text: year, tint: CinemaTheme.muted)
                    }
                    if let runtime = model.detail?.runtime {
                        CinemaPill(text: runtime, tint: CinemaTheme.muted)
                    }
                    if let episodes = model.detail?.episodes, !episodes.isEmpty {
                        CinemaPill(text: "\(episodes.count) حلقة", tint: CinemaTheme.muted)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 14)
        }
        .frame(height: 300)
    }

    private var meta: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Button {
                    playBest()
                } label: {
                    Label(resumeEntry == nil ? "مشاهدة" : "أكمل", systemImage: "play.fill")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(.white, in: Capsule())
                }
                .buttonStyle(CinemaPressStyle())
                .disabled(model.streams.isEmpty)
                .opacity(model.streams.isEmpty ? 0.5 : 1)

                Button {
                    onFavorite()
                    saved.toggle()
                } label: {
                    Label(saved ? "محفوظ" : "حفظ", systemImage: saved ? "checkmark" : "plus")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(Color.white.opacity(0.1), in: Capsule())
                }
                .buttonStyle(CinemaPressStyle())
            }

            HStack(spacing: 8) {
                Button {
                    markWatched()
                } label: {
                    Label("علّم كمشاهد", systemImage: "checkmark.circle")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(CinemaTheme.surface, in: Capsule())
                }
                .buttonStyle(.plain)

                if resumeEntry != nil {
                    Button {
                        Haptics.select()
                        Task {
                            _ = await CinemaAPI.shared.removeProgress(
                                itemId: item.id,
                                videoLabel: episode?.name
                            )
                            await LibraryModel.shared.load()
                        }
                    } label: {
                        Label("مسح موضع التوقف", systemImage: "arrow.counterclockwise")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(CinemaTheme.surface, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }

                if let trailer = model.detail?.trailers?.first, let url = URL(string: trailer) {
                    Button {
                        Haptics.tap()
                        detailSheet = .trailer(url)
                    } label: {
                        Label("الإعلان", systemImage: "film")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(CinemaTheme.accent.opacity(0.14), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
            }

            if let genres = model.detail?.genres, !genres.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(genres.prefix(8), id: \.self) { genre in
                            CinemaPill(text: genre, tint: CinemaTheme.muted)
                        }
                    }
                }
            }

            if let cast = model.detail?.cast, !cast.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        Text("بطولة")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                        ForEach(cast.prefix(8), id: \.self) { person in
                            Button {
                                Haptics.select()
                                UIPasteboard.general.string = person
                                copied = "نُسخ \(person)"
                            } label: {
                                CinemaPill(text: person, tint: .white)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            if let directors = model.detail?.director, !directors.isEmpty {
                Text("إخراج: \(directors.prefix(3).joined(separator: "، "))")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .textSelection(.enabled)
            }

            if let overview = model.detail?.item.overview ?? item.overview, !overview.isEmpty {
                Text(overview)
                    .font(.system(size: 14))
                    .lineSpacing(5)
                    .foregroundStyle(.white.opacity(0.9))
                    .lineLimit(6)
            }

            HStack(spacing: 14) {
                if let episodes = model.detail?.episodes, !episodes.isEmpty {
                    Label("\(episodes.count) حلقة", systemImage: "rectangle.stack")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(CinemaTheme.muted)
                }
                if let imdb = imdbID,
                   let page = URL(string: "https://www.imdb.com/title/\(imdb)/") {
                    Link(destination: page) {
                        Label("IMDb", systemImage: "arrow.up.right.square")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                    }
                }
                Spacer()
            }
        }
        .padding(.horizontal, 20)
    }

    /// A short Arabic reason for a source's failure. The engine's own message is
    /// English with a full URL inside it, which is not something a viewer should
    /// read on the page: the source's name and one word are enough, and the whole
    /// text is kept in the event log for a report.
    private func reason(_ failure: String) -> String {
        let name = failure.split(separator: ":").first.map(String.init) ?? failure
        let lowered = failure.lowercased()
        let cause: String
        if lowered.contains("timeout") || lowered.contains("timed out")
            || lowered.contains("deadline") || lowered.contains("مهلة") {
            cause = "انتهت المهلة"
        } else if lowered.contains("invalid") || lowered.contains("غير صالح")
            || lowered.contains("config") {
            cause = "يحتاج إعدادًا"
        } else if lowered.contains("403") || lowered.contains("forbidden")
            || lowered.contains("401") || lowered.contains("unauthorized") {
            cause = "الخادم رفض"
        } else if lowered.contains("404") || lowered.contains("not found") {
            cause = "غير موجود"
        } else if lowered.contains("dns") || lowered.contains("offline")
            || lowered.contains("network") || lowered.contains("connect") {
            cause = "تعذر الاتصال"
        } else {
            cause = "لم يستجب"
        }
        return "\(name.trimmingCharacters(in: .whitespaces)): \(cause)"
    }

    /// The failures as one readable line, three at most.
    private var failureNote: String {
        model.failures.prefix(3).map(reason).joined(separator: " · ")
    }

    private var imdbID: String? {
        let id = model.detail?.item.id ?? item.id
        return id.hasPrefix("tt") ? String(id.split(separator: ":").first ?? "") : nil
    }

    /// Draws the title as a card and hands it to the share sheet, so the film is
    /// sent to a friend as a picture rather than as a bare link.
    private func buildCard() {
        guard !buildingCard else { return }
        buildingCard = true
        Task { @MainActor in
            let badge = saved ? "من مكتبتك" : nil
            let image = await CinemaCards.title(
                model.detail?.item ?? item,
                badge: badge
            )
            buildingCard = false
            guard let image else {
                copied = "تعذّر إنشاء البطاقة"
                return
            }
            detailSheet = .card(image)
            EventLog.shared.add("card", "بطاقة \(item.name)")
        }
    }

    private func share() {
        let id = model.detail?.item.id ?? item.id
        let link: String
        if let imdb = imdbID {
            link = "https://www.imdb.com/title/\(imdb)/"
        } else {
            link = id
        }
        UIPasteboard.general.string = link
        shared = true
        Task {
            try? await Task.sleep(nanoseconds: 1_600_000_000)
            shared = false
        }
    }

    private var episodes: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let seasons = model.detail?.seasons, seasons.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(seasons, id: \.self) { value in
                            Button {
                                Haptics.select()
                                season = value
                                episode = nil
                                UserDefaults.standard.set(value, forKey: "cinemax.season." + item.id)
                            } label: {
                                Text("الموسم \(value)")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle((season ?? seasons.first) == value ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background {
                                        Capsule().fill((season ?? seasons.first) == value
                                                       ? CinemaTheme.accent
                                                       : Color.white.opacity(0.08))
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
            if let summary = seasonSummary {
                Text(summary)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(CinemaTheme.muted)
                    .padding(.horizontal, 20)
            }
            if (model.detail?.episodes.count ?? 0) > 3 {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 12))
                        .foregroundStyle(CinemaTheme.muted)
                    TextField("ابحث عن حلقة", text: $episodeQuery)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12))
                        .foregroundStyle(.white)
                    if !episodeQuery.isEmpty {
                        Button {
                            episodeQuery = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 12))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(CinemaTheme.surface, in: Capsule())
                .padding(.horizontal, 20)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(playlist) { entry in
                        Button {
                            Haptics.tap()
                            episode = entry
                            Task {
                                qualityFilter = nil
                                await model.loadStreams(id: entry.id, type: item.type)
                            }
                        } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                RemoteImage(url: entry.posterURL ?? entry.backdropURL)
                                    .frame(width: 168, height: 96)
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .strokeBorder(
                                                episode?.id == entry.id ? CinemaTheme.accent : CinemaTheme.hairline,
                                                lineWidth: episode?.id == entry.id ? 2 : 1
                                            )
                                    )
                                    .overlay(alignment: .topTrailing) {
                                        if watched(entry) {
                                            Image(systemName: "checkmark.circle.fill")
                                                .font(.system(size: 15, weight: .bold))
                                                .foregroundStyle(.white)
                                                .background(CinemaTheme.accent, in: Circle())
                                                .padding(6)
                                        }
                                    }
                                Text(entry.name)
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                                if let date = entry.releaseInfo, !date.isEmpty {
                                    Text(date)
                                        .font(.system(size: 9))
                                        .foregroundStyle(CinemaTheme.muted)
                                        .lineLimit(1)
                                }
                            }
                            .frame(width: 168)
                        }
                        .buttonStyle(CinemaPressStyle())
                        .contextMenu {
                            Button {
                                Haptics.tap()
                                toggleWatched(entry)
                            } label: {
                                Label(
                                    watched(entry) ? "إلغاء تعليم المشاهدة" : "علّم كمشاهد",
                                    systemImage: "checkmark.circle"
                                )
                            }
                            Button {
                                Haptics.select()
                                UIPasteboard.general.string = entry.name
                                copied = "نُسخ اسم الحلقة"
                            } label: {
                                Label("نسخ اسم الحلقة", systemImage: "doc.on.doc")
                            }
                            Button {
                                Haptics.tap()
                                downloadEpisode(entry)
                            } label: {
                                Label("تنزيل هذه الحلقة", systemImage: "arrow.down.circle")
                            }
                            Button {
                                Haptics.tap()
                                downloadUpcoming(after: entry)
                            } label: {
                                Label("تنزيل ٣ حلقات بعدها", systemImage: "square.stack.3d.down.right")
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
        }
    }

    private func watched(_ episode: CinemaItem) -> Bool {
        LibraryModel.shared.progress.contains { entry in
            entry.item.id == item.id && entry.fraction >= 0.9 && entry.episodeLabel == episode.name
        }
    }

    /// Downloads one episode: asks its addons for a link the same way pressing
    /// play does, then hands it to the queue on the device.
    private func downloadEpisode(_ entry: CinemaItem) {
        copied = "جارٍ جلب مصدر \(entry.name)…"
        Task {
            let streams = await CinemaAPI.shared.freshStreams(for: entry.id, type: item.type)
            guard let best = streams.first(where: { $0.externalUrl == nil }) else {
                await MainActor.run { copied = "لا رابط قابل للتنزيل لهذه الحلقة" }
                return
            }
            await MainActor.run {
                DownloadManager.shared.start(
                    title: "\(item.name) · \(entry.name)",
                    urlString: best.url,
                    headers: best.headers ?? [:]
                )
                copied = "أُضيفت \(entry.name) إلى التنزيل"
                Haptics.select()
            }
        }
    }

    /// Queues the three episodes that follow the one tapped, inside the season
    /// on screen: a season has its own numbering, so the whole episode list
    /// would have queued the first episodes of the next season.
    private func downloadUpcoming(after entry: CinemaItem) {
        let entries = playlist
        guard let index = entries.firstIndex(where: { $0.id == entry.id }) else { return }
        let upcoming = Array(entries.dropFirst(index + 1).prefix(3))
        guard !upcoming.isEmpty else {
            copied = "لا حلقات بعد \(entry.name)"
            return
        }
        copied = "جارٍ جلب مصادر \(upcoming.count) حلقات…"
        Task {
            var picks: [(title: String, url: String, headers: [String: String])] = []
            for next in upcoming {
                let streams = await CinemaAPI.shared.freshStreams(for: next.id, type: item.type)
                guard let best = streams.first(where: { $0.externalUrl == nil }) else { continue }
                picks.append(("\(item.name) · \(next.name)", best.url, best.headers ?? [:]))
            }
            await MainActor.run {
                for pick in picks {
                    DownloadManager.shared.start(
                        title: pick.title,
                        urlString: pick.url,
                        headers: pick.headers
                    )
                }
                copied = picks.isEmpty
                    ? "لم يُعثر على رابط لحلقة قادمة"
                    : "أُضيفت \(picks.count) حلقات إلى التنزيل"
                EventLog.shared.add("download", "حلقات بعد \(entry.name): \(picks.count)")
                if !picks.isEmpty { Haptics.select() }
            }
        }
    }

    /// Marks one episode watched, or takes that mark back.
    private func toggleWatched(_ entry: CinemaItem) {
        if watched(entry) {
            Task {
                _ = await CinemaAPI.shared.removeProgress(
                    itemId: item.id,
                    videoLabel: entry.name
                )
                await LibraryModel.shared.load()
            }
        } else {
            let minutes: Int64 = 100 * 60 * 1000
            CinemaAPI.shared.saveProgress(
                item: item,
                positionMs: minutes,
                durationMs: minutes,
                videoId: entry.id,
                videoLabel: entry.name
            )
            Task { await LibraryModel.shared.load() }
        }
    }

    /// The season on screen, so the summary line follows the chips.
    private var currentSeason: Int? {
        season ?? model.detail?.seasons.first
    }

    private var seasonSummary: String? {
        guard let value = currentSeason, let detail = model.detail else { return nil }
        let inSeason = detail.episodes.filter { entry in
            let parts = entry.id.split(separator: ":")
            guard parts.count >= 2, let number = Int(parts[parts.count - 2]) else { return false }
            return number == value
        }
        guard !inSeason.isEmpty else { return nil }
        let seen = inSeason.filter { watched($0) }.count
        return "شوهدت \(seen) من \(inSeason.count) حلقة في هذا الموسم"
    }

    /// The addons that returned a source here, for the filter chips. A switched-off
    /// addon leaves the chips row with its links.
    private var addonNames: [String] {
        Array(Set(model.streams.filter { !offAddons.contains($0.addon) }.map(\.addon))).sorted()
    }

    /// Links the viewer can actually see: not hidden, and not from a stopped addon.
    private var visibleStreamCount: Int {
        model.streams.filter {
            !hiddenSources.contains($0.url) && !offAddons.contains($0.addon)
        }.count
    }

    private func addonChip(_ title: String, _ value: String?) -> some View {
        Button {
            Haptics.select()
            withAnimation(.easeInOut(duration: 0.15)) { addonFilter = value }
        } label: {
            Text(title)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(addonFilter == value ? .black : .white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background {
                    Capsule().fill(
                        addonFilter == value ? CinemaTheme.accent : Color.white.opacity(0.08)
                    )
                }
        }
        .buttonStyle(.plain)
        .contextMenu {
            if let value {
                Button {
                    toggleAddon(value)
                } label: {
                    Label("إطفاء \(value)", systemImage: "power")
                }
            }
        }
    }

    // MARK: - Source controls

    /// One menu for everything the viewer can do to the list itself.
    private var sourcesMenu: some View {
        Menu {
            Button {
                Haptics.tap()
                Task { await model.loadStreams(id: episode?.id ?? item.id, type: item.type) }
            } label: {
                Label("إعادة جلب المصادر", systemImage: "arrow.clockwise")
            }
            if !hiddenSources.isEmpty {
                Button {
                    Haptics.tap()
                    showAllSources()
                } label: {
                    Label("إظهار \(hiddenSources.count) رابطًا مخفيًا", systemImage: "eye")
                }
            }
            if !offAddons.isEmpty {
                Button {
                    Haptics.tap()
                    offAddons = []
                    SourcePrefs.resetAddons()
                } label: {
                    Label("تشغيل \(offAddons.count) إضافة متوقفة", systemImage: "power")
                }
            }
            Button {
                Haptics.select()
                compactSources.toggle()
            } label: {
                Label(
                    compactSources ? "عرض عريض" : "عرض مضغوط",
                    systemImage: compactSources ? "rectangle.expand.vertical" : "rectangle.compress.vertical"
                )
            }
        } label: {
            Label("خيارات المصادر", systemImage: "slider.horizontal.3")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(CinemaTheme.accent)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(CinemaTheme.accent.opacity(0.14), in: Capsule())
        }
    }

    private func groupKey(_ quality: String) -> String { "quality:" + quality }

    /// A quality group shows its first forty rows and the rest on request: a source
    /// list of three hundred links must not build three hundred views to open.
    private func groupLimit(_ quality: String) -> Int {
        expandedGroups.contains(quality) ? Int.max : 40
    }

    private func foldGroup(_ quality: String) {
        let key = groupKey(quality)
        if foldedGroups.contains(key) {
            foldedGroups.remove(key)
        } else {
            foldedGroups.insert(key)
        }
        SourcePrefs.fold(key, foldedGroups.contains(key))
    }

    private func hide(_ stream: StreamChoice) {
        Haptics.tap()
        hiddenSources.insert(stream.url)
        SourcePrefs.hide(stream.url, true)
    }

    private func showAllSources() {
        hiddenSources = []
        SourcePrefs.resetHiding()
    }

    /// Switches one addon's links on or off everywhere, and stops asking it.
    private func toggleAddon(_ name: String) {
        Haptics.select()
        let turningOff = !offAddons.contains(name)
        if turningOff {
            offAddons.insert(name)
        } else {
            offAddons.remove(name)
        }
        SourcePrefs.setAddon(name, on: !turningOff)
        if turningOff, addonFilter == name { addonFilter = nil }
    }

    /// The link this title last played with, marked in the list.
    private var lastPlayedURL: String? {
        UserDefaults.standard.string(forKey: "cinemax.lastSource." + item.id)
    }

    /// Remembers the chosen link so the next visit marks it.
    private func remember(_ stream: StreamChoice) {
        UserDefaults.standard.set(stream.url, forKey: "cinemax.lastSource." + item.id)
    }

    /// The watch button does not trust the first row blindly: with racing on, the top
    /// few links are probed in parallel and the first that really serves bytes plays.
    private func playBest() {
        // Nothing was answered for the series id: the page opens on the episode
        // it should, asks for that episode's links, and then plays them.
        if playableStreams.isEmpty {
            guard !autoPicked, let next = episodeToOpenOn() else { return }
            autoPicked = true
            episode = next
            Task {
                await model.loadStreams(id: next.id, type: item.type)
                await MainActor.run {
                    if playableStreams.isEmpty {
                        copied = "لا رابط لهذه الحلقة"
                    } else {
                        playBest()
                    }
                }
            }
            return
        }
        guard let first = playableStreams.first else { return }
        guard raceSources, playableStreams.count > 1 else {
            play(first)
            return
        }
        Task {
            let candidates = Array(playableStreams.prefix(3))
            let winner = await StreamRacer.shared.firstPlayable(candidates) ?? first
            await MainActor.run {
                if winner.url != first.url {
                    EventLog.shared.add("race", "فاز \(winner.addon) على \(first.addon)")
                    Haptics.select()
                }
                play(winner)
            }
        }
    }

    /// Remembers the chosen link so the next visit marks it, and picks an adaptive
    /// link's rendition before the player opens instead of letting it guess.
    private func play(_ stream: StreamChoice) {
        remember(stream)
        guard StreamExpiry.isExpiring(stream.url) else {
            launchPlayback(stream)
            return
        }
        // The signature on this link is nearly gone: ask the addon for a fresh one and
        // play that instead of handing the player a URL that will 403 mid-film.
        EventLog.shared.add("sources", "رابط قارب على الانتهاء من \(stream.addon)")
        Task {
            let fresh = await CinemaAPI.shared.freshStreams(
                for: episode?.id ?? item.id,
                type: item.type
            )
            let replacement = fresh.first { $0.addon == stream.addon && $0.externalUrl == nil }
                ?? fresh.first { $0.externalUrl == nil }
                ?? stream
            await MainActor.run {
                if replacement.url != stream.url {
                    EventLog.shared.add("sources", "جُدّد رابط \(replacement.addon)")
                }
                launchPlayback(replacement)
            }
        }
    }

    private func launchPlayback(_ stream: StreamChoice) {
        // The film opens at once. Picking an adaptive link's rendition first was
        // a network request of several seconds before any picture appeared, and
        // both players adapt a master playlist themselves; the setting turns the
        // pre-pick back on when a fixed rendition is wanted.
        guard CinemaxPrefs.hlsRendition, HLSParser.shared.isHLS(stream.url) else {
            playbackEngine = CinemaxPrefs.engine(for: stream.url)
            withAnimation(.easeInOut(duration: 0.15)) { playing = stream }
            return
        }
        // Choosing a rendition is a network request of a few seconds; the page
        // says so instead of looking frozen with nothing on it.
        opening = true
        Task {
            let resolved = await HLSParser.shared.bestStream(
                for: stream.url,
                headers: stream.headers ?? [:],
                preferredQuality: CinemaxPrefs.preferredQuality
            )
            await MainActor.run {
                var chosen = stream
                if let resolved {
                    chosen.url = resolved.url
                    EventLog.shared.add("hls", "\(resolved.label) ← \(stream.addon)")
                }
                playbackEngine = CinemaxPrefs.engine(for: chosen.url)
                opening = false
                withAnimation(.easeInOut(duration: 0.15)) { playing = chosen }
            }
        }
    }

    /// Warms the next episode's sources while the viewer is still deciding, so the
    /// next tap shows a ready list instead of asking every addon again.
    private func prefetchNextEpisode() {
        guard prefetchNext, let detail = model.detail, !detail.episodes.isEmpty else { return }
        let anchor = episode?.id ?? item.id
        guard let index = detail.episodes.firstIndex(where: { $0.id == anchor }),
              index + 1 < detail.episodes.count else { return }
        let next = detail.episodes[index + 1]
        guard prefetchedEpisode != next.id else { return }
        prefetchedEpisode = next.id
        Task {
            _ = try? await CinemaAPI.shared.streamsReport(for: next.id, type: item.type)
            EventLog.shared.add("prefetch", "جهّز مصادر \(next.name)")
        }
    }

    private var qualityOrder: [String] {
        let known = ["4K", "1080p", "720p", "480p"]
        let present = Set(model.streams.map { stream -> String in
            guard let quality = stream.quality else { return "أخرى" }
            return known.contains(quality) ? quality : "أخرى"
        })
        return known.filter { present.contains($0) } + (present.contains("أخرى") ? ["أخرى"] : [])
    }

    private func qualityWeight(_ quality: String?) -> Double {
        switch quality ?? "" {
        case "4K": return 8
        case "1080p": return 6
        case "720p": return 4
        case "480p": return 2
        default: return 1
        }
    }

    /// Quality stays the main signal; the measured speed breaks ties upwards, so a
    /// fast 1080p can pass a crawling 4K but quality is never ignored.
    private func streamScore(_ stream: StreamChoice) -> Double {
        var score = qualityWeight(stream.quality)
        // With the automatic engine, a link the system player reads leads: it plays
        // with the correct shape in both orientations, so a container only libmpv
        // reads should not win the button unless it is genuinely better.
        if CinemaxPrefs.playerEngine == "auto", CinemaxPrefs.systemReadable(stream.url) {
            score += 2
        }
        if let speed = speeds[stream.url] {
            score += min(speed, 20.0) / 5.0
        }
        // A tier the viewer asked for climbs above the rest without hiding anything.
        if let preferred = preferredTier, stream.quality == preferred {
            score += 3
        }
        // What this device measured beats any guess: an addon that answers quickly
        // is worth up to three points, an adaptive link a little, a proxy less.
        if let ms = addonLatency[stream.addon], ms > 0 {
            score += max(0, Double(3_000 - min(ms, 3_000)) / 1_000.0)
        }
        if HLSParser.shared.isHLS(stream.url) { score += 0.4 }
        if isProxied(stream) { score -= 1 }
        return score
    }

    private var preferredTier: String? {
        let value = CinemaxPrefs.preferredQuality
        return value == "auto" ? nil : value
    }

    private func isProxied(_ stream: StreamChoice) -> Bool {
        let url = stream.url.lowercased()
        let title = (stream.title + " " + stream.addon).lowercased()
        let host = url.split(separator: "/").dropFirst(2).first.map(String.init) ?? ""
        return title.contains("proxy") || host.contains("prox")
            || url.contains("proxy") || url.contains("?proxy") || url.contains("&proxy")
    }

    /// Rows the player may use: a link this app can open, never a page for the browser.
    private var playableStreams: [StreamChoice] {
        filteredStreams.filter { $0.externalUrl == nil }
    }

    private var filteredStreams: [StreamChoice] {
        let known = ["4K", "1080p", "720p", "480p"]
        let list = model.streams.filter { stream in
            if hiddenSources.contains(stream.url) { return false }
            if offAddons.contains(stream.addon) { return false }
            if let addonFilter, stream.addon != addonFilter { return false }
            guard let qualityFilter else { return true }
            let tier = stream.quality.flatMap { known.contains($0) ? $0 : nil } ?? "أخرى"
            return tier == qualityFilter
        }
        // The viewer picks whether quality or the measured speed leads the list.
        var sorted: [StreamChoice]
        if CinemaxPrefs.speedFirst {
            sorted = list.sorted { lhs, rhs in
                let left = speeds[lhs.url] ?? 0
                let right = speeds[rhs.url] ?? 0
                if abs(left - right) > 0.05 { return left > right }
                return streamScore(lhs) > streamScore(rhs)
            }
        } else {
            sorted = list.sorted { streamScore($0) > streamScore($1) }
        }
        // The link this title played with last time goes back to the top.
        if let remembered = lastPlayedURL,
           let index = sorted.firstIndex(where: { $0.url == remembered }),
           index > 0 {
            let entry = sorted.remove(at: index)
            sorted.insert(entry, at: 0)
        }
        return sorted
    }

    private struct StreamGroup: Identifiable {
        let id: String
        let streams: [StreamChoice]
    }

    private var groupedStreams: [StreamGroup] {
        var order: [String] = []
        var buckets: [String: [StreamChoice]] = [:]
        for stream in filteredStreams {
            let key = stream.quality ?? "أخرى"
            if buckets[key] == nil {
                buckets[key] = []
                order.append(key)
            }
            buckets[key]?.append(stream)
        }
        return order.map { StreamGroup(id: $0, streams: buckets[$0] ?? []) }
    }

    private var sources: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                Haptics.select()
                withAnimation(.easeInOut(duration: 0.2)) { sourcesCollapsed.toggle() }
            } label: {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(
                        episode == nil
                            ? "مصادر المشاهدة (\(model.streams.count))"
                            : "مصادر \(episode?.name ?? "") (\(model.streams.count))"
                    )
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.white)
                    Image(systemName: sourcesCollapsed ? "chevron.left" : "chevron.up")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(CinemaTheme.muted)
                    Spacer()
                    Text(sourcesCollapsed ? "إظهار" : "إخفاء")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 2)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if !sourcesCollapsed, !model.loadingStreams {
                HStack(spacing: 6) {
                    Text("\(visibleStreamCount) رابطًا من \(addonNames.count) مصدرًا")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(CinemaTheme.muted)
                    if !model.failures.isEmpty {
                        Text("· \(model.failures.count) لم يستجب")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(CinemaTheme.favourite)
                    }
                    if let seconds = model.firstResultSeconds {
                        Text(String(format: "· أول مصدر في %.1f ث", seconds))
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(CinemaTheme.accent)
                    }
                    Spacer()
                }
                .padding(.horizontal, 20)

                HStack(spacing: 10) {
                    sourcesMenu
                    Text(compactSources ? "عرض مضغوط" : "عرض عريض")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    if !hiddenSources.isEmpty {
                        Text("· \(hiddenSources.count) مخفي")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    if !offAddons.isEmpty {
                        Text("· \(offAddons.count) إضافة متوقفة")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(CinemaTheme.favourite)
                    }
                    Spacer()
                }
                .padding(.horizontal, 20)
                if !model.failures.isEmpty {
                    Text(failureNote)
                        .font(.system(size: 10))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineLimit(2)
                        .padding(.horizontal, 20)
                }
            }
            if sourcesCollapsed {
                EmptyView()
            } else if model.loadingStreams {
                HStack(spacing: 8) {
                    ProgressView().tint(CinemaTheme.accent)
                    Text("جارٍ جلب المصادر… أول ما يصل يظهر فورًا")
                        .font(.system(size: 13))
                        .foregroundStyle(CinemaTheme.muted)
                }
                .padding(.horizontal, 20)
                Text("كل إضافة تُسأل بالتوازي، وأي إضافة تتأخر أكثر من ٧ ثوانٍ يُكمل البحث بدونها.")
                    .font(.system(size: 10))
                    .foregroundStyle(CinemaTheme.muted)
                    .padding(.horizontal, 20)
            } else if model.streams.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("لا يوجد رابط مباشر لهذا العنوان من إضافاتك.")
                        .font(.system(size: 13))
                        .foregroundStyle(CinemaTheme.muted)
                    if !model.failures.isEmpty {
                        Text(failureNote)
                            .font(.system(size: 10))
                            .foregroundStyle(CinemaTheme.favourite)
                            .lineLimit(3)
                    }
                    Button {
                        Haptics.tap()
                        Task {
                            await model.loadStreams(id: episode?.id ?? item.id, type: item.type)
                        }
                    } label: {
                        Text("أعد المحاولة")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 9)
                            .background(CinemaTheme.accent, in: Capsule())
                    }
                    .buttonStyle(CinemaPressStyle())
                }
                .padding(.horizontal, 20)
            } else if filteredStreams.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("كل الروابط مخفية أو إضافتها متوقفة.")
                        .font(.system(size: 13))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        Button {
                            Haptics.tap()
                            showAllSources()
                        } label: {
                            Text("إظهار كل الروابط")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(.black)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(CinemaTheme.accent, in: Capsule())
                        }
                        .buttonStyle(CinemaPressStyle())
                        if !offAddons.isEmpty {
                            Button {
                                Haptics.tap()
                                offAddons = []
                                SourcePrefs.resetAddons()
                            } label: {
                                Text("تشغيل كل الإضافات")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(CinemaTheme.accent.opacity(0.14), in: Capsule())
                            }
                            .buttonStyle(CinemaPressStyle())
                        }
                    }
                }
                .padding(.horizontal, 20)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        qualityChip("الكل", nil)
                        ForEach(qualityOrder, id: \.self) { quality in
                            qualityChip(quality, quality)
                        }
                    }
                    .padding(.horizontal, 20)
                }
                if addonNames.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            addonChip("كل الإضافات", nil)
                            ForEach(addonNames, id: \.self) { name in
                                addonChip(name, name)
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                }
                ForEach(groupedStreams) { group in
                    VStack(alignment: .leading, spacing: 8) {
                        Button {
                            Haptics.select()
                            withAnimation(.easeInOut(duration: 0.15)) { foldGroup(group.id) }
                        } label: {
                            HStack(spacing: 6) {
                                Image(
                                    systemName: foldedGroups.contains(groupKey(group.id))
                                        ? "chevron.left"
                                        : "chevron.down"
                                )
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(CinemaTheme.muted)
                                Text("\(group.id) · \(group.streams.count)")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.muted)
                                Spacer()
                                if foldedGroups.contains(groupKey(group.id)) {
                                    Text("فتح")
                                        .font(.system(size: 10, weight: .semibold))
                                        .foregroundStyle(CinemaTheme.accent)
                                }
                            }
                            .padding(.horizontal, 20)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)

                        if !foldedGroups.contains(groupKey(group.id)) {
                            ForEach(group.streams.prefix(groupLimit(group.id))) { stream in
                                sourceRow(stream)
                            }
                            if group.streams.count > groupLimit(group.id) {
                                Button {
                                    Haptics.select()
                                    withAnimation(.easeInOut(duration: 0.15)) {
                                        _ = expandedGroups.insert(group.id)
                                    }
                                } label: {
                                    Text(
                                        "عرض بقية \(group.id) (\(group.streams.count - groupLimit(group.id)))"
                                    )
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                                    .padding(.vertical, 4)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
        }
    }

    private func qualityChip(_ title: String, _ value: String?) -> some View {
        Button {
            Haptics.select()
            withAnimation(.easeInOut(duration: 0.15)) { qualityFilter = value }
        } label: {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(qualityFilter == value ? .black : .white)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background {
                    Capsule().fill(qualityFilter == value ? CinemaTheme.accent : Color.white.opacity(0.08))
                }
        }
        .buttonStyle(.plain)
    }

    private func sourceRow(_ stream: StreamChoice) -> some View {
        Button {
            Haptics.tap()
            // A row that only points at a page opens that page, instead of trying to
            // play something that was never a file and failing without a word.
            if let external = stream.externalUrl, let url = URL(string: external) {
                detailSheet = .web(url)
                return
            }
            play(stream)
        } label: {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(stream.addon)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                    Text(stream.title)
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineLimit(2)
                }
                Spacer()
                if stream.url == playableStreams.first?.url, stream.url != lastPlayedURL {
                    CinemaPill(text: "موصى به")
                }
                if stream.url == lastPlayedURL {
                    CinemaPill(text: "آخر ما شغّلت", tint: CinemaTheme.muted)
                }
                if let size = stream.size {
                    CinemaPill(text: size, tint: CinemaTheme.muted)
                }
                if let speed = speeds[stream.url] {
                    CinemaPill(text: String(format: "⚡ %.1f MB/s", speed), tint: speed > 1.5 ? CinemaTheme.accent : CinemaTheme.muted)
                }
                if let quality = stream.quality {
                    CinemaPill(text: quality)
                }
                if let note = stream.note {
                    CinemaPill(text: note, tint: CinemaTheme.muted)
                }
                if StreamExpiry.isExpiring(stream.url) {
                    CinemaPill(text: "ينتهي قريبًا", tint: CinemaTheme.favourite)
                }
                if isProxied(stream) {
                    CinemaPill(text: "بروكسي", tint: CinemaTheme.muted)
                }
                if playing?.url == stream.url {
                    ProgressView().tint(CinemaTheme.accent)
                } else if stream.externalUrl != nil {
                    Image(systemName: "safari")
                        .font(.system(size: 20))
                        .foregroundStyle(CinemaTheme.muted)
                } else {
                    Image(systemName: "play.circle.fill")
                        .font(.system(size: 22))
                        .foregroundStyle(CinemaTheme.accent)
                }
            }
            .padding(compactSources ? 10 : 14)
            .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(
                        playing?.url == stream.url ? CinemaTheme.accent : CinemaTheme.hairline,
                        lineWidth: playing?.url == stream.url ? 1.5 : 1
                    )
            )
        }
        .buttonStyle(CinemaPressStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(stream.addon). \(stream.title). \(stream.quality ?? "")")
        .accessibilityHint("تشغيل هذا المصدر")
        .contextMenu {
            Button {
                Haptics.tap()
                hide(stream)
            } label: {
                Label("إخفاء هذا الرابط", systemImage: "eye.slash")
            }
            Button {
                toggleAddon(stream.addon)
            } label: {
                Label("إطفاء \(stream.addon)", systemImage: "power")
            }
            Divider()
            Button {
                DownloadManager.shared.start(
                    title: item.name,
                    urlString: stream.url,
                    headers: stream.headers ?? [:]
                )
                Haptics.tap()
            } label: {
                Label("تنزيل على الجهاز", systemImage: "arrow.down.circle")
            }
            Button {
                UIPasteboard.general.string = stream.url
                Haptics.tap()
            } label: {
                Label("نسخ الرابط", systemImage: "doc.on.doc")
            }
        }
    }
}

// MARK: - Event log

/// A ring buffer of what the app did and what went wrong, kept on the device, so a
/// problem can be reported with its history instead of a guess.
@MainActor
final class EventLog: ObservableObject {
    static let shared = EventLog()

    struct Entry: Identifiable, Codable {
        let id: String
        let at: Date
        let tag: String
        let text: String
    }

    @Published private(set) var entries: [Entry] = []

    private let limit = 200
    private let key = "cinemax.eventLog.v1"

    private init() {
        guard let data = UserDefaults.standard.data(forKey: key),
              let stored = try? JSONDecoder().decode([Entry].self, from: data) else { return }
        entries = stored
    }

    func add(_ tag: String, _ text: String) {
        entries.insert(Entry(id: UUID().uuidString, at: Date(), tag: tag, text: text), at: 0)
        if entries.count > limit {
            entries.removeLast(entries.count - limit)
        }
        persist()
    }

    func clear() {
        entries = []
        persist()
    }

    /// The whole history as plain text, ready to be pasted into a message.
    var text: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM-dd HH:mm:ss"
        return entries
            .map { "\(formatter.string(from: $0.at)) [\($0.tag)] \($0.text)" }
            .joined(separator: "\n")
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(entries) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }
}

// MARK: - Instant search

/// A small index of everything the app already knows about: the rows on screen,
/// the library, the viewer's lists and the downloads. Typing is answered from
/// memory before the addons reply, and it keeps answering with no network.
@MainActor
final class SearchIndex: ObservableObject {
    static let shared = SearchIndex()

    private struct Entry {
        let item: CinemaItem
        let normalized: String
        let words: [String]
        /// The synopsis, folded: an Arabic description is what makes an Arabic
        /// query find a title whose name is still in English.
        let about: String
    }

    @Published private(set) var count = 0

    private var entries: [String: Entry] = [:]
    private var builtAt: Date?

    /// Rebuilds at most every two minutes, so it tracks new rows without work.
    func refresh() {
        if let builtAt, Date().timeIntervalSince(builtAt) < 120 { return }
        rebuild()
    }

    func rebuild() {
        var fresh: [String: Entry] = [:]
        func add(_ item: CinemaItem) {
            guard !item.name.isEmpty else { return }
            let normalized = Self.normalize(item.name)
            guard !normalized.isEmpty else { return }
            fresh[item.id] = Entry(
                item: item,
                normalized: normalized,
                words: normalized.split(whereSeparator: { $0 == " " || $0 == "-" || $0 == ":" }).map(String.init),
                about: Self.normalize(item.overview ?? "")
            )
        }
        HomeModel.shared.shelves.forEach { $0.items.forEach(add) }
        if let smart = HomeModel.shared.smartShelf { smart.items.forEach(add) }
        LibraryModel.shared.favorites.forEach(add)
        LibraryModel.shared.progress.forEach { add($0.item) }
        ListsStore.shared.lists.forEach { $0.items.forEach(add) }
        entries = fresh
        count = fresh.count
        builtAt = Date()
    }

    /// Folds the letters Arabic is typed with and without: hamza, taa marbuta,
    /// alef maqsura, diacritics and tatweel all end up as one letter. It is
    /// shared with the self test, which proves the folding before a search runs.
    static func normalize(_ text: String) -> String {
        var value = text.folding(
            options: [.diacriticInsensitive, .caseInsensitive, .widthInsensitive],
            locale: nil
        )
        let folds = [
            ("أ", "ا"), ("إ", "ا"), ("آ", "ا"), ("ٱ", "ا"),
            ("ة", "ه"), ("ى", "ي"), ("ؤ", "و"), ("ئ", "ي"), ("ـ", "")
        ]
        for (from, to) in folds {
            value = value.replacingOccurrences(of: from, with: to)
        }
        value = value.filter { character in
            guard let scalar = character.unicodeScalars.first else { return false }
            return !(0x064B...0x0652).contains(scalar.value)
        }
        return value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func search(_ query: String, limit: Int = 24) -> [CinemaItem] {
        let needle = Self.normalize(query.trimmingCharacters(in: .whitespacesAndNewlines))
        guard needle.count >= 2 else { return [] }
        var scored: [(item: CinemaItem, score: Double)] = []
        for entry in entries.values {
            guard let score = match(needle, entry) else { continue }
            scored.append((entry.item, score))
        }
        return scored
            .sorted {
                $0.score == $1.score
                    ? $0.item.name.localizedCompare($1.item.name) == .orderedAscending
                    : $0.score > $1.score
            }
            .prefix(limit)
            .map(\.item)
    }

    private func match(_ needle: String, _ entry: Entry) -> Double? {
        let rating = Double(entry.item.rating?.replacingOccurrences(of: ",", with: ".") ?? "") ?? 0
        if entry.normalized == needle { return 120 }
        if entry.normalized.hasPrefix(needle) { return 100 + rating / 100 }
        if entry.words.contains(where: { $0.hasPrefix(needle) }) { return 80 + rating / 100 }
        if entry.normalized.contains(needle) { return 60 + rating / 100 }
        if entry.words.contains(where: { $0.contains(needle) }) { return 45 + rating / 100 }
        // A title whose name is still English is found by its translated Arabic
        // synopsis, lower than a name hit but above the loose letter order.
        if !entry.about.isEmpty, entry.about.contains(needle) { return 30 + rating / 100 }
        // Every typed letter appears in order: this forgives a missed vowel or two.
        if needle.count >= 3, isSubsequence(needle, of: entry.normalized) { return 20 + rating / 100 }
        return nil
    }

    private func isSubsequence(_ needle: String, of haystack: String) -> Bool {
        var cursor = haystack.startIndex
        for character in needle {
            guard let found = haystack[cursor...].firstIndex(of: character) else { return false }
            cursor = haystack.index(after: found)
        }
        return true
    }
}

// MARK: - Search

@MainActor
final class SearchModel: ObservableObject {
    static let shared = SearchModel()

    @Published var results: [CinemaItem] = []
    @Published var loading = false
    @Published var error: String?
    /// How many sources have answered the live search so far.
    @Published var answered = 0

    private var task: Task<Void, Never>?
    private var generation = 0

    /// Answers a query from every installed source at once, and paints each
    /// source's hits the moment it arrives instead of waiting for the slowest one.
    /// A newer query cancels the run before it, so an old answer can never
    /// replace a fresh one.
    func run(_ query: String) async {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.count >= 2 else {
            stop()
            results = []
            lastQuery = nil
            return
        }
        // The same search within a minute is answered from memory. The spinner is
        // stopped with it: before this it stayed on while nothing was fetched,
        // and the "يبحث في كل المصادر" line sat under a list already painted.
        if let last = lastQuery, last.text == text.lowercased(),
           Date().timeIntervalSince(last.at) < 60 {
            task?.cancel()
            task = nil
            loading = false
            answered = 0
            error = nil
            results = last.results
            return
        }
        stop()
        generation += 1
        let current = generation
        let token = "search-\(current)"
        loading = true
        error = nil
        answered = 0
        let running = Task { [weak self] in
            guard let self else { return }
            let found = await CinemaAPI.shared.searchStreaming(text, token: token) { partial in
                Task { @MainActor in
                    guard self.generation == current else { return }
                    self.results = partial
                    self.answered += 1
                }
            }
            await MainActor.run {
                guard self.generation == current else { return }
                self.results = found
                self.lastQuery = (text.lowercased(), found, Date())
                self.loading = false
            }
        }
        task = running
        await running.value
    }

    /// Stops the live search: the sources behind the old query are cancelled
    /// instead of finishing an answer nobody is waiting for.
    func stop() {
        task?.cancel()
        task = nil
        CinemaAPI.shared.cancelSearch("search-\(generation)")
        loading = false
    }

    private var lastQuery: (text: String, results: [CinemaItem], at: Date)?
}

/// The one thing that makes an Arabic query answer: a free TMDB key. The source
/// addons index their catalogues by their English titles, so without a key an
/// Arabic search comes back empty for a reason the screen should say.
struct ArabicSearchHint: View {
    var onSettings: () -> Void = {}

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "character.book.closed")
                .font(.system(size: 26, weight: .light))
                .foregroundStyle(CinemaTheme.accent)
            Text("العربية تحتاج مفتاح TMDB")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(.white)
            Text("إضافات المصادر تُبحث بعناوينها الإنجليزية. أضف مفتاح TMDB المجاني من الإعدادات ← TMDB لتعود العناوين العربية بوصفها المترجم. والبحث داخل ما حمّلته يعمل بلا مفتاح.")
                .font(.system(size: 13))
                .foregroundStyle(CinemaTheme.muted)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
            Button(action: onSettings) {
                Label("افتح الإعدادات", systemImage: "arrow.up.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.black)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 9)
                    .background(CinemaTheme.accent, in: Capsule())
            }
            .buttonStyle(CinemaPressStyle())
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .background(
            CinemaTheme.surface,
            in: RoundedRectangle(cornerRadius: 20, style: .continuous)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
        )
        .padding(.horizontal, 20)
    }
}

struct SearchView: View {
    @State private var query = ""
    @ObservedObject private var model = SearchModel.shared
    @State private var detailItem: CinemaItem?
    @Environment(\.dismiss) private var dismiss
    /// The search opens with the keyboard up: it is a sheet for searching, and
    /// making the viewer tap the field first is a step for nothing.
    @FocusState private var typing: Bool
    @State private var listItem: CinemaItem?
    @State private var searchTask: Task<Void, Never>?
    @State private var history: [String] = CinemaxPrefs.searchHistory
    @ObservedObject private var index = SearchIndex.shared
    @State private var instant: [CinemaItem] = []
    @AppStorage("cinemax.searchKind") private var kind = "all"
    @AppStorage("cinemax.searchSort") private var sort = "best"

    /// True when the query is typed in Arabic, which the source addons rarely
    /// index: their catalogues answer by their English titles.
    private var arabicTyped: Bool {
        query.unicodeScalars.contains { (0x0600...0x06FF).contains($0.value) }
    }

    private var columns: [GridItem] {
        [GridItem(.adaptive(minimum: CinemaxPrefs.posterMinWidth), spacing: 12)]
    }

    private var visible: [CinemaItem] {
        switch kind {
        case "movie": return model.results.filter { $0.type == "movie" }
        case "series": return model.results.filter { $0.type == "series" }
        case "channel": return model.results.filter { $0.type == "channel" || $0.type == "tv" }
        default: return model.results
        }
    }

    /// Best match first: prefix, then contained, then word matches, then rating.
    private var ranked: [CinemaItem] {
        let list = visible
        switch sort {
        case "az":
            return list.sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
        case "new":
            return list.sorted { yearValue($0) > yearValue($1) }
        default:
            return list.sorted { score($0) > score($1) }
        }
    }

    private func yearValue(_ item: CinemaItem) -> Int {
        guard let raw = item.releaseInfo else { return 0 }
        return Int(raw.prefix(4)) ?? 0
    }

    private func score(_ item: CinemaItem) -> Double {
        // The same folding the local index uses, so «أَلْف» and «الف» rank alike.
        let text = SearchIndex.normalize(query)
        guard !text.isEmpty else { return 0 }
        let name = SearchIndex.normalize(item.name)
        var value = 0.0
        if name.hasPrefix(text) { value += 6 }
        if name.contains(text) { value += 4 }
        for token in text.split(separator: " ") where name.contains(token) {
            value += 1.5
        }
        // An Arabic synopsis from TMDB finds a title whose name is English.
        let about = SearchIndex.normalize(item.overview ?? "")
        if !about.isEmpty, about.contains(text) { value += 1 }
        if let rating = item.rating,
           let number = Double(rating.replacingOccurrences(of: ",", with: ".")) {
            value += number / 10
        }
        return value
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .foregroundStyle(CinemaTheme.muted)
                        TextField("ابحث عن فيلم أو مسلسل", text: $query)
                            .textFieldStyle(.plain)
                            .foregroundStyle(.white)
                            .submitLabel(.search)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                            .focused($typing)
                            .onSubmit {
                                Haptics.tap()
                                searchTask?.cancel()
                                searchTask = Task {
                                    await model.run(query)
                                    if query.trimmingCharacters(in: .whitespaces).count >= 2,
                                       !model.results.isEmpty {
                                        CinemaxPrefs.rememberSearch(query)
                                        history = CinemaxPrefs.searchHistory
                                    }
                                }
                            }
                        if !query.isEmpty {
                            Button {
                                Haptics.tap()
                                // The sources behind the cleared query keep
                                // working otherwise, and their late answer would
                                // repaint the list the viewer just emptied.
                                model.stop()
                                query = ""
                                model.results = []
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(.ultraThinMaterial, in: Capsule())
                    .overlay(Capsule().strokeBorder(CinemaTheme.hairline, lineWidth: 1))
                    .padding(.horizontal, 20)

                    if !history.isEmpty && query.isEmpty {
                        recentSearches
                    }

                    kindChips

                    sortChips

                    if !instantResults.isEmpty {
                        instantResultsSection
                    }

                    if model.loading {
                        HStack(spacing: 8) {
                            ProgressView().tint(CinemaTheme.accent)
                            Text(model.answered == 0
                                 ? "يبحث في كل المصادر…"
                                 : "يُتابع · \(model.results.count) نتيجة من \(model.answered) مصدرًا")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundStyle(CinemaTheme.muted)
                            Spacer()
                            Button("إيقاف") {
                                Haptics.tap()
                                model.stop()
                            }
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 20)
                    }

                    if let error = model.error, ranked.isEmpty, !model.loading {
                        Text(error).font(.system(size: 13)).foregroundStyle(CinemaTheme.muted).padding(20)
                    } else if ranked.isEmpty {
                        if !model.loading {
                            if arabicTyped, CinemaAPI.shared.tmdbKey.isEmpty {
                                ArabicSearchHint {
                                    typing = false
                                    dismiss()
                                    // The sheet closes first, then the settings
                                    // screen opens where the key lives.
                                    CinemaNavigator.shared.tab = 5
                                }
                            } else {
                                emptyState
                            }
                        }
                    } else {
                        Text("\(ranked.count) نتيجة" + (model.loading ? " حتى الآن" : "") + (model.answered > 0 ? " · \(model.answered) مصدرًا" : ""))
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(CinemaTheme.muted)
                            .padding(.horizontal, 20)
                        LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                            ForEach(ranked) { item in
                                PosterCard(
                                    item: item,
                                    width: CinemaxPrefs.posterMinWidth,
                                    saved: HomeModel.shared.favorites.contains(item.id),
                                    onLongClick: {
                                        Haptics.tap()
                                        HomeModel.shared.toggle(item)
                                    },
                                    onOpen: { detailItem = item }
                                )
                                .contextMenu {
                                    Button {
                                        Haptics.tap()
                                        listItem = item
                                    } label: {
                                        Label("أضف إلى قائمة", systemImage: "text.badge.plus")
                                    }
                                    Button {
                                        Haptics.tap()
                                        HomeModel.shared.toggle(item)
                                    } label: {
                                        Label(
                                            HomeModel.shared.favorites.contains(item.id)
                                                ? "إزالة من المحفوظة"
                                                : "أضف إلى المحفوظة",
                                            systemImage: "heart"
                                        )
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                    Spacer(minLength: 120)
                }
                .padding(.top, 8)
            }
            .background(CinemaTheme.canvas.ignoresSafeArea())
            .scrollDismissesKeyboard(.immediately)
            .navigationTitle("البحث")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        typing = false
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
            }
            .task {
                index.refresh()
                typing = true
            }
            .onChange(of: query) { value in
                index.refresh()
                instant = index.search(value)
                searchTask?.cancel()
                searchTask = Task {
                    try? await Task.sleep(nanoseconds: 320_000_000)
                    guard !Task.isCancelled else { return }
                    await model.run(value)
                    if value.trimmingCharacters(in: .whitespaces).count >= 2, !model.results.isEmpty {
                        CinemaxPrefs.rememberSearch(value)
                        history = CinemaxPrefs.searchHistory
                    }
                }
            }
        }
        .fullScreenCover(item: $detailItem) { DetailView(item: $0) }
        .sheet(item: $listItem) { ListsPickerSheet(item: $0) }
    }

    /// What the index found, minus anything the addons already returned.
    private var instantResults: [CinemaItem] {
        let known = Set(model.results.map(\.id))
        return instant.filter { !known.contains($0.id) }
    }

    private var instantResultsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 10))
                    .foregroundStyle(CinemaTheme.accent)
                Text("فوري من مكتبتك وإضافاتك")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)
                Text("\(instantResults.count)")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)
                Spacer()
            }
            .padding(.horizontal, 20)

            LazyVGrid(columns: columns, alignment: .leading, spacing: 12) {
                ForEach(instantResults) { item in
                    PosterCard(
                        item: item,
                        width: CinemaxPrefs.posterMinWidth,
                        saved: HomeModel.shared.favorites.contains(item.id),
                        onOpen: { detailItem = item }
                    )
                    .contextMenu {
                        Button {
                            Haptics.tap()
                            listItem = item
                        } label: {
                            Label("أضف إلى قائمة", systemImage: "text.badge.plus")
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
        }
    }

    private var recentSearches: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("آخر ما بحثت عنه")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)
                Spacer()
                Button("مسح") {
                    CinemaxPrefs.clearSearchHistory()
                    history = []
                }
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(CinemaTheme.muted)
            }
            .padding(.horizontal, 20)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(history, id: \.self) { entry in
                        Button {
                            Haptics.select()
                            query = entry
                        } label: {
                            Text(entry)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(CinemaTheme.surface, in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button(role: .destructive) {
                                Haptics.tap()
                                CinemaxPrefs.removeSearch(entry)
                                history = CinemaxPrefs.searchHistory
                            } label: {
                                Label("حذف هذا البحث", systemImage: "trash")
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
        }
    }

    private var kindChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach([("all", "الكل"), ("movie", "أفلام"), ("series", "مسلسلات"), ("channel", "قنوات")], id: \.0) { entry in
                    Button {
                        Haptics.select()
                        withAnimation(.easeInOut(duration: 0.15)) { kind = entry.0 }
                    } label: {
                        Text(entry.1)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(kind == entry.0 ? .black : .white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background {
                                Capsule().fill(kind == entry.0 ? CinemaTheme.accent : Color.white.opacity(0.08))
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 20)
        }
    }

    private var sortChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach([("best", "الأفضل"), ("new", "الأحدث"), ("az", "أبجدي")], id: \.0) { entry in
                    Button {
                        Haptics.select()
                        withAnimation(.easeInOut(duration: 0.15)) { sort = entry.0 }
                    } label: {
                        Text(entry.1)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(sort == entry.0 ? .black : .white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background {
                                Capsule().fill(
                                    sort == entry.0 ? CinemaTheme.accent : Color.white.opacity(0.08)
                                )
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 20)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "sparkle.magnifyingglass")
                .font(.system(size: 30, weight: .light))
                .foregroundStyle(CinemaTheme.accent)
            Text(query.isEmpty ? "اكتب حرفين على الأقل للبحث في كل إضافاتك" : "لا نتائج لهذا البحث")
                .font(.system(size: 14))
                .foregroundStyle(CinemaTheme.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }
}

// MARK: - Library

@MainActor
final class LibraryModel: ObservableObject {
    static let shared = LibraryModel()

    @Published var favorites: [CinemaItem] = []
    @Published var progress: [ProgressItem] = []
    @Published var segment = 0

    func load() async {
        async let favoritesTask = CinemaAPI.shared.favorites()
        async let progressTask = CinemaAPI.shared.progress()
        favorites = await favoritesTask
        progress = await progressTask
    }
}

struct LibraryView: View {
    @ObservedObject private var model = LibraryModel.shared
    @ObservedObject private var lists = ListsStore.shared
    @State private var detailItem: CinemaItem?
    @State private var listItem: CinemaItem?
    @AppStorage("cinemax.librarySort") private var sort = "recent"
    @State private var query = ""
    @State private var typeFilter = "all"

    private let typeFilters: [(id: String, title: String)] = [
        ("all", "الكل"), ("movie", "أفلام"), ("series", "مسلسلات")
    ]

    private var columns: [GridItem] {
        [GridItem(.adaptive(minimum: CinemaxPrefs.posterMinWidth), spacing: 12)]
    }

    private var favorites: [CinemaItem] {
        let text = query.trimmingCharacters(in: .whitespaces)
        let scoped = model.favorites.filter(matchesScope)
        let filtered = text.isEmpty
            ? scoped
            : scoped.filter { $0.name.localizedCaseInsensitiveContains(text) }
        switch sort {
        case "az": return filtered.sorted { $0.name < $1.name }
        case "rating": return filtered.sorted { ratingValue($0) > ratingValue($1) }
        default: return filtered
        }
    }

    /// The sort button cycles newest → alphabetical → best rated.
    private var sortLabel: String {
        switch sort {
        case "az": return "أبجدي"
        case "rating": return "التقييم"
        case "remaining": return "المتبقي"
        default: return "الأحدث"
        }
    }

    private func ratingValue(_ item: CinemaItem) -> Double {
        guard let raw = item.rating else { return 0 }
        return Double(raw.replacingOccurrences(of: ",", with: ".")) ?? 0
    }

    private var progress: [ProgressItem] {
        let text = query.trimmingCharacters(in: .whitespaces)
        let scoped = model.progress.filter { matchesScope($0.item) }
        let filtered = text.isEmpty
            ? scoped
            : scoped.filter { $0.item.name.localizedCaseInsensitiveContains(text) }
        switch sort {
        case "az": return filtered.sorted { $0.item.name < $1.item.name }
        case "remaining": return filtered.sorted { $0.remainingMinutes < $1.remainingMinutes }
        default: return filtered
        }
    }

    /// Narrows the library to films or series, alongside the free-text search.
    private func matchesScope(_ item: CinemaItem) -> Bool {
        typeFilter == "all" || item.type == typeFilter
    }

    private func scopeSuffix(_ id: String) -> String {
        let source = model.segment == 1 ? model.progress.map(\.item) : model.favorites
        let count = id == "all" ? source.count : source.filter { $0.type == id }.count
        return count > 0 ? " · \(count)" : ""
    }

    /// A saved title that is also started shows its progress bar here.
    private func resumeFraction(_ item: CinemaItem) -> Double {
        model.progress.first { $0.item.id == item.id }?.fraction ?? 0
    }

    private var filterNotice: String? {
        let source = model.segment == 1 ? model.progress.map(\.item) : model.favorites
        guard !source.isEmpty, favorites.isEmpty, progress.isEmpty else { return nil }
        return "لا نتائج لهذا الفلتر"
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    Picker("", selection: $model.segment) {
                        Text("المحفوظة (\(model.favorites.count))").tag(0)
                        Text("قيد المشاهدة (\(model.progress.count))").tag(1)
                        Text("قوائمي (\(lists.lists.count))").tag(3)
                        Text("التنزيلات (\(DownloadManager.shared.jobs.count))").tag(2)
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, 20)

                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                        TextField("ابحث في مكتبتك", text: $query)
                            .textFieldStyle(.plain)
                            .font(.system(size: 12))
                            .foregroundStyle(.white)
                        Button {
                            Haptics.select()
                            withAnimation(.easeInOut(duration: 0.15)) {
                                switch sort {
                                case "recent": sort = "az"
                                case "az": sort = "rating"
                                case "rating": sort = "remaining"
                                default: sort = "recent"
                                }
                            }
                        } label: {
                            Text(sortLabel)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(CinemaTheme.accent)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(CinemaTheme.accent.opacity(0.14), in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(CinemaTheme.surface, in: Capsule())
                    .padding(.horizontal, 20)

                    if model.segment == 0 || model.segment == 1 {
                    HStack(spacing: 8) {
                        ForEach(typeFilters, id: \.id) { filter in
                            Button {
                                Haptics.select()
                                withAnimation(.easeInOut(duration: 0.15)) { typeFilter = filter.id }
                            } label: {
                                Text(filter.title + scopeSuffix(filter.id))
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(typeFilter == filter.id ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background {
                                        Capsule().fill(
                                            typeFilter == filter.id ? CinemaTheme.accent : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    }

                    if model.segment == 3 {
                        ListsView(query: $query)
                    } else if model.segment == 2 {
                        DownloadsView()
                    } else if model.segment == 0 {
                        if favorites.isEmpty {
                            empty(
                                filterNotice ?? "لا شيء محفوظ بعد",
                                filterNotice == nil ? "اضغط ♥ على أي عنوان ليظهر هنا." : "غيّر الفلتر أو امسح البحث."
                            )
                        } else {
                            LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                                ForEach(favorites) { item in
                                    PosterCard(
                                        item: item,
                                        width: CinemaxPrefs.posterMinWidth,
                                        progress: resumeFraction(item),
                                        saved: true,
                                        onLongClick: {
                                            Haptics.tap()
                                            listItem = item
                                        },
                                        onOpen: { detailItem = item }
                                    )
                                    .contextMenu {
                                        Button {
                                            Haptics.tap()
                                            listItem = item
                                        } label: {
                                            Label("أضف إلى قائمة", systemImage: "text.badge.plus")
                                        }
                                        Button(role: .destructive) {
                                            Haptics.tap()
                                            removeFavorite(item)
                                        } label: {
                                            Label("إزالة من المحفوظة", systemImage: "heart.slash")
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    } else {
                        if progress.isEmpty {
                            empty(
                                filterNotice ?? "لا شيء قيد المشاهدة",
                                filterNotice == nil ? "أي فيلم تبدأه يظهر هنا مع موضع التوقف." : "غيّر الفلتر أو امسح البحث."
                            )
                        } else {
                            HStack(spacing: 6) {
                                Text("\(progress.count) عنوانًا")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(.white)
                                Text("· \(progress.reduce(0) { $0 + $1.remainingMinutes }) دقيقة متبقية")
                                    .font(.system(size: 11))
                                    .foregroundStyle(CinemaTheme.muted)
                                Spacer()
                            }
                            .padding(.horizontal, 20)
                            LazyVStack(spacing: 12) {
                                ForEach(progress) { entry in
                                    ContinueCard(entry: entry) { detailItem = entry.item }
                                        .contextMenu {
                                            Button {
                                                Haptics.tap()
                                                listItem = entry.item
                                            } label: {
                                                Label("أضف إلى قائمة", systemImage: "text.badge.plus")
                                            }
                                            Button(role: .destructive) {
                                                Haptics.tap()
                                                removeResume(entry)
                                            } label: {
                                                Label("إزالة من قيد المشاهدة", systemImage: "trash")
                                            }
                                        }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                    Spacer(minLength: 120)
                }
                .padding(.top, 8)
            }
            .background(CinemaTheme.canvas.ignoresSafeArea())
            .navigationTitle("مكتبتي")
            .task { await model.load() }
            .refreshable { await model.load() }
        }
        .fullScreenCover(item: $detailItem) { DetailView(item: $0) }
        .sheet(item: $listItem) { ListsPickerSheet(item: $0) }
    }

    private func removeFavorite(_ item: CinemaItem) {
        _ = CinemaAPI.shared.toggleFavorite(item)
        model.favorites.removeAll { $0.id == item.id }
        HomeModel.shared.favorites.remove(item.id)
    }

    /// Forgets one stop position, for a title the viewer has given up on.
    private func removeResume(_ entry: ProgressItem) {
        model.progress.removeAll { $0.id == entry.id }
        HomeModel.shared.resetCache()
        Task {
            _ = await CinemaAPI.shared.removeProgress(itemId: entry.item.id, videoLabel: entry.episodeLabel)
            await model.load()
        }
    }

    private func empty(_ title: String, _ subtitle: String) -> some View {
        VStack(spacing: 8) {
            Text(title).font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
            Text(subtitle).font(.system(size: 13)).foregroundStyle(CinemaTheme.muted).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
        .padding(.horizontal, 30)
    }
}

// MARK: - Settings

/// The previous single-scroll settings page, kept as the source of the cards the
/// new grouped screens reuse. The live settings screen is `SettingsView` below.
struct LegacySettingsPage: View {
    @AppStorage("cinemax.design") private var design = "cinematic"
    @AppStorage("cinemax.accent") private var accent = "raif"
    @AppStorage("cinemax.oled") private var oled = false
    @AppStorage("cinemax.reduceMotion") private var reduceMotion = false
    @AppStorage("cinemax.autoplayNext") private var autoplayNext = true
    @State private var addons: [InstalledAddonRow] = []
    @State private var suggested: [SuggestedAddonRow] = []
    @State private var addonURL = ""
    @State private var addonBusy = false
    @State private var addonMessage: String?
    @State private var tmdbKey = ""
    @State private var keyMessage: String?

    private let designs: [(id: String, title: String, detail: String)] = [
        ("cinematic", "سينمائي زجاجي", "هيرو بعرض الشاشة، وهج بلون البوستر، شريط عائم زجاجي"),
        ("wall", "جدار بوسترات", "بحث أولًا وشبكات بوسترات كثيفة وعدّادات، بلا هيرو"),
        ("panorama", "بانورامي", "خلفية واحدة غامرة ثم بطاقات عريضة 16:9")
    ]

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(Brand.name.uppercased())
                            .font(.system(size: 11, weight: .semibold))
                            .tracking(3)
                            .foregroundStyle(CinemaTheme.muted)
                        Text("الإعدادات")
                            .font(.system(size: 32, weight: .heavy))
                            .foregroundStyle(.white)
                        Text("الإصدار \(CinemaAPI.shared.versionLabel)")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)

                    card(title: "المظهر") {
                        Text("لون الواجهة")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(CinemaTheme.muted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 58), spacing: 10)], spacing: 14) {
                            ForEach(AccentPalette.entries, id: \.id) { entry in
                                Button {
                                    Haptics.select()
                                    accent = entry.id
                                } label: {
                                    VStack(spacing: 6) {
                                        ZStack {
                                            Circle()
                                                .fill(AccentPalette.color(entry.id))
                                                .frame(width: 42, height: 42)
                                            if accent == entry.id {
                                                Circle()
                                                    .strokeBorder(Color.white, lineWidth: 2)
                                                    .frame(width: 52, height: 52)
                                                Image(systemName: "checkmark")
                                                    .font(.system(size: 14, weight: .bold))
                                                    .foregroundStyle(.black)
                                            }
                                        }
                                        .frame(height: 54)
                                        Text(entry.name)
                                            .font(.system(size: 10))
                                            .foregroundStyle(accent == entry.id ? .white : CinemaTheme.muted)
                                            .lineLimit(1)
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        Toggle(isOn: $oled) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("أسود AMOLED")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(.white)
                                Text("خلفية سوداء خالصة بدل الأسود الناعم")
                                    .font(.system(size: 11))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                        }
                        .tint(CinemaTheme.accent)
                        Toggle(isOn: $reduceMotion) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("تقليل الحركة")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(.white)
                                Text("يوقف الحركات التلقائية وانتقالات البطاقات")
                                    .font(.system(size: 11))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                        }
                        .tint(CinemaTheme.accent)
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("لغة التطبيق")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(.white)
                                Text("لغة الجهاز")
                                    .font(.system(size: 11))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            Spacer()
                            Text("العربية")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    }

                    card(title: "التصميم") {
                        VStack(spacing: 8) {
                            ForEach(designs, id: \.id) { option in
                                Button {
                                    Haptics.select()
                                    design = option.id
                                } label: {
                                    HStack(spacing: 12) {
                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(option.title)
                                                .font(.system(size: 15, weight: .semibold))
                                                .foregroundStyle(.white)
                                            Text(option.detail)
                                                .font(.system(size: 11))
                                                .foregroundStyle(CinemaTheme.muted)
                                                .multilineTextAlignment(.leading)
                                        }
                                        Spacer()
                                        Image(systemName: design == option.id ? "checkmark.circle.fill" : "circle")
                                            .font(.system(size: 20))
                                            .foregroundStyle(design == option.id ? CinemaTheme.accent : CinemaTheme.muted)
                                    }
                                    .padding(12)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(
                                        design == option.id
                                            ? CinemaTheme.accent.opacity(0.14)
                                            : CinemaTheme.elevated,
                                        in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    card(title: "التشغيل") {
                        Toggle(isOn: $autoplayNext) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("تشغيل الحلقة التالية تلقائيًا")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(.white)
                                Text("ينتقل المشغّل إلى الحلقة التالية حين تنتهي الحالية")
                                    .font(.system(size: 11))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                        }
                        .tint(CinemaTheme.accent)
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("المشغّل")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(.white)
                                Text("libmpv — يدعم MKV وAVI وHEVC والترجمة المدمجة")
                                    .font(.system(size: 11))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            Spacer()
                            Text("MPV")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    }

                    card(title: "الإضافات") {
                        if addons.isEmpty {
                            Text("لا إضافة مثبتة بعد. الصق رابط manifest أو ثبّت المصادر الجاهزة.")
                                .font(.system(size: 12))
                                .foregroundStyle(CinemaTheme.muted)
                        } else {
                            ForEach(addons) { addon in
                                HStack(spacing: 10) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(addon.name)
                                            .font(.system(size: 14, weight: .semibold))
                                            .foregroundStyle(.white)
                                            .lineLimit(1)
                                        Text("الإصدار \(addon.version)")
                                            .font(.system(size: 11))
                                            .foregroundStyle(CinemaTheme.muted)
                                    }
                                    Spacer()
                                    Button {
                                        Haptics.tap()
                                        CinemaAPI.shared.removeAddon(addon.url)
                                        addons.removeAll { $0.url == addon.url }
                                        addonMessage = "أُزيلت \(addon.name)"
                                    } label: {
                                        Image(systemName: "trash")
                                            .font(.system(size: 14, weight: .semibold))
                                            .foregroundStyle(CinemaTheme.favourite)
                                            .frame(width: 34, height: 34)
                                            .background(CinemaTheme.elevated, in: Circle())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }

                        HStack(spacing: 8) {
                            TextField("رابط manifest https://…", text: $addonURL)
                                .textFieldStyle(.plain)
                                .font(.system(size: 12))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(CinemaTheme.elevated, in: Capsule())
                            Button(addonBusy ? "…" : "تثبيت") {
                                Task { await installAddon() }
                            }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(CinemaTheme.accent, in: Capsule())
                            .disabled(addonBusy || addonURL.trimmingCharacters(in: .whitespaces).isEmpty)
                        }

                        Button {
                            Task { await installAll() }
                        } label: {
                            Text(addonBusy ? "جارٍ التثبيت…" : "ثبّت المصادر الجاهزة (\(suggested.count))")
                                .font(.system(size: 13, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 11)
                                .background(Color.white.opacity(0.1), in: Capsule())
                                .foregroundStyle(.white)
                        }
                        .buttonStyle(CinemaPressStyle())
                        .disabled(addonBusy || suggested.isEmpty)

                        if let addonMessage {
                            Text(addonMessage)
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    }
                    .task { await loadAddons() }

                    card(title: "TMDB") {
                        Text("مفتاح TMDB (مجاني) يضيف أربعة صفوف كاملة بعناوين ووصف عربي وتقييمات حقيقية: الأكثر تداولًا، أفلام شائعة، مسلسلات شائعة، أعلى تقييمًا. بدون مفتاح تبقى إضافاتك فقط.")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                            .lineSpacing(3)
                        HStack(spacing: 8) {
                            TextField("مفتاح TMDB", text: $tmdbKey)
                                .textFieldStyle(.plain)
                                .font(.system(size: 12))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(CinemaTheme.elevated, in: Capsule())
                            Button("حفظ") {
                                CinemaAPI.shared.saveTmdbKey(tmdbKey.trimmingCharacters(in: .whitespacesAndNewlines))
                                keyMessage = "تم حفظ المفتاح — اسحب الرئيسية للتحديث"
                                Haptics.tap()
                            }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(CinemaTheme.accent, in: Capsule())
                        }
                        if let keyMessage {
                            Text(keyMessage)
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    }
                    .task { tmdbKey = CinemaAPI.shared.tmdbKey }

                    card(title: "عن التطبيق") {
                        Text("واجهة سينمائية سريعة مبنية بـ SwiftUI فوق نفس محرك المصادر: إضافات Stremio، مشغّل libmpv للـMKV، ترجمة مدمجة، ومتابعة مشاهدة.")
                            .font(.system(size: 13))
                            .foregroundStyle(CinemaTheme.muted)
                            .lineSpacing(4)
                    }

                    Spacer(minLength: 130)
                }
            }
            .background(CinemaTheme.canvas.ignoresSafeArea())
        }
    }

    private func loadAddons() async {
        addons = await CinemaAPI.shared.installedAddons()
        suggested = await CinemaAPI.shared.suggestedAddons()
    }

    private func installAddon() async {
        let url = addonURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !url.isEmpty else { return }
        addonBusy = true
        addonMessage = nil
        do {
            let name = try await CinemaAPI.shared.installAddon(url)
            addonURL = ""
            addonMessage = "تم تثبيت \(name)"
            addons = await CinemaAPI.shared.installedAddons()
        } catch {
            addonMessage = error.localizedDescription
        }
        addonBusy = false
    }

    private func installAll() async {
        addonBusy = true
        addonMessage = nil
        let json = await CinemaAPI.shared.installAllSuggested()
        addonBusy = false
        let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]
        let added = (object?["added"] as? NSNumber)?.intValue ?? 0
        let failed = (object?["failed"] as? NSNumber)?.intValue ?? 0
        addonMessage = failed > 0 ? "تم تثبيت \(added) وتعذّر \(failed)" : "تم تثبيت \(added) مصدرًا"
        addons = await CinemaAPI.shared.installedAddons()
    }

    private func card<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CinemaTheme.muted)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: CinemaTheme.cardRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: CinemaTheme.cardRadius, style: .continuous)
                .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
        )
        .padding(.horizontal, 20)
    }
}

// MARK: - Root

/// First launch: what the app needs before it can play anything, on one screen.
struct OnboardingView: View {
    var onAddons: () -> Void
    var onClose: () -> Void

    private let points: [(icon: String, title: String, detail: String)] = [
        (
            "puzzlepiece.extension.fill",
            "ثبّت إضافاتك",
            "المصادر الجاهزة تُثبَّت من الإعدادات ← الإضافات، أو الصق رابط manifest لأي إضافة Stremio."
        ),
        (
            "star.bubble.fill",
            "مفتاح TMDB المجاني",
            "يعطيك صفوفًا عربية بوصف وتقييم، و«أعمال مشابهة» في أسفل كل صفحة."
        ),
        (
            "play.rectangle.fill",
            "أي صيغة تعمل",
            "مشغّل libmpv يفتح MKV و HEVC مع الترجمة والصوت المدمج، وسرعة وتأخير ونسبة عرض."
        ),
        (
            "arrow.down.circle.fill",
            "نزّل للمشاهدة بلا شبكة",
            "تنزيل متوازٍ يُخبرك عند اكتماله، وتجده في مكتبتك ← التنزيلات."
        )
    ]

    var body: some View {
        ZStack {
            CinemaTheme.canvas.ignoresSafeArea()
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    Text(Brand.name.uppercased())
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(4)
                        .foregroundStyle(CinemaTheme.muted)
                    Text("قبل أول مشاهدة")
                        .font(.system(size: 30, weight: .heavy))
                        .foregroundStyle(.white)
                    Text("خطوات صغيرة، وبعدها كل شيء يعمل من داخل التطبيق.")
                        .font(.system(size: 13))
                        .foregroundStyle(CinemaTheme.muted)

                    VStack(spacing: 10) {
                        ForEach(points, id: \.title) { point in
                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: point.icon)
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                                    .frame(width: 34, height: 34)
                                    .background(
                                        CinemaTheme.accent.opacity(0.14),
                                        in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    )
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(point.title)
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(.white)
                                    Text(point.detail)
                                        .font(.system(size: 11))
                                        .foregroundStyle(CinemaTheme.muted)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(12)
                            .background(
                                CinemaTheme.surface,
                                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                            )
                        }
                    }

                    Button {
                        Haptics.tap()
                        onAddons()
                    } label: {
                        Text("افتح الإضافات")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(CinemaTheme.accent, in: Capsule())
                    }
                    .buttonStyle(CinemaPressStyle())

                    Button {
                        Haptics.tap()
                        onClose()
                    } label: {
                        Text("لاحقًا")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                }
                .padding(22)
            }
        }
        .preferredColorScheme(.dark)
    }
}

// MARK: - Siri and Shortcuts

// MARK: - Spotlight

/// Puts the viewer's own library into the system's search: a saved or started
/// title appears on the Spotlight screen and opens its page with one tap.
enum SpotlightIndexer {
    private static let prefix = "item:"

    static func reindex(favorites: [CinemaItem], progress: [ProgressItem], lists: [CinemaList]) {
        var seen = Set<String>()
        var entries: [CSSearchableItem] = []

        func add(_ item: CinemaItem, keyword: String) {
            guard seen.insert(item.id).inserted else { return }
            let attributes = CSSearchableItemAttributeSet(contentType: .movie)
            attributes.title = item.name
            attributes.contentDescription = [item.releaseInfo, item.overview]
                .compactMap { $0 }
                .joined(separator: " · ")
            attributes.keywords = [
                Brand.name,
                keyword,
                item.type == "series" ? "مسلسل" : "فيلم"
            ]
            if let poster = item.posterURL {
                attributes.thumbnailURL = poster
            }
            entries.append(
                CSSearchableItem(
                    uniqueIdentifier: prefix + item.id,
                    domainIdentifier: "cinemax.library",
                    attributeSet: attributes
                )
            )
        }

        progress.forEach { add($0.item, keyword: "قيد المشاهدة") }
        favorites.forEach { add($0, keyword: "محفوظ") }
        lists.forEach { list in
            list.items.forEach { add($0, keyword: list.name) }
        }
        guard !entries.isEmpty else { return }
        CSSearchableIndex.default().indexSearchableItems(entries) { _ in }
    }
}

/// A wish Siri leaves in the defaults; the app picks it up the moment it comes
/// forward and clears it, so a phrase never fires twice.
enum CinemaPendingCommand {
    private static let key = "cinemax.pendingCommand"

    static func put(_ value: String) {
        UserDefaults.standard.set(value, forKey: key)
    }

    static func take() -> String? {
        let value = UserDefaults.standard.string(forKey: key)
        if value != nil { UserDefaults.standard.removeObject(forKey: key) }
        return value
    }
}

/// "أكمل آخر ما شاهدت" — Siri and Shortcuts can say it in Arabic or English.
struct CinemaResumeIntent: AppIntent {
    static var title: LocalizedStringResource = "أكمل آخر ما شاهدت"
    static var description = IntentDescription("يفتح CINEMAX على آخر عنوان كنت تشاهده.")
    static var openAppWhenRun: Bool = true

    func perform() async throws -> some IntentResult {
        CinemaPendingCommand.put("resume")
        return .result()
    }
}

/// "فاجئني" — opens the app on a title the viewer has not started.
struct CinemaSurpriseIntent: AppIntent {
    static var title: LocalizedStringResource = "فاجئني بعنوان"
    static var description = IntentDescription("يفتح CINEMAX على عنوان لم تبدأه بعد.")
    static var openAppWhenRun: Bool = true

    func perform() async throws -> some IntentResult {
        CinemaPendingCommand.put("surprise")
        return .result()
    }
}

/// "ابحث" — brings the app forward on its search screen.
struct CinemaSearchIntent: AppIntent {
    static var title: LocalizedStringResource = "ابحث في CINEMAX"
    static var description = IntentDescription("يفتح شاشة البحث في CINEMAX.")
    static var openAppWhenRun: Bool = true

    func perform() async throws -> some IntentResult {
        CinemaPendingCommand.put("search")
        return .result()
    }
}

struct CinemaAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: CinemaResumeIntent(),
            phrases: [
                "أكمل آخر حلقة في \(.applicationName)",
                "Resume watching in \(.applicationName)"
            ],
            shortTitle: "أكمل المشاهدة",
            systemImageName: "play.circle.fill"
        )
        AppShortcut(
            intent: CinemaSurpriseIntent(),
            phrases: [
                "فاجئني في \(.applicationName)",
                "Surprise me in \(.applicationName)"
            ],
            shortTitle: "فاجئني",
            systemImageName: "dice.fill"
        )
        AppShortcut(
            intent: CinemaSearchIntent(),
            phrases: [
                "ابحث في \(.applicationName)",
                "Search \(.applicationName)"
            ],
            shortTitle: "ابحث",
            systemImageName: "magnifyingglass"
        )
    }
}

/// A tab asked for from a screen that cannot change the tab itself, such as the
/// Arabic search hint, which has to open the settings screen for a TMDB key.
@MainActor
final class CinemaNavigator: ObservableObject {
    static let shared = CinemaNavigator()

    @Published var tab: Int?
}

struct CinemaRootView: View {
    @AppStorage("cinemax.tab") private var selection = 0
    @AppStorage("cinemax.appLock") private var appLock = false
    @AppStorage("cinemax.corner") private var corner = "soft"
    @Environment(\.scenePhase) private var scenePhase
    @State private var unlocked = false
    @AppStorage("cinemax.accent") private var accent = "raif"
    @AppStorage("cinemax.oled") private var oled = false
    @AppStorage("cinemax.onboarded") private var onboarded = false
    @ObservedObject private var network = ConnectivityMonitor.shared
    @ObservedObject private var navigator = CinemaNavigator.shared
    @State private var showOnboarding = false
    @State private var voiceItem: CinemaItem?

    var body: some View {
        ZStack(alignment: .bottom) {
            CinemaTheme.canvas.ignoresSafeArea()
            Group {
                switch selection {
                case 0: HomeView(selection: $selection)
                case 1: HypeCatalogScreen(type: "series")
                case 2: HypeCatalogScreen(type: "movie")
                case 3: LibraryView()
                case 4: HypeRecentScreen()
                default: SettingsView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            // Rebuilds the screens when the accent or the canvas changes, so the new
            // colour reaches every view instead of only newly created ones.
            .id(accent + (oled ? "-oled" : "-soft") + "-" + corner)
            HypeTabBar(selection: $selection)
        }
        .overlay(alignment: .top) {
            if !network.online {
                HStack(spacing: 8) {
                    Image(systemName: "wifi.slash")
                        .font(.system(size: 12, weight: .semibold))
                    Text("لا يوجد اتصال — ما حفظته يبقى متاحًا")
                        .font(.system(size: 11, weight: .semibold))
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(CinemaTheme.favourite.opacity(0.9), in: Capsule())
                .padding(.top, 6)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: network.online)
        .preferredColorScheme(.dark)
        .environment(\.layoutDirection, .rightToLeft)
        .tint(CinemaTheme.accent)
        .task {
            // One move to the Hype interface: the coral accent and its home become
            // the house look, unless the viewer had picked a colour or a layout of
            // their own. The flag makes this run exactly once.
            if !UserDefaults.standard.bool(forKey: "cinemax.hype.v1") {
                UserDefaults.standard.set(true, forKey: "cinemax.hype.v1")
                if UserDefaults.standard.string(forKey: "cinemax.accent") == "raif" {
                    UserDefaults.standard.set("hype", forKey: "cinemax.accent")
                    accent = "hype"
                } else if UserDefaults.standard.string(forKey: "cinemax.accent") == nil {
                    accent = "hype"
                }
                if UserDefaults.standard.string(forKey: "cinemax.design") == nil
                    || UserDefaults.standard.string(forKey: "cinemax.design") == "cinematic" {
                    UserDefaults.standard.set("hype", forKey: "cinemax.design")
                }
            }
            // The Hype interface is what was asked for, so it is set once more
            // here for a layout left from an earlier build. A design the viewer
            // picks after this stays their own.
            if !UserDefaults.standard.bool(forKey: "cinemax.hype.v2") {
                UserDefaults.standard.set(true, forKey: "cinemax.hype.v2")
                UserDefaults.standard.set("hype", forKey: "cinemax.design")
                UserDefaults.standard.set("hype", forKey: "cinemax.accent")
                accent = "hype"
            }
            // The picture opens with the engine that reads the link: the system
            // player for what it plays natively (HLS, MP4) with the correct shape
            // in both orientations, libmpv for the containers only it reads. Set
            // once, so an engine the viewer picks afterwards stays their own.
            if !UserDefaults.standard.bool(forKey: "cinemax.engine.v1") {
                UserDefaults.standard.set(true, forKey: "cinemax.engine.v1")
                let engine = UserDefaults.standard.string(forKey: "cinemax.playerEngine")
                if engine == nil || engine == "mpv" {
                    UserDefaults.standard.set("auto", forKey: "cinemax.playerEngine")
                }
            }
            // A ratio or a zoom left by an older build is what stretches a
            // picture: the player no longer stores either, so a crop answered by
            // an earlier version is cleared here and the next film opens fitted.
            // "fill" is deliberately not allowed any more: cropping the picture
            // is what made it come out larger than the page in portrait.
            let allowedAspects = ["", "auto", "fit", "16:9", "4:3", "2.35:1"]
            if let stored = UserDefaults.standard.string(forKey: "cinemax.aspect"),
               !allowedAspects.contains(stored) {
                UserDefaults.standard.set("", forKey: "cinemax.aspect")
            }
            let storedZoom = (UserDefaults.standard.object(forKey: "cinemax.zoom") as? Double) ?? 0
            if storedZoom < -0.5 || storedZoom > 0.5 {
                UserDefaults.standard.set(0, forKey: "cinemax.zoom")
            }
            if !onboarded { showOnboarding = true }
            if appLock { authenticate() }
            handlePendingCommand()
            await LibraryModel.shared.load()
            // A paid service or a proxy behind an installed source is not free:
            // it is removed once, before the free set is offered, so the list
            // holds only what plays over http or peer-to-peer.
            if !UserDefaults.standard.bool(forKey: "cinemax.free.v1") {
                UserDefaults.standard.set(true, forKey: "cinemax.free.v1")
                await CuratedSources.removeUnfree()
            }
            // First launch: the free set is offered once, and an addon the viewer
            // removes afterwards is never put back behind their back.
            await CuratedSources.offerIfNeeded()
            HomeModel.shared.resetCache()
            SpotlightIndexer.reindex(
                favorites: LibraryModel.shared.favorites,
                progress: LibraryModel.shared.progress,
                lists: ListsStore.shared.lists
            )
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                if appLock { authenticate() }
                handlePendingCommand()
            }
        }
        .onChange(of: navigator.tab) { value in
            // A screen that cannot change the tab asks for one here, and the ask
            // is cleared so the same tab can be asked for again later.
            guard let value else { return }
            navigator.tab = nil
            selection = value
        }
        .overlay {
            if appLock, !unlocked {
                ZStack {
                    CinemaTheme.canvas.ignoresSafeArea()
                    VStack(spacing: 14) {
                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 40, weight: .light))
                            .foregroundStyle(CinemaTheme.accent)
                        Text("\(Brand.name) مقفل")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(.white)
                        Text("افتح بقفل الجهاز أو Face ID للمتابعة")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                            .multilineTextAlignment(.center)
                        Button {
                            Haptics.tap()
                            authenticate()
                        } label: {
                            Text("فتح")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(.black)
                                .padding(.horizontal, 26)
                                .padding(.vertical, 11)
                                .background(CinemaTheme.accent, in: Capsule())
                        }
                        .buttonStyle(CinemaPressStyle())
                    }
                    .padding(24)
                }
                .transition(.opacity)
            }
        }
        .sheet(isPresented: $showOnboarding) {
            OnboardingView(
                onAddons: {
                    onboarded = true
                    showOnboarding = false
                    selection = 3
                },
                onClose: {
                    onboarded = true
                    showOnboarding = false
                }
            )
        }
        .fullScreenCover(item: $voiceItem) { item in
            DetailView(item: item)
        }
    }

    /// Runs a wish left by Siri or the Shortcuts app: the last stop position, or a
    /// title the viewer has not begun. Nothing happens when neither exists.
    private func handlePendingCommand() {
        guard let command = CinemaPendingCommand.take() else { return }
        Task {
            await LibraryModel.shared.load()
            await HomeModel.shared.load()
            await MainActor.run {
                switch command {
                case "resume":
                    voiceItem = LibraryModel.shared.progress.first?.item
                case "surprise":
                    let started = Set(LibraryModel.shared.progress.map(\.item.id))
                    let all = HomeModel.shared.visibleShelves.flatMap(\.items)
                    let fresh = all.filter { !started.contains($0.id) }
                    voiceItem = (fresh.isEmpty ? all : fresh).randomElement()
                case "search":
                    selection = 1
                default:
                    // A title tapped in the system's search arrives as "item:<id>".
                    if command.hasPrefix("item:") {
                        let wanted = String(command.dropFirst("item:".count))
                        let pool = LibraryModel.shared.favorites
                            + LibraryModel.shared.progress.map(\.item)
                            + ListsStore.shared.lists.flatMap(\.items)
                        voiceItem = pool.first { $0.id == wanted }
                    }
                }
                if voiceItem == nil { Haptics.tap() }
            }
        }
    }

    /// Asks for the device passcode or Face ID; a device with neither stays open
    /// rather than locking the viewer out of their own app.
    private func authenticate() {
        let context = LAContext()
        var failure: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &failure) else {
            unlocked = true
            return
        }
        context.evaluatePolicy(
            .deviceOwnerAuthentication,
            localizedReason: "لفتح CINEMAX"
        ) { success, _ in
            Task { @MainActor in unlocked = success }
        }
    }
}


// MARK: - Home designs

/// The home surface, chosen in Settings → التصميم. Three complete directions are
/// shipped together so one can be adopted after comparing them on a device.
struct HomeView: View {
    @Binding var selection: Int
    @AppStorage("cinemax.design") private var design = "cinematic"

    var body: some View {
        switch design {
        case "hype":
            HypeHomeView(selection: $selection)
        case "wall":
            PosterWallHomeView(selection: $selection)
        case "panorama":
            PanoramaHomeView(selection: $selection)
        default:
            CinematicHomeView(selection: $selection)
        }
    }
}

/// The filter chips shared by every home design.
struct FilterChipsBar: View {
    @ObservedObject var model: HomeModel

    private let filters: [(id: String, title: String)] = [
        ("all", "الكل"), ("movie", "أفلام"), ("series", "مسلسلات"), ("channel", "قنوات")
    ]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(filters, id: \.id) { filter in
                    Button {
                        Haptics.select()
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { model.filter = filter.id }
                    } label: {
                        Text(filter.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(model.filter == filter.id ? .black : .white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 9)
                            .background {
                                Capsule()
                                    .fill(model.filter == filter.id ? CinemaTheme.accent : Color.white.opacity(0.08))
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 20)
        }
        .padding(.bottom, 8)
    }
}

// MARK: - Hype interface

/// The name the interface wears: ours, spelled here once, so the wordmark, the
/// settings header and the system's search all follow it. The look is Hype's; the
/// name on it stays CINEMAX.
enum Brand {
    static let name = "CINEMAX"
    static let first = "cine"
    static let second = "max"
}

/// The day's own film: one title, the same all day and in every launch, and a
/// different one tomorrow. It is seeded by the day rather than drawn by luck, so
/// the day has a film to open instead of a slot machine.
enum DailyFeature {
    /// The last pick and the day it belongs to: the band asks for it on every
    /// layout pass, and sorting the whole pool there would run hundreds of times
    /// a second for a value that only changes at midnight.
    private static var cached: (day: Int, count: Int, pick: CinemaItem?)?
    private static var weekday: (day: Int, label: String)?

    static func pick(from items: [CinemaItem], day: Date = Date()) -> CinemaItem? {
        let calendar = Calendar(identifier: .gregorian)
        let number = Int(calendar.startOfDay(for: day).timeIntervalSince1970 / 86_400)
        if let cached, cached.day == number, cached.count == items.count {
            return cached.pick
        }
        var pool = items.filter { !$0.name.isEmpty }
        guard !pool.isEmpty else {
            cached = (number, items.count, nil)
            return nil
        }
        pool.sort { $0.id < $1.id }
        let chosen = pool[abs(number) % pool.count]
        cached = (number, items.count, chosen)
        return chosen
    }

    /// The Arabic name of the day, so the band reads «فيلم اليوم · الخميس».
    /// The formatter and its answer are kept for the day: a formatter built on
    /// every pass is the second cost on the same band.
    static func label(_ day: Date = Date()) -> String {
        let calendar = Calendar(identifier: .gregorian)
        let number = Int(calendar.startOfDay(for: day).timeIntervalSince1970 / 86_400)
        if let weekday, weekday.day == number { return weekday.label }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ar")
        formatter.dateFormat = "EEEE"
        let label = formatter.string(from: day)
        weekday = (number, label)
        return label
    }
}

/// A slim band under the search: the day's film, its poster, and one tap to it.
struct DailyTitleBand: View {
    let item: CinemaItem
    let weekday: String
    let onOpen: () -> Void

    private var meta: String {
        var parts: [String] = []
        if let year = item.releaseInfo, !year.isEmpty { parts.append(year) }
        if let rating = item.rating, !rating.isEmpty { parts.append("★ \(rating)") }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 12) {
                RemoteImage(url: item.posterURL)
                    .frame(width: 44, height: 66)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 10, weight: .semibold))
                        Text("فيلم اليوم · \(weekday)")
                            .font(.system(size: 11, weight: .semibold))
                    }
                    .foregroundStyle(CinemaTheme.accent)
                    Text(item.name)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    if !meta.isEmpty {
                        Text(meta)
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.tertiary)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 22))
                    .foregroundStyle(.white.opacity(0.9))
            }
            .padding(10)
            .background(
                CinemaTheme.elevated,
                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
            )
        }
        .buttonStyle(CinemaPressStyle())
    }
}

/// Three titles for one evening: a film, a series and a third, drawn from the
/// rows already loaded. An evening is a decision, not a scroll: the sheet makes
/// it, and the line can be sent to whoever is coming.
struct WatchPartySheet: View {
    let pool: [CinemaItem]
    let onOpen: (CinemaItem) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var picks: [CinemaItem] = []

    private var text: String {
        let names = picks.map { "• \($0.name)" }.joined(separator: "\n")
        return "سهرة CINEMAX اليوم:\n\(names)\n\nاخترتها من مكتبتي."
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("سهرة الليلة")
                            .font(.system(size: 24, weight: .heavy))
                            .foregroundStyle(.white)
                        Text("فيلم ومسلسل وثالث — بلا حيرة")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    Spacer()
                    CircleButton(system: "xmark") { dismiss() }
                }

                ForEach(picks) { item in
                    Button {
                        Haptics.tap()
                        dismiss()
                        onOpen(item)
                    } label: {
                        HStack(spacing: 12) {
                            RemoteImage(url: item.posterURL)
                                .frame(width: 54, height: 81)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            VStack(alignment: .leading, spacing: 3) {
                                Text(item.type == "series" ? "مسلسل" : "فيلم")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                                Text(item.name)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                                Text(meta(item))
                                    .font(.system(size: 11))
                                    .foregroundStyle(CinemaTheme.tertiary)
                                    .lineLimit(1)
                            }
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.left")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                        .padding(10)
                        .background(
                            CinemaTheme.surface,
                            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                        )
                    }
                    .buttonStyle(CinemaPressStyle())
                }

                HStack(spacing: 10) {
                    Button {
                        Haptics.select()
                        draw()
                    } label: {
                        Label("أعد الاختيار", systemImage: "arrow.triangle.2.circlepath")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 16)
                            .frame(height: 44)
                            .background(Color.white.opacity(0.1), in: Capsule())
                    }
                    .buttonStyle(CinemaPressStyle())
                    if !picks.isEmpty {
                        ShareLink(item: text) {
                            Label("شارك السهرة", systemImage: "square.and.arrow.up")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(.black)
                                .padding(.horizontal, 16)
                                .frame(height: 44)
                                .background(CinemaTheme.accent, in: Capsule())
                        }
                    }
                }
                Spacer(minLength: 20)
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .task {
            if picks.isEmpty { draw() }
        }
    }

    private func meta(_ item: CinemaItem) -> String {
        var parts: [String] = []
        if let year = item.releaseInfo, !year.isEmpty { parts.append(year) }
        if let rating = item.rating, !rating.isEmpty { parts.append("★ \(rating)") }
        return parts.joined(separator: " · ")
    }

    /// One film, one series, and one more, all different, so an evening has a
    /// shape instead of three films in a row.
    private func draw() {
        let films = pool.filter { $0.type == "movie" }
        let series = pool.filter { $0.type == "series" }
        var chosen: [CinemaItem] = []
        if let film = films.randomElement() { chosen.append(film) }
        if let show = series.randomElement() { chosen.append(show) }
        let rest = pool.filter { item in !chosen.contains { $0.id == item.id } }
        if let third = rest.randomElement() { chosen.append(third) }
        picks = chosen
    }
}

/// The Hype look: a coral wordmark over a pill search, one full-bleed hero with
/// its eyebrow, chips and a white play pill, then a raised sheet of rows that
/// scrolls under a five-tab bar.
struct HypeLogo: View {
    var body: some View {
        HStack(spacing: 7) {
            PlayGlyph()
                .frame(width: 26, height: 24)
            HStack(spacing: 0) {
                Text(Brand.first)
                    .foregroundStyle(.white)
                Text(Brand.second)
                    .foregroundStyle(CinemaTheme.accent)
            }
            .font(.system(size: 23, weight: .heavy))
        }
        // The wordmark is read left to right whatever the interface's language:
        // inside an Arabic layout the two halves were laid out from the right, so
        // "cine" and "max" came out as "maxcine" and the mark no longer spelled
        // the app's own name.
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityLabel(Brand.name)
    }
}

/// The two-tone play triangle of the wordmark.
struct PlayGlyph: View {
    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let height = proxy.size.height
            ZStack {
                Path { path in
                    path.move(to: CGPoint(x: 0, y: 0))
                    path.addLine(to: CGPoint(x: width * 0.62, y: height / 2))
                    path.addLine(to: CGPoint(x: 0, y: height))
                    path.closeSubpath()
                }
                .fill(CinemaTheme.favourite)
                Path { path in
                    path.move(to: CGPoint(x: width * 0.55, y: 0))
                    path.addLine(to: CGPoint(x: width, y: height / 2))
                    path.addLine(to: CGPoint(x: width * 0.55, y: height))
                    path.closeSubpath()
                }
                .fill(CinemaTheme.accent)
            }
        }
    }
}

/// A poster the way Hype draws it: 2:3 artwork, a two-line title and one meta
/// line with a gold star.
struct HypeCard: View {
    let item: CinemaItem
    var width: CGFloat = 136
    var progress: Double = 0
    var saved = false
    let onOpen: () -> Void

    private var meta: String {
        var parts: [String] = []
        if let year = item.releaseInfo, !year.isEmpty { parts.append(year) }
        if let rating = item.rating, !rating.isEmpty { parts.append("★ \(rating)") }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 7) {
                ZStack(alignment: .bottomLeading) {
                    RemoteImage(url: item.posterURL)
                        .frame(width: width, height: width * 1.5)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    if progress > 0 {
                        Capsule()
                            .fill(CinemaTheme.accent)
                            .frame(width: max(8, width * progress), height: 3)
                            .padding(6)
                    }
                }
                .overlay(alignment: .topTrailing) {
                    if saved {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(CinemaTheme.favourite)
                            .padding(6)
                            .background(.black.opacity(0.5), in: Circle())
                            .padding(6)
                    }
                }
                Text(item.name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .frame(width: width, alignment: .leading)
                Text(meta)
                    .font(.system(size: 12))
                    .foregroundStyle(CinemaTheme.tertiary)
                    .lineLimit(1)
                    .frame(width: width, alignment: .leading)
            }
            .frame(width: width, alignment: .leading)
        }
        .buttonStyle(CinemaPressStyle())
    }
}

/// One row: a bold heading with an inline gray subtitle, then the posters. The
/// first row on the page carries the ghost rank numerals Hype puts behind them.
struct HypeRow: View {
    let title: String
    var subtitle: String = ""
    let items: [CinemaItem]
    var ranked = false
    var fraction: (String) -> Double = { _ in 0 }
    var saved: (String) -> Bool = { _ in false }
    let onOpen: (CinemaItem) -> Void
    var onMore: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.white)
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundStyle(CinemaTheme.tertiary)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                if let onMore {
                    Button {
                        Haptics.tap()
                        onMore()
                    } label: {
                        Text("الكل")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    .buttonStyle(.plain)
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 14) {
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                        if ranked, index < 10 {
                            ZStack(alignment: .bottomLeading) {
                                Text("\(index + 1)")
                                    .font(.system(size: 92, weight: .heavy))
                                    .foregroundStyle(.white.opacity(0.10))
                                    .offset(x: -8, y: 22)
                                HypeCard(
                                    item: item,
                                    progress: fraction(item.id),
                                    saved: saved(item.id),
                                    onOpen: { onOpen(item) }
                                )
                                .padding(.leading, 30)
                            }
                        } else {
                            HypeCard(
                                item: item,
                                progress: fraction(item.id),
                                saved: saved(item.id),
                                onOpen: { onOpen(item) }
                            )
                        }
                    }
                }
                .padding(.horizontal, 2)
            }
        }
    }
}

/// A quiet panel for a page with nothing in it yet.
struct HypeEmpty: View {
    let icon: String
    let title: String
    let detail: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 24, weight: .light))
                .foregroundStyle(CinemaTheme.accent)
            Text(title)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(.white)
            Text(detail)
                .font(.system(size: 13))
                .foregroundStyle(CinemaTheme.muted)
                .lineSpacing(4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
        )
    }
}

/// Design 4 — the Hype interface: wordmark and pill search, one hero, a raised
/// sheet of rows, and a five-tab bar under it.
struct HypeHomeView: View {
    @Binding var selection: Int
    @ObservedObject private var model = HomeModel.shared
    @ObservedObject private var lists = ListsStore.shared
    @AppStorage("cinemax.showHero") private var showHero = true
    @State private var heroIndex = 0
    @State private var detailItem: CinemaItem?
    /// One sheet only: SwiftUI keeps one presentation per view, so the episode
    /// list and the search would fight for it and one would never appear.
    @State private var panel: HypeSheet?

    private enum HypeSheet: Identifiable {
        case shelf(Shelf)
        case search
        case party

        var id: String {
            switch self {
            case .shelf(let shelf): return "shelf:" + shelf.id
            case .search: return "search"
            case .party: return "party"
            }
        }
    }

    private var hero: CinemaItem? {
        guard !model.heroItems.isEmpty else { return nil }
        return model.heroItems[min(max(0, heroIndex), model.heroItems.count - 1)]
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                header
                // The day's own film sits under the search, so opening the app
                // has a beginning instead of a wall of rows to choose from.
                if let daily = DailyFeature.pick(from: model.visibleShelves.flatMap(\.items)) {
                    DailyTitleBand(item: daily, weekday: DailyFeature.label()) {
                        Haptics.tap()
                        detailItem = daily
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 10)
                }
                if showHero, let hero {
                    heroBlock(hero)
                        .id(hero.id)
                        .transition(.opacity)
                }
                sheet
                Spacer(minLength: 120)
            }
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .refreshable { await model.load(force: true) }
        .task { await model.load() }
        .task(id: model.heroItems.count) {
            guard !CinemaTheme.reduceMotion, model.heroItems.count > 1 else { return }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                guard !Task.isCancelled else { return }
                withAnimation(.easeInOut(duration: 0.5)) {
                    heroIndex = (heroIndex + 1) % max(1, model.heroItems.count)
                }
            }
        }
        .fullScreenCover(item: $detailItem) { item in
            DetailView(item: item, onFavorite: { model.toggle(item) })
        }
        .sheet(item: $panel) { value in
            switch value {
            case .shelf(let shelf):
                ShelfGridScreen(shelf: shelf, onOpen: { item in
                    panel = nil
                    detailItem = item
                })
            case .search:
                SearchView()
            case .party:
                WatchPartySheet(pool: model.visibleShelves.flatMap(\.items)) { item in
                    panel = nil
                    detailItem = item
                }
                .presentationDetents([.medium, .large])
            }
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(spacing: 12) {
            HStack(spacing: 10) {
                HypeLogo()
                Spacer(minLength: 0)
                Button {
                    Haptics.tap()
                    selection = 3
                } label: {
                    bubble("arrow.down.circle.fill", accent: true)
                }
                .buttonStyle(.plain)
                Menu {
                    Button {
                        selection = 3
                    } label: {
                        Label("المحفوظة", systemImage: "heart")
                    }
                    Button {
                        selection = 4
                    } label: {
                        Label("الأخير", systemImage: "clock")
                    }
                    Button {
                        surprise()
                    } label: {
                        Label("فاجئني بفيلم", systemImage: "dice")
                    }
                    Button {
                        Haptics.tap()
                        panel = .party
                    } label: {
                        Label("سهرة الليلة", systemImage: "popcorn")
                    }
                    Button {
                        selection = 5
                    } label: {
                        Label("الإعدادات", systemImage: "slider.horizontal.3")
                    }
                    if !lists.lists.isEmpty {
                        Divider()
                        ForEach(lists.lists) { list in
                            Button {
                                selection = 3
                            } label: {
                                Label("\(list.name) · \(list.items.count)", systemImage: "text.badge.plus")
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "list.bullet")
                            .font(.system(size: 12, weight: .semibold))
                        Image(systemName: "chevron.down")
                            .font(.system(size: 9, weight: .bold))
                    }
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 38)
                    .background(
                        CinemaTheme.elevated,
                        in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
                    )
                }
            }
            Button {
                Haptics.tap()
                panel = .search
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 14, weight: .semibold))
                    Text("ابحث عن فيلم أو مسلسل")
                        .font(.system(size: 14))
                    Spacer(minLength: 0)
                }
                .foregroundStyle(CinemaTheme.muted)
                .padding(.horizontal, 16)
                .frame(height: 44)
                .background(CinemaTheme.elevated.opacity(0.8), in: Capsule())
                .overlay(Capsule().strokeBorder(CinemaTheme.hairline, lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.top, 6)
        .padding(.bottom, 14)
    }

    /// A title the viewer has not started yet, taken from the rows on screen.
    private func surprise() {
        Haptics.select()
        let started = Set(model.continueWatching.map(\.item.id))
        let all = model.visibleShelves.flatMap(\.items)
        let fresh = all.filter { !started.contains($0.id) }
        guard let pick = (fresh.isEmpty ? all : fresh).randomElement() else { return }
        EventLog.shared.add("discover", "فاجئني: \(pick.name)")
        detailItem = pick
    }

    private func bubble(_ name: String, accent: Bool) -> some View {
        Image(systemName: name)
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(accent ? CinemaTheme.accent : CinemaTheme.muted)
            .frame(width: 40, height: 38)
            .background(
                accent ? CinemaTheme.accent.opacity(0.18) : CinemaTheme.elevated,
                in: RoundedRectangle(cornerRadius: 12, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
            )
    }

    // MARK: Hero

    private func heroBlock(_ item: CinemaItem) -> some View {
        ZStack(alignment: .bottomLeading) {
            RemoteImage(url: item.backdropURL)
                .frame(height: 460)
                .frame(maxWidth: .infinity)
                .clipped()
            LinearGradient(
                stops: [
                    .init(color: .black.opacity(0.50), location: 0.0),
                    .init(color: .black.opacity(0.10), location: 0.32),
                    .init(color: CinemaTheme.canvas.opacity(0.35), location: 0.62),
                    .init(color: CinemaTheme.canvas, location: 1.0)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            VStack(alignment: .leading, spacing: 10) {
                Text(eyebrow(item))
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(1.6)
                    .foregroundStyle(CinemaTheme.tertiary)
                    .lineLimit(2)
                Text(item.name)
                    .font(.system(size: 33, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 8) {
                    if let year = item.releaseInfo, !year.isEmpty { chip(year) }
                    if let rating = item.rating, !rating.isEmpty { chip("★ \(rating)") }
                    Text(item.type == "series" ? "مسلسل" : "فيلم")
                        .font(.system(size: 13))
                        .foregroundStyle(CinemaTheme.muted)
                }
                if let overview = item.overview, !overview.isEmpty {
                    Text(overview)
                        .font(.system(size: 15))
                        .foregroundStyle(.white.opacity(0.85))
                        .lineSpacing(3)
                        .lineLimit(2)
                }
                HStack(spacing: 10) {
                    Button {
                        Haptics.tap()
                        detailItem = item
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 14, weight: .bold))
                            Text(item.type == "series" ? "اعرض الحلقات" : "شاهد")
                                .font(.system(size: 16, weight: .bold))
                        }
                        .foregroundStyle(.black)
                        .padding(.horizontal, 24)
                        .frame(height: 48)
                        .background(
                            Color(red: 0.96, green: 0.95, blue: 0.97),
                            in: Capsule()
                        )
                    }
                    .buttonStyle(CinemaPressStyle())
                    Button {
                        Haptics.tap()
                        detailItem = item
                    } label: {
                        HStack(spacing: 7) {
                            Image(systemName: "info.circle")
                                .font(.system(size: 14, weight: .semibold))
                            Text("التفاصيل")
                                .font(.system(size: 15, weight: .semibold))
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 18)
                        .frame(height: 48)
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(Capsule().strokeBorder(.white.opacity(0.22), lineWidth: 1))
                    }
                    .buttonStyle(CinemaPressStyle())
                    Button {
                        Haptics.tap()
                        model.toggle(item)
                    } label: {
                        Image(systemName: model.favorites.contains(item.id) ? "heart.fill" : "heart")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(
                                model.favorites.contains(item.id) ? CinemaTheme.favourite : .white
                            )
                            .frame(width: 48, height: 48)
                            .background(.ultraThinMaterial, in: Circle())
                            .overlay(Circle().strokeBorder(.white.opacity(0.22), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    Spacer(minLength: 0)
                }
                .padding(.top, 2)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .frame(height: 460)
        .frame(maxWidth: .infinity, alignment: .leading)
        .clipped()
    }

    private func eyebrow(_ item: CinemaItem) -> String {
        var parts = [item.type == "series" ? "مسلسل" : "فيلم"]
        if model.favorites.contains(item.id) { parts.append("محفوظ") }
        parts.append("من مكتبتك")
        parts.append("يُحدَّث مع مصادرك")
        return parts.joined(separator: "  ·  ").uppercased()
    }

    private func chip(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .medium))
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .frame(height: 28)
            .background(
                Color.white.opacity(0.10),
                in: RoundedRectangle(cornerRadius: 8, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .strokeBorder(.white.opacity(0.22), lineWidth: 1)
            )
    }

    // MARK: Sheet of rows

    private var sheet: some View {
        VStack(alignment: .leading, spacing: 24) {
            if model.loading {
                HStack(spacing: 14) {
                    ForEach(0..<3, id: \.self) { _ in
                        ShimmerBlock(height: 204, width: 136)
                    }
                }
            } else if model.visibleShelves.isEmpty {
                HypeEmpty(
                    icon: "puzzlepiece.extension",
                    title: "لا شيء بعد",
                    detail: "ثبّت إضافة كتالوج من الإعدادات ← الإضافات، وسيصل أول صف من البوسترات."
                )
            } else {
                if !model.continueWatching.isEmpty {
                    HypeRow(
                        title: "أكمل المشاهدة",
                        subtitle: "من حيث توقفت",
                        items: model.continueWatching.map(\.item),
                        fraction: { id in
                            model.continueWatching.first { $0.item.id == id }?.fraction ?? 0
                        },
                        saved: { model.favorites.contains($0) },
                        onOpen: { detailItem = $0 }
                    )
                }
                ForEach(Array(model.visibleShelves.enumerated()), id: \.element.id) { index, shelf in
                    HypeRow(
                        title: shelf.title,
                        subtitle: "\(shelf.items.count) عنوانًا",
                        items: shelf.items,
                        ranked: index == 0,
                        fraction: { id in
                            model.continueWatching.first { $0.item.id == id }?.fraction ?? 0
                        },
                        saved: { model.favorites.contains($0) },
                        onOpen: { detailItem = $0 },
                        onMore: { panel = .shelf(shelf) }
                    )
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 22)
        .padding(.bottom, 28)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            CinemaTheme.sheet,
            in: RoundedRectangle(cornerRadius: 26, style: .continuous)
        )
        .padding(.horizontal, 6)
        .padding(.top, -14)
    }
}

/// التبويب الأول للنوعين: الأفلام والمسلسلات كما وصلت من المصادر، في شبكة
/// بوسترات واحدة مع بحث سريع.
struct HypeCatalogScreen: View {
    let type: String
    @ObservedObject private var model = HomeModel.shared
    @State private var query = ""
    @State private var detailItem: CinemaItem?

    private let columns = [GridItem(.adaptive(minimum: 104), spacing: 14)]

    private var title: String { type == "movie" ? "أفلام" : "مسلسلات" }

    private var items: [CinemaItem] {
        let text = query.trimmingCharacters(in: .whitespaces)
        var seen = Set<String>()
        return model.shelves
            .flatMap(\.items)
            .filter { $0.type == type }
            .filter { item in
                guard seen.insert(item.id).inserted else { return false }
                return text.isEmpty || item.name.localizedCaseInsensitiveContains(text)
            }
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.system(size: 30, weight: .heavy))
                        .foregroundStyle(.white)
                    Text("\(items.count) عنوانًا من أول صفحة لكل إضافة")
                        .font(.system(size: 13))
                        .foregroundStyle(CinemaTheme.tertiary)
                }
                .padding(.top, 8)

                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    TextField("ابحث في \(title)", text: $query)
                        .textFieldStyle(.plain)
                        .font(.system(size: 14))
                        .foregroundStyle(.white)
                        .autocorrectionDisabled()
                    if !query.isEmpty {
                        Button { query = "" } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(CinemaTheme.muted)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .frame(height: 44)
                .background(CinemaTheme.elevated.opacity(0.8), in: Capsule())
                .overlay(Capsule().strokeBorder(CinemaTheme.hairline, lineWidth: 1))

                if items.isEmpty {
                    HypeEmpty(
                        icon: "square.stack.3d.up",
                        title: model.loading ? "جارٍ الجلب…" : "لا \(title) بعد",
                        detail: "الأفلام والمسلسلات تأتي من صفوف إضافاتك. ثبّت مصدرًا من الإعدادات أو أضف إضافة كتالوج."
                    )
                } else {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 18) {
                        ForEach(items) { item in
                            HypeCard(item: item, width: 104) {
                                detailItem = item
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 120)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .task { await model.load() }
        .refreshable { await model.load(force: true) }
        .fullScreenCover(item: $detailItem) { DetailView(item: $0) }
    }
}

/// ما بدأه المشاهد ولم يكمله، في صفحة واحدة.
struct HypeRecentScreen: View {
    @ObservedObject private var library = LibraryModel.shared
    @State private var detailItem: CinemaItem?

    private let columns = [GridItem(.adaptive(minimum: 104), spacing: 14)]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("الأخير")
                        .font(.system(size: 30, weight: .heavy))
                        .foregroundStyle(.white)
                    Text(
                        library.progress.isEmpty
                            ? "ما تبدأ مشاهدته يظهر هنا"
                            : "\(library.progress.count) عنوانًا بدأت مشاهدته"
                    )
                    .font(.system(size: 13))
                    .foregroundStyle(CinemaTheme.tertiary)
                }
                .padding(.top, 8)

                if library.progress.isEmpty {
                    HypeEmpty(
                        icon: "clock",
                        title: "لا شيء بعد",
                        detail: "افتح أي عنوان وابدأ المشاهدة، وسيظهر هنا مع موضع توقفك بالضبط."
                    )
                } else {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 18) {
                        ForEach(library.progress) { entry in
                            HypeCard(
                                item: entry.item,
                                width: 104,
                                progress: entry.fraction
                            ) {
                                detailItem = entry.item
                            }
                            .contextMenu {
                                Button(role: .destructive) {
                                    Haptics.tap()
                                    Task {
                                        _ = await CinemaAPI.shared.removeProgress(
                                            itemId: entry.item.id,
                                            videoLabel: entry.episodeLabel
                                        )
                                        await LibraryModel.shared.load()
                                    }
                                } label: {
                                    Label("إزالة من الأخير", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 120)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .task { await library.load() }
        .fullScreenCover(item: $detailItem) { DetailView(item: $0) }
    }
}

/// Hype's bottom bar: five equal columns, the active one white.
struct HypeTabBar: View {
    @Binding var selection: Int

    private let tabs: [(icon: String, title: String)] = [
        ("house", "الرئيسية"),
        ("tv", "مسلسلات"),
        ("film", "أفلام"),
        ("heart", "المحفوظة"),
        ("clock", "الأخير")
    ]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(tabs.enumerated()), id: \.offset) { index, tab in
                Button {
                    Haptics.select()
                    selection = index
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: selection == index ? tab.icon + ".fill" : tab.icon)
                            .font(.system(size: 20, weight: .regular))
                        Text(tab.title)
                            .font(.system(size: 10.5, weight: .medium))
                    }
                    .foregroundStyle(selection == index ? .white : CinemaTheme.tertiary)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            // Settings stay one tap away in every design: the tab bar is the one
            // thing on screen in all of them, and a design without it would hide
            // the settings screen entirely.
            Button {
                Haptics.select()
                selection = 5
            } label: {
                Image(systemName: selection == 5 ? "gearshape.fill" : "gearshape")
                    .font(.system(size: 18, weight: .regular))
                    .foregroundStyle(selection == 5 ? .white : CinemaTheme.tertiary)
                    .frame(width: 40, height: 40)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.top, 10)
        .padding(.bottom, 4)
        .background(alignment: .top) {
            CinemaTheme.canvas
                .overlay(alignment: .top) {
                    Rectangle()
                        .fill(CinemaTheme.hairline)
                        .frame(height: 1)
                }
                .ignoresSafeArea(edges: .bottom)
        }
    }
}

/// Design 2 — the poster wall: search first, dense three-column grids, counters,
/// no hero and no glow. The opposite trade-off to the cinematic home.
struct PosterWallHomeView: View {
    @Binding var selection: Int
    @ObservedObject private var model = HomeModel.shared
    @State private var detailItem: CinemaItem?
    @State private var query = ""

    private let columns = [GridItem(.adaptive(minimum: 104), spacing: 10)]

    private var shelves: [Shelf] {
        guard !query.isEmpty else { return model.visibleShelves }
        return model.visibleShelves.compactMap { shelf in
            let hits = shelf.items.filter { $0.name.localizedCaseInsensitiveContains(query) }
            return hits.isEmpty ? nil : Shelf(id: shelf.id, title: shelf.title, items: hits)
        }
    }

    private var total: Int { model.shelves.reduce(0) { $0 + $1.items.count } }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("استكشف")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundStyle(.white)
                    Text("\(total) عنوانًا محمّلًا من \(model.shelves.count) إضافة")
                        .font(.system(size: 13))
                        .foregroundStyle(CinemaTheme.muted)
                    Text("كل إضافة تعرض أول صفحة فقط؛ الأرقام هنا لما وصل الآن لا لكل ما تحتويه الإضافات.")
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted.opacity(0.8))
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)

                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass").foregroundStyle(CinemaTheme.muted)
                    TextField("ابحث داخل ما لديك", text: $query)
                        .textFieldStyle(.plain)
                        .foregroundStyle(.white)
                    if !query.isEmpty {
                        Button { query = "" } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(CinemaTheme.muted)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(CinemaTheme.surface, in: Capsule())
                .overlay(Capsule().strokeBorder(CinemaTheme.hairline, lineWidth: 1))
                .padding(.horizontal, 20)

                FilterChipsBar(model: model)

                HStack(spacing: 10) {
                    counter("\(model.shelves.flatMap(\.items).filter { $0.type == "movie" }.count)", "أفلام")
                    counter("\(model.shelves.flatMap(\.items).filter { $0.type == "series" }.count)", "مسلسلات")
                    counter("\(model.channels)", "قنوات")
                }
                .padding(.horizontal, 20)

                if model.loading {
                    LazyVGrid(columns: columns, spacing: 14) {
                        ForEach(0..<12, id: \.self) { _ in ShimmerBlock(height: 170, width: nil) }
                    }
                    .padding(.horizontal, 20)
                } else if shelves.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("لا شيء هنا بعد")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(.white)
                        Text("ثبّت المصادر الجاهزة أو أضف إضافة من الإعدادات ← الإضافات.")
                            .font(.system(size: 13))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                } else {
                    ForEach(shelves) { shelf in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(alignment: .firstTextBaseline) {
                                Text(shelf.title)
                                    .font(.system(size: 17, weight: .bold))
                                    .foregroundStyle(.white)
                                Spacer()
                                Text("\(shelf.items.count)")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            LazyVGrid(columns: columns, alignment: .leading, spacing: 14) {
                                ForEach(shelf.items) { item in
                                    PosterCard(
                                        item: item,
                                        width: 104,
                                        progress: model.continueWatching.first { $0.item.id == item.id }?.fraction ?? 0,
                                        saved: model.favorites.contains(item.id),
                                        onOpen: { detailItem = item }
                                    )
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                }
                Spacer(minLength: 120)
            }
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .task { await model.load() }
        .refreshable { await model.load(force: true) }
        .fullScreenCover(item: $detailItem) { DetailView(item: $0) }
    }

    private func counter(_ value: String, _ title: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 20, weight: .heavy))
                .foregroundStyle(CinemaTheme.accent)
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(CinemaTheme.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(CinemaTheme.elevated, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

private extension HomeModel {
    var channels: Int {
        shelves.flatMap(\.items).filter { $0.type == "channel" || $0.type == "tv" }.count
    }
}

/// Design 3 — the panorama: one full-bleed backdrop with the title over it, then
/// wide 16:9 cards instead of posters. Fewer, larger pictures per screen.
struct PanoramaHomeView: View {
    @Binding var selection: Int
    @ObservedObject private var model = HomeModel.shared
    @AppStorage("cinemax.showContinue") private var showContinue = true
    @State private var detailItem: CinemaItem?
    @State private var glow: [Color] = [CinemaTheme.accent]

    private var spotlight: CinemaItem? { model.heroItems.first }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                hero
                FilterChipsBar(model: model)
                    .padding(.top, 14)
                if model.loading {
                    HStack(spacing: 12) {
                        ForEach(0..<2, id: \.self) { _ in ShimmerBlock(height: 140, width: 248) }
                    }
                    .padding(.horizontal, 20)
                } else {
                    if showContinue, !model.continueWatching.isEmpty {
                        SectionHeader(title: "أكمل المشاهدة")
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(spacing: 12) {
                                ForEach(model.continueWatching) { entry in
                                    ContinueCard(entry: entry) { detailItem = entry.item }
                                        .contextMenu {
                                            Button(role: .destructive) {
                                                Haptics.tap()
                                                model.removeResume(entry)
                                            } label: {
                                                Label("إزالة من قيد المشاهدة", systemImage: "trash")
                                            }
                                        }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                    ForEach(model.visibleShelves) { shelf in
                        SectionHeader(title: shelf.title)
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(spacing: 12) {
                                ForEach(shelf.items) { item in
                                    WideCard(item: item) { detailItem = item }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                }
                Spacer(minLength: 120)
            }
        }
        .background(AmbientGlow(colors: glow).ignoresSafeArea())
        .task { await model.load() }
        .refreshable { await model.load(force: true) }
        .fullScreenCover(item: $detailItem) { DetailView(item: $0) }
    }

    private var hero: some View {
        ZStack(alignment: .bottomLeading) {
            RemoteImage(url: spotlight?.backdropURL ?? spotlight?.posterURL)
                .frame(height: 520)
                .overlay(
                    LinearGradient(
                        stops: [
                            .init(color: .black.opacity(0.15), location: 0.0),
                            .init(color: CinemaTheme.canvas.opacity(0.85), location: 0.72),
                            .init(color: CinemaTheme.canvas, location: 1)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            VStack(alignment: .leading, spacing: 12) {
                Text(Brand.name.uppercased())
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(4)
                    .foregroundStyle(.white.opacity(0.65))
                if let item = spotlight {
                    Text(item.name)
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                        .shadow(color: .black.opacity(0.5), radius: 14, y: 6)
                    HStack(spacing: 8) {
                        if let rating = item.rating, !rating.isEmpty { CinemaPill(text: "★ \(rating)") }
                        if let year = item.releaseInfo, !year.isEmpty { CinemaPill(text: year, tint: CinemaTheme.muted) }
                        CinemaPill(text: item.type == "series" ? "مسلسل" : "فيلم", tint: CinemaTheme.muted)
                    }
                    Button { detailItem = item } label: {
                        Label("ابدأ المشاهدة", systemImage: "play.fill")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 13)
                            .background(.white, in: Capsule())
                    }
                    .buttonStyle(CinemaPressStyle())
                    .padding(.top, 4)
                } else if model.loading {
                    ShimmerBlock(height: 26, width: 240)
                }
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 24)
        }
        .frame(height: 520)
        .task(id: spotlight?.id) {
            guard let url = spotlight?.posterURL else { return }
            let colors = await PaletteSampler.colors(for: url)
            if !colors.isEmpty { glow = colors }
        }
    }
}


// MARK: - Settings hub

/// A grouped settings row: icon, title, one line of context, and a chevron.
struct SettingsRow: View {
    let icon: String
    let title: String
    let detail: String
    var value: String?

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(CinemaTheme.accent)
                .frame(width: 34, height: 34)
                .background(CinemaTheme.accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                Text(detail)
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            Spacer(minLength: 8)
            if let value {
                Text(value)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(CinemaTheme.muted)
            }
            Image(systemName: "chevron.left")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CinemaTheme.muted.opacity(0.7))
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
        )
    }
}

/// One titled group of rows.
struct SettingsSection<Content: View>: View {
    let title: String
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CinemaTheme.muted)
                .padding(.leading, 4)
            VStack(spacing: 10) { content() }
        }
    }
}

struct SettingsView: View {
    @AppStorage("cinemax.design") private var design = "cinematic"
    @AppStorage("cinemax.accent") private var accent = "raif"
    @AppStorage("cinemax.oled") private var oled = false
    @AppStorage("cinemax.autoplayNext") private var autoplayNext = true
    @AppStorage("cinemax.subtitleLang") private var subtitleLang = "ar"
    @State private var query = ""

    /// Every screen the hub can reach, with the words a viewer might search for.
    private struct Destination: Identifiable {
        let id: String
        let title: String
        let detail: String
        let icon: String
        let keywords: String
        var value: String? = nil
    }

    private var destinations: [Destination] {
        [
            Destination(id: "appearance", title: "المظهر واللغة", detail: "لون، أنماط جاهزة، زوايا، اهتزاز", icon: "paintpalette.fill", keywords: "لون مظهر نمط زوايا اهتزاز oled accent theme"),
            Destination(id: "layout", title: "التخطيط", detail: "تصميم الرئيسية، الهيرو، الصفوف", icon: "rectangle.3.group.fill", keywords: "تخطيط رئيسية صفوف هيرو بوستر home layout"),
            Destination(id: "addons", title: "الإضافات", detail: "تثبيت، فحص، تحديث، ترتيب، إعدادات كل إضافة", icon: "puzzlepiece.extension.fill", keywords: "اضافات ستريميو addons stremio manifest"),
            Destination(id: "sources", title: "المصادر", detail: "تشغيل إضافة أو إطفاؤها، المخفي، العرض", icon: "slider.horizontal.3", keywords: "مصادر اطفاء تشغيل اخفاء طي عرض sources hide off"),
            Destination(id: "siri", title: "Siri والاختصارات", detail: "العبارات التي تنفّذها بصوتك", icon: "waveform", keywords: "siri سيري اختصارات shortcuts صوت"),
            Destination(id: "nuvio", title: "مزوّدو Nuvio", detail: "مستودعات، مزوّدون، بروكسي وكوكي", icon: "bolt.horizontal.circle.fill", keywords: "نيفيو مزود مستودع بروكسي كوكي nuvio provider repo"),
            Destination(id: "tmdb", title: "TMDB", detail: "مفتاح الكتالوج العربي", icon: "star.bubble.fill", keywords: "tmdb مفتاح كتالوج"),
            Destination(id: "sharing", title: "تصدير ومشاركة", detail: "روابط، ملف JSON، استيراد", icon: "square.and.arrow.up.on.square.fill", keywords: "تصدير استيراد مشاركة json نسخة روابط share"),
            Destination(id: "playback", title: "التشغيل", detail: "المشغّل، التقديم، المصادر", icon: "play.rectangle.fill", keywords: "تشغيل مشغل تقديم مصدر جودة player"),
            Destination(id: "subtitles", title: "الترجمة", detail: "اللغة، الحجم، الاختيار التلقائي", icon: "captions.bubble.fill", keywords: "ترجمة لغة حجم subtitle"),
            Destination(id: "streaming", title: "البث والمشغّل", detail: "فك ترميز، كاش، تدوير", icon: "antenna.radiowaves.left.and.right", keywords: "بث كاش ترميز تدوير streaming cache hwdec"),
            Destination(id: "library", title: "المكتبة والبحث", detail: "ترتيب افتراضي ونوع البحث", icon: "books.vertical.fill", keywords: "مكتبة بحث ترتيب library search"),
            Destination(id: "stats", title: "الإحصاءات", detail: "ما في مكتبتك وحجم التنزيلات", icon: "chart.bar.fill", keywords: "احصائيات stats"),
            Destination(id: "data", title: "البيانات", detail: "مسح، نسخة احتياطية، قفل التطبيق", icon: "externaldrive.fill", keywords: "بيانات نسخ احتياطي مسح قفل data backup lock"),
            Destination(id: "about", title: "عن التطبيق", detail: "الإصدار، التشخيص، شاشة البداية", icon: "info.circle.fill", keywords: "حول اصدار about version"),
            Destination(id: "selftest", title: "الفحص الذاتي", detail: "قراءة HLS، سباق المرايا، تطبيع البحث، الملفات", icon: "checkmark.shield.fill", keywords: "فحص اختبار تشخيص selftest test diagnose hls race")
        ]
    }

    private var matches: [Destination] {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return [] }
        return destinations.filter {
            $0.title.localizedCaseInsensitiveContains(text)
                || $0.detail.localizedCaseInsensitiveContains(text)
                || $0.keywords.localizedCaseInsensitiveContains(text)
        }
    }

    @ViewBuilder
    private func destinationView(_ id: String) -> some View {
        switch id {
        case "appearance": AppearanceSettings()
        case "layout": LayoutSettings()
        case "addons": AddonsSettings()
        case "sources": SourcesControlSettings()
        case "siri": SiriSettings()
        case "nuvio": NuvioSettings()
        case "tmdb": TmdbSettings()
        case "sharing": SharingSettings()
        case "playback": PlaybackSettings()
        case "subtitles": SubtitleSettings()
        case "streaming": StreamingSettings()
        case "library": LibrarySettings()
        case "stats": StatsSettings()
        case "data": DataSettings()
        case "about": AboutSettings()
        case "selftest": SelfTestView()
        default: EmptyView()
        }
    }

    private var subtitleName: String {
        switch subtitleLang {
        case "ar": return "العربية"
        case "en": return "English"
        default: return "إيقاف"
        }
    }

    private var designName: String {
        switch design {
        case "hype": return "واجهة Hype"
        case "wall": return "جدار بوسترات"
        case "panorama": return "بانورامي"
        default: return "سينمائي"
        }
    }

    private var accentName: String {
        AccentPalette.entries.first { $0.id == accent }?.name ?? "Raif"
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 22) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(Brand.name.uppercased())
                            .font(.system(size: 11, weight: .semibold))
                            .tracking(3)
                            .foregroundStyle(CinemaTheme.muted)
                        Text("الإعدادات")
                            .font(.system(size: 34, weight: .heavy))
                            .foregroundStyle(.white)
                        Text("الإصدار \(CinemaAPI.shared.versionLabel)")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)

                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                        TextField("ابحث في الإعدادات", text: $query)
                            .textFieldStyle(.plain)
                            .font(.system(size: 13))
                            .foregroundStyle(.white)
                            .autocorrectionDisabled()
                        if !query.isEmpty {
                            Button {
                                query = ""
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 12))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 11)
                    .background(CinemaTheme.surface, in: Capsule())
                    .padding(.horizontal, 20)

                    if !query.isEmpty {
                        SettingsSection(title: matches.isEmpty ? "لا نتائج" : "نتائج البحث (\(matches.count))") {
                            if matches.isEmpty {
                                Text("جرّب كلمة أخرى: مشغّل، ترجمة، إضافات، مظهر، نسخ احتياطي…")
                                    .font(.system(size: 12))
                                    .foregroundStyle(CinemaTheme.muted)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(14)
                                    .background(
                                        CinemaTheme.surface,
                                        in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                                    )
                            } else {
                                ForEach(matches) { destination in
                                    NavigationLink {
                                        destinationView(destination.id)
                                    } label: {
                                        SettingsRow(
                                            icon: destination.icon,
                                            title: destination.title,
                                            detail: destination.detail,
                                            value: destination.value
                                        )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                    }

                    SettingsSection(title: "المظهر") {
                        NavigationLink {
                            AppearanceSettings()
                        } label: {
                            SettingsRow(
                                icon: "paintpalette.fill",
                                title: "المظهر واللغة",
                                detail: "لون الواجهة، أسود AMOLED، تقليل الحركة",
                                value: accentName
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            LayoutSettings()
                        } label: {
                            SettingsRow(
                                icon: "rectangle.3.group.fill",
                                title: "التخطيط",
                                detail: "شكل الصفحة الرئيسية: هيرو أو شبكة أو بانوراما",
                                value: designName
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 20)

                    SettingsSection(title: "المحتوى") {
                        NavigationLink {
                            AddonsSettings()
                        } label: {
                            SettingsRow(
                                icon: "puzzlepiece.extension.fill",
                                title: "الإضافات",
                                detail: "ثبّت المصادر الجاهزة، أضف رابطًا، أو أزل إضافة"
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            SourcesControlSettings()
                        } label: {
                            SettingsRow(
                                icon: "slider.horizontal.3",
                                title: "المصادر",
                                detail: "إطفاء إضافة أو تشغيلها، الروابط المخفية، وشكل القائمة"
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            NuvioSettings()
                        } label: {
                            SettingsRow(
                                icon: "bolt.horizontal.circle.fill",
                                title: "مزوّدو Nuvio",
                                detail: "مستودع سكربتات المصادر، وتشغيل كل مزوّد أو إيقافه"
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            TmdbSettings()
                        } label: {
                            SettingsRow(
                                icon: "star.bubble.fill",
                                title: "TMDB",
                                detail: "صفوف عربية بوصف وتقييمات — يحتاج مفتاحًا مجانيًا"
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 20)

                    SettingsSection(title: "التشغيل") {
                        NavigationLink {
                            PlaybackSettings()
                        } label: {
                            SettingsRow(
                                icon: "play.rectangle.fill",
                                title: "التشغيل",
                                detail: "المشغّل، الحلقة التالية تلقائيًا",
                                value: autoplayNext ? "تلقائي" : "يدوي"
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            SubtitleSettings()
                        } label: {
                            SettingsRow(
                                icon: "captions.bubble.fill",
                                title: "الترجمة",
                                detail: "اللغة المفضلة، حجم الخط، والاختيار التلقائي",
                                value: subtitleName
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            StreamingSettings()
                        } label: {
                            SettingsRow(
                                icon: "antenna.radiowaves.left.and.right",
                                title: "البث والمشغل",
                                detail: "فك الترميز العتادي، بروفايل الأداء، وحجم كاش البث"
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            SiriSettings()
                        } label: {
                            SettingsRow(
                                icon: "waveform",
                                title: "Siri والاختصارات",
                                detail: "قل «أكمل آخر حلقة» أو «فاجئني» فيعمل التطبيق"
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 20)

                    SettingsSection(title: "مكتبتي") {
                        NavigationLink {
                            LibrarySettings()
                        } label: {
                            SettingsRow(
                                icon: "books.vertical.fill",
                                title: "المكتبة والبحث",
                                detail: "الترتيب الافتراضي ونوع البحث المحفوظ"
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            StatsSettings()
                        } label: {
                            SettingsRow(
                                icon: "chart.bar.fill",
                                title: "الإحصاءات",
                                detail: "المحفوظة، مواضع التوقف، وحجم ما نُزّل على الجهاز",
                                value: "\(LibraryModel.shared.favorites.count + LibraryModel.shared.progress.count)"
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 20)

                    SettingsSection(title: "المشاركة") {
                        NavigationLink {
                            SharingSettings()
                        } label: {
                            SettingsRow(
                                icon: "square.and.arrow.up.on.square.fill",
                                title: "تصدير ومشاركة",
                                detail: "روابط Stremio ومستودعات Nuvio: نسخ، مشاركة، واستيراد"
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 20)

                    SettingsSection(title: "النظام") {
                        NavigationLink {
                            SelfTestView()
                        } label: {
                            SettingsRow(
                                icon: "checkmark.shield.fill",
                                title: "الفحص الذاتي",
                                detail: "يجرّب قراءة HLS وسباق المرايا وتطبيع البحث داخل التطبيق"
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            DataSettings()
                        } label: {
                            SettingsRow(
                                icon: "externaldrive.fill",
                                title: "البيانات",
                                detail: "مسح سجل المشاهدة والمفضلة وكاش الكتالوج"
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            AboutSettings()
                        } label: {
                            SettingsRow(
                                icon: "info.circle.fill",
                                title: "عن التطبيق",
                                detail: "ما يدعمه المحرك، والإصدار الحالي",
                                value: CinemaAPI.shared.versionLabel
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 20)

                    Spacer(minLength: 140)
                }
                .padding(.bottom, 20)
            }
            .background(CinemaTheme.canvas.ignoresSafeArea())
        }
    }
}

// MARK: - Settings sub-screens

struct AppearanceSettings: View {
    @AppStorage("cinemax.accent") private var accent = "raif"
    @AppStorage("cinemax.oled") private var oled = false
    @AppStorage("cinemax.reduceMotion") private var reduceMotion = false
    @AppStorage("cinemax.haptics") private var haptics = true
    @AppStorage("cinemax.corner") private var corner = "soft"
    @AppStorage("cinemax.hapticsLevel") private var hapticsLevel = "light"

    private struct ThemePreset: Identifiable {
        let id: String
        let title: String
        let detail: String
        let accentID: String
        let oled: Bool
        let cornerValue: String
        let hapticLevel: String
    }

    private let presets: [ThemePreset] = [
        ThemePreset(
            id: "cinema",
            title: "ليل السينما",
            detail: "ذهبي دافئ، أسود عميق، زوايا ناعمة",
            accentID: "raif",
            oled: true,
            cornerValue: "soft",
            hapticLevel: "light"
        ),
        ThemePreset(
            id: "neon",
            title: "نيون",
            detail: "وردي صارخ، أسود خالص، زوايا دائرية",
            accentID: "pink",
            oled: true,
            cornerValue: "round",
            hapticLevel: "medium"
        ),
        ThemePreset(
            id: "gold",
            title: "كلاسيكي ذهبي",
            detail: "كهرماني، خلفية ناعمة، زوايا حادة",
            accentID: "amber",
            oled: false,
            cornerValue: "sharp",
            hapticLevel: "heavy"
        ),
        ThemePreset(
            id: "calm",
            title: "هادئ",
            detail: "أزرق محيطي، أسود عميق، بلا حركة",
            accentID: "ocean",
            oled: true,
            cornerValue: "soft",
            hapticLevel: "light"
        )
    ]

    private let corners: [(id: String, title: String)] = [
        ("sharp", "حادة"), ("soft", "ناعمة"), ("round", "دائرية")
    ]

    private let hapticLevels: [(id: String, title: String)] = [
        ("light", "خفيف"), ("medium", "متوسط"), ("heavy", "قوي")
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 22) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("أنماط جاهزة بنقرة")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    LazyVGrid(
                        columns: [GridItem(.adaptive(minimum: 150), spacing: 10)],
                        spacing: 10
                    ) {
                        ForEach(presets) { preset in
                            Button {
                                Haptics.select()
                                apply(preset)
                            } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    HStack(spacing: 5) {
                                        Circle()
                                            .fill(AccentPalette.color(preset.accentID))
                                            .frame(width: 12, height: 12)
                                        Capsule()
                                            .fill(preset.oled ? Color.black : Color(white: 0.12))
                                            .frame(width: 20, height: 12)
                                            .overlay(Capsule().strokeBorder(CinemaTheme.hairline, lineWidth: 1))
                                        Spacer()
                                        if accent == preset.accentID {
                                            Image(systemName: "checkmark.circle.fill")
                                                .font(.system(size: 12))
                                                .foregroundStyle(CinemaTheme.accent)
                                        }
                                    }
                                    Text(preset.title)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(.white)
                                    Text(preset.detail)
                                        .font(.system(size: 10))
                                        .foregroundStyle(CinemaTheme.muted)
                                        .multilineTextAlignment(.leading)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(
                                    CinemaTheme.surface,
                                    in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                Text("لون الواجهة")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 62), spacing: 12)], spacing: 16) {
                    ForEach(AccentPalette.entries, id: \.id) { entry in
                        Button {
                            Haptics.select()
                            accent = entry.id
                        } label: {
                            VStack(spacing: 7) {
                                ZStack {
                                    Circle()
                                        .fill(AccentPalette.color(entry.id))
                                        .frame(width: 44, height: 44)
                                    if accent == entry.id {
                                        Circle()
                                            .strokeBorder(Color.white, lineWidth: 2)
                                            .frame(width: 54, height: 54)
                                        Image(systemName: "checkmark")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundStyle(.black)
                                    }
                                }
                                .frame(height: 56)
                                Text(entry.name)
                                    .font(.system(size: 10))
                                    .foregroundStyle(accent == entry.id ? .white : CinemaTheme.muted)
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }

                VStack(spacing: 12) {
                    Toggle(isOn: $oled) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("أسود AMOLED")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(.white)
                            Text("خلفية سوداء خالصة بدل الأسود الناعم")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    }
                    .tint(CinemaTheme.accent)

                    Toggle(isOn: $reduceMotion) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("تقليل الحركة")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(.white)
                            Text("يوقف الحركات التلقائية وانتقالات البطاقات")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    }
                    .tint(CinemaTheme.accent)

                    Toggle(isOn: $haptics) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("الاهتزاز عند اللمس")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(.white)
                            Text("رد فعل لمسي خفيف على الأزرار والبطاقات")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    }
                    .tint(CinemaTheme.accent)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("شكل زوايا البوسترات")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                        HStack(spacing: 8) {
                            ForEach(corners, id: \.id) { entry in
                                Button {
                                    Haptics.select()
                                    withAnimation(.easeInOut(duration: 0.2)) { corner = entry.id }
                                } label: {
                                    Text(entry.title)
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(corner == entry.id ? .black : .white)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                        .background {
                                            Capsule().fill(
                                                corner == entry.id
                                                    ? CinemaTheme.accent
                                                    : Color.white.opacity(0.08)
                                            )
                                        }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("قوة الاهتزاز")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                        HStack(spacing: 8) {
                            ForEach(hapticLevels, id: \.id) { entry in
                                Button {
                                    hapticsLevel = entry.id
                                    Haptics.tap()
                                } label: {
                                    Text(entry.title)
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(hapticsLevel == entry.id ? .black : .white)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                        .background {
                                            Capsule().fill(
                                                hapticsLevel == entry.id
                                                    ? CinemaTheme.accent
                                                    : Color.white.opacity(0.08)
                                            )
                                        }
                                }
                                .buttonStyle(.plain)
                                .disabled(!haptics)
                                .opacity(haptics ? 1 : 0.4)
                            }
                        }
                    }
                }
                .padding(16)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("المظهر واللغة")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// One tap sets a whole look: colour, canvas, corners and touch feel.
    private func apply(_ preset: ThemePreset) {
        accent = preset.accentID
        oled = preset.oled
        corner = preset.cornerValue
        hapticsLevel = preset.hapticLevel
        reduceMotion = preset.id == "calm"
        Haptics.tap()
    }
}

struct LayoutSettings: View {
    @AppStorage("cinemax.design") private var design = "hype"
    @AppStorage("cinemax.heroAuto") private var heroAuto = true
    @AppStorage("cinemax.showContinue") private var showContinue = true
    @AppStorage("cinemax.showHero") private var showHero = true
    @AppStorage("cinemax.autoRefresh") private var autoRefresh = true
    @AppStorage("cinemax.maxShelves") private var maxShelves = 28
    @State private var posterWidth = Int(CinemaxPrefs.posterMinWidth)

    private let shelfLimits: [(value: Int, title: String)] = [
        (14, "14 صفًا"), (28, "28 صفًا"), (42, "42 صفًا")
    ]

    private let options: [(id: String, title: String, detail: String)] = [
        ("hype", "واجهة Hype", "شعار مرجاني وبحث كبسولة، هيرو واحد ولوحة صفوف، وخمسة تبويبات"),
        ("cinematic", "سينمائي زجاجي", "هيرو بعرض الشاشة ووهج بلون البوستر وصفوف بوسترات"),
        ("wall", "جدار بوسترات", "بحث أولًا وشبكات كثيفة وعدّادات، بلا هيرو"),
        ("panorama", "بانورامي", "خلفية واحدة غامرة ثم بطاقات عريضة 16:9")
    ]

    private let densities: [(width: Int, title: String)] = [
        (128, "كبيرة"), (112, "متوسطة"), (96, "صغيرة")
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 22) {
                VStack(spacing: 10) {
                    ForEach(options, id: \.id) { option in
                        Button {
                            Haptics.select()
                            withAnimation(.easeInOut(duration: 0.2)) { design = option.id }
                        } label: {
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(option.title)
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundStyle(.white)
                                    Text(option.detail)
                                        .font(.system(size: 11))
                                        .foregroundStyle(CinemaTheme.muted)
                                        .multilineTextAlignment(.leading)
                                }
                                Spacer()
                                Image(systemName: design == option.id ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 20))
                                    .foregroundStyle(design == option.id ? CinemaTheme.accent : CinemaTheme.muted)
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(
                                design == option.id ? CinemaTheme.accent.opacity(0.14) : CinemaTheme.surface,
                                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .strokeBorder(
                                        design == option.id ? CinemaTheme.accent.opacity(0.6) : CinemaTheme.hairline,
                                        lineWidth: 1
                                    )
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("حجم البوسترات في الشبكات")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(densities, id: \.width) { entry in
                            Button {
                                Haptics.select()
                                posterWidth = entry.width
                                CinemaxPrefs.setPosterWidth(entry.width)
                            } label: {
                                Text(entry.title)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(posterWidth == entry.width ? .black : .white)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 9)
                                    .background {
                                        Capsule().fill(
                                            posterWidth == entry.width ? CinemaTheme.accent : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                Toggle(isOn: $heroAuto) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("الهيرو يتقدم تلقائيًا")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("ينتقل الغلاف الكبير إلى العنوان التالي كل ثماني ثوانٍ")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)
                .padding(.top, 4)

                Toggle(isOn: $showContinue) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("صف «أكمل المشاهدة»")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("يظهر أعلى الرئيسية حين يكون لك موضع توقف محفوظ")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                Toggle(isOn: $showHero) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("الغلاف الكبير (الهيرو)")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("إيقافه يجعل الرئيسية صفوفًا فقط، بلا غلاف في الأعلى")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                Toggle(isOn: $autoRefresh) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("تحديث الكتالوجات عند كل زيارة")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("إيقافه يجعل الكتالوج يبقى دقيقة كاملة قبل إعادة الجلب، لتوفير البيانات")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                VStack(alignment: .leading, spacing: 8) {
                    Text("عدد الصفوف في الرئيسية")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(shelfLimits, id: \.value) { entry in
                            Button {
                                Haptics.select()
                                maxShelves = entry.value
                                HomeModel.shared.resetCache()
                            } label: {
                                Text(entry.title)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(maxShelves == entry.value ? .black : .white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background {
                                        Capsule().fill(
                                            maxShelves == entry.value ? CinemaTheme.accent : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    Text("عدد أكبر يعني صفوفًا أكثر من إضافاتك، وجلبًا أطول قليلًا في أول تحميل.")
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineSpacing(3)
                }

                if !HomeModel.shared.hiddenShelves.isEmpty {
                    HStack(spacing: 8) {
                        Text("\(HomeModel.shared.hiddenShelves.count) صفًا مخفيًا في الرئيسية")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                        Spacer()
                        Button("إظهار الكل") {
                            Haptics.select()
                            HomeModel.shared.unhideAll()
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                        .buttonStyle(.plain)
                    }
                }
                }
                .padding(16)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("التخطيط")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct SubtitleSettings: View {
    @AppStorage("cinemax.subtitleLang") private var language = "ar"
    @AppStorage("cinemax.subtitleSize") private var size = "medium"
    @AppStorage("cinemax.subtitleAuto") private var autoSelect = true

    private let languages: [(id: String, title: String)] = [
        ("ar", "العربية"), ("en", "English"), ("off", "إيقاف")
    ]
    private let sizes: [(id: String, title: String)] = [
        ("small", "صغير"), ("medium", "متوسط"), ("large", "كبير")
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("اللغة المفضلة")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(languages, id: \.id) { entry in
                            Button {
                                Haptics.select()
                                language = entry.id
                            } label: {
                                Text(entry.title)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(language == entry.id ? .black : .white)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 9)
                                    .background {
                                        Capsule().fill(
                                            language == entry.id ? CinemaTheme.accent : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("حجم خط الترجمة")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(sizes, id: \.id) { entry in
                            Button {
                                Haptics.select()
                                size = entry.id
                            } label: {
                                Text(entry.title)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(size == entry.id ? .black : .white)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 9)
                                    .background {
                                        Capsule().fill(
                                            size == entry.id ? CinemaTheme.accent : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                // A dark frame that shows exactly how big the subtitle will be.
                VStack {
                    Spacer(minLength: 0)
                    Text("هكذا ستظهر الترجمة على الشاشة")
                        .font(.system(size: previewSize, weight: .semibold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 14)
                        .shadow(color: .black.opacity(0.9), radius: 3, y: 1)
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 116)
                .background(Color.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
                )

                Toggle(isOn: $autoSelect) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("اختيار الترجمة تلقائيًا")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("عند تشغيل ملف فيه ترجمة باللغة المفضلة تُختار وحدها")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                Text("الملفات التي لا تحتوي ترجمة مدمجة: افتح لوحة التشغيل ← «ترجمة من رابط» والصق رابط ملف .srt أو .vtt، ويجلبه المشغّل مباشرة.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)
            }
            .padding(16)
            .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("الترجمة")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// The point size the player uses for each of the three steps.
    private var previewSize: CGFloat {
        switch size {
        case "small": return 13
        case "large": return 21
        default: return 17
        }
    }
}

// MARK: - Self test

/// Runs the parts that decide whether a source plays — the HLS reader, the
/// racing probe, the expiry reader, the search folding and the file writer —
/// and shows every answer with the time it took, so a slow or wrong result is
/// visible on the screen instead of guessed at.
struct SelfTestView: View {
    private struct Outcome: Identifiable {
        let id: String
        let title: String
        var running = false
        var passed: Bool?
        var note = ""
        var milliseconds = 0
    }

    @State private var outcomes: [Outcome] = []
    @State private var running = false

    /// Apple's own test stream: a master playlist that always answers and needs
    /// no key, so the reader is checked against real bytes.
    private let sample = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"

    private var summary: String {
        let done = outcomes.filter { $0.passed != nil }
        guard !done.isEmpty else { return "الفحص الذاتي" }
        let passed = done.filter { $0.passed == true }.count
        return "\(passed) من \(done.count) ناجح"
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(summary)
                        .font(.system(size: 24, weight: .heavy))
                        .foregroundStyle(.white)
                    Text("يفحص داخل التطبيق ما يقرّر إن كان المصدر سيعمل: تطبيع البحث، انتهاء صلاحية الروابط، قراءة HLS، سباق المرايا، وحفظ الملفات. فحصان منها يحتاجان اتصالًا.")
                        .font(.system(size: 12))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineSpacing(4)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))

                Button {
                    Haptics.tap()
                    Task { @MainActor in await run() }
                } label: {
                    HStack(spacing: 8) {
                        if running {
                            ProgressView().tint(.black)
                        } else {
                            Image(systemName: "bolt.horizontal.circle.fill")
                                .font(.system(size: 13, weight: .bold))
                        }
                        Text(running ? "يُجري الفحوص…" : "تشغيل الفحوص")
                            .font(.system(size: 14, weight: .bold))
                    }
                    .foregroundStyle(.black)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(CinemaTheme.accent, in: Capsule())
                }
                .buttonStyle(CinemaPressStyle())
                .disabled(running)

                VStack(spacing: 0) {
                    ForEach(outcomes) { outcome in
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: icon(outcome))
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(tint(outcome))
                                .frame(width: 22)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(outcome.title)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(.white)
                                if !outcome.note.isEmpty {
                                    Text(outcome.note)
                                        .font(.system(size: 11))
                                        .foregroundStyle(CinemaTheme.muted)
                                        .lineLimit(3)
                                        .lineSpacing(2)
                                }
                            }
                            Spacer()
                            if outcome.milliseconds > 0 {
                                Text("\(outcome.milliseconds) م.ث")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                        }
                        .padding(14)
                        if outcome.id != outcomes.last?.id {
                            Divider().overlay(CinemaTheme.hairline)
                        }
                    }
                }
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))

                Text("النتيجة تُسجَّل في سجل الأحداث، فما يظهر هنا يُقارن بما يحدث أثناء التشغيل.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(3)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("الفحص الذاتي")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func icon(_ outcome: Outcome) -> String {
        if outcome.running { return "hourglass" }
        guard let passed = outcome.passed else { return "circle" }
        return passed ? "checkmark.circle.fill" : "exclamationmark.triangle.fill"
    }

    private func tint(_ outcome: Outcome) -> Color {
        if outcome.running { return CinemaTheme.muted }
        guard let passed = outcome.passed else { return CinemaTheme.muted }
        return passed ? CinemaTheme.accent : CinemaTheme.favourite
    }

    @MainActor
    private func begin(_ id: String) -> Date {
        if let index = outcomes.firstIndex(where: { $0.id == id }) {
            outcomes[index].running = true
        }
        return Date()
    }

    @MainActor
    private func finish(_ id: String, passed: Bool, note: String, since start: Date) {
        guard let index = outcomes.firstIndex(where: { $0.id == id }) else { return }
        outcomes[index].running = false
        outcomes[index].passed = passed
        outcomes[index].note = note
        outcomes[index].milliseconds = Int(Date().timeIntervalSince(start) * 1000)
    }

    @MainActor
    private func run() async {
        guard !running else { return }
        running = true
        outcomes = [
            Outcome(id: "fold", title: "تطبيع البحث العربي"),
            Outcome(id: "expiry", title: "قراءة انتهاء صلاحية الرابط"),
            Outcome(id: "hls", title: "قراءة قائمة HLS"),
            Outcome(id: "race", title: "سباق المرايا"),
            Outcome(id: "files", title: "الكتابة في مجلد التنزيلات"),
            Outcome(id: "display", title: "الدوران ومقاس الشاشة"),
            Outcome(id: "aspect", title: "نسبة الصورة والتكبير"),
            Outcome(id: "search", title: "البحث في كل المصادر"),
            Outcome(id: "storage", title: "حفظ واسترجاع الإعدادات")
        ]
        await checkFold()
        await checkExpiry()
        await checkHLS()
        await checkRace()
        await checkFiles()
        await checkDisplay()
        await checkAspect()
        await checkSearch()
        await checkStorage()
        running = false
        EventLog.shared.add("selftest", summary)
    }

    @MainActor
    private func checkFold() async {
        let start = begin("fold")
        let arabic = SearchIndex.normalize("أَلْف لَيْلَة")
        guard arabic == SearchIndex.normalize("الف ليله") else {
            finish("fold", passed: false, note: "لم يتطابق: \(arabic)", since: start)
            return
        }
        guard SearchIndex.normalize("The Matrix") == "the matrix" else {
            finish("fold", passed: false, note: "اللاتيني لم يُطوى كما يجب", since: start)
            return
        }
        finish("fold", passed: true, note: "«أَلْف لَيْلَة» ← «\(arabic)»", since: start)
    }

    @MainActor
    private func checkExpiry() async {
        let start = begin("expiry")
        let near = Int(Date().addingTimeInterval(120).timeIntervalSince1970)
        let far = Int(Date().addingTimeInterval(600).timeIntervalSince1970 * 1000)
        guard StreamExpiry.isExpiring("https://x.test/film.m3u8?expires=\(near)") else {
            finish("expiry", passed: false, note: "الرابط القريب لم يُقرأ منتهيًا", since: start)
            return
        }
        guard !StreamExpiry.isExpiring("https://x.test/film.m3u8?exp=\(far)", within: 300) else {
            finish("expiry", passed: false, note: "الرابط البعيد اعتُبر منتهيًا", since: start)
            return
        }
        guard StreamExpiry.date(in: "https://x.test/film.m3u8") == nil else {
            finish("expiry", passed: false, note: "رابط بلا وقت أعطى تاريخًا", since: start)
            return
        }
        finish("expiry", passed: true, note: "ثوانٍ وميلي ثانية، وبلا وقت لا شيء", since: start)
    }

    @MainActor
    private func checkHLS() async {
        let start = begin("hls")
        let resolved = await HLSParser.shared.bestStream(
            for: sample,
            headers: [:],
            preferredQuality: nil
        )
        guard let resolved else {
            finish("hls", passed: false, note: "لم تُقرأ القائمة — قد يكون الاتصال مقطوعًا", since: start)
            return
        }
        finish("hls", passed: true, note: "اختير \(resolved.label) من القائمة الرئيسية", since: start)
    }

    @MainActor
    private func checkRace() async {
        let start = begin("race")
        let dead = StreamChoice(
            url: "https://cinemax-selftest.invalid/none.mp4",
            addon: "فحص",
            title: "رابط ميت",
            quality: nil
        )
        let alive = StreamChoice(url: sample, addon: "فحص", title: "رابط حيّ", quality: nil)
        guard let winner = await StreamRacer.shared.firstPlayable([dead, alive]) else {
            finish("race", passed: false, note: "لم يجب أي رابط — قد يكون الاتصال مقطوعًا", since: start)
            return
        }
        guard winner.url == alive.url else {
            finish("race", passed: false, note: "فاز الرابط الميت على الحيّ", since: start)
            return
        }
        finish("race", passed: true, note: "فاز الحيّ وتم إلغاء الباقي", since: start)
    }

    @MainActor
    private func checkFiles() async {
        let start = begin("files")
        let folder = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let probe = folder.appendingPathComponent("cinemax-selftest.txt")
        do {
            try "ok".write(to: probe, atomically: true, encoding: .utf8)
            let back = try String(contentsOf: probe, encoding: .utf8)
            try FileManager.default.removeItem(at: probe)
            guard back == "ok" else {
                finish("files", passed: false, note: "القراءة رجعت فارغة", since: start)
                return
            }
            finish("files", passed: true, note: "كتب وقرأ وحذف في مجلد المستندات", since: start)
        } catch {
            finish("files", passed: false, note: error.localizedDescription, since: start)
        }
    }

    @MainActor
    private func checkDisplay() async {
        let start = begin("display")
        let declared = Bundle.main.object(
            forInfoDictionaryKey: "UISupportedInterfaceOrientations"
        ) as? [String] ?? []
        guard declared.contains("UIInterfaceOrientationLandscapeRight") else {
            finish("display", passed: false, note: "الملف لا يعلن الوضع الأفقي", since: start)
            return
        }
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState != .background }
        let root = scene?.keyWindow?.rootViewController?.supportedInterfaceOrientations
        let mask = OrientationManager.shared.mask
        guard root == mask else {
            finish(
                "display",
                passed: false,
                note: "الجذر لا يتبع إعداد الدوران (\(root?.rawValue ?? 0) ≠ \(mask.rawValue))",
                since: start
            )
            return
        }
        let size = UIScreen.main.bounds.size
        let scale = UIScreen.main.scale
        finish(
            "display",
            passed: true,
            note: "\(Int(size.width))×\(Int(size.height)) · \(Int(scale))x · الوضع الأفقي متاح",
            since: start
        )
    }

    /// Counts how many sources answered, from inside the check's own closures.
    private final class Counter {
        var value = 0
    }

    /// The picture's own numbers: a ratio left from an older build is what
    /// stretches the film, so it is found and cleared here rather than guessed
    /// at on the screen.
    @MainActor
    private func checkAspect() async {
        let start = begin("aspect")
        let defaults = UserDefaults.standard
        let allowed = ["", "auto", "fit", "fill", "16:9", "4:3", "2.35:1"]
        let aspect = defaults.string(forKey: "cinemax.aspect") ?? ""
        let zoom = (defaults.object(forKey: "cinemax.zoom") as? Double) ?? 0
        guard allowed.contains(aspect) else {
            // An unknown ratio is cleared instead of being carried into the next
            // film, where it would show as a stretched picture.
            defaults.set("", forKey: "cinemax.aspect")
            finish(
                "aspect",
                passed: false,
                note: "نسبة غير معروفة «\(aspect)» — أُعيدت إلى ملاءمة",
                since: start
            )
            return
        }
        guard zoom >= -0.5, zoom <= 0.5 else {
            defaults.set(0, forKey: "cinemax.zoom")
            finish("aspect", passed: false, note: "تكبير خارج الحد \(zoom) — أُعيد إلى 0", since: start)
            return
        }
        finish(
            "aspect",
            passed: true,
            note: aspect.isEmpty ? "ملاءمة بلا قص ولا تكبير" : "النسبة «\(aspect)» والتكبير \(zoom)",
            since: start
        )
    }

    /// The live search, run the way the screen runs it: every source answers
    /// on its own, and the count says which of them answered at all.
    @MainActor
    private func checkSearch() async {
        let start = begin("search")
        let counter = Counter()
        let token = "selftest-\(UUID().uuidString)"
        let found = await CinemaAPI.shared.searchStreaming("matrix", token: token) { _ in
            Task { @MainActor in counter.value += 1 }
        }
        // The sources behind the check are stopped too, so a slow addon does not
        // keep working after the answer was already read.
        CinemaAPI.shared.cancelSearch(token)
        guard !found.isEmpty else {
            finish(
                "search",
                passed: false,
                note: "لم تُرجع إضافة نتيجة — الاتصال أو المصادر",
                since: start
            )
            return
        }
        finish(
            "search",
            passed: true,
            note: "\(found.count) نتيجة · \(max(counter.value, 1)) مصدرًا أجاب",
            since: start
        )
    }

    @MainActor
    private func checkStorage() async {
        let start = begin("storage")
        let key = "cinemax.selftest"
        let value = UUID().uuidString
        UserDefaults.standard.set(value, forKey: key)
        let back = UserDefaults.standard.string(forKey: key)
        UserDefaults.standard.removeObject(forKey: key)
        guard back == value else {
            finish("storage", passed: false, note: "القيمة لم ترجع كما كُتبت", since: start)
            return
        }
        finish("storage", passed: true, note: "كتب وقرأ ثم مسح المفتاح", since: start)
    }
}

struct PlaybackSettings: View {
    @AppStorage("cinemax.autoplayNext") private var autoplayNext = true
    @AppStorage("cinemax.skipIntro") private var skipIntro = true
    @AppStorage("cinemax.autoSkipIntro") private var autoSkipIntro = false
    @AppStorage("cinemax.seekSeconds") private var seekSeconds = 10
    @AppStorage("cinemax.autoHideControls") private var autoHideControls = true
    @AppStorage("cinemax.autoResume") private var autoResume = true
    @AppStorage("cinemax.speedFirst") private var speedFirst = false
    @AppStorage("cinemax.prefQuality") private var prefQuality = "auto"
    @AppStorage("cinemax.playerEngine") private var playerEngine = "auto"
    @AppStorage("cinemax.defaultSpeed") private var defaultSpeed = 1.0

    private let engines: [(id: String, title: String)] = [
        ("auto", "تلقائي"), ("mpv", "libmpv"), ("system", "مشغل النظام"), ("external", "تطبيق خارجي")
    ]

    private let speeds: [Double] = [0.75, 1, 1.25, 1.5]
    private let steps: [Int] = [5, 10, 15, 30]
    private let qualities: [(id: String, title: String)] = [
        ("auto", "تلقائي"), ("4K", "4K"), ("1080p", "1080p"), ("720p", "720p")
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                Toggle(isOn: $autoplayNext) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("تشغيل الحلقة التالية تلقائيًا")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("ينتقل المشغّل إلى الحلقة التالية حين تنتهي الحالية")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                Toggle(isOn: $skipIntro) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("زر تخطي المقدمة")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("يظهر في أول دقيقة ونصف من الأفلام والمسلسلات الطويلة")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                Toggle(isOn: $autoSkipIntro) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("تخطي المقدمة تلقائيًا")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("ينتقل إلى الدقيقة الأولى ونصف وحده في العناوين الطويلة")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                VStack(alignment: .leading, spacing: 8) {
                    Text("المشغّل المستخدم عند فتح أي مصدر")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(engines, id: \.id) { entry in
                            Button {
                                Haptics.select()
                                playerEngine = entry.id
                            } label: {
                                Text(entry.title)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(playerEngine == entry.id ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background {
                                        Capsule().fill(
                                            playerEngine == entry.id
                                                ? CinemaTheme.accent
                                                : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    Text(engineDetail)
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineSpacing(3)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("الجودة المفضلة")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(qualities, id: \.id) { entry in
                            Button {
                                Haptics.select()
                                prefQuality = entry.id
                            } label: {
                                Text(entry.title)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(prefQuality == entry.id ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background {
                                        Capsule().fill(
                                            prefQuality == entry.id ? CinemaTheme.accent : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    Text("المصادر الأخرى تبقى ظاهرة؛ الجودة المختارة تُقدَّم في أعلى القائمة فقط.")
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineSpacing(3)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("ترتيب المصادر")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        orderChip("الجودة أولًا", value: false)
                        orderChip("السرعة أولًا", value: true)
                    }
                    Text("السرعة تُقاس من كل مصدر عند فتح الصفحة، وتُحفظ لتُظهر الأسرع أولًا حين تختاره.")
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineSpacing(3)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("مقدار التقديم والتأخير")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(steps, id: \.self) { step in
                            Button {
                                Haptics.select()
                                seekSeconds = step
                            } label: {
                                Text("\(step) ث")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(seekSeconds == step ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background {
                                        Capsule().fill(
                                            seekSeconds == step ? CinemaTheme.accent : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                Toggle(isOn: $autoHideControls) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("إخفاء أدوات المشغّل تلقائيًا")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("تختفي الأزرار بعد ثوانٍ من التشغيل، وتعود بنقرة على الصورة")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                Toggle(isOn: $autoResume) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("متابعة من موضع التوقف")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("يفتح المشغّل من حيث توقفت؛ إيقافه يبدأ كل عنوان من أوله")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                VStack(alignment: .leading, spacing: 8) {
                    Text("سرعة التشغيل الافتراضية")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(speeds, id: \.self) { value in
                            Button {
                                Haptics.select()
                                defaultSpeed = value
                            } label: {
                                Text(value == 1 ? "عادي" : String(format: "%g×", value))
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(abs(defaultSpeed - value) < 0.01 ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background {
                                        Capsule().fill(
                                            abs(defaultSpeed - value) < 0.01
                                                ? CinemaTheme.accent
                                                : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("المشغّل")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("libmpv — MKV، AVI، HEVC، ترجمة وصوت مدمج، سرعة تشغيل")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    Spacer()
                    Text("MPV")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                }
            }
            .padding(16)
            .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("التشغيل")
        .navigationBarTitleDisplayMode(.inline)
    }
    /// What the chosen engine brings, so the trade-off is not a mystery.
    private var engineDetail: String {
        switch playerEngine {
        case "auto":
            return "تلقائي: يفتح روابط HLS وMP4 في مشغل النظام لتعمل الصورة داخل الصورة وAirPlay، ويُبقي libmpv للملفات الثقيلة (MKV وHEVC وAV1) بكل خيارات اللوحة."
        case "system":
            return "AVPlayer من النظام: يشغّل HLS وMP4 بسلاسة، ويدعم الصورة داخل الصورة وAirPlay. الصيغ الغريبة (MKV/AV1) قد ترفضها."
        case "external":
            return "يفتح الرابط في مشغّل آخر مثبت على الجهاز عبر ورقة المشاركة (VLC أو Infuse أو أي مشغّل)."
        default:
            return "libmpv: يفتح كل الصيغ تقريبًا (MKV وHEVC وAV1) مع كل خيارات اللوحة، لكن الصورة داخل الصورة غير متاحة."
        }
    }

    private func orderChip(_ title: String, value: Bool) -> some View {        Button {
            Haptics.select()
            speedFirst = value
        } label: {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(speedFirst == value ? .black : .white)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background {
                    Capsule().fill(speedFirst == value ? CinemaTheme.accent : Color.white.opacity(0.08))
                }
        }
        .buttonStyle(.plain)
    }
}

/// The knobs that decide how the player draws and buffers a stream. A black
/// picture is usually one of these two switches, so both are in the viewer's hands.
struct StreamingSettings: View {
    @AppStorage("cinemax.hwdec") private var hardwareDecoding = true
    @AppStorage("cinemax.mpvFast") private var fastProfile = false
    @AppStorage("cinemax.cacheMB") private var cacheMB = 256
    @AppStorage("cinemax.allowRotation") private var allowRotation = true
    @AppStorage("cinemax.subCp1256") private var arabicSubtitleEncoding = false

    private let caches: [Int] = [128, 256, 512]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                Toggle(isOn: $hardwareDecoding) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("فك الترميز العتادي (VideoToolbox)")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("أوقفه إذا بقيت صيغة معينة على شاشة سوداء؛ الفك البرمجي أبطأ لكنه يعمل مع كل شيء")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                Toggle(isOn: $fastProfile) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("بروفايل الأداء السريع")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("يخفف المعالجة لتشغيل 4K بسلاسة على الأجهزة الأضعف، بجودة رسم أقل قليلًا")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                Toggle(isOn: $allowRotation) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("تدوير الشاشة في المشغّل")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("يسمح للمشغّل بالدوران إلى الأفقي وحده؛ بقية التطبيق يبقى رأسيًا")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                Toggle(isOn: $arabicSubtitleEncoding) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("ترميز عربي لملفات الترجمة (Windows-1256)")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Text("فعّله إذا ظهرت الترجمة العربية رموزًا غريبة بدل الحروف")
                            .font(.system(size: 11))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .tint(CinemaTheme.accent)

                VStack(alignment: .leading, spacing: 8) {
                    Text("حجم كاش البث")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(caches, id: \.self) { size in
                            Button {
                                Haptics.select()
                                cacheMB = size
                            } label: {
                                Text("\(size) MB")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(cacheMB == size ? .black : .white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background {
                                        Capsule().fill(
                                            cacheMB == size ? CinemaTheme.accent : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    Text("كاش أكبر يمتص تقطع المصدر البطيء، ويستهلك ذاكرة أكثر. الافتراضي 256 MB.")
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineSpacing(3)
                }

                Text("كل ما هنا يسري على التشغيل التالي. إذا ظهرت شاشة سوداء: جرّب «مصدر آخر» أولًا، ثم أوقف فك الترميز العتادي، ثم فعّل بروفايل الأداء.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)
            }
            .padding(16)
            .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("البث والمشغل")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct AboutSettings: View {
    @AppStorage("cinemax.onboarded") private var onboarded = true
    @ObservedObject private var log = EventLog.shared
    @State private var copied = false
    @State private var addonsCount = 0
    @State private var reposCount = 0

    private var diagnostics: String {
        let device = UIDevice.current
        let hardware = (UserDefaults.standard.object(forKey: "cinemax.hwdec") as? Bool) ?? true
        let storedCache = UserDefaults.standard.integer(forKey: "cinemax.cacheMB")
        let cache = storedCache == 0 ? 256 : storedCache
        return [
            "CINEMAX \(CinemaAPI.shared.versionLabel)",
            "iOS \(device.systemVersion)",
            device.model,
            "المشغّل: \(CinemaxPrefs.playerEngine)",
            "فك الترميز العتادي: " + (hardware ? "مفعّل" : "متوقف"),
            "كاش البث: \(cache) MB"
        ].joined(separator: "\n")
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                Text("CINEMAX \(CinemaAPI.shared.versionLabel)")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                Text("واجهة SwiftUI فوق محرك واحد: إضافات Stremio، مشغّل libmpv للـMKV مع الترجمة المدمجة، مصادِر متوازية مرتّبة بالجودة والسرعة، متابعة مشاهدة محلية، وكتالوج TMDB عربي اختياري.")
                    .font(.system(size: 13))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(5)
                Text("التطبيق لا يستضيف أي وسائط؛ يعرض ما تجلبه إضافاتك.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)

                Divider().overlay(CinemaTheme.hairline)

                Text("البيانات الوصفية والصور من TMDB، وتُستخدم وفق شروط استخدامهم. المصادر من إضافات Stremio التي تضيفها بنفسك.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)

                Divider().overlay(CinemaTheme.hairline)

                HStack {
                    Text("المشغّل")
                        .font(.system(size: 12))
                        .foregroundStyle(CinemaTheme.muted)
                    Spacer()
                    Text("libmpv · MKV/AVI/HEVC")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                }
                HStack {
                    Text("الواجهة")
                        .font(.system(size: 12))
                        .foregroundStyle(CinemaTheme.muted)
                    Spacer()
                    Text("SwiftUI")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                }
                HStack {
                    Text("المصادر")
                        .font(.system(size: 12))
                        .foregroundStyle(CinemaTheme.muted)
                    Spacer()
                    Text("\(addonsCount) إضافة · \(reposCount) مستودع")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                }

                Divider().overlay(CinemaTheme.hairline)

                VStack(alignment: .leading, spacing: 6) {
                    Text("ما الجديد في هذا الإصدار")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                    ForEach([
                        "مشغّل libmpv مع إصلاح الشاشة السوداء (MoltenVK) واستعادة الصورة بعد الخلفية",
                        "مصادر من كل إضافة Stremio ومن مزوّدي Nuvio مع بيان ما لم يستجب",
                        "لوحة مشغّل: مصدر آخر، ترجمة من رابط، سطوع، سرعة، مؤقت نوم، وقائمة حلقات",
                        "إعدادات البث: فك ترميز عتادي أو برمجي، بروفايل أداء، وحجم كاش",
                        "ثلاثة تصاميم للرئيسية، إخفاء الصفوف، وترتيب المكتبة والبحث",
                        "إدارة الإضافات مع نقل الأولوية وفتح إعدادات كل إضافة داخل التطبيق",
                        "تنزيل متوازٍ بإشعار، إحصاءات محلية، ونسخة احتياطية كاملة"
                    ], id: \.self) { line in
                        HStack(alignment: .top, spacing: 6) {
                            Text("•").foregroundStyle(CinemaTheme.accent)
                            Text(line)
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }

                Link(destination: URL(string: "https://www.themoviedb.org/")!) {
                    Text("themoviedb.org")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                }

                Divider().overlay(CinemaTheme.hairline)

                Button {
                    Haptics.tap()
                    UIPasteboard.general.string = diagnostics
                    withAnimation(.easeInOut(duration: 0.2)) { copied = true }
                    Task {
                        try? await Task.sleep(nanoseconds: 1_600_000_000)
                        withAnimation(.easeInOut(duration: 0.2)) { copied = false }
                    }
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("نسخ معلومات التشغيل")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.white)
                            Text(copied ? "نُسخت إلى الحافظة" : "الإصدار والنظام والمشغّل وإعداداته")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                        Spacer()
                        Image(systemName: "doc.on.doc")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                    }
                }
                .buttonStyle(.plain)

                Divider().overlay(CinemaTheme.hairline)

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("سجل الأحداث")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.white)
                        Spacer()
                        Text("\(log.entries.count)")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    Text("آخر ما فعله التطبيق: جلب المصادر وزمنها، التنزيلات ومحاولاتها، والأخطاء. يُرسل مع أي بلاغ.")
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineSpacing(3)
                    HStack(spacing: 10) {
                        Button {
                            Haptics.tap()
                            UIPasteboard.general.string = log.text.isEmpty ? "لا أحداث بعد" : log.text
                            withAnimation(.easeInOut(duration: 0.2)) { copied = true }
                            Task {
                                try? await Task.sleep(nanoseconds: 1_600_000_000)
                                withAnimation(.easeInOut(duration: 0.2)) { copied = false }
                            }
                        } label: {
                            Text("نسخ السجل")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(.black)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(CinemaTheme.accent, in: Capsule())
                        }
                        .buttonStyle(CinemaPressStyle())

                        Button {
                            Haptics.tap()
                            log.clear()
                        } label: {
                            Text("مسح")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(CinemaTheme.favourite)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(Color.white.opacity(0.08), in: Capsule())
                        }
                        .buttonStyle(.plain)
                        Spacer()
                    }
                }

                Divider().overlay(CinemaTheme.hairline)

                Button {
                    Haptics.select()
                    onboarded = false
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("أظهر شاشة البداية مجددًا")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.white)
                            Text(onboarded
                                 ? "ستظهر عند فتح التطبيق في المرة القادمة"
                                 : "ستظهر عند فتح التطبيق")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                        Spacer()
                        Image(systemName: "arrow.counterclockwise")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                    }
                }
                .buttonStyle(.plain)
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("عن التطبيق")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            addonsCount = await CinemaAPI.shared.installedAddons().count
            reposCount = await CinemaAPI.shared.nuvioRepositories().count
        }
    }
}

/// A web page the app shows in place, for an addon's own settings screen.
struct SafariTarget: Identifiable {
    let id = UUID()
    let url: URL
}

struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let controller = SFSafariViewController(url: url)
        controller.preferredControlTintColor = UIColor(CinemaTheme.accent)
        controller.dismissButtonStyle = .close
        return controller
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

/// Stremio addons serve their settings at /configure beside their manifest.
func addonConfigureURL(_ manifest: String) -> URL? {
    guard var components = URLComponents(string: manifest) else { return nil }
    var path = components.path
    if path.hasSuffix("/") { path.removeLast() }
    if path.hasSuffix("manifest.json") {
        path = String(path.dropLast("manifest.json".count))
    }
    components.path = path + "configure"
    components.query = nil
    components.fragment = nil
    return components.url
}

struct AddonsSettings: View {
    @State private var addons: [InstalledAddonRow] = []
    @State private var suggested: [SuggestedAddonRow] = []
    @State private var addonURL = ""
    @State private var query = ""
    @State private var busy = false
    @State private var message: String?
    @State private var installing: String?
    @State private var order = "az"
    @State private var checks: [String: AddonCheckRow] = [:]
    @State private var checking = false
    @State private var updating: String?
    @State private var configTarget: SafariTarget?
    @State private var showInstalled = true
    @State private var showSuggested = true
    @State private var installedQuery = ""
    @State private var profiles: [AddonProfile] = []
    @State private var filter = "all"
    @State private var preview: AddonPreview?
    @State private var previewURL = ""
    @State private var previewing = false
    @State private var showCatalogs = false
    @State private var browsing: AddonBrowseTarget?

    private let orders: [(id: String, title: String)] = [
        ("az", "أ–ي"), ("za", "ي–أ"), ("fastest", "الأسرع"), ("recent", "الأحدث")
    ]

    private let filters: [(id: String, title: String)] = [
        ("all", "الكل"), ("stream", "مصادر"), ("catalog", "كتالوجات"),
        ("subtitles", "ترجمة"), ("meta", "تفاصيل")
    ]

    /// Filtered and ordered the way the viewer asked, from the profiles the bridge
    /// reports, so a capability filter is answered by each manifest itself.
    private var visibleInstalled: [InstalledAddonRow] {
        let text = installedQuery.trimmingCharacters(in: .whitespaces)
        var list = addons
        if !text.isEmpty {
            list = list.filter { $0.name.localizedCaseInsensitiveContains(text) }
        }
        if filter != "all" {
            let matching = Set(profiles.filter { profile in
                profile.resources.contains(filter)
                    || (filter == "catalog" && !profile.catalogs.isEmpty)
            }.map(\.url))
            list = list.filter { matching.contains($0.url) }
        }
        switch order {
        case "za":
            return list.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedDescending }
        case "fastest":
            return list.sorted { (checks[$0.url]?.ms ?? .max) < (checks[$1.url]?.ms ?? .max) }
        case "recent":
            let dates = Dictionary(profiles.map { ($0.url, $0.installedAt) }, uniquingKeysWith: { first, _ in first })
            return list.sorted { (dates[$0.url] ?? 0) > (dates[$1.url] ?? 0) }
        default:
            return list.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        }
    }

    /// The addon the manager knows most about: a profile, or a bare row until the
    /// profiles arrive.
    private func profile(for addon: InstalledAddonRow) -> AddonProfile {
        profiles.first { $0.url == addon.url || $0.id == addon.id }
            ?? AddonProfile(
                id: addon.id,
                name: addon.name,
                url: addon.url,
                version: addon.version,
                about: "",
                installedAt: 0,
                resources: [],
                types: [],
                catalogs: [],
                configurable: false
            )
    }

    private var visibleSuggested: [SuggestedAddonRow] {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return suggested }
        return suggested.filter { $0.name.localizedCaseInsensitiveContains(text) }
    }

    private var missing: Int {
        suggested.filter { source in addons.allSatisfy { $0.url != source.url } }.count
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                summary

                freeSet

                HStack(spacing: 8) {
                    Button {
                        Task { await runCheck() }
                    } label: {
                        HStack(spacing: 6) {
                            if checking { ProgressView().tint(.white) }
                            Text(checking ? "جارٍ الفحص…" : "فحص الإضافات")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(Color.white.opacity(0.1), in: Capsule())
                    }
                    .buttonStyle(CinemaPressStyle())
                    .disabled(checking || addons.isEmpty)

                    Button {
                        Task { await updateAll() }
                    } label: {
                        Text(updateAllLabel)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(CinemaTheme.accent.opacity(0.14), in: Capsule())
                    }
                    .buttonStyle(CinemaPressStyle())
                    .disabled(checking || updating != nil || addons.isEmpty)
                }

                healthLine

                Button {
                    Task { await installAll() }
                } label: {
                    HStack(spacing: 8) {
                        if busy { ProgressView().tint(.black) }
                        Text(busy ? "جارٍ التثبيت…" : "ثبّت الناقص (\(missing))")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.black)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(CinemaTheme.accent, in: Capsule())
                }
                .buttonStyle(CinemaPressStyle())
                .disabled(busy || missing == 0)

                HStack(spacing: 8) {
                    TextField("رابط manifest https://…", text: $addonURL)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12))
                        .foregroundStyle(.white)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 11)
                        .background(CinemaTheme.surface, in: Capsule())
                    Button(busy ? "…" : "إضافة") {
                        Task { await install(addonURL) }
                    }
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 11)
                    .background(Color.white.opacity(0.12), in: Capsule())
                    .disabled(busy || addonURL.trimmingCharacters(in: .whitespaces).isEmpty)
                }

                HStack(spacing: 16) {
                    Button {
                        Task { await preparePreview() }
                    } label: {
                        HStack(spacing: 5) {
                            if previewing { ProgressView().tint(CinemaTheme.accent) }
                            Text(previewing ? "جارٍ القراءة…" : "معاينة قبل التثبيت")
                                .font(.system(size: 11, weight: .semibold))
                        }
                        .foregroundStyle(CinemaTheme.accent)
                    }
                    .buttonStyle(.plain)
                    .disabled(previewing || addonURL.trimmingCharacters(in: .whitespaces).isEmpty)

                    Button {
                        Haptics.tap()
                        let pasted = UIPasteboard.general.string ?? ""
                        if let range = pasted.range(of: "http") {
                            addonURL = String(pasted[range.lowerBound...])
                                .trimmingCharacters(in: .whitespacesAndNewlines)
                            message = "لُصق الرابط من الحافظة"
                        } else {
                            message = "لا رابط في الحافظة"
                        }
                    } label: {
                        Label("لصق", systemImage: "doc.on.clipboard")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(.white.opacity(0.85))
                    }
                    .buttonStyle(.plain)
                    Spacer()
                }

                if let message {
                    Text(message)
                        .font(.system(size: 12))
                        .foregroundStyle(message.contains("تعذّر") ? CinemaTheme.favourite : CinemaTheme.accent)
                }

                installedSection
                catalogSection
                suggestedSection
            }
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("الإضافات")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
        .sheet(item: $configTarget) { target in
            SafariView(url: target.url)
                .ignoresSafeArea()
                .onDisappear {
                    // The addon may have rewritten its manifest, so start over.
                    HomeModel.shared.resetCache()
                    Task { await load() }
                }
        }
        .sheet(isPresented: Binding(get: { preview != nil }, set: { if !$0 { preview = nil } })) {
            if let preview { previewSheet(preview) }
        }
        .sheet(item: $browsing) { target in
            AddonCatalogBrowser(shelf: target.shelf, type: target.type)
        }
    }

    private var summary: some View {
        HStack(spacing: 12) {
            counter("\(addons.count)", "مثبّتة")
            counter("\(suggested.count)", "جاهزة")
            counter("\(missing)", "ناقصة")
        }
    }

    private func counter(_ value: String, _ title: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 20, weight: .heavy))
                .foregroundStyle(CinemaTheme.accent)
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(CinemaTheme.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var installedSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Button {
                    Haptics.select()
                    withAnimation(.easeInOut(duration: 0.2)) { showInstalled.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: showInstalled ? "chevron.up" : "chevron.left")
                            .font(.system(size: 11, weight: .bold))
                        Text("المثبّتة (\(addons.count))")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundStyle(CinemaTheme.muted)
                }
                .buttonStyle(.plain)
                Spacer()
                if showInstalled {
                    Menu {
                        ForEach(orders, id: \.id) { entry in
                            Button {
                                Haptics.select()
                                order = entry.id
                            } label: {
                                Label(
                                    entry.title,
                                    systemImage: order == entry.id ? "checkmark" : "arrow.up.arrow.down"
                                )
                            }
                        }
                    } label: {
                        Label(
                            orders.first { $0.id == order }?.title ?? "ترتيب",
                            systemImage: "arrow.up.arrow.down"
                        )
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                    }
                }
            }
            if !showInstalled {
                EmptyView()
            } else if addons.isEmpty {
                Text("لا إضافة مثبتة بعد.")
                    .font(.system(size: 12))
                    .foregroundStyle(CinemaTheme.muted)
            } else {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                    TextField("ابحث بين المثبّتة", text: $installedQuery)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12))
                        .foregroundStyle(.white)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    Text("\(visibleInstalled.count)")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(CinemaTheme.surface, in: Capsule())

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(filters, id: \.id) { entry in
                            Button {
                                Haptics.select()
                                filter = entry.id
                            } label: {
                                Text(entry.title)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(filter == entry.id ? .black : .white)
                                    .padding(.horizontal, 11)
                                    .padding(.vertical, 6)
                                    .background {
                                        Capsule().fill(
                                            filter == entry.id
                                                ? CinemaTheme.accent
                                                : Color.white.opacity(0.08)
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                VStack(spacing: 0) {
                    ForEach(Array(visibleInstalled.enumerated()), id: \.element.id) { index, addon in
                        NavigationLink {
                            AddonDetailView(profile: profile(for: addon), onChange: {
                                Task { await load() }
                            })
                        } label: {
                            HStack(spacing: 10) {
                                avatar(addon.name)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(addon.name)
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(.white)
                                        .lineLimit(1)
                                    HStack(spacing: 6) {
                                        if let check = checks[addon.url] {
                                            Circle()
                                                .fill(check.ok ? CinemaTheme.accent : CinemaTheme.favourite)
                                                .frame(width: 6, height: 6)
                                            Text(check.ok ? "\(check.ms) مللي" : "لا يستجيب")
                                                .font(.system(size: 10))
                                                .foregroundStyle(CinemaTheme.muted)
                                        }
                                        Text(profile(for: addon).capabilitySummary)
                                            .font(.system(size: 10))
                                            .foregroundStyle(CinemaTheme.muted)
                                            .lineLimit(1)
                                    }
                                    if checks[addon.url]?.hasUpdate == true {
                                        Text("إصدار أحدث متاح")
                                            .font(.system(size: 10, weight: .semibold))
                                            .foregroundStyle(CinemaTheme.accent)
                                    }
                                }
                                Spacer(minLength: 8)
                                if updating == addon.url {
                                    ProgressView().tint(CinemaTheme.accent)
                                }
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            .padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button {
                                UIPasteboard.general.string = addon.url
                                message = "نُسخ رابط \(addon.name)"
                            } label: {
                                Label("نسخ الرابط", systemImage: "doc.on.doc")
                            }
                            ShareLink(item: addon.url) {
                                Label("مشاركة الرابط", systemImage: "square.and.arrow.up")
                            }
                            Button {
                                Task { await move(addon, -1) }
                            } label: {
                                Label("نقل للأعلى", systemImage: "arrow.up")
                            }
                            Button {
                                Task { await move(addon, 1) }
                            } label: {
                                Label("نقل للأسفل", systemImage: "arrow.down")
                            }
                            Button(role: .destructive) {
                                remove(addon)
                            } label: {
                                Label("حذف الإضافة", systemImage: "trash")
                            }
                        }
                        if index < visibleInstalled.count - 1 {
                            Divider().overlay(CinemaTheme.hairline)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
        }
    }

    /// Promotes or demotes an addon; its catalogues then come earlier or later.
    private func move(_ addon: InstalledAddonRow, _ delta: Int) async {
        Haptics.select()
        let updated = await CinemaAPI.shared.moveAddon(url: addon.url, delta: delta)
        if !updated.isEmpty { addons = updated }
        HomeModel.shared.resetCache()
    }

    private var suggestedSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button {
                Haptics.select()
                withAnimation(.easeInOut(duration: 0.2)) { showSuggested.toggle() }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: showSuggested ? "chevron.up" : "chevron.left")
                        .font(.system(size: 11, weight: .bold))
                    Text("المصادر الجاهزة (\(suggested.count))")
                        .font(.system(size: 12, weight: .semibold))
                }
                .foregroundStyle(CinemaTheme.muted)
            }
            .buttonStyle(.plain)

            if showSuggested {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12))
                    .foregroundStyle(CinemaTheme.muted)
                TextField("ابحث بين المصادر", text: $query)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12))
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(CinemaTheme.surface, in: Capsule())

            VStack(spacing: 0) {
                ForEach(Array(visibleSuggested.enumerated()), id: \.element.id) { index, source in
                    let installed = addons.contains { $0.url == source.url }
                    HStack(spacing: 10) {
                        Text(source.name)
                            .font(.system(size: 13))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Spacer()
                        if installing == source.url {
                            ProgressView().tint(CinemaTheme.accent)
                        } else if installed {
                            Text("مثبّت")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(CinemaTheme.accent)
                        } else {
                            HStack(spacing: 10) {
                                Button {
                                    Haptics.tap()
                                    Task { await previewSuggested(source) }
                                } label: {
                                    Image(systemName: "eye")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundStyle(CinemaTheme.muted)
                                }
                                .buttonStyle(.plain)
                                Button("تثبيت") {
                                    Task { await install(source.url) }
                                }
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(CinemaTheme.accent)
                                .disabled(busy)
                            }
                        }
                    }
                    .padding(.vertical, 9)
                    if index < visibleSuggested.count - 1 {
                        Divider().overlay(CinemaTheme.hairline)
                    }
                }
            }
            .padding(.horizontal, 14)
            .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
        }
    }

    private var updateAllLabel: String {
        let count = checks.values.filter { $0.hasUpdate }.count
        return count > 0 ? "حدّث الكل (\(count))" : "حدّث الكل"
    }

    /// What of the free set is already installed, and whether the set is being
    /// installed right now so a second tap does not start it again.
    @State private var curatedInstalled: Set<String> = []
    @State private var curatedBusy = false

    /// The free set: the addons and repositories that play over plain http or
    /// peer-to-peer, with what is already installed marked on each row.
    private var freeSet: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text("المجموعة المجانية")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white)
                Text("\(curatedInstalled.count)/\(CuratedSources.all.count)")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(Color.white.opacity(0.08), in: Capsule())
                Spacer()
                Button {
                    Haptics.tap()
                    Task { await installFreeSet() }
                } label: {
                    Text(curatedBusy ? "…" : "ثبّت الناقص")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                }
                .buttonStyle(.plain)
                .disabled(curatedBusy)
            }
            Text("إضافات ومستودعات مجانية تبثّ عبر HTTP أو P2P بلا ديبرد ولا بروكسي. تحققتُ من كل رابط قبل إدراجه، وحُذف ما صار ميتًا (WebStreamr مثلًا يُعلن نفسه متوقفًا).")
                .font(.system(size: 11))
                .foregroundStyle(CinemaTheme.muted)
                .lineSpacing(3)
            ForEach(CuratedSources.all) { source in
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: curatedInstalled.contains(source.id)
                          ? "checkmark.circle.fill"
                          : "arrow.down.circle")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(curatedInstalled.contains(source.id)
                                         ? CinemaTheme.accent
                                         : CinemaTheme.muted)
                        .frame(width: 20)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(source.name)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.white)
                        Text(source.note)
                            .font(.system(size: 10))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    Spacer()
                    if source.isRepository {
                        Text("مستودع")
                            .font(.system(size: 9, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(Color.white.opacity(0.08), in: Capsule())
                    }
                }
            }
        }
        .padding(16)
        .background(
            CinemaTheme.surface,
            in: RoundedRectangle(cornerRadius: CinemaTheme.cardRadius, style: .continuous)
        )
        .overlay(
            RoundedRectangle(cornerRadius: CinemaTheme.cardRadius, style: .continuous)
                .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
        )
        .padding(.horizontal, 20)
        .task { curatedInstalled = await CuratedSources.installedIDs() }
    }

    private func installFreeSet() async {
        guard !curatedBusy else { return }
        curatedBusy = true
        let added = await CuratedSources.installMissing()
        addons = await CinemaAPI.shared.installedAddons()
        curatedInstalled = await CuratedSources.installedIDs()
        curatedBusy = false
        message = added > 0 ? "أُضيف \(added) من المجموعة المجانية" : "كل المجموعة مثبتة بالفعل"
    }

    private func runCheck() async {
        checking = true
        message = nil
        let results = await CinemaAPI.shared.checkAddons()
        checks = Dictionary(uniqueKeysWithValues: results.map { ($0.url, $0) })
        let alive = results.filter { $0.ok }.count
        message = "الفحص: \(alive) تعمل من \(results.count)"
        checking = false
    }

    private func updateAll() async {
        checking = true
        message = nil
        let count = await CinemaAPI.shared.updateAllAddons()
        HomeModel.shared.resetCache()
        await load()
        checking = false
        message = count > 0 ? "تم تحديث \(count) إضافة" : "كل الإضافات محدثة"
        await runCheck()
    }

    private func load() async {
        async let installedTask = CinemaAPI.shared.installedAddons()
        async let availableTask = CinemaAPI.shared.suggestedAddons()
        let installed = await installedTask
        let available = await availableTask
        addons = installed
        suggested = available.sorted {
            $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
        }
        profiles = await CinemaAPI.shared.addonProfiles()
    }

    private func install(_ url: String) async {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        busy = true
        installing = trimmed
        message = nil
        do {
            let name = try await CinemaAPI.shared.installAddon(trimmed)
            if trimmed == addonURL.trimmingCharacters(in: .whitespacesAndNewlines) { addonURL = "" }
            message = "تم تثبيت \(name)"
            HomeModel.shared.resetCache()
            await load()
        } catch {
            message = error.localizedDescription
        }
        installing = nil
        busy = false
    }

    private func installAll() async {
        busy = true
        message = nil
        let json = await CinemaAPI.shared.installAllSuggested()
        let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]
        let added = (object?["added"] as? NSNumber)?.intValue ?? 0
        let failed = (object?["failed"] as? NSNumber)?.intValue ?? 0
        message = failed > 0 ? "تم تثبيت \(added) وتعذّر \(failed)" : "تم تثبيت \(added) مصدرًا"
        HomeModel.shared.resetCache()
        await load()
        busy = false
    }

    // MARK: - Addon manager helpers

    private var fastestCheck: AddonCheckRow? {
        checks.values.filter { $0.ok }.min { $0.ms < $1.ms }
    }

    private var deadCount: Int {
        checks.values.filter { !$0.ok }.count
    }

    private var healthText: String {
        guard let fastest = fastestCheck else { return "لا إضافة تستجيب الآن" }
        return "أسرع: \(fastest.name) — \(fastest.ms) مللي"
    }

    @ViewBuilder
    private var healthLine: some View {
        if !checks.isEmpty {
            HStack(spacing: 8) {
                Image(systemName: deadCount > 0 ? "exclamationmark.triangle.fill" : "bolt.fill")
                    .font(.system(size: 10))
                    .foregroundStyle(deadCount > 0 ? CinemaTheme.favourite : CinemaTheme.accent)
                Text(healthText)
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineLimit(1)
                Spacer(minLength: 8)
                if deadCount > 0 {
                    Text("\(deadCount) لا تستجيب")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(CinemaTheme.favourite)
                }
            }
        }
    }

    /// Reads the manifest of the pasted link before anything is installed.
    private func preparePreview() async {
        let trimmed = addonURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        previewing = true
        message = nil
        do {
            let result = try await CinemaAPI.shared.previewAddon(trimmed)
            previewURL = trimmed
            preview = result
        } catch {
            message = error.localizedDescription
        }
        previewing = false
    }

    private func previewSheet(_ result: AddonPreview) -> some View {
        let capabilities = result.resources.compactMap(capabilityName)
        let types = result.types.map(typeName)
        return NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 12) {
                        avatar(result.name, size: 48)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(result.name)
                                .font(.system(size: 17, weight: .bold))
                                .foregroundStyle(.white)
                            Text("الإصدار \(result.version)")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                        Spacer()
                    }

                    if !result.about.isEmpty {
                        Text(result.about)
                            .font(.system(size: 12))
                            .foregroundStyle(.white.opacity(0.85))
                            .lineSpacing(4)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    if !capabilities.isEmpty || !types.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(capabilities, id: \.self) { pill($0) }
                                ForEach(types, id: \.self) { pill($0) }
                            }
                        }
                    }

                    VStack(spacing: 0) {
                        infoRow("الكتالوجات", "\(result.catalogs)")
                        Divider().overlay(CinemaTheme.hairline)
                        infoRow("الأنواع", result.types.isEmpty ? "—" : result.types.joined(separator: " · "))
                        Divider().overlay(CinemaTheme.hairline)
                        infoRow(
                            "القدرات",
                            result.resources.isEmpty ? "—" : result.resources.joined(separator: " · ")
                        )
                        Divider().overlay(CinemaTheme.hairline)
                        infoRow("صفحة إعدادات", result.configurable ? "نعم" : "لا")
                    }
                    .padding(.horizontal, 14)
                    .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                    Button {
                        Haptics.tap()
                        let target = previewURL
                        preview = nil
                        Task { await install(target) }
                    } label: {
                        Text(result.installed ? "إعادة التثبيت والتحديث" : "تثبيت الآن")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(CinemaTheme.accent, in: Capsule())
                    }
                    .buttonStyle(CinemaPressStyle())

                    Text("لن يُثبَّت شيء قبل أن تضغط الزر أعلاه.")
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                }
                .padding(20)
            }
            .background(CinemaTheme.canvas.ignoresSafeArea())
            .navigationTitle("معاينة الإضافة")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("إغلاق") { preview = nil }
                        .foregroundStyle(CinemaTheme.accent)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    /// The Arabic name of a protocol resource, when the app knows it.
    private func capabilityName(_ resource: String) -> String? {
        switch resource {
        case "catalog": return "كتالوجات"
        case "stream": return "مصادر"
        case "subtitles": return "ترجمة"
        case "meta": return "تفاصيل"
        default: return nil
        }
    }

    private func typeName(_ type: String) -> String {
        switch type {
        case "movie": return "أفلام"
        case "series": return "مسلسلات"
        case "channel": return "قنوات"
        case "tv": return "بث مباشر"
        default: return type
        }
    }

    private func avatar(_ name: String, size: CGFloat = 34) -> some View {
        Text(String(name.prefix(1)).uppercased())
            .font(.system(size: size * 0.42, weight: .heavy))
            .foregroundStyle(.black)
            .frame(width: size, height: size)
            .background(
                CinemaTheme.accent.opacity(0.9),
                in: RoundedRectangle(cornerRadius: size * 0.32, style: .continuous)
            )
    }

    private func pill(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(CinemaTheme.accent)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(CinemaTheme.accent.opacity(0.14), in: Capsule())
    }

    private func infoRow(_ title: String, _ value: String) -> some View {
        HStack(spacing: 10) {
            Text(title)
                .font(.system(size: 12))
                .foregroundStyle(CinemaTheme.muted)
            Spacer(minLength: 12)
            Text(value)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 10)
    }

    /// Every catalogue the installed addons bring, reachable from one place.
    private var catalogProfiles: [AddonProfile] {
        profiles.filter { !$0.catalogs.isEmpty }
    }

    private var catalogCount: Int {
        catalogProfiles.reduce(0) { $0 + $1.catalogs.count }
    }

    @ViewBuilder
    private var catalogSection: some View {
        if !catalogProfiles.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Button {
                    Haptics.select()
                    withAnimation(.easeInOut(duration: 0.2)) { showCatalogs.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: showCatalogs ? "chevron.up" : "chevron.left")
                            .font(.system(size: 11, weight: .bold))
                        Text("كتالوجات الإضافات (\(catalogCount))")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundStyle(CinemaTheme.muted)
                }
                .buttonStyle(.plain)

                if showCatalogs {
                    ForEach(catalogProfiles) { profile in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) {
                                avatar(profile.name, size: 24)
                                Text(profile.name)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                                Spacer()
                                Text("\(profile.catalogs.count)")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    ForEach(profile.catalogs, id: \.key) { catalog in
                                        Button {
                                            Haptics.tap()
                                            browsing = AddonBrowseTarget(
                                                shelf: Shelf(
                                                    id: profile.id + ":" + catalog.id,
                                                    title: catalog.name,
                                                    items: []
                                                ),
                                                type: catalog.type
                                            )
                                        } label: {
                                            catalogChip(catalog)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                        .padding(12)
                        .background(
                            CinemaTheme.surface,
                            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                        )
                    }
                }
            }
        }
    }

    private func catalogChip(_ catalog: AddonCatalogInfo) -> some View {
        HStack(spacing: 5) {
            Image(systemName: typeIcon(catalog.type))
                .font(.system(size: 9, weight: .semibold))
            Text(catalog.name)
                .font(.system(size: 11, weight: .semibold))
                .lineLimit(1)
        }
        .foregroundStyle(CinemaTheme.accent)
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(CinemaTheme.accent.opacity(0.12), in: Capsule())
    }

    private func typeIcon(_ type: String) -> String {
        switch type {
        case "series": return "tv.fill"
        case "channel", "tv": return "dot.radiowaves.left.and.right"
        default: return "film.fill"
        }
    }

    /// Reads a shipped source's manifest before it is installed.
    private func previewSuggested(_ source: SuggestedAddonRow) async {
        previewing = true
        message = nil
        do {
            previewURL = source.url
            preview = try await CinemaAPI.shared.previewAddon(source.url)
        } catch {
            message = error.localizedDescription
        }
        previewing = false
    }

    private func remove(_ addon: InstalledAddonRow) {
        Haptics.tap()
        CinemaAPI.shared.removeAddon(addon.url)
        addons.removeAll { $0.url == addon.url }
        profiles.removeAll { $0.url == addon.url }
        checks[addon.url] = nil
        message = "أُزيلت \(addon.name)"
        HomeModel.shared.resetCache()
    }
}

/// A catalogue of an addon, wrapped so it can be presented on its own.
struct AddonBrowseTarget: Identifiable {
    let shelf: Shelf
    let type: String
    var id: String { shelf.id }
}

/// A catalogue opened from an addon's own page, with its details pushed on top.
private struct AddonCatalogBrowser: View {
    let shelf: Shelf
    let type: String

    @State private var item: CinemaItem?

    var body: some View {
        ShelfGridScreen(shelf: shelf, catalogType: type, onOpen: { item = $0 })
            .fullScreenCover(item: $item) { chosen in
                DetailView(item: chosen, onFavorite: {})
            }
    }
}

/// Everything one addon brings: what it answers, its catalogues and its
/// actions, instead of a single crowded row in the addon manager.
struct AddonDetailView: View {
    let profile: AddonProfile
    let onChange: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var latency: Int64?
    @State private var alive = true
    @State private var checking = false
    @State private var updating = false
    @State private var removing = false
    @State private var message: String?
    @State private var configTarget: SafariTarget?
    @State private var browsing: AddonBrowseTarget?

    private var configureURL: URL? { addonConfigureURL(profile.url) }

    /// The capabilities with their Arabic names, one of each.
    private var capabilities: [String] {
        var seen = Set<String>()
        return profile.resources.compactMap { resource -> String? in
            let title: String
            switch resource {
            case "catalog": title = "كتالوجات"
            case "stream": title = "مصادر"
            case "subtitles": title = "ترجمة"
            case "meta": title = "تفاصيل"
            default: title = resource
            }
            guard !title.isEmpty, seen.insert(title).inserted else { return nil }
            return title
        }
    }

    private var types: [String] {
        var seen = Set<String>()
        return profile.types.map(typeName).filter { seen.insert($0).inserted }
    }

    private var statusText: String {
        if checking { return "جارٍ الفحص…" }
        guard let latency else { return "لم يُفحص بعد" }
        return alive ? "يستجيب في \(latency) مللي" : "لا يستجيب الآن"
    }

    private var installedLabel: String {
        let date = Date(timeIntervalSince1970: Double(profile.installedAt) / 1000)
        return date.formatted(date: .abbreviated, time: .shortened)
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                header
                capabilitiesRow

                if !profile.about.isEmpty {
                    Text(profile.about)
                        .font(.system(size: 12))
                        .foregroundStyle(.white.opacity(0.85))
                        .lineSpacing(4)
                        .fixedSize(horizontal: false, vertical: true)
                }

                facts
                catalogs
                actions

                if let message {
                    Text(message)
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.accent)
                        .lineSpacing(3)
                }

                Text("ترتيب الإضافة يحدد أي كتالوج يظهر أولًا في الرئيسية، وأي مصدر يُجرَّب قبل غيره.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)
            }
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle(profile.name)
        .navigationBarTitleDisplayMode(.inline)
        .task { await check() }
        .sheet(item: $configTarget) { target in
            SafariView(url: target.url)
                .ignoresSafeArea()
                .onDisappear {
                    HomeModel.shared.resetCache()
                    onChange()
                    Task { await check() }
                }
        }
        .sheet(item: $browsing) { target in
            AddonCatalogBrowser(shelf: target.shelf, type: target.type)
        }
        .confirmationDialog(
            "حذف \(profile.name)؟",
            isPresented: $removing,
            titleVisibility: .visible
        ) {
            Button("حذف الإضافة", role: .destructive) {
                CinemaAPI.shared.removeAddon(profile.url)
                HomeModel.shared.resetCache()
                onChange()
                dismiss()
            }
            Button("إلغاء", role: .cancel) {}
        } message: {
            Text("ستختفي كتالوجاتها ومصادرها من التطبيق.")
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Text(String(profile.name.prefix(1)).uppercased())
                .font(.system(size: 20, weight: .heavy))
                .foregroundStyle(.black)
                .frame(width: 52, height: 52)
                .background(CinemaTheme.accent, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                Text(profile.name)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                HStack(spacing: 6) {
                    Text("الإصدار \(profile.version)")
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                    Circle()
                        .fill(alive ? CinemaTheme.accent : CinemaTheme.favourite)
                        .frame(width: 5, height: 5)
                    Text(statusText)
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                }
            }
            Spacer(minLength: 8)
            Button {
                Haptics.tap()
                Task { await check() }
            } label: {
                if checking {
                    ProgressView()
                        .tint(CinemaTheme.accent)
                        .frame(width: 34, height: 34)
                } else {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                        .frame(width: 34, height: 34)
                        .background(CinemaTheme.elevated, in: Circle())
                }
            }
            .buttonStyle(.plain)
            .disabled(checking)
        }
    }

    @ViewBuilder
    private var capabilitiesRow: some View {
        if !capabilities.isEmpty || !types.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(capabilities, id: \.self) { name in
                        pill(name, filled: true)
                    }
                    ForEach(types, id: \.self) { name in
                        pill(name, filled: false)
                    }
                }
            }
        }
    }

    private var facts: some View {
        VStack(spacing: 0) {
            fact("الحالة", statusText)
            Divider().overlay(CinemaTheme.hairline)
            fact("الأنواع", types.isEmpty ? "—" : types.joined(separator: " · "))
            Divider().overlay(CinemaTheme.hairline)
            fact("القدرات", capabilities.isEmpty ? "—" : capabilities.joined(separator: " · "))
            Divider().overlay(CinemaTheme.hairline)
            fact("الكتالوجات", "\(profile.catalogs.count)")
            Divider().overlay(CinemaTheme.hairline)
            fact("صفحة إعدادات", configureURL == nil ? "لا" : "نعم")
            if profile.installedAt > 0 {
                Divider().overlay(CinemaTheme.hairline)
                fact("أُضيفت في", installedLabel)
            }
            Divider().overlay(CinemaTheme.hairline)
            Button {
                Haptics.tap()
                UIPasteboard.general.string = profile.url
                message = "نُسخ الرابط"
            } label: {
                HStack(spacing: 10) {
                    Text("الرابط")
                        .font(.system(size: 12))
                        .foregroundStyle(CinemaTheme.muted)
                    Spacer(minLength: 12)
                    Text(profile.url)
                        .font(.system(size: 10))
                        .foregroundStyle(CinemaTheme.accent)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                .padding(.vertical, 11)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    @ViewBuilder
    private var catalogs: some View {
        if !profile.catalogs.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("كتالوجاتها (\(profile.catalogs.count))")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)

                VStack(spacing: 0) {
                    ForEach(Array(profile.catalogs.enumerated()), id: \.element.key) { index, catalog in
                        Button {
                            Haptics.tap()
                            browsing = AddonBrowseTarget(
                                shelf: Shelf(
                                    id: profile.id + ":" + catalog.id,
                                    title: catalog.name,
                                    items: []
                                ),
                                type: catalog.type
                            )
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: catalogIcon(catalog.type))
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                                    .frame(width: 28, height: 28)
                                    .background(CinemaTheme.elevated, in: Circle())
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(catalog.name)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(.white)
                                        .lineLimit(1)
                                    Text(catalogDetail(catalog))
                                        .font(.system(size: 10))
                                        .foregroundStyle(CinemaTheme.muted)
                                        .lineLimit(1)
                                }
                                Spacer(minLength: 8)
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            .padding(.vertical, 9)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if index < profile.catalogs.count - 1 {
                            Divider().overlay(CinemaTheme.hairline)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
        }
    }

    private var actions: some View {
        VStack(spacing: 10) {
            if let configureURL {
                actionButton("صفحة إعدادات الإضافة", icon: "gearshape.fill") {
                    configTarget = SafariTarget(url: configureURL)
                }
            }
            actionButton(
                updating ? "جارٍ التحديث…" : "تحديث الإضافة",
                icon: "arrow.triangle.2.circlepath"
            ) {
                Task { await update() }
            }
            actionButton("نسخ الرابط", icon: "doc.on.doc") {
                UIPasteboard.general.string = profile.url
                message = "نُسخ الرابط"
            }
            ShareLink(item: profile.url) {
                HStack(spacing: 10) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                        .frame(width: 24)
                    Text("مشاركة الرابط")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                    Spacer()
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 13)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            actionButton("حذف الإضافة", icon: "trash", tint: CinemaTheme.favourite) {
                removing = true
            }
        }
    }

    private func actionButton(
        _ title: String,
        icon: String,
        tint: Color = .white,
        action: @escaping () -> Void
    ) -> some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 24)
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(tint)
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
            .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(updating)
    }

    private func fact(_ title: String, _ value: String) -> some View {
        HStack(spacing: 10) {
            Text(title)
                .font(.system(size: 12))
                .foregroundStyle(CinemaTheme.muted)
            Spacer(minLength: 12)
            Text(value)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 11)
    }

    private func pill(_ title: String, filled: Bool) -> some View {
        Text(title)
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(filled ? CinemaTheme.accent : .white.opacity(0.85))
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background {
                Capsule().fill(filled ? CinemaTheme.accent.opacity(0.14) : Color.white.opacity(0.08))
            }
    }

    private func catalogIcon(_ type: String) -> String {
        switch type {
        case "series": return "tv.fill"
        case "channel", "tv": return "dot.radiowaves.left.and.right"
        default: return "film.fill"
        }
    }

    private func catalogDetail(_ catalog: AddonCatalogInfo) -> String {
        let genres = catalog.genres?.count ?? 0
        let kind = typeName(catalog.type)
        return genres > 0 ? "\(kind) · \(genres) نوعًا" : kind
    }

    private func typeName(_ type: String) -> String {
        switch type {
        case "movie": return "أفلام"
        case "series": return "مسلسلات"
        case "channel": return "قنوات"
        case "tv": return "بث مباشر"
        default: return type
        }
    }

    /// Re-reads the manifest: this is both the health check and the version check.
    private func check() async {
        checking = true
        let started = Date()
        do {
            let result = try await CinemaAPI.shared.previewAddon(profile.url)
            latency = Int64(Date().timeIntervalSince(started) * 1000)
            alive = true
            if result.version != profile.version {
                message = "الإصدار المتاح الآن \(result.version)"
            }
        } catch {
            latency = nil
            alive = false
            message = error.localizedDescription
        }
        checking = false
    }

    private func update() async {
        updating = true
        message = nil
        do {
            let summary = try await CinemaAPI.shared.updateAddon(profile.url)
            let parts = summary.split(separator: "|")
            message = parts.count == 2 ? "حُدّثت إلى \(parts[1])" : "تم التحديث"
            HomeModel.shared.resetCache()
            onChange()
            await check()
        } catch {
            message = error.localizedDescription
        }
        updating = false
    }
}

/// The control panel for the sources list: which addons may answer, what the viewer
/// hid, and how the list itself is shown.
struct SourcesControlSettings: View {
    @State private var addons: [InstalledAddonRow] = []
    @State private var offAddons: Set<String> = SourcePrefs.offAddons
    @State private var hiddenCount = SourcePrefs.hidden.count
    @State private var foldedCount = SourcePrefs.folded.count
    @State private var health: [SourceHealthRow] = []
    @AppStorage("cinemax.sourcesCompact") private var compact = false
    @AppStorage("cinemax.speedFirst") private var speedFirst = false
    @AppStorage("cinemax.prefetchNext") private var prefetchNext = true
    @AppStorage("cinemax.raceSources2") private var raceSources = false
    @AppStorage("cinemax.prefQuality") private var prefQuality = "auto"

    private let qualities: [(id: String, title: String)] = [
        ("auto", "تلقائي"), ("4K", "4K"), ("1080p", "1080p"), ("720p", "720p")
    ]

    private var onCount: Int {
        addons.filter { !offAddons.contains($0.name) }.count
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 10) {
                    tile("\(onCount)", "تعمل", tint: CinemaTheme.accent)
                    tile("\(offAddons.count)", "متوقفة", tint: CinemaTheme.favourite)
                    tile("\(hiddenCount)", "روابط مخفية", tint: CinemaTheme.muted)
                }

                Text("الإضافة المتوقفة لا تُسأل أصلًا، فلا تستهلك وقتًا في جلب المصادر. الروابط المخفية تُستبعد من القائمة فقط.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)

                addonSection
                healthSection
                displaySection
                manageSection
            }
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("المصادر")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            addons = await CinemaAPI.shared.installedAddons()
            offAddons = SourcePrefs.offAddons
            hiddenCount = SourcePrefs.hidden.count
            foldedCount = SourcePrefs.folded.count
            health = await CinemaAPI.shared.sourceHealth()
        }
    }

    private func tile(_ value: String, _ title: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 20, weight: .heavy))
                .foregroundStyle(tint)
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(CinemaTheme.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    @ViewBuilder
    private var addonSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("الإضافات والمزوّدون")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CinemaTheme.muted)

            if addons.isEmpty {
                Text("لا إضافة مثبتة بعد.")
                    .font(.system(size: 12))
                    .foregroundStyle(CinemaTheme.muted)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(addons.enumerated()), id: \.element.id) { index, addon in
                        HStack(spacing: 10) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(addon.name)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                                Text("الإصدار \(addon.version)")
                                    .font(.system(size: 10))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            Spacer()
                            Toggle("", isOn: Binding(
                                get: { !offAddons.contains(addon.name) },
                                set: { on in setAddon(addon.name, on: on) }
                            ))
                            .labelsHidden()
                            .tint(CinemaTheme.accent)
                        }
                        .padding(.vertical, 9)
                        if index < addons.count - 1 {
                            Divider().overlay(CinemaTheme.hairline)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
        }
    }

    /// What the app has measured: how fast each source answers, and which ones are
    /// resting after failing three times in a row.
    @ViewBuilder
    private var healthSection: some View {
        if !health.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("سرعة الاستجابة المقاسة")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)

                VStack(spacing: 0) {
                    ForEach(Array(health.enumerated()), id: \.element.id) { index, row in
                        HStack(spacing: 10) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(row.name)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                                Text(row.failures > 0 ? "\(row.failures) فشل متتالٍ" : "تستجيب بانتظام")
                                    .font(.system(size: 10))
                                    .foregroundStyle(
                                        row.failures > 0 ? CinemaTheme.favourite : CinemaTheme.muted
                                    )
                            }
                            Spacer(minLength: 8)
                            if row.skipped {
                                Text("يُتجاوز الآن")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.favourite)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(CinemaTheme.favourite.opacity(0.14), in: Capsule())
                            }
                            Text(row.ms > 0 ? String(format: "%.1f ث", Double(row.ms) / 1000) : "—")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(CinemaTheme.accent)
                        }
                        .padding(.vertical, 9)
                        if index < health.count - 1 {
                            Divider().overlay(CinemaTheme.hairline)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                Text("إضافة تفشل ثلاث مرات متتالية تُتجاوز عشر دقائق ثم تُجرَّب من جديد، وأول إجابة تعيدها فورًا.")
                    .font(.system(size: 10))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(3)

                Button {
                    Haptics.tap()
                    CinemaAPI.shared.resetSourceHealth()
                    health = []
                } label: {
                    actionLabel(
                        "مسح القياسات وإعادة المحاولة للجميع",
                        icon: "arrow.counterclockwise",
                        tint: .white
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var displaySection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("طريقة العرض والترتيب")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CinemaTheme.muted)

            Toggle(isOn: $compact) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("عرض مضغوط للمصادر")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                    Text("صفوف أقصر بلا وسوم إضافية، لرؤية روابط أكثر في الشاشة")
                        .font(.system(size: 10))
                        .foregroundStyle(CinemaTheme.muted)
                }
            }
            .tint(CinemaTheme.accent)

            Toggle(isOn: $speedFirst) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("السرعة قبل الجودة")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                    Text("يُقاس الرابط بتحميل جزء صغير، والأسرع يظهر أولًا")
                        .font(.system(size: 10))
                        .foregroundStyle(CinemaTheme.muted)
                }
            }
            .tint(CinemaTheme.accent)

            Toggle(isOn: $prefetchNext) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("جلب مسبق للحلقة التالية")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                    Text("يجهّز روابط الحلقة التالية أثناء المشاهدة لتفتح فورًا")
                        .font(.system(size: 10))
                        .foregroundStyle(CinemaTheme.muted)
                }
            }
            .tint(CinemaTheme.accent)

            Toggle(isOn: $raceSources) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("سباق الروابط قبل التشغيل")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                    Text("يجرّب أول ثلاثة روابط بطلب صغير ويشغّل أول واحد يستجيب فعلًا")
                        .font(.system(size: 10))
                        .foregroundStyle(CinemaTheme.muted)
                }
            }
            .tint(CinemaTheme.accent)

            VStack(alignment: .leading, spacing: 8) {
                Text("الجودة المفضلة")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted)
                HStack(spacing: 8) {
                    ForEach(qualities, id: \.id) { entry in
                        Button {
                            Haptics.select()
                            prefQuality = entry.id
                        } label: {
                            Text(entry.title)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(prefQuality == entry.id ? .black : .white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background {
                                    Capsule().fill(
                                        prefQuality == entry.id
                                            ? CinemaTheme.accent
                                            : Color.white.opacity(0.08)
                                    )
                                }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(14)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var manageSection: some View {
        VStack(spacing: 10) {
            Button {
                Haptics.tap()
                offAddons = []
                SourcePrefs.resetAddons()
            } label: {
                actionLabel(
                    "تشغيل كل الإضافات",
                    icon: "power",
                    tint: offAddons.isEmpty ? CinemaTheme.muted : .white
                )
            }
            .buttonStyle(.plain)
            .disabled(offAddons.isEmpty)

            Button {
                Haptics.tap()
                hiddenCount = 0
                SourcePrefs.resetHiding()
            } label: {
                actionLabel(
                    "إظهار كل الروابط المخفية (\(hiddenCount))",
                    icon: "eye",
                    tint: hiddenCount == 0 ? CinemaTheme.muted : .white
                )
            }
            .buttonStyle(.plain)
            .disabled(hiddenCount == 0)

            Button {
                Haptics.tap()
                foldedCount = 0
                for quality in ["4K", "1080p", "720p", "480p", "أخرى"] {
                    SourcePrefs.fold("quality:" + quality, false)
                }
            } label: {
                actionLabel(
                    "فتح كل المجموعات المطويّة",
                    icon: "chevron.down",
                    tint: CinemaTheme.muted
                )
            }
            .buttonStyle(.plain)
        }
    }

    private func actionLabel(_ title: String, icon: String, tint: Color) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 24)
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(tint)
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 13)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .contentShape(Rectangle())
    }

    private func setAddon(_ name: String, on: Bool) {
        Haptics.select()
        if on {
            offAddons.remove(name)
        } else {
            offAddons.insert(name)
        }
        SourcePrefs.setAddon(name, on: on)
    }
}

/// What Siri and the Shortcuts app can do with CINEMAX, with the exact phrases a
/// viewer can say and a way to reach the Shortcuts app to build their own.
struct SiriSettings: View {
    private let commands: [(phrase: String, detail: String, icon: String)] = [
        (
            "أكمل آخر حلقة في CINEMAX",
            "يفتح آخر عنوان كنت تشاهده عند موضع التوقف",
            "play.circle.fill"
        ),
        (
            "فاجئني في CINEMAX",
            "يفتح عنوانًا لم تبدأه بعد من صفوفك",
            "dice.fill"
        ),
        (
            "ابحث في CINEMAX",
            "ينقلك مباشرة إلى شاشة البحث",
            "magnifyingglass"
        )
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                Text("يعمل CINEMAX مع Siri والاختصارات دون أي إعداد. قل العبارة، أو افتح تطبيق الاختصارات وأضف الإجراء بنفسك.")
                    .font(.system(size: 12))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)

                VStack(spacing: 0) {
                    ForEach(Array(commands.enumerated()), id: \.element.phrase) { index, command in
                        HStack(spacing: 12) {
                            Image(systemName: command.icon)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(CinemaTheme.accent)
                                .frame(width: 28)
                            VStack(alignment: .leading, spacing: 3) {
                                Text("«\(command.phrase)»")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .fixedSize(horizontal: false, vertical: true)
                                Text(command.detail)
                                    .font(.system(size: 10))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            Spacer(minLength: 6)
                        }
                        .padding(.vertical, 11)
                        if index < commands.count - 1 {
                            Divider().overlay(CinemaTheme.hairline)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                Button {
                    Haptics.tap()
                    if let url = URL(string: "shortcuts://") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "square.stack.3d.up.fill")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("افتح تطبيق الاختصارات")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.white)
                            Text("ابنِ اختصارًا يجمع الإجراء مع خطوات أخرى")
                                .font(.system(size: 10))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                        Spacer()
                        Image(systemName: "arrow.up.forward")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 13)
                    .background(
                        CinemaTheme.surface,
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                    )
                }
                .buttonStyle(.plain)

                Text("العبارات تعمل بالعربية والإنجليزية، ويجب أن تحتوي اسم التطبيق. أول استخدام قد يطلب إذن Siri مرة واحدة.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)
            }
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("Siri والاختصارات")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Defaults for the library and the search screen, so both open the way the
/// viewer left them instead of resetting on every launch.
struct LibrarySettings: View {
    @AppStorage("cinemax.librarySort") private var sort = "recent"
    @AppStorage("cinemax.searchKind") private var searchKind = "all"
    @AppStorage("cinemax.posterWidth") private var posterWidth = 112

    private let sorts: [(id: String, title: String)] = [
        ("recent", "الأحدث"), ("az", "أبجدي"), ("rating", "التقييم"), ("remaining", "المتبقي")
    ]
    private let kinds: [(id: String, title: String)] = [
        ("all", "الكل"), ("movie", "أفلام"), ("series", "مسلسلات"), ("channel", "قنوات")
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("ترتيب المكتبة الافتراضي")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(sorts, id: \.id) { entry in
                            chip(entry.title, active: sort == entry.id) { sort = entry.id }
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("نوع البحث الافتراضي")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        ForEach(kinds, id: \.id) { entry in
                            chip(entry.title, active: searchKind == entry.id) { searchKind = entry.id }
                        }
                    }
                }

                Text("هذه القيم تُحفظ وتُستخدم عند فتح المكتبة أو البحث في المرة القادمة.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)
            }
            .padding(16)
            .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("المكتبة والبحث")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func chip(_ title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.select()
            action()
        } label: {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(active ? .black : .white)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background {
                    Capsule().fill(active ? CinemaTheme.accent : Color.white.opacity(0.08))
                }
        }
        .buttonStyle(.plain)
    }
}

/// A local, account-free look at what the viewer has built up: what is saved,
/// where they stopped, and what the downloads take on the device.
struct StatsSettings: View {
    @ObservedObject private var library = LibraryModel.shared
    @ObservedObject private var downloads = DownloadManager.shared
    @State private var watchedEpisodes = 0
    @State private var addonCount = 0
    /// The card the viewer asked for, and whether one is being drawn now.
    @State private var card: ShareImage?
    @State private var buildingCard = false

    private var finished: [DownloadJob] { downloads.jobs.filter { $0.state == "done" } }

    private var averageProgress: Int {
        guard !library.progress.isEmpty else { return 0 }
        let total = library.progress.reduce(0.0) { $0 + $1.fraction }
        return Int((total / Double(library.progress.count)) * 100)
    }

    private var remainingMinutes: Int {
        library.progress.reduce(0) { $0 + $1.remainingMinutes }
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 10) {
                    tile("\(library.favorites.count)", "محفوظة", icon: "heart.fill")
                    tile("\(library.progress.count)", "قيد المشاهدة", icon: "play.circle.fill")
                    tile("\(finished.count)", "تنزيل مكتمل", icon: "arrow.down.circle.fill")
                }

                Button {
                    Haptics.tap()
                    buildCard()
                } label: {
                    Label(
                        buildingCard ? "يُجهّز البطاقة…" : "بطاقة إحصاءاتي",
                        systemImage: "photo.on.rectangle"
                    )
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(CinemaTheme.accent, in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(buildingCard)

                if !library.progress.isEmpty {
                    chartCard(
                        title: "أكثر ما بقي لك مشاهدة",
                        subtitle: "أعلى ستة عناوين بالدقائق المتبقية"
                    ) {
                        Chart(Array(library.progress.prefix(6))) { entry in
                            BarMark(
                                x: .value("الدقائق", max(1, entry.remainingMinutes)),
                                y: .value("العنوان", shortName(entry.item.name))
                            )
                            .foregroundStyle(CinemaTheme.accent.gradient)
                            .cornerRadius(4)
                        }
                        .chartXAxis(.hidden)
                        .chartYAxis {
                            AxisMarks(position: .trailing) { _ in
                                AxisValueLabel()
                                    .font(.system(size: 9))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                        }
                        .frame(height: 190)
                    }
                }

                if !library.favorites.isEmpty {
                    chartCard(title: "ما حفظته", subtitle: "أفلام مقابل مسلسلات") {
                        Chart {
                            BarMark(
                                x: .value("النوع", "أفلام"),
                                y: .value("العدد", savedMovies)
                            )
                            .foregroundStyle(CinemaTheme.accent)
                            .cornerRadius(6)
                            BarMark(
                                x: .value("النوع", "مسلسلات"),
                                y: .value("العدد", savedSeries)
                            )
                            .foregroundStyle(CinemaTheme.favourite)
                            .cornerRadius(6)
                        }
                        .chartYAxis(.hidden)
                        .frame(height: 140)
                    }
                }

                VStack(spacing: 0) {
                    row("متوسط ما شوهد", "\(averageProgress)٪")
                    Divider().overlay(CinemaTheme.hairline)
                    row("ساعات المشاهدة المسجلة", watchedHours)
                    Divider().overlay(CinemaTheme.hairline)
                    row("وقت متبقٍ في القائمة", remainingLabel)
                    Divider().overlay(CinemaTheme.hairline)
                    row("تنزيلات جارية", "\(downloads.jobs.filter { $0.state == "running" }.count)")
                    Divider().overlay(CinemaTheme.hairline)
                    row("حلقات معلّمة كمشاهدة", "\(watchedEpisodes)")
                    Divider().overlay(CinemaTheme.hairline)
                    row("إضافات مثبتة", "\(addonCount)")
                    Divider().overlay(CinemaTheme.hairline)
                    row("قوائمي", "\(ListsStore.shared.lists.count)")
                    Divider().overlay(CinemaTheme.hairline)
                    row("عناوين في القوائم", "\(ListsStore.shared.totalTitles)")
                    Divider().overlay(CinemaTheme.hairline)
                    row("ملفات التنزيل على الجهاز", storageSize)
                    Divider().overlay(CinemaTheme.hairline)
                    row("آخر تحديث للكتالوج", CinemaxPrefs.lastRefreshLabel)
                }
                .padding(.horizontal, 14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                Text("كل هذه الأرقام محلية على جهازك، ولا تُرسل إلى أي خدمة.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)
            }
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("الإحصاءات")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $card) { entry in
            ShareSheet(items: [entry.image])
        }
        .task {
            await library.load()
            let extra = await CinemaAPI.shared.extraCounts()
            watchedEpisodes = extra.watched
            addonCount = extra.addons
        }
    }

    /// Everything the app has clocked, added up: an honest number, not a guess.
    private var watchedHours: String {
        let millis = library.progress.reduce(Int64(0)) { $0 + $1.positionMillis }
        guard millis > 0 else { return "٠" }
        let hours = Double(millis) / 3_600_000
        if hours < 1 {
            return "\(Int(Double(millis) / 60_000)) دقيقة"
        }
        return String(format: "%.1f ساعة", hours)
    }

    private var savedMovies: Int {
        library.favorites.filter { $0.type == "movie" }.count
    }

    private var savedSeries: Int {
        library.favorites.filter { $0.type == "series" }.count
    }

    /// Draws the numbers as a card and hands it to the share sheet, so what the
    /// app recorded travels as a picture instead of a paragraph.
    private func buildCard() {
        guard !buildingCard else { return }
        buildingCard = true
        Task { @MainActor in
            let millis = library.progress.reduce(Int64(0)) { $0 + $1.positionMillis }
            let hours = Double(millis) / 3_600_000
            let hoursLabel: String
            if millis <= 0 {
                hoursLabel = "٠"
            } else if hours < 1 {
                hoursLabel = "\(max(1, Int(Double(millis) / 60_000)))د"
            } else {
                hoursLabel = String(format: "%.1f", hours)
            }
            let completed = library.progress.filter { $0.fraction >= 0.9 }.count
            let image = CinemaCards.stats(
                hours: hoursLabel,
                finished: completed,
                saved: library.favorites.count,
                lists: ListsStore.shared.lists.count,
                downloaded: downloadedShortLabel
            )
            buildingCard = false
            guard let image else { return }
            card = ShareImage(image: image)
            EventLog.shared.add("card", "بطاقة إحصاءاتي")
        }
    }

    /// The downloads' size in the units a viewer reads, for the card's column.
    private var downloadedShortLabel: String {
        let bytes = finished.reduce(Int64(0)) { $0 + $1.total }
        guard bytes > 0 else { return "—" }
        let gigabytes = Double(bytes) / 1_073_741_824
        if gigabytes >= 1 { return String(format: "%.1f GB", gigabytes) }
        return String(format: "%.0f MB", Double(bytes) / 1_048_576)
    }

    /// A title long enough to break a bar chart is shortened at a word boundary.
    private func shortName(_ name: String) -> String {
        guard name.count > 18 else { return name }
        return String(name.prefix(17)) + "…"
    }

    private func chartCard<Content: View>(
        title: String,
        subtitle: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
            Text(subtitle)
                .font(.system(size: 10))
                .foregroundStyle(CinemaTheme.muted)
            content()
        }
        .padding(14)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var remainingLabel: String {
        let minutes = remainingMinutes
        if minutes < 60 { return "\(minutes) د" }
        let hours = minutes / 60
        let rest = minutes % 60
        return rest == 0 ? "\(hours) س" : "\(hours) س \(rest) د"
    }

    private var storageSize: String {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("CINEMAX", isDirectory: true)
        let files = (try? FileManager.default.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: [.fileSizeKey]
        )) ?? []
        let total = files.reduce(Int64(0)) { sum, url in
            let size = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
            return sum + Int64(size)
        }
        return String(format: "%.0f MB", Double(total) / 1_048_576)
    }

    private func tile(_ value: String, _ title: String, icon: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(CinemaTheme.accent)
            Text(value)
                .font(.system(size: 22, weight: .heavy))
                .foregroundStyle(.white)
            Text(title)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(CinemaTheme.muted)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func row(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 13))
                .foregroundStyle(.white)
            Spacer(minLength: 10)
            Text(value)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CinemaTheme.accent)
        }
        .padding(.vertical, 12)
    }
}

/// A file handed to the system share sheet.
struct ShareFile: Identifiable {
    let id = UUID()
    let url: URL
}

// MARK: - Cards

/// A picture handed to the share sheet, kept identifiable so a screen can show
/// the sheet for the picture it just drew.
struct ShareImage: Identifiable {
    let id = UUID()
    let image: UIImage
}

/// A title drawn as a card worth sending: its own poster behind the name, the
/// year and the rating, in a colour pulled from that poster.
struct CinemaTitleCard: View {
    let name: String
    let meta: String
    let badge: String
    let poster: UIImage?
    let tint: Color

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Color.black
            if let poster {
                Image(uiImage: poster)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 1080, height: 1350)
                    .clipped()
            }
            LinearGradient(
                colors: [.black.opacity(0), .black.opacity(0.5), .black.opacity(0.97)],
                startPoint: .top,
                endPoint: .bottom
            )
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    Text(Brand.name.uppercased())
                        .font(.system(size: 24, weight: .heavy))
                        .tracking(6)
                        .foregroundStyle(.white.opacity(0.85))
                    Capsule()
                        .fill(tint)
                        .frame(width: 52, height: 5)
                    Spacer()
                }
                Spacer()
                if !badge.isEmpty {
                    Text(badge)
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.7))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 7)
                        .background(Color.white.opacity(0.12), in: Capsule())
                }
                Text(name)
                    .font(.system(size: 76, weight: .heavy))
                    .foregroundStyle(.white)
                    .lineLimit(3)
                    .minimumScaleFactor(0.4)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                if !meta.isEmpty {
                    Text(meta)
                        .font(.system(size: 28, weight: .semibold))
                        .foregroundStyle(tint)
                }
            }
            .padding(76)
            .frame(width: 1080, height: 1350, alignment: .bottomLeading)
        }
        .frame(width: 1080, height: 1350)
        .clipped()
    }
}

/// A list drawn as a card: its first posters laid as a wall under its name.
struct CinemaListCard: View {
    let name: String
    let count: Int
    let posters: [UIImage]
    let tint: Color

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Color.black
            // A plain grid, not a lazy one: a lazy stack draws nothing inside a
            // picture that is rendered off screen.
            VStack(spacing: 10) {
                ForEach(0..<3, id: \.self) { row in
                    HStack(spacing: 10) {
                        ForEach(0..<3, id: \.self) { column in
                            let index = row * 3 + column
                            ZStack {
                                Rectangle().fill(Color.white.opacity(0.06))
                                if posters.indices.contains(index) {
                                    Image(uiImage: posters[index])
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                }
                            }
                            .frame(width: 353, height: 250)
                            .clipped()
                        }
                    }
                }
            }
            .frame(width: 1080)
            .opacity(0.55)
            .blur(radius: 5)
            LinearGradient(
                colors: [.black.opacity(0.2), .black.opacity(0.9), .black],
                startPoint: .top,
                endPoint: .bottom
            )
            VStack(alignment: .leading, spacing: 14) {
                Text(Brand.name.uppercased())
                    .font(.system(size: 24, weight: .heavy))
                    .tracking(6)
                    .foregroundStyle(.white.opacity(0.85))
                Spacer()
                Text(name)
                    .font(.system(size: 72, weight: .heavy))
                    .foregroundStyle(.white)
                    .lineLimit(3)
                    .minimumScaleFactor(0.4)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                Text("\(count) عنوانًا · قائمة من CINEMAX")
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(tint)
            }
            .padding(76)
            .frame(width: 1080, height: 1350, alignment: .bottomLeading)
        }
        .frame(width: 1080, height: 1350)
        .clipped()
    }
}

/// The viewer's own numbers drawn as a card, so what the app recorded can be sent
/// to someone as a picture instead of described in a message.
struct CinemaStatsCard: View {
    let hours: String
    let finished: Int
    let saved: Int
    let lists: Int
    let downloaded: String
    let tint: Color

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Color.black
            RadialGradient(
                colors: [tint.opacity(0.5), .black],
                center: .topTrailing,
                startRadius: 30,
                endRadius: 950
            )
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 14) {
                    Text(Brand.name.uppercased())
                        .font(.system(size: 24, weight: .heavy))
                        .tracking(6)
                        .foregroundStyle(.white.opacity(0.85))
                    Capsule()
                        .fill(tint)
                        .frame(width: 52, height: 5)
                    Spacer()
                }
                Spacer()
                Text("ساعات المشاهدة")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.6))
                Text(hours)
                    .font(.system(size: 136, weight: .heavy))
                    .foregroundStyle(.white)
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                HStack(alignment: .top, spacing: 46) {
                    stat("\(finished)", "مكتمل")
                    stat("\(saved)", "محفوظ")
                    stat("\(lists)", "قوائم")
                    stat(downloaded, "على الجهاز")
                }
                .padding(.top, 10)
            }
            .padding(76)
            .frame(width: 1080, height: 1350, alignment: .bottomLeading)
        }
        .frame(width: 1080, height: 1350)
        .clipped()
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(tint)
            Text(label)
                .font(.system(size: 22, weight: .medium))
                .foregroundStyle(.white.opacity(0.55))
        }
    }
}

/// The system player's own panel: the sleep timer, the speed and the card that
/// libmpv's panel carries, where Apple's controls only cover play and captions.
struct SystemOptionsSheet: View {
    let title: String
    let speed: Double
    let sleepMinutes: Int?
    let sleepRemaining: Int
    let canShareCard: Bool

    let onSpeed: (Double) -> Void
    let onSleep: (Int?) -> Void
    let onCard: () -> Void
    let onClose: () -> Void

    private let speeds: [Double] = [0.75, 1, 1.25, 1.5]
    private let timers: [Int] = [15, 30, 45, 60]

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture(perform: onClose)
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("خيارات مشغل النظام")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                    Spacer()
                    CircleButton(system: "xmark") { onClose() }
                }
                Text(title)
                    .font(.system(size: 12))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineLimit(1)

                label("سرعة التشغيل")
                HStack(spacing: 8) {
                    ForEach(speeds, id: \.self) { value in
                        Button {
                            onSpeed(value)
                        } label: {
                            Text(value == 1 ? "عادي" : String(format: "%g×", value))
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(abs(speed - value) < 0.01 ? .black : .white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background {
                                    Capsule().fill(
                                        abs(speed - value) < 0.01
                                            ? CinemaTheme.accent
                                            : Color.white.opacity(0.1)
                                    )
                                }
                        }
                        .buttonStyle(.plain)
                    }
                }

                HStack(spacing: 6) {
                    label("مؤقت النوم")
                    Spacer()
                    if sleepMinutes != nil {
                        Text("يتبقى \(sleepRemaining / 60):\(String(format: "%02d", sleepRemaining % 60))")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .monospacedDigit()
                    }
                }
                HStack(spacing: 8) {
                    timerChip(nil, "بلا")
                    ForEach(timers, id: \.self) { minutes in
                        timerChip(minutes, "\(minutes) د")
                    }
                }

                if canShareCard {
                    Button(action: onCard) {
                        Label("بطاقة العنوان", systemImage: "photo.on.rectangle")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(CinemaTheme.accent.opacity(0.12), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }

                Text("الصورة داخل الصورة وAirPlay من أدوات النظام: زر في المشغّل، أو من مركز التحكم.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(3)
            }
            .padding(18)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
            )
            .padding(.horizontal, 16)
            .padding(.bottom, 18)
        }
        .preferredColorScheme(.dark)
    }

    private func label(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(CinemaTheme.muted)
    }

    private func timerChip(_ minutes: Int?, _ text: String) -> some View {
        Button {
            onSleep(minutes)
        } label: {
            Text(text)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(sleepMinutes == minutes ? .black : .white)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background {
                    Capsule().fill(
                        sleepMinutes == minutes ? CinemaTheme.accent : Color.white.opacity(0.1)
                    )
                }
        }
        .buttonStyle(.plain)
    }
}

/// Draws both cards: the poster and its colours are loaded first, so the card
/// never renders as an empty grey rectangle.
enum CinemaCards {
    @MainActor
    static func title(_ item: CinemaItem, badge: String? = nil) async -> UIImage? {
        let posterURL = item.posterURL ?? item.backdropURL
        var poster: UIImage?
        var tint = CinemaTheme.accent
        if let posterURL {
            poster = await CinemaImageStore.shared.image(for: posterURL)
            let colours = await PaletteSampler.colors(for: posterURL)
            if let first = colours.first { tint = first }
        }
        let meta = [item.releaseInfo, item.rating.map { "★ \($0)" }]
            .compactMap { $0 }
            .filter { !$0.isEmpty }
            .joined(separator: "   ")
        let card = CinemaTitleCard(
            name: item.name,
            meta: meta,
            badge: badge ?? (item.type == "series" ? "مسلسل" : "فيلم"),
            poster: poster,
            tint: tint
        )
        return render(card)
    }

    @MainActor
    static func list(_ list: CinemaList) async -> UIImage? {
        var posters: [UIImage] = []
        var tint = CinemaTheme.accent
        var coloured = false
        for entry in list.items.prefix(9) {
            guard let url = entry.posterURL ?? entry.backdropURL else { continue }
            if !coloured {
                let colours = await PaletteSampler.colors(for: url)
                if let first = colours.first {
                    tint = first
                    coloured = true
                }
            }
            if let image = await CinemaImageStore.shared.image(for: url) {
                posters.append(image)
            }
        }
        let card = CinemaListCard(
            name: list.name,
            count: list.items.count,
            posters: posters,
            tint: tint
        )
        return render(card)
    }

    @MainActor
    static func stats(
        hours: String,
        finished: Int,
        saved: Int,
        lists: Int,
        downloaded: String
    ) -> UIImage? {
        let card = CinemaStatsCard(
            hours: hours,
            finished: finished,
            saved: saved,
            lists: lists,
            downloaded: downloaded,
            tint: CinemaTheme.accent
        )
        return render(card)
    }

    @MainActor
    private static func render(_ card: some View) -> UIImage? {
        let renderer = ImageRenderer(content: card)
        renderer.scale = 1
        renderer.proposedSize = ProposedViewSize(width: 1080, height: 1350)
        return renderer.uiImage
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// Library data: what is stored and a way to empty it.
struct DataSettings: View {
    @State private var progress = 0
    @State private var favorites = 0
    @State private var message: String?
    @AppStorage("cinemax.notifications") private var notifications = true
    @AppStorage("cinemax.appLock") private var appLock = false
    @State private var confirming: String?
    @State private var shareFile: ShareFile?
    @State private var importing = false

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 12) {
                    box("\(favorites)", "في المفضلة")
                    box("\(progress)", "قيد المشاهدة")
                }

                VStack(spacing: 0) {
                    action("مسح سجل المشاهدة", "لا يمس المفضلة", destructive: true) {
                        confirming = "progress"
                    }
                    Divider().overlay(CinemaTheme.hairline)
                    action("مسح المفضلة", "يزيل العناوين المحفوظة فقط", destructive: true) {
                        confirming = "favorites"
                    }
                    Divider().overlay(CinemaTheme.hairline)
                    action("مسح ما اكتمل", "يزيل من قيد المشاهدة ما وصل نهايته", destructive: false) {
                        let done = LibraryModel.shared.progress.filter { $0.fraction >= 0.95 }
                        for entry in done {
                            _ = await CinemaAPI.shared.removeProgress(
                                itemId: entry.item.id,
                                videoLabel: entry.episodeLabel
                            )
                        }
                        await LibraryModel.shared.load()
                        HomeModel.shared.resetCache()
                        message = done.isEmpty ? "لا شيء مكتمل" : "حُذف \(done.count) عنوانًا مكتملًا"
                    }
                    Divider().overlay(CinemaTheme.hairline)
                    action("إعادة تحميل الكتالوج", "يمسح الكاش ويجلب الصفوف من جديد", destructive: false) {
                        HomeModel.shared.resetCache()
                        message = "جارٍ إعادة التحميل"
                    }
                }
                .padding(.horizontal, 14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                VStack(spacing: 0) {
                    Toggle(isOn: $appLock) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("قفل التطبيق")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(.white)
                            Text("يُطلب قفل الجهاز أو Face ID عند كل فتح — لحماية مكتبتك ممن يفتح الهاتف")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    }
                    .tint(CinemaTheme.accent)
                    .padding(.vertical, 12)

                    Divider().overlay(CinemaTheme.hairline)

                    Toggle(isOn: $notifications) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("تنبيهات الحلقات")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(.white)
                            Text("تذكير بالحلقات الجديدة من عناوينك")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                    }
                    .tint(CinemaTheme.accent)
                    .padding(.vertical, 12)

                    Divider().overlay(CinemaTheme.hairline)

                    Button {
                        Haptics.tap()
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("إعدادات الإشعارات في iOS")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(.white)
                                Text("السماح بالإشعارات من إعدادات النظام")
                                    .font(.system(size: 11))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            Spacer()
                            Image(systemName: "arrow.up.right.square")
                                .font(.system(size: 13))
                                .foregroundStyle(CinemaTheme.accent)
                        }
                        .padding(.vertical, 12)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    Divider().overlay(CinemaTheme.hairline)

                    HStack {
                        Text("حجم البيانات المحلية")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(.white)
                        Spacer()
                        Text(storageSize)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    .padding(.vertical, 12)
                }
                .padding(.horizontal, 14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                Button {
                    Haptics.tap()
                    CinemaxPrefs.resetAll()
                    message = "أُعيدت إعدادات المظهر والتشغيل إلى الافتراضي"
                } label: {
                    Text("إعادة كل الإعدادات للافتراضي")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(CinemaTheme.favourite)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(CinemaTheme.favourite.opacity(0.12), in: Capsule())
                }
                .buttonStyle(.plain)

                Button {
                    Haptics.tap()
                    UIPasteboard.general.string = CinemaxPrefs.exportSettings()
                    message = "نُسخت إعداداتك إلى الحافظة"
                } label: {
                    Text("نسخ إعداداتي")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(Color.white.opacity(0.1), in: Capsule())
                }
                .buttonStyle(.plain)

                VStack(spacing: 0) {
                    action("نسخ نسخة احتياطية", "المفضلة وسجل المشاهدة والحلقات والإضافات", destructive: false) {
                        let text = await CinemaAPI.shared.exportBackup()
                        UIPasteboard.general.string = text
                        message = "نُسخت النسخة الاحتياطية إلى الحافظة"
                    }
                    Divider().overlay(CinemaTheme.hairline)
                    action("استعادة من الحافظة", "تلصق نسخة سابقة وتعيدها كما هي", destructive: true) {
                        let raw = UIPasteboard.general.string ?? ""
                        do {
                            let summary = try await CinemaAPI.shared.importBackup(raw)
                            message = "استُعيدت: \(summary)"
                            let counts = await CinemaAPI.shared.libraryCounts()
                            progress = counts.progress
                            favorites = counts.favorites
                            HomeModel.shared.resetCache()
                            LibraryModel.shared.favorites = []
                            await LibraryModel.shared.load()
                        } catch {
                            message = error.localizedDescription
                        }
                    }
                    Divider().overlay(CinemaTheme.hairline)
                    action("تصدير ملف نسخة", "ملف JSON يمكن حفظه في الملفات أو إرساله", destructive: false) {
                        await exportFile()
                    }
                    Divider().overlay(CinemaTheme.hairline)
                    action("استيراد من ملف", "اختر ملف نسخة محفوظة على جهازك", destructive: false) {
                        importing = true
                    }
                    Divider().overlay(CinemaTheme.hairline)
                    action("حذف كل التنزيلات", downloadsLabel, destructive: true) {
                        for job in DownloadManager.shared.jobs {
                            DownloadManager.shared.remove(job)
                        }
                        message = "حُذفت كل التنزيلات"
                    }
                    Divider().overlay(CinemaTheme.hairline)
                    action("إفراغ كاش الصور", "يحرّر الذاكرة التي تحتفظ بالبوسترات", destructive: false) {
                        CinemaImageStore.shared.clear()
                        URLCache.shared.removeAllCachedResponses()
                        message = "أُفرغ كاش الصور والطلبات"
                    }
                    Divider().overlay(CinemaTheme.hairline)
                    action("تفريغ ذاكرة المصادر", "يمسح قوائم الروابط المحفوظة لمدة دقيقة", destructive: false) {
                        CinemaAPI.streamCache.removeAll()
                        message = "أُفرغت ذاكرة المصادر"
                    }
                }
                .padding(.horizontal, 14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                if let message {
                    Text(message)
                        .font(.system(size: 12))
                        .foregroundStyle(CinemaTheme.accent)
                }
            }
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("البيانات")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            confirming == "progress" ? "مسح سجل المشاهدة؟" : "مسح المفضلة؟",
            isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } }),
            titleVisibility: .visible
        ) {
            Button("مسح", role: .destructive) {
                let target = confirming
                confirming = nil
                Task { await confirm(target) }
            }
            Button("إلغاء", role: .cancel) { confirming = nil }
        } message: {
            Text("لا يمكن التراجع عن هذه الخطوة.")
        }
        .task {
            let counts = await CinemaAPI.shared.libraryCounts()
            progress = counts.progress
            favorites = counts.favorites
        }
        .sheet(item: $shareFile) { file in
            ShareSheet(items: [file.url])
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
            importFile(result)
        }
    }

    /// Writes the backup to a file so it can be saved in Files or sent anywhere.
    private func exportFile() async {
        let text = await CinemaAPI.shared.exportBackup()
        let name = "CINEMAX-backup-\(Int(Date().timeIntervalSince1970)).json"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try text.write(to: url, atomically: true, encoding: .utf8)
            shareFile = ShareFile(url: url)
            message = "جاهز للمشاركة أو الحفظ في الملفات"
        } catch {
            message = "تعذّر إنشاء ملف النسخة"
        }
    }

    private func importFile(_ result: Result<URL, Error>) {
        switch result {
        case .failure:
            message = "تعذّر فتح الملف"
        case .success(let url):
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            guard let text = try? String(contentsOf: url, encoding: .utf8) else {
                message = "الملف غير مقروء"
                return
            }
            Task {
                do {
                    let summary = try await CinemaAPI.shared.importBackup(text)
                    message = "استُعيدت: \(summary)"
                    let counts = await CinemaAPI.shared.libraryCounts()
                    progress = counts.progress
                    favorites = counts.favorites
                    HomeModel.shared.resetCache()
                    await LibraryModel.shared.load()
                } catch {
                    message = error.localizedDescription
                }
            }
        }
    }

    private func confirm(_ target: String?) async {
        guard let target else { return }
        if target == "progress" {
            let counts = await CinemaAPI.shared.clearProgress()
            progress = counts.progress
            favorites = counts.favorites
            HomeModel.shared.resetCache()
            message = "تم مسح سجل المشاهدة"
        } else {
            let counts = await CinemaAPI.shared.clearFavorites()
            progress = counts.progress
            favorites = counts.favorites
            LibraryModel.shared.favorites = []
            HomeModel.shared.favorites = []
            message = "تم مسح المفضلة"
        }
    }

    private var downloadsLabel: String {
        let jobs = DownloadManager.shared.jobs
        guard !jobs.isEmpty else { return "لا توجد تنزيلات" }
        let total = jobs.reduce(Int64(0)) { $0 + $1.total }
        return String(format: "%d ملفًا · %.0f MB", jobs.count, Double(total) / 1_048_576)
    }

    private var storageSize: String {
        let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let attributes = try? FileManager.default.attributesOfItem(atPath: url.path)
        let size = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
        let mb = Double(size) / 1_048_576
        return mb < 1 ? "أقل من 1 MB" : String(format: "%.1f MB", mb)
    }

    private func box(_ value: String, _ title: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 24, weight: .heavy))
                .foregroundStyle(CinemaTheme.accent)
            Text(title)
                .font(.system(size: 11))
                .foregroundStyle(CinemaTheme.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func action(
        _ title: String,
        _ subtitle: String,
        destructive: Bool,
        run: @escaping () async -> Void
    ) -> some View {
        Button {
            Haptics.tap()
            Task { await run() }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(destructive ? CinemaTheme.favourite : .white)
                    Text(subtitle)
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.muted)
                }
                Spacer()
                Image(systemName: "chevron.left")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(CinemaTheme.muted.opacity(0.6))
            }
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Nuvio provider repositories, the second half of the source engine: paste a
/// repository URL, then switch individual providers on or off.
struct NuvioSettings: View {
    @State private var repos: [NuvioRepositoryRow] = []
    @State private var input = ""
    @State private var busy = false
    @State private var loading = true
    @State private var message: String?
    @AppStorage("cinemax.nuvio.proxy") private var proxy = ""
    @AppStorage("cinemax.nuvio.proxyHosts") private var proxyHosts = "showbox,febbox,pstream"
    @AppStorage("cinemax.nuvio.showboxCookie") private var showboxCookie = ""
    @State private var expanded: Set<String> = []
    @State private var providerQuery = ""

    /// Narrows the providers of every repository by name.
    private func filteredProviders(_ repo: NuvioRepositoryRow) -> [NuvioProviderRow] {
        let text = providerQuery.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return repo.providers }
        return repo.providers.filter { $0.name.localizedCaseInsensitiveContains(text) }
    }

    private let sample = "https://raw.githubusercontent.com/tapframe/nuvio-providers/refs/heads/main"

    /// Adds the two free provider repositories the app offers, and skips whatever
    /// is already installed so a second tap changes nothing.
    private func installFreeRepositories() async {
        guard !busy else { return }
        busy = true
        var added = 0
        let known = Set(repos.map(\.url))
        for source in CuratedSources.repositories where !known.contains(source.url) {
            if (try? await CinemaAPI.shared.addNuvioRepository(source.url)) != nil {
                added += 1
            }
        }
        repos = await CinemaAPI.shared.nuvioRepositories()
        busy = false
        message = added > 0
            ? "أُضيف \(added) مستودعًا مجانيًا"
            : "المستودعان مثبتان بالفعل"
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("أضف مستودع مزوّدين")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 8) {
                        TextField("https://…", text: $input)
                            .textFieldStyle(.plain)
                            .font(.system(size: 12))
                            .foregroundStyle(.white)
                            .keyboardType(.URL)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                        Button(busy ? "…" : "أضف") {
                            Task { await add() }
                        }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                        .disabled(busy || input.trimmingCharacters(in: .whitespaces).isEmpty)
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(CinemaTheme.elevated.opacity(0.7), in: Capsule())

                    Button("استخدم مستودع tapframe الجاهز") {
                        Haptics.select()
                        input = sample
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(CinemaTheme.accent)
                    .buttonStyle(.plain)

                    Button("أضف المستودعين المجانيين") {
                        Haptics.select()
                        Task { await installFreeRepositories() }
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(CinemaTheme.accent)
                    .buttonStyle(.plain)

                    if let message {
                        Text(message)
                            .font(.system(size: 11))
                            .foregroundStyle(message.contains("تعذّر") ? CinemaTheme.favourite : CinemaTheme.accent)
                    }
                }
                .padding(14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                VStack(alignment: .leading, spacing: 10) {
                    Text("بروكسي وكوكي المزوّدين")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)

                    TextField("https://proxy.example.workers.dev/?destination=", text: $proxy)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12))
                        .foregroundStyle(.white)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(CinemaTheme.elevated.opacity(0.7), in: Capsule())

                    TextField("النطاقات التي يمرّ بها البروكسي", text: $proxyHosts)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12))
                        .foregroundStyle(.white)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(CinemaTheme.elevated.opacity(0.7), in: Capsule())

                    SecureField("كوكي FebBox (ui) — اختياري", text: $showboxCookie)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12))
                        .foregroundStyle(.white)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(CinemaTheme.elevated.opacity(0.7), in: Capsule())

                    Text("بعض مزوّدي ShowBox لا يعملون إلا عبر بروكسي ينتهي بـ?destination=، وبعضهم يحتاج كوكي حسابك ليعطي جودة 4K وحصة أكبر. القيم تُحفظ على جهازك وتُضاف لطلبات السكربتات فقط.")
                        .font(.system(size: 10))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineSpacing(3)
                }
                .padding(14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                if loading {
                    ProgressView().tint(CinemaTheme.accent).padding(.vertical, 20)
                } else if repos.isEmpty {
                    Text("لا مستودع بعد. التطبيق يقرأ سكربتات المزوّدين من مستودع Nuvio ويشغّلها في صندوق معزول بلا وصول للملفات أو الشبكة إلا عبر https.")
                        .font(.system(size: 12))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineSpacing(4)
                } else {
                    if !repos.isEmpty {
                        HStack {
                            Button(expanded.count == repos.count ? "طيّ الكل" : "توسيع الكل") {
                                Haptics.select()
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    expanded = expanded.count == repos.count
                                        ? []
                                        : Set(repos.map(\.url))
                                }
                            }
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .buttonStyle(.plain)
                            Spacer()
                        }
                    }
                    if !repos.isEmpty {
                        HStack(spacing: 8) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                            TextField("ابحث بين المزوّدين", text: $providerQuery)
                                .textFieldStyle(.plain)
                                .font(.system(size: 12))
                                .foregroundStyle(.white)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 9)
                        .background(CinemaTheme.surface, in: Capsule())
                    }
                    ForEach(repos) { repo in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Button {
                                    Haptics.select()
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        if expanded.contains(repo.url) {
                                            expanded.remove(repo.url)
                                        } else {
                                            expanded.insert(repo.url)
                                        }
                                    }
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: expanded.contains(repo.url) ? "chevron.up" : "chevron.left")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundStyle(CinemaTheme.muted)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(repo.name)
                                                .font(.system(size: 14, weight: .semibold))
                                                .foregroundStyle(.white)
                                            Text(
                                                "\(repo.enabledCount) من \(repo.providers.count) مفعّل" +
                                                (repo.version.isEmpty ? "" : " · \(repo.version)")
                                            )
                                            .font(.system(size: 10))
                                            .foregroundStyle(CinemaTheme.muted)
                                        }
                                    }
                                }
                                .buttonStyle(.plain)
                                Spacer()
                                ShareLink(item: repo.url) {
                                    Image(systemName: "square.and.arrow.up")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(CinemaTheme.accent)
                                        .frame(width: 32, height: 32)
                                        .background(CinemaTheme.elevated, in: Circle())
                                }
                                Button {
                                    Haptics.tap()
                                    Task { await remove(repo) }
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(CinemaTheme.favourite)
                                        .frame(width: 32, height: 32)
                                        .background(CinemaTheme.elevated, in: Circle())
                                }
                                .buttonStyle(.plain)
                            }
                            if expanded.contains(repo.url) {
                                ForEach(filteredProviders(repo)) { provider in
                                    Toggle(isOn: Binding(
                                        get: { provider.enabled },
                                        set: { value in Task { await toggle(provider, value) } }
                                    )) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(provider.name)
                                                .font(.system(size: 13, weight: .medium))
                                                .foregroundStyle(.white)
                                            HStack(spacing: 6) {
                                                Text(provider.types.joined(separator: " · "))
                                                    .font(.system(size: 10))
                                                    .foregroundStyle(CinemaTheme.muted)
                                                if provider.disabledOnIos == true {
                                                    Text("لا يدعم iOS")
                                                        .font(.system(size: 9, weight: .semibold))
                                                        .foregroundStyle(CinemaTheme.favourite)
                                                        .padding(.horizontal, 6)
                                                        .padding(.vertical, 2)
                                                        .background(CinemaTheme.favourite.opacity(0.14), in: Capsule())
                                                }
                                                if provider.hasSettings == true {
                                                    Text("إعدادات داخلية")
                                                        .font(.system(size: 9, weight: .semibold))
                                                        .foregroundStyle(CinemaTheme.accent)
                                                        .padding(.horizontal, 6)
                                                        .padding(.vertical, 2)
                                                        .background(CinemaTheme.accent.opacity(0.14), in: Capsule())
                                                }
                                            }
                                        }
                                    }
                                    .tint(CinemaTheme.accent)
                                }
                            } else if !repo.providers.isEmpty {
                                Text("مطويّ — اضغط اسم المستودع لعرض مزوّديه")
                                    .font(.system(size: 10))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                        }
                        .padding(14)
                        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                }

                Text("مزوّدو Nuvio يحتاجون مفتاح TMDB، وكل مزوّد مفعّل يزيد عدد المصادر عند فتح أي عنوان.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)
            }
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("مزوّدو Nuvio")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func load() async {
        repos = await CinemaAPI.shared.nuvioRepositories()
        loading = false
    }

    private func add() async {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        busy = true
        message = nil
        do {
            repos = try await CinemaAPI.shared.addNuvioRepository(text)
            input = ""
            message = "أُضيف المستودع وفُعّلت مزوّداته"
        } catch {
            message = "تعذّر: \(error.localizedDescription)"
        }
        busy = false
    }

    private func remove(_ repo: NuvioRepositoryRow) async {
        repos = await CinemaAPI.shared.removeNuvioRepository(repo.url)
        message = "أُزيل \(repo.name)"
    }

    private func toggle(_ provider: NuvioProviderRow, _ enabled: Bool) async {
        Haptics.select()
        repos = await CinemaAPI.shared.setNuvioProvider(provider.key, enabled: enabled)
    }
}

/// The portable form of a setup: the Stremio manifests and the Nuvio
/// repositories, in one JSON file that can be shared or kept.
struct SourcesExport: Codable {
    let version: Int
    let exportedAt: String
    let stremio: [String]
    let nuvio: [String]
}

/// Moving a setup between devices: the Stremio manifests and the Nuvio
/// repositories as plain links, to copy, share or paste back in.
struct SharingSettings: View {
    @State private var addons: [InstalledAddonRow] = []
    @State private var repos: [NuvioRepositoryRow] = []
    @State private var importText = ""
    @State private var working = false
    @State private var message: String?
    @State private var loading = true
    @State private var shareFile: ShareFile?
    @State private var importingFile = false

    private var stremioLinks: String {
        addons.map(\.url).joined(separator: "\n")
    }

    private var nuvioLinks: String {
        repos.map(\.url).joined(separator: "\n")
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                section(
                    title: "روابط Stremio (\(addons.count))",
                    detail: "كل رابط manifest لإضافة مثبتة، سطر لكل إضافة",
                    links: stremioLinks,
                    rows: addons.map { (name: $0.name, url: $0.url) }
                )

                section(
                    title: "مستودعات Nuvio (\(repos.count))",
                    detail: "روابط المستودعات المضافة، سطر لكل مستودع",
                    links: nuvioLinks,
                    rows: repos.map { (name: $0.name, url: $0.url) }
                )

                VStack(alignment: .leading, spacing: 10) {
                    Text("ملف JSON")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    HStack(spacing: 10) {
                        Button {
                            Task { await exportJSON() }
                        } label: {
                            Label("تصدير ملف", systemImage: "square.and.arrow.up")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(.black)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 9)
                                .background(CinemaTheme.accent, in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .disabled(working)

                        Button {
                            Haptics.tap()
                            importingFile = true
                        } label: {
                            Label("استيراد ملف", systemImage: "square.and.arrow.down")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 9)
                                .background(CinemaTheme.elevated, in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .disabled(working)
                        Spacer()
                    }
                    Text("ملف واحد يحمل كل روابط الإضافات ومستودعات Nuvio — للنقل بين الأجهزة أو المشاركة أو الحفظ.")
                        .font(.system(size: 10))
                        .foregroundStyle(CinemaTheme.muted)
                        .lineSpacing(3)
                }
                .padding(14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                VStack(alignment: .leading, spacing: 8) {
                    Text("استيراد روابط Stremio")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                    TextField("الصق رابطًا أو أكثر، كل رابط في سطر", text: $importText, axis: .vertical)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12))
                        .foregroundStyle(.white)
                        .lineLimit(3...6)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(12)
                        .background(CinemaTheme.elevated.opacity(0.7), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    Button {
                        Task { await importAddons() }
                    } label: {
                        Text(working ? "جارٍ التثبيت…" : "ثبّت الروابط")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(CinemaTheme.accent, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .disabled(working || importText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                    if let message {
                        Text(message)
                            .font(.system(size: 11))
                            .foregroundStyle(message.contains("تعذّر") ? CinemaTheme.favourite : CinemaTheme.accent)
                    }
                }
                .padding(14)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                Text("المشاركة تفتح ورقة iOS، ومنها النسخ أو الإرسال أو الحفظ في الملفات. من يملك الروابط يستطيع تثبيت نفس الإضافات في Stremio أو إضافتها هنا.")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(4)
            }
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("تصدير ومشاركة")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .sheet(item: $shareFile) { file in
            ShareSheet(items: [file.url])
        }
        .fileImporter(isPresented: $importingFile, allowedContentTypes: [.json]) { result in
            importJSON(result)
        }
    }

    /// Writes both lists into one JSON file and offers it to the share sheet.
    private func exportJSON() async {
        addons = await CinemaAPI.shared.installedAddons()
        repos = await CinemaAPI.shared.nuvioRepositories()
        let payload = SourcesExport(
            version: 1,
            exportedAt: ISO8601DateFormatter().string(from: Date()),
            stremio: addons.map(\.url),
            nuvio: repos.map(\.url)
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(payload) else {
            message = "تعذّر تكوين الملف"
            return
        }
        let name = "CINEMAX-sources-\(Int(Date().timeIntervalSince1970)).json"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try data.write(to: url, options: .atomic)
            shareFile = ShareFile(url: url)
            message = "ملف المصادر جاهز للمشاركة أو الحفظ"
        } catch {
            message = "تعذّر إنشاء الملف"
        }
    }

    /// Accepts the app's own file, a plain array of links, or pasted text.
    private func importJSON(_ result: Result<URL, Error>) {
        guard case .success(let url) = result else {
            message = "تعذّر فتح الملف"
            return
        }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else {
            message = "الملف غير مقروء"
            return
        }
        var addonLinks: [String] = []
        var repoSources: [String] = []
        if let decoded = try? JSONDecoder().decode(SourcesExport.self, from: data) {
            addonLinks = decoded.stremio
            repoSources = decoded.nuvio
        } else if let simple = try? JSONDecoder().decode([String].self, from: data) {
            addonLinks = simple
        } else if let text = String(data: data, encoding: .utf8) {
            addonLinks = text
                .split(whereSeparator: { $0 == "\n" || $0 == "," || $0 == " " })
                .map { String($0).trimmingCharacters(in: .whitespaces) }
                .filter { $0.hasPrefix("http") }
        }
        guard !addonLinks.isEmpty || !repoSources.isEmpty else {
            message = "لا روابط في الملف"
            return
        }
        Task { await apply(addons: addonLinks, repositories: repoSources) }
    }

    private func apply(addons links: [String], repositories: [String]) async {
        working = true
        var installed = 0
        var failed = 0
        var addedRepos = 0
        for link in Set(links) {
            do {
                _ = try await CinemaAPI.shared.installAddon(link)
                installed += 1
            } catch {
                failed += 1
            }
        }
        for repo in Set(repositories) {
            do {
                _ = try await CinemaAPI.shared.addNuvioRepository(repo)
                addedRepos += 1
            } catch {
                failed += 1
            }
        }
        HomeModel.shared.resetCache()
        self.addons = await CinemaAPI.shared.installedAddons()
        self.repos = await CinemaAPI.shared.nuvioRepositories()
        working = false
        var summary = "ثُبّت \(installed) إضافة و\(addedRepos) مستودعًا"
        if failed > 0 { summary += " · تعذّر \(failed)" }
        message = summary
    }

    private func section(
        title: String,
        detail: String,
        links: String,
        rows: [(name: String, url: String)]
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(CinemaTheme.muted)
            Text(detail)
                .font(.system(size: 10))
                .foregroundStyle(CinemaTheme.muted.opacity(0.8))

            HStack(spacing: 10) {
                Button {
                    Haptics.tap()
                    UIPasteboard.general.string = links
                    message = "نُسخت \(rows.count) روابط"
                } label: {
                    Label("نسخ الكل", systemImage: "doc.on.doc")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(CinemaTheme.elevated, in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(rows.isEmpty)

                if !links.isEmpty {
                    ShareLink(item: links) {
                        Label("مشاركة الكل", systemImage: "square.and.arrow.up")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .background(CinemaTheme.accent.opacity(0.14), in: Capsule())
                    }
                }
                Spacer()
            }

            if rows.isEmpty {
                Text("لا شيء بعد.")
                    .font(.system(size: 12))
                    .foregroundStyle(CinemaTheme.muted)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack(spacing: 8) {
                                Text(row.name)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                                Spacer()
                                Button {
                                    Haptics.tap()
                                    UIPasteboard.general.string = row.url
                                    message = "نُسخ رابط \(row.name)"
                                } label: {
                                    Image(systemName: "doc.on.doc")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(CinemaTheme.muted)
                                        .frame(width: 30, height: 30)
                                        .background(CinemaTheme.elevated, in: Circle())
                                }
                                .buttonStyle(.plain)
                                ShareLink(item: row.url) {
                                    Image(systemName: "square.and.arrow.up")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(CinemaTheme.accent)
                                        .frame(width: 30, height: 30)
                                        .background(CinemaTheme.accent.opacity(0.14), in: Circle())
                                }
                            }
                            Text(row.url)
                                .font(.system(size: 10))
                                .foregroundStyle(CinemaTheme.muted)
                                .lineLimit(2)
                                .textSelection(.enabled)
                        }
                        .padding(.vertical, 9)
                        if index < rows.count - 1 {
                            Divider().overlay(CinemaTheme.hairline)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
        }
    }

    private func load() async {
        addons = await CinemaAPI.shared.installedAddons()
        repos = await CinemaAPI.shared.nuvioRepositories()
        loading = false
    }

    private func importAddons() async {
        let links = importText
            .split(whereSeparator: { $0 == "\n" || $0 == " " || $0 == "," })
            .map { String($0).trimmingCharacters(in: .whitespaces) }
            .filter { $0.hasPrefix("http") }
        guard !links.isEmpty else {
            message = "لا روابط صالحة في النص"
            return
        }
        working = true
        var installed = 0
        var failed = 0
        for link in Set(links) {
            do {
                _ = try await CinemaAPI.shared.installAddon(link)
                installed += 1
            } catch {
                failed += 1
            }
        }
        HomeModel.shared.resetCache()
        addons = await CinemaAPI.shared.installedAddons()
        importText = ""
        working = false
        message = failed == 0 ? "ثُبّت \(installed) إضافة" : "ثُبّت \(installed) وتعذّر \(failed)"
    }
}

struct TmdbSettings: View {
    @State private var key = ""
    @State private var saved = false

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                Text("TMDB هو الكتالوج المجاني الوحيد الذي يعطي عنوانًا ووصفًا عربيًا وتقييمًا حقيقيًا. بعد حفظ المفتاح تظهر أربعة صفوف فوق صفوف إضافاتك: الأكثر تداولًا، أفلام شائعة، مسلسلات شائعة، وأعلى تقييمًا.")
                    .font(.system(size: 13))
                    .foregroundStyle(CinemaTheme.muted)
                    .lineSpacing(5)

                HStack(spacing: 8) {
                    TextField("مفتاح TMDB", text: $key)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12))
                        .foregroundStyle(.white)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 11)
                        .background(CinemaTheme.elevated, in: Capsule())
                    Button("حفظ") {
                        CinemaAPI.shared.saveTmdbKey(key.trimmingCharacters(in: .whitespacesAndNewlines))
                        HomeModel.shared.resetCache()
                        saved = true
                        Haptics.tap()
                    }
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.black)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 11)
                    .background(CinemaTheme.accent, in: Capsule())
                }

                if saved {
                    Text("تم الحفظ — حدّث الرئيسية بالسحب لرؤية الصفوف.")
                        .font(.system(size: 11))
                        .foregroundStyle(CinemaTheme.accent)
                }

                Link(destination: URL(string: "https://www.themoviedb.org/settings/api")!) {
                    Text("كيف أحصل على مفتاح مجاني؟")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(CinemaTheme.accent)
                }
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(20)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle("TMDB")
        .navigationBarTitleDisplayMode(.inline)
        .task { key = CinemaAPI.shared.tmdbKey }
    }
}


// MARK: - Preferences shared by the screens

/// The small preferences the interface keeps in UserDefaults, so the settings
/// screens and the views that use them agree without extra plumbing.
enum CinemaxPrefs {
    private static let posterWidthKey = "cinemax.posterWidth"
    private static let historyKey = "cinemax.search.history"
    private static let lastRefreshKey = "cinemax.lastRefresh"

    /// The width of a poster in grids, driven by the library density setting.
    static var posterMinWidth: CGFloat {
        let stored = UserDefaults.standard.integer(forKey: posterWidthKey)
        return stored == 0 ? 112 : CGFloat(stored)
    }

    static func setPosterWidth(_ value: Int) {
        UserDefaults.standard.set(value, forKey: posterWidthKey)
    }

    static var defaultSpeed: Double {
        let stored = UserDefaults.standard.double(forKey: "cinemax.defaultSpeed")
        return stored == 0 ? 1 : stored
    }

    static var notifications: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.notifications") as? Bool) ?? true
    }

    /// How far the player's skip buttons jump, picked in settings.
    static var seekSeconds: Int {
        let stored = UserDefaults.standard.integer(forKey: "cinemax.seekSeconds")
        return stored == 0 ? 10 : stored
    }

    /// Hides the player's controls a few seconds after the picture is tapped awake.
    static var autoHideControls: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.autoHideControls") as? Bool) ?? true
    }

    /// Continues a title where it stopped; turning it off always starts at zero.
    static var autoResume: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.autoResume") as? Bool) ?? true
    }

    /// Lets the home hero slide onward by itself.
    static var heroAutoRotate: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.heroAuto") as? Bool) ?? true
    }

    /// Puts the fastest source on top instead of the highest quality.
    static var speedFirst: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.speedFirst") as? Bool) ?? false
    }

    /// The quality tier the viewer wants first: "auto", "4K", "1080p" or "720p".
    static var preferredQuality: String {
        UserDefaults.standard.string(forKey: "cinemax.prefQuality") ?? "auto"
    }

    /// True when an adaptive link's rendition is picked before playback opens.
    /// Off by default: that pick is a network request of several seconds before
    /// any picture, and both players adapt a master playlist by themselves.
    static var hlsRendition: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.hlsRendition") as? Bool) ?? false
    }

    /// The engine a link opens with. The default is automatic: the system player
    /// takes what it reads natively (HLS, MP4), which gives the picture its own
    /// correct shape in both orientations and brings Picture in Picture, and a
    /// container only libmpv understands (MKV, HEVC, AV1) stays with libmpv.
    static var playerEngine: String {
        UserDefaults.standard.string(forKey: "cinemax.playerEngine") ?? "auto"
    }

    /// The engine one link should open with. An adaptive or progressive stream is
    /// what the system player reads natively, and that is what brings Picture in
    /// Picture and AirPlay; a container only libmpv understands stays with it.
    static func engine(for urlString: String) -> String {
        let chosen = playerEngine
        guard chosen == "auto" else { return chosen }
        return systemReadable(urlString) ? "system" : "mpv"
    }

    /// True when the system player reads this link natively: an adaptive HLS
    /// playlist, or an MP4/MOV file. It is the same test the automatic engine
    /// uses, shown on its own so the player can offer Picture in Picture.
    static func systemReadable(_ urlString: String) -> Bool {
        let lowered = urlString.lowercased()
        if lowered.contains(".m3u8") || lowered.contains("format=m3u8") { return true }
        let path = lowered.split(separator: "?").first.map(String.init) ?? lowered
        for suffix in [".mp4", ".m4v", ".mov"] where path.hasSuffix(suffix) { return true }
        return false
    }

    /// How many transfers may run at once; the rest wait their turn.
    static var downloadLimit: Int {
        let stored = UserDefaults.standard.integer(forKey: "cinemax.downloadLimit")
        return stored == 0 ? 2 : max(1, min(4, stored))
    }

    static func setDownloadLimit(_ value: Int) {
        UserDefaults.standard.set(max(1, min(4, value)), forKey: "cinemax.downloadLimit")
    }

    /// Lets the player turn the window to landscape while it is open.
    static var allowRotation: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.allowRotation") as? Bool) ?? true
    }

    /// The system's reduce-motion switch, mirrored in the app's own settings.
    static var reduceMotion: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.reduceMotion") as? Bool) ?? false
    }

    /// Refetches the catalogues on every visit instead of once a minute.
    static var autoRefresh: Bool {
        (UserDefaults.standard.object(forKey: "cinemax.autoRefresh") as? Bool) ?? true
    }

    static var searchHistory: [String] {
        UserDefaults.standard.stringArray(forKey: historyKey) ?? []
    }

    static func rememberSearch(_ query: String) {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.count >= 2 else { return }
        var items = searchHistory.filter { $0.caseInsensitiveCompare(text) != .orderedSame }
        items.insert(text, at: 0)
        UserDefaults.standard.set(Array(items.prefix(10)), forKey: historyKey)
    }

    static func clearSearchHistory() {
        UserDefaults.standard.removeObject(forKey: historyKey)
    }

    /// Drops one remembered search, from the chip's own menu.
    static func removeSearch(_ query: String) {
        let items = searchHistory.filter { $0.caseInsensitiveCompare(query) != .orderedSame }
        UserDefaults.standard.set(items, forKey: historyKey)
    }

    static var lastRefresh: Date? {
        UserDefaults.standard.object(forKey: lastRefreshKey) as? Date
    }

    static func markRefreshed() {
        UserDefaults.standard.set(Date(), forKey: lastRefreshKey)
    }

    static var lastRefreshLabel: String {
        guard let date = lastRefresh else { return "لم يُحدَّث بعد" }
        let minutes = Int(Date().timeIntervalSince(date) / 60)
        if minutes < 1 { return "حُدّث الآن" }
        if minutes < 60 { return "حُدّث قبل \(minutes) د" }
        let hours = minutes / 60
        return hours < 24 ? "حُدّث قبل \(hours) س" : "حُدّث أمس"
    }

    /// A readable snapshot of the interface preferences, for the copy action.
    @MainActor
    static func exportSettings() -> String {
        let defaults = UserDefaults.standard
        let keys = [
            "cinemax.accent", "cinemax.oled", "cinemax.reduceMotion", "cinemax.haptics",
            "cinemax.autoplayNext", "cinemax.design", "cinemax.defaultSpeed",
            "cinemax.notifications", "cinemax.seekSeconds", "cinemax.autoHideControls",
            "cinemax.autoResume", "cinemax.heroAuto", "cinemax.skipIntro",
            "cinemax.prefQuality", "cinemax.playerEngine", "cinemax.hwdec",
            "cinemax.mpvFast", "cinemax.cacheMB", "cinemax.allowRotation",
            "cinemax.subCp1256", "cinemax.librarySort", "cinemax.searchKind",
            "cinemax.showContinue", "cinemax.maxShelves",
            posterWidthKey
        ]
        let lines = keys.map { key in
            "\(key) = \(defaults.object(forKey: key).map { "\($0)" } ?? "—")"
        }
        return (["CINEMAX \(CinemaAPI.shared.versionLabel)"] + lines).joined(separator: "\n")
    }

    static func resetAll() {
        for key in [
            "cinemax.accent", "cinemax.oled", "cinemax.reduceMotion", "cinemax.autoplayNext",
            "cinemax.design", posterWidthKey, "cinemax.defaultSpeed", "cinemax.notifications",
            "cinemax.seekSeconds", "cinemax.autoHideControls", "cinemax.autoResume",
            "cinemax.heroAuto", "cinemax.skipIntro", "cinemax.prefQuality",
            "cinemax.playerEngine", "cinemax.subtitleLang", "cinemax.onboarded",
            "cinemax.subtitleSize", "cinemax.subtitleAuto", "cinemax.hwdec",
            "cinemax.mpvFast", "cinemax.cacheMB", "cinemax.allowRotation",
            "cinemax.subCp1256", "cinemax.librarySort", "cinemax.searchKind",
            "cinemax.showContinue", "cinemax.maxShelves", "cinemax.hiddenShelves",
            "cinemax.homeFilter", "cinemax.sourcesCollapsed",
            "cinemax.appLock", "cinemax.autoSkipIntro", "cinemax.searchSort",
            "cinemax.rowOrder", "cinemax.volume", "cinemax.aspect", "cinemax.zoom",
            "cinemax.subPos", "cinemax.showHero", "cinemax.autoRefresh",
            "cinemax.playerHints", "cinemax.lists.v1", "cinemax.corner",
            "cinemax.hapticsLevel", "cinemax.sourcesCompact", "cinemax.prefetchNext",
            "cinemax.eventLog.v1",
            historyKey, lastRefreshKey
        ] {
            UserDefaults.standard.removeObject(forKey: key)
        }
    }
}


/// A whole shelf as a grid, opened from the "المزيد" action on a row. When the
/// addon accepts `skip` and `genre`, the grid keeps walking the rest of the
/// catalogue instead of stopping at the first page.
struct ShelfGridScreen: View {
    let shelf: Shelf
    /// A catalogue opened from an addon's own page knows its type up front.
    var catalogType: String? = nil
    let onOpen: (CinemaItem) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var items: [CinemaItem] = []
    @State private var genres: [String] = []
    @State private var genre: String?
    @State private var loadingMore = false
    @State private var exhausted = false
    @State private var listItem: CinemaItem?

    private var addonId: String {
        String(shelf.id.split(separator: ":", maxSplits: 1).first ?? "")
    }

    private var catalogId: String {
        let parts = shelf.id.split(separator: ":", maxSplits: 1)
        return parts.count > 1 ? String(parts[1]) : shelf.id
    }

    private var type: String {
        catalogType ?? shelf.items.first?.type ?? "movie"
    }

    private var visible: [CinemaItem] {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return items }
        return items.filter { $0.name.localizedCaseInsensitiveContains(text) }
    }

    private var columns: [GridItem] {
        [GridItem(.adaptive(minimum: CinemaxPrefs.posterMinWidth), spacing: 12)]
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                        TextField("ابحث داخل هذه القائمة", text: $query)
                            .textFieldStyle(.plain)
                            .font(.system(size: 12))
                            .foregroundStyle(.white)
                        Text("\(visible.count)")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(CinemaTheme.surface, in: Capsule())
                    .padding(.horizontal, 20)

                    if !genres.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                genreChip("الكل", nil)
                                ForEach(genres, id: \.self) { value in
                                    genreChip(value, value)
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }

                    LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                        ForEach(visible) { item in
                            PosterCard(
                                item: item,
                                width: CinemaxPrefs.posterMinWidth,
                                saved: HomeModel.shared.favorites.contains(item.id),
                                onOpen: { onOpen(item) }
                            )
                            .contextMenu {
                                Button {
                                    Haptics.tap()
                                    listItem = item
                                } label: {
                                    Label("أضف إلى قائمة", systemImage: "text.badge.plus")
                                }
                                Button {
                                    Haptics.tap()
                                    HomeModel.shared.toggle(item)
                                } label: {
                                    Label(
                                        HomeModel.shared.favorites.contains(item.id)
                                            ? "إزالة من المحفوظة"
                                            : "أضف إلى المحفوظة",
                                        systemImage: "heart"
                                    )
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 20)

                    if !exhausted {
                        Button {
                            Haptics.select()
                            Task { await loadPage(reset: false) }
                        } label: {
                            HStack(spacing: 8) {
                                if loadingMore {
                                    ProgressView().tint(CinemaTheme.accent)
                                }
                                Text(loadingMore ? "جارٍ التحميل…" : "تحميل المزيد")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(CinemaTheme.surface, in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .disabled(loadingMore)
                        .padding(.horizontal, 20)
                        .padding(.top, 5)
                    }

                    Spacer(minLength: 40)
                }
                .padding(.top, 10)
            }
            .background(CinemaTheme.canvas.ignoresSafeArea())
            .navigationTitle(shelf.title)
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { await loadPage(reset: true) }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("تم") { dismiss() }
                        .foregroundStyle(CinemaTheme.accent)
                }
            }
            .task {
                items = shelf.items
                await loadPage(reset: true)
            }
        }
        .preferredColorScheme(.dark)
        .sheet(item: $listItem) { ListsPickerSheet(item: $0) }
    }

    private func genreChip(_ title: String, _ value: String?) -> some View {
        Button {
            Haptics.select()
            genre = value
            Task { await loadPage(reset: true) }
        } label: {
            Text(title)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(genre == value ? .black : .white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background {
                    Capsule().fill(genre == value ? CinemaTheme.accent : Color.white.opacity(0.08))
                }
        }
        .buttonStyle(.plain)
    }

    private func loadPage(reset: Bool) async {
        if !reset && (loadingMore || exhausted) { return }
        loadingMore = true
        do {
            let page = try await CinemaAPI.shared.catalogPage(
                addonId: addonId,
                catalogId: catalogId,
                type: type,
                skip: reset ? 0 : items.count,
                genre: genre
            )
            genres = page.genres ?? []
            if reset {
                items = page.items.isEmpty ? shelf.items : page.items
                exhausted = page.items.isEmpty
            } else {
                let known = Set(items.map(\.id))
                let added = page.items.filter { !known.contains($0.id) }
                items.append(contentsOf: added)
                exhausted = !page.hasMore || added.isEmpty
            }
        } catch {
            exhausted = true
        }
        loadingMore = false
    }
}


// MARK: - My lists

/// A list the viewer keeps: films for Friday, episodes to finish, anything.
struct CinemaList: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var createdAt: Double
    var items: [CinemaItem]

    var posterURLs: [URL] { items.compactMap(\.posterURL) }
}

/// The viewer's own lists, kept on the device: no account, no server.
@MainActor
final class ListsStore: ObservableObject {
    static let shared = ListsStore()

    @Published private(set) var lists: [CinemaList] = []

    private let key = "cinemax.lists.v1"

    private init() {
        guard let data = UserDefaults.standard.data(forKey: key),
              let decoded = try? JSONDecoder().decode([CinemaList].self, from: data) else { return }
        lists = decoded
    }

    private func save() {
        if let data = try? JSONEncoder().encode(lists) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    func list(_ id: String) -> CinemaList? {
        lists.first { $0.id == id }
    }

    /// Titles across every list, each counted once.
    var totalTitles: Int {
        Set(lists.flatMap { $0.items.map(\.id) }).count
    }

    /// The lists already holding a title, so a picker can show its ticks.
    func listIDs(holding itemId: String) -> Set<String> {
        Set(lists.filter { list in list.items.contains { $0.id == itemId } }.map(\.id))
    }

    @discardableResult
    func create(named name: String) -> CinemaList {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let created = CinemaList(
            id: UUID().uuidString,
            name: trimmed.isEmpty ? "قائمة جديدة" : trimmed,
            createdAt: Date().timeIntervalSince1970,
            items: []
        )
        lists.insert(created, at: 0)
        save()
        return created
    }

    func rename(_ id: String, to name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let index = lists.firstIndex(where: { $0.id == id }) else { return }
        lists[index].name = trimmed
        save()
    }

    func delete(_ id: String) {
        lists.removeAll { $0.id == id }
        save()
    }

    /// Adds the title unless the list already holds it.
    func add(_ item: CinemaItem, to id: String) {
        guard let index = lists.firstIndex(where: { $0.id == id }) else { return }
        guard !lists[index].items.contains(where: { $0.id == item.id }) else { return }
        lists[index].items.insert(item, at: 0)
        save()
    }

    func remove(_ item: CinemaItem, from id: String) {
        guard let index = lists.firstIndex(where: { $0.id == id }) else { return }
        lists[index].items.removeAll { $0.id == item.id }
        save()
    }

    func toggle(_ item: CinemaItem, in id: String) {
        if listIDs(holding: item.id).contains(id) {
            remove(item, from: id)
        } else {
            add(item, to: id)
        }
    }

    /// One list as plain text, ready to be sent to someone.
    func shareText(_ list: CinemaList) -> String {
        let titles = list.items.map { "• \($0.name)" }.joined(separator: "\n")
        return "\(list.name)\n\(titles)\n\nمن CINEMAX"
    }
}

/// The viewer's lists, as a segment of the library.
struct ListsView: View {
    @Binding var query: String
    @ObservedObject private var store = ListsStore.shared

    @State private var creating = false
    @State private var newName = ""
    @State private var renaming: CinemaList?
    @State private var renameText = ""
    @State private var deleting: CinemaList?
    /// The list's card, drawn when the viewer asks for it, and whether one is
    /// being drawn right now so a second tap does not start again.
    @State private var card: ShareImage?
    @State private var buildingCard = false

    private var visible: [CinemaList] {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return store.lists }
        return store.lists.filter { list in
            list.name.localizedCaseInsensitiveContains(text)
                || list.items.contains { $0.name.localizedCaseInsensitiveContains(text) }
        }
    }

    private let columns = [GridItem(.adaptive(minimum: 150), spacing: 12)]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Text("\(store.lists.count) قائمة · \(store.totalTitles) عنوانًا")
                    .font(.system(size: 11))
                    .foregroundStyle(CinemaTheme.muted)
                Spacer()
                Button {
                    Haptics.tap()
                    newName = ""
                    creating = true
                } label: {
                    Label("قائمة جديدة", systemImage: "plus")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.black)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(CinemaTheme.accent, in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 20)

            if store.lists.isEmpty {
                notice(
                    "لا قوائم بعد",
                    "اجمع ما تريد مشاهدته لاحقًا: أفلام الجمعة، أو مسلسلات لم تبدأها. أضف أي عنوان من صفحته."
                )
            } else if visible.isEmpty {
                notice("لا قوائم بهذا البحث", "جرّب كلمة أخرى أو امسح البحث.")
            } else {
                LazyVGrid(columns: columns, alignment: .leading, spacing: 14) {
                    ForEach(visible) { list in
                        NavigationLink {
                            ListDetailView(listId: list.id)
                        } label: {
                            listCard(list)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button {
                                renaming = list
                                renameText = list.name
                            } label: {
                                Label("إعادة التسمية", systemImage: "pencil")
                            }
                            ShareLink(item: store.shareText(list)) {
                                Label("مشاركة القائمة", systemImage: "square.and.arrow.up")
                            }
                            Button {
                                Haptics.tap()
                                buildCard(list)
                            } label: {
                                Label(
                                    buildingCard ? "يُجهّز البطاقة…" : "بطاقة القائمة",
                                    systemImage: "photo.on.rectangle"
                                )
                            }
                            Button(role: .destructive) {
                                deleting = list
                            } label: {
                                Label("حذف القائمة", systemImage: "trash")
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
            }

            Spacer(minLength: 40)
        }
        .padding(.top, 6)
        .sheet(item: $card) { entry in
            ShareSheet(items: [entry.image])
        }
        .alert("قائمة جديدة", isPresented: $creating) {
            TextField("اسم القائمة", text: $newName)
            Button("إنشاء") {
                Haptics.select()
                store.create(named: newName)
            }
            Button("إلغاء", role: .cancel) {}
        } message: {
            Text("تظهر القائمة في مكتبتك، وتضيف إليها أي عنوان من صفحته.")
        }
        .alert(
            "إعادة التسمية",
            isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })
        ) {
            TextField("اسم القائمة", text: $renameText)
            Button("حفظ") {
                if let list = renaming { store.rename(list.id, to: renameText) }
                renaming = nil
            }
            Button("إلغاء", role: .cancel) { renaming = nil }
        }
        .alert(
            "حذف القائمة؟",
            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })
        ) {
            Button("حذف", role: .destructive) {
                if let list = deleting { store.delete(list.id) }
                deleting = nil
            }
            Button("إلغاء", role: .cancel) { deleting = nil }
        } message: {
            Text(deleting?.name ?? "")
        }
    }

    /// Draws the list's posters as one card, then hands the picture to the share
    /// sheet so the list travels as a picture rather than as a page of text.
    private func buildCard(_ list: CinemaList) {
        guard !buildingCard, !list.items.isEmpty else { return }
        buildingCard = true
        Task { @MainActor in
            let image = await CinemaCards.list(list)
            buildingCard = false
            guard let image else { return }
            card = ShareImage(image: image)
            EventLog.shared.add("card", "بطاقة قائمة \(list.name)")
        }
    }

    private func listCard(_ list: CinemaList) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack {
                CinemaTheme.elevated
                if list.items.isEmpty {
                    Image(systemName: "rectangle.stack.badge.plus")
                        .font(.system(size: 24, weight: .light))
                        .foregroundStyle(CinemaTheme.muted)
                } else {
                    HStack(spacing: 3) {
                        ForEach(Array(list.items.prefix(3).enumerated()), id: \.element.id) { index, entry in
                            RemoteImage(url: entry.posterURL)
                                .frame(width: 50, height: 76)
                                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                .rotationEffect(.degrees(Double(index - 1) * 5))
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 92)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

            Text(list.name)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
            Text(list.items.isEmpty ? "فارغة" : "\(list.items.count) عنوانًا")
                .font(.system(size: 10))
                .foregroundStyle(CinemaTheme.muted)
        }
        .padding(10)
        .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func notice(_ title: String, _ detail: String) -> some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
            Text(detail)
                .font(.system(size: 12))
                .foregroundStyle(CinemaTheme.muted)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
        .padding(.horizontal, 24)
    }
}

/// One list: its titles, how to drop one, and how to open it.
struct ListDetailView: View {
    let listId: String

    @ObservedObject private var store = ListsStore.shared
    @State private var detailItem: CinemaItem?
    @State private var query = ""

    private var list: CinemaList? { store.list(listId) }

    private var items: [CinemaItem] {
        guard let list else { return [] }
        let text = query.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return list.items }
        return list.items.filter { $0.name.localizedCaseInsensitiveContains(text) }
    }

    private let columns = [GridItem(.adaptive(minimum: 112), spacing: 12)]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 12))
                        .foregroundStyle(CinemaTheme.muted)
                    TextField("ابحث في القائمة", text: $query)
                        .textFieldStyle(.plain)
                        .font(.system(size: 12))
                        .foregroundStyle(.white)
                    Text("\(items.count)")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(CinemaTheme.muted)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(CinemaTheme.surface, in: Capsule())
                .padding(.horizontal, 20)

                if items.isEmpty {
                    Text(
                        list?.items.isEmpty == true
                            ? "أضف عنوانًا من صفحة أي فيلم أو مسلسل: زر القوائم في الأعلى."
                            : "لا نتائج في هذه القائمة."
                    )
                    .font(.system(size: 12))
                    .foregroundStyle(CinemaTheme.muted)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
                    .padding(.horizontal, 24)
                } else {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                        ForEach(items) { entry in
                            PosterCard(
                                item: entry,
                                width: 112,
                                saved: HomeModel.shared.favorites.contains(entry.id),
                                onOpen: { detailItem = entry }
                            )
                            .contextMenu {
                                Button(role: .destructive) {
                                    Haptics.tap()
                                    store.remove(entry, from: listId)
                                } label: {
                                    Label("إزالة من القائمة", systemImage: "minus.circle")
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                }

                Spacer(minLength: 40)
            }
            .padding(.top, 8)
        }
        .background(CinemaTheme.canvas.ignoresSafeArea())
        .navigationTitle(list?.name ?? "قائمة")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let list {
                ToolbarItem(placement: .topBarTrailing) {
                    ShareLink(item: store.shareText(list)) {
                        Image(systemName: "square.and.arrow.up")
                            .foregroundStyle(CinemaTheme.accent)
                    }
                }
            }
        }
        .fullScreenCover(item: $detailItem) { entry in
            DetailView(item: entry)
        }
    }
}

/// Puts a title into one of the viewer's lists, or into a new one.
struct ListsPickerSheet: View {
    let item: CinemaItem

    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var store = ListsStore.shared
    @State private var creating = false
    @State private var newName = ""

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 10) {
                        RemoteImage(url: item.posterURL)
                            .frame(width: 42, height: 62)
                            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.name)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(.white)
                                .lineLimit(2)
                            Text("اختر قائمة أو أنشئ واحدة")
                                .font(.system(size: 11))
                                .foregroundStyle(CinemaTheme.muted)
                        }
                        Spacer()
                    }

                    if store.lists.isEmpty {
                        Text("لا قوائم بعد. أنشئ واحدة وسيُضاف إليها هذا العنوان مباشرة.")
                            .font(.system(size: 12))
                            .foregroundStyle(CinemaTheme.muted)
                            .lineSpacing(4)
                    } else {
                        VStack(spacing: 0) {
                            ForEach(Array(store.lists.enumerated()), id: \.element.id) { index, list in
                                Button {
                                    Haptics.tap()
                                    store.toggle(item, in: list.id)
                                } label: {
                                    HStack(spacing: 10) {
                                        Image(
                                            systemName: store.listIDs(holding: item.id).contains(list.id)
                                                ? "checkmark.circle.fill"
                                                : "circle"
                                        )
                                        .font(.system(size: 16))
                                        .foregroundStyle(
                                            store.listIDs(holding: item.id).contains(list.id)
                                                ? CinemaTheme.accent
                                                : CinemaTheme.muted
                                        )
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(list.name)
                                                .font(.system(size: 13, weight: .semibold))
                                                .foregroundStyle(.white)
                                                .lineLimit(1)
                                            Text("\(list.items.count) عنوانًا")
                                                .font(.system(size: 10))
                                                .foregroundStyle(CinemaTheme.muted)
                                        }
                                        Spacer()
                                    }
                                    .padding(.vertical, 10)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                if index < store.lists.count - 1 {
                                    Divider().overlay(CinemaTheme.hairline)
                                }
                            }
                        }
                        .padding(.horizontal, 14)
                        .background(
                            CinemaTheme.surface,
                            in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                        )
                    }

                    Button {
                        Haptics.tap()
                        newName = ""
                        creating = true
                    } label: {
                        Label("قائمة جديدة", systemImage: "plus")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(CinemaTheme.accent.opacity(0.14), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
            }
            .background(CinemaTheme.canvas.ignoresSafeArea())
            .navigationTitle("قوائمي")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("تم") { dismiss() }
                        .foregroundStyle(CinemaTheme.accent)
                }
            }
            .alert("قائمة جديدة", isPresented: $creating) {
                TextField("اسم القائمة", text: $newName)
                Button("إنشاء") {
                    Haptics.select()
                    let created = store.create(named: newName)
                    store.add(item, to: created.id)
                }
                Button("إلغاء", role: .cancel) {}
            } message: {
                Text("سيُضاف \(item.name) إليها.")
            }
        }
        .preferredColorScheme(.dark)
    }
}

// MARK: - Downloads

struct DownloadJob: Identifiable, Codable, Equatable {
    let id: String
    let title: String
    let url: String
    /// Referer and User-Agent the host demands before it serves the file.
    var headers: [String: String]?
    var received: Int64
    var total: Int64
    /// running · done · failed · cancelled
    var state: String
    var filePath: String?

    var progress: Double {
        guard total > 0 else { return 0 }
        return min(1, Double(received) / Double(total))
    }

    var sizeLabel: String {
        guard total > 0 else { return "—" }
        let mb = Double(total) / 1_048_576
        return String(format: "%.0f MB", mb)
    }
}

// MARK: - Background transfers

/// A stable name for a value: a link names the same folder and the same task on
/// every launch, unlike Swift's own hash which changes from run to run.
enum CinemaHash {
    static func stable(_ value: String) -> String {
        var hash: UInt64 = 0xcbf2_9ce4_8422_2325
        for byte in value.utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 0x0000_0100_0000_01b3
        }
        return String(hash, radix: 16)
    }
}

/// Runs a download's parts on a background URLSession, which iOS carries on in a
/// process of its own: a film keeps downloading after the viewer closes the app,
/// and every part it finished is already on disk when they come back. Four ranges
/// still travel at once, so a host that caps one connection stays fast, and a
/// transfer iOS kept alive is adopted instead of being asked for twice.
final class BackgroundTransfers: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    static let shared = BackgroundTransfers()
    static let identifier = "com.cinemax.transfers"
    /// The fixed number of ranges a file is cut into, kept here so a transfer
    /// picked up after a relaunch plans the very same parts.
    static let parts = 4

    private struct Plan {
        let jobKey: String
        let index: Int
        let expected: Int64
        let directory: URL
        var written: Int64
    }

    private struct Wing {
        var total: Int64
        var baseline: Int64
        var open: Int
        var failure: Error?
        var waiter: CheckedContinuation<Void, Error>?
        var report: ((Int64) -> Void)?
    }

    private let lock = NSLock()
    private var plans: [Int: Plan] = [:]
    private var wings: [String: Wing] = [:]
    /// The tasks travelling for each job, so a pause stops exactly those.
    private var travelling: [String: [Int]] = [:]
    private var resumeHandler: (() -> Void)?
    private var backgroundSession: URLSession?

    /// The session is built once and kept: iOS hands its events to whatever
    /// object is its delegate when the app wakes up for a finished transfer.
    private func session() -> URLSession {
        lock.lock()
        if let backgroundSession {
            lock.unlock()
            return backgroundSession
        }
        lock.unlock()
        let configuration = URLSessionConfiguration.background(withIdentifier: Self.identifier)
        // Waking the app for a finished transfer is what makes a download end on
        // disk even when CINEMAX was closed halfway through it.
        configuration.sessionSendsLaunchEvents = true
        // Not discretionary: a transfer the viewer asked for starts now, not when
        // the system decides the phone is idle enough for it.
        configuration.isDiscretionary = false
        configuration.httpMaximumConnectionsPerHost = Self.parts + 2
        configuration.waitsForConnectivity = true
        let created = URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
        lock.lock()
        backgroundSession = created
        lock.unlock()
        return created
    }

    /// Keeps the completion handler iOS hands the app when it wakes it for a
    /// transfer, and makes sure the session exists to deliver the events to it.
    func hold(_ handler: @escaping () -> Void) {
        lock.lock()
        resumeHandler = handler
        lock.unlock()
        _ = session()
    }

    /// The transfers iOS still has for these jobs, by their stable key.
    func liveKeys() async -> Set<String> {
        let tasks = await session().allTasks
        var keys = Set<String>()
        for task in tasks {
            if let key = Self.key(from: task.taskDescription) { keys.insert(key) }
        }
        return keys
    }

    /// Reads the job key out of a task's own description, which is what survives
    /// a relaunch while the task itself travels on.
    static func key(from description: String?) -> String? {
        guard let description else { return nil }
        let pieces = description.split(separator: "|")
        guard pieces.count == 3 else { return nil }
        return String(pieces[0])
    }

    private func tag(_ jobKey: String, _ index: Int) -> String {
        "\(jobKey)|\(index)|\(Self.parts)"
    }

    private func bounds(_ index: Int, total: Int64) -> (from: Int64, to: Int64) {
        let chunk = max(1, total / Int64(Self.parts))
        let from = Int64(index) * chunk
        let to = index == Self.parts - 1 ? total - 1 : from + chunk - 1
        return (from, to)
    }

    /// Starts the ranges that are missing and waits until every one of them has
    /// landed in the job's folder. Cancelling the waiting task stops the
    /// transfers while keeping the parts that already landed.
    func begin(
        jobKey: String,
        url: URL,
        headers: [String: String],
        directory: URL,
        total: Int64,
        missing: [Int],
        alreadyLanded: Int64,
        report: @escaping (Int64) -> Void
    ) async throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let queue = session()

        // Whatever iOS kept for this job is registered before anything else is
        // planned, so a part already travelling is never requested a second time.
        let live = await queue.allTasks
        var adopted: [Int: URLSessionTask] = [:]
        for task in live {
            guard let description = task.taskDescription,
                  Self.key(from: description) == jobKey else { continue }
            let pieces = description.split(separator: "|")
            guard pieces.count == 3, let index = Int(pieces[1]) else { continue }
            adopted[index] = task
        }

        // The whole registration is one locked step, so an event that arrives in
        // the middle never sees a half-planned job, and no lock is taken inside
        // an asynchronous context.
        lock.withLock {
            wings[jobKey] = Wing(
                total: total,
                baseline: alreadyLanded,
                open: 0,
                failure: nil,
                waiter: nil,
                report: report
            )
            travelling[jobKey] = []
            for (index, task) in adopted {
                let (from, to) = bounds(index, total: total)
                plans[task.taskIdentifier] = Plan(
                    jobKey: jobKey,
                    index: index,
                    expected: to - from + 1,
                    directory: directory,
                    written: 0
                )
                wings[jobKey]?.open += 1
                travelling[jobKey]?.append(task.taskIdentifier)
            }
        }

        for index in missing where adopted[index] == nil {
            let (from, to) = bounds(index, total: total)
            var request = URLRequest(url: url)
            request.setValue("bytes=\(from)-\(to)", forHTTPHeaderField: "Range")
            for (name, value) in headers {
                request.setValue(value, forHTTPHeaderField: name)
            }
            let task = queue.downloadTask(with: request)
            task.taskDescription = tag(jobKey, index)
            let stillOpen = lock.withLock {
                plans[task.taskIdentifier] = Plan(
                    jobKey: jobKey,
                    index: index,
                    expected: to - from + 1,
                    directory: directory,
                    written: 0
                )
                wings[jobKey]?.open += 1
                travelling[jobKey]?.append(task.taskIdentifier)
                return wings[jobKey]?.open ?? 0
            }
            if stillOpen > 0 { task.resume() }
        }

        let left = lock.withLock { wings[jobKey]?.open ?? 0 }

        if left > 0 {
            // A part whose events never arrive — which happens when iOS finishes
            // a transfer in the instant before it is registered here — would
            // leave the job waiting forever. The wait is therefore capped: a
            // transfer that has not finished within four minutes is stopped, and
            // the caller's next attempt plans only the parts really missing.
            let stallGuard = Task {
                try? await Task.sleep(nanoseconds: 240_000_000_000)
                guard !Task.isCancelled else { return }
                self.cancel(jobKey: jobKey)
            }
            defer { stallGuard.cancel() }
            do {
                try await withTaskCancellationHandler {
                    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                        let pending = lock.withLock { () -> (waits: Bool, failure: Error?) in
                            if let wing = wings[jobKey], wing.open > 0 {
                                wings[jobKey]?.waiter = continuation
                                return (true, nil)
                            }
                            return (false, wings[jobKey]?.failure)
                        }
                        if pending.waits { return }
                        if let failure = pending.failure {
                            continuation.resume(throwing: failure)
                        } else {
                            continuation.resume()
                        }
                    }
                } onCancel: {
                    self.cancel(jobKey: jobKey)
                }
            } catch {
                lock.withLock {
                    wings[jobKey] = nil
                    travelling[jobKey] = nil
                }
                throw error
            }
        }

        let failure = lock.withLock { () -> Error? in
            let failure = wings[jobKey]?.failure
            wings[jobKey] = nil
            travelling[jobKey] = nil
            return failure
        }
        if let failure { throw failure }
    }

    /// Stops this job's transfers, keeping whatever parts already landed so the
    /// transfer continues from them the next time it is started.
    func cancel(jobKey: String) {
        let queue = session()
        lock.lock()
        let identifiers = travelling[jobKey] ?? []
        travelling[jobKey] = nil
        for identifier in identifiers { plans[identifier] = nil }
        let waiter = wings[jobKey]?.waiter
        wings[jobKey] = nil
        lock.unlock()
        queue.getTasksWithCompletionHandler { tasks, _, _ in
            for task in tasks where identifiers.contains(task.taskIdentifier) {
                task.cancel()
            }
        }
        waiter?.resume(throwing: CancellationError())
    }

    // MARK: - URLSessionDownloadDelegate

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        lock.lock()
        guard var plan = plans[downloadTask.taskIdentifier] else {
            lock.unlock()
            return
        }
        plan.written = totalBytesWritten
        plans[downloadTask.taskIdentifier] = plan
        var landed: Int64 = 0
        for (_, entry) in plans where entry.jobKey == plan.jobKey {
            landed += entry.written
        }
        guard let wing = wings[plan.jobKey] else {
            lock.unlock()
            return
        }
        let value = min(wing.total, wing.baseline + landed)
        let report = wing.report
        lock.unlock()
        report?(value)
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        lock.lock()
        guard let plan = plans[downloadTask.taskIdentifier] else {
            lock.unlock()
            return
        }
        lock.unlock()
        let target = plan.directory.appendingPathComponent("part-\(plan.index)")
        do {
            try FileManager.default.createDirectory(
                at: plan.directory,
                withIntermediateDirectories: true
            )
            if FileManager.default.fileExists(atPath: target.path) {
                try FileManager.default.removeItem(at: target)
            }
            // The system's temporary file is gone the moment this returns, so the
            // part is moved before anything else happens.
            try FileManager.default.moveItem(at: location, to: target)
        } catch {
            note(plan.jobKey, error: error)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        guard let plan = plans.removeValue(forKey: task.taskIdentifier) else {
            lock.unlock()
            return
        }
        travelling[plan.jobKey]?.removeAll { $0 == task.taskIdentifier }
        if let error, let wing = wings[plan.jobKey] {
            wings[plan.jobKey]?.failure = wing.failure ?? error
        }
        if let wing = wings[plan.jobKey] {
            wings[plan.jobKey]?.open = max(0, wing.open - 1)
        }
        var waiter: CheckedContinuation<Void, Error>?
        var failure: Error?
        if let wing = wings[plan.jobKey], wing.open == 0 {
            waiter = wing.waiter
            failure = wing.failure
            wings[plan.jobKey]?.waiter = nil
        }
        lock.unlock()
        if let waiter {
            if let failure {
                waiter.resume(throwing: failure)
            } else {
                waiter.resume()
            }
        }
    }

    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        lock.lock()
        let handler = resumeHandler
        resumeHandler = nil
        lock.unlock()
        handler?()
    }

    private func note(_ jobKey: String, error: Error) {
        lock.lock()
        if let wing = wings[jobKey] {
            wings[jobKey]?.failure = wing.failure ?? error
        }
        lock.unlock()
    }
}

/// Downloads a link to the device over several parallel range requests, which is
/// the fastest free way to pull a file: most servers cap a single connection, so
/// four ranges together finish far sooner than one stream. The transfers run on
/// the background session, so closing the app no longer stops them.
@MainActor
final class DownloadManager: ObservableObject {
    static let shared = DownloadManager()

    @Published private(set) var jobs: [DownloadJob] = []
    /// Bytes per second for the transfers that are moving.
    @Published private(set) var speeds: [String: Int64] = [:]

    private var lastSample: [String: (date: Date, bytes: Int64)] = [:]
    private var runners: [String: Task<Void, Never>] = [:]
    /// How many times a stopped transfer is tried again before it is marked failed.
    private let maxRetries = 2
    private let storageKey = "cinemax.downloads.v1"

    private init() {
        let interrupted = load()
        reattach(interrupted)
    }

    /// Picks back up what iOS kept downloading while the app was closed: a job
    /// iOS is still fetching carries on from the parts it has, and one whose
    /// parts all landed is joined and finished as soon as the app opens.
    private func reattach(_ candidates: [String]) {
        guard !candidates.isEmpty else { return }
        Task { @MainActor in
            let live = await BackgroundTransfers.shared.liveKeys()
            var changed = false
            for id in candidates {
                guard let job = jobs.first(where: { $0.id == id }) else { continue }
                if live.contains(CinemaHash.stable(id)) {
                    update(id) { $0.state = "running" }
                    launch(job)
                    EventLog.shared.add("download", "استئناف \(job.title) بعد إغلاق التطبيق")
                    changed = true
                } else if partsComplete(job) {
                    update(id) { $0.state = "running" }
                    launch(job)
                    changed = true
                }
            }
            if changed { save() }
            // A transfer that was waiting its turn when the app closed starts
            // here, as soon as the room the limit leaves is known: without this
            // a queued file waited for a tap on resume that nobody makes.
            pump()
        }
    }

    /// True when every part of the file already landed, so only joining is left.
    private func partsComplete(_ job: DownloadJob) -> Bool {
        guard job.total > 1_048_576 else { return false }
        let directory = partsDirectory(for: job.id)
        let parts = BackgroundTransfers.parts
        let chunk = max(1, job.total / Int64(parts))
        for index in 0..<parts {
            let from = Int64(index) * chunk
            let to = index == parts - 1 ? job.total - 1 : from + chunk - 1
            let part = directory.appendingPathComponent("part-\(index)")
            let size = (try? part.resourceValues(forKeys: [.fileSizeKey]))?.fileSize ?? 0
            if Int64(size) != to - from + 1 { return false }
        }
        return true
    }

    func isRunning(_ url: String) -> Bool {
        jobs.contains { $0.id == url && $0.state == "running" }
    }

    private var activeCount: Int {
        jobs.filter { $0.state == "running" || $0.state == "retrying" }.count
    }

    /// Starts whatever waited its turn, up to the limit the viewer picked, so a
    /// queue of episodes finishes two by two instead of fighting for the link.
    func pump() {
        var active = activeCount
        for job in jobs where job.state == "queued" {
            guard active < CinemaxPrefs.downloadLimit else { break }
            update(job.id) { entry in entry.state = "running" }
            guard let fresh = jobs.first(where: { $0.id == job.id }) else { continue }
            launch(fresh)
            active += 1
        }
        save()
    }

    func start(title: String, urlString: String, headers: [String: String] = [:]) {
        guard URL(string: urlString) != nil else { return }
        if jobs.contains(where: { $0.id == urlString && $0.state == "running" }) { return }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
        let job = DownloadJob(
            id: urlString,
            title: title,
            url: urlString,
            headers: headers.isEmpty ? nil : headers,
            received: 0,
            total: 0,
            state: "running",
            filePath: nil
        )
        // A fresh download of the same link replaces the old entry instead of
        // leaving a second row for one file, and the file it wrote is removed so
        // two copies never sit on the device.
        for existing in jobs where existing.id == urlString {
            BackgroundTransfers.shared.cancel(jobKey: CinemaHash.stable(existing.id))
            runners[existing.id]?.cancel()
            runners[existing.id] = nil
            if let path = existing.filePath {
                try? FileManager.default.removeItem(atPath: path)
            }
        }
        jobs.removeAll { $0.id == urlString }
        jobs.insert(job, at: 0)
        save()
        // A transfer waits its turn when the limit is already busy, instead of
        // splitting the connection the running ones need. The new job is not
        // counted against itself.
        let others = jobs.filter {
            $0.id != job.id && ($0.state == "running" || $0.state == "retrying")
        }.count
        if others >= CinemaxPrefs.downloadLimit {
            update(job.id) { entry in entry.state = "queued" }
            save()
            EventLog.shared.add("download", "في الانتظار \(title)")
            return
        }
        launch(job)
        save()
    }

    /// Stops the transfer but keeps the parts that already landed, so it can go on.
    func pause(_ job: DownloadJob) {
        runners[job.id]?.cancel()
        runners[job.id] = nil
        speeds[job.id] = nil
        // The background session is told directly too: a transfer iOS is running
        // for this job must not keep pulling bytes the viewer stopped.
        BackgroundTransfers.shared.cancel(jobKey: CinemaHash.stable(job.id))
        update(job.id) { entry in
            if entry.state == "running" || entry.state == "retrying" { entry.state = "paused" }
        }
        save()
        pump()
    }

    /// Picks a paused or failed transfer up from the parts already on disk, and
    /// queues it behind the ones already moving.
    func resume(_ job: DownloadJob) {
        guard jobs.contains(where: { $0.id == job.id }), runners[job.id] == nil else { return }
        update(job.id) { entry in
            entry.state = "queued"
        }
        save()
        pump()
    }

    func isPaused(_ url: String) -> Bool {
        jobs.contains { $0.id == url && $0.state == "paused" }
    }

    private func launch(_ job: DownloadJob) {
        let id = job.id
        let title = job.title
        let urlString = job.url
        let headers = job.headers ?? [:]
        runners[id] = Task { [weak self] in
            await self?.run(id: id, title: title, urlString: urlString, headers: headers)
        }
    }

    func cancel(_ job: DownloadJob) {
        runners[job.id]?.cancel()
        runners[job.id] = nil
        speeds[job.id] = nil
        BackgroundTransfers.shared.cancel(jobKey: CinemaHash.stable(job.id))
        update(job.id) { entry in
            entry.state = "cancelled"
        }
        save()
        pump()
    }

    func remove(_ job: DownloadJob) {
        runners[job.id]?.cancel()
        runners[job.id] = nil
        speeds[job.id] = nil
        BackgroundTransfers.shared.cancel(jobKey: CinemaHash.stable(job.id))
        if let path = job.filePath {
            try? FileManager.default.removeItem(atPath: path)
        }
        try? FileManager.default.removeItem(at: partsDirectory(for: job.id))
        jobs.removeAll { $0.id == job.id }
        save()
        pump()
    }

    /// A folder per transfer, named from a stable hash so a paused download finds
    /// its parts again after the app is closed and opened.
    private func partsDirectory(for id: String) -> URL {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let root = caches.appendingPathComponent("CINEMAX-parts", isDirectory: true)
        return root.appendingPathComponent(CinemaHash.stable(id), isDirectory: true)
    }

    /// Measures how fast the transfer moves, for the speed and the remaining time.
    private func sample(_ id: String) {
        guard let job = jobs.first(where: { $0.id == id }), job.state == "running" else { return }
        let now = Date()
        guard let previous = lastSample[id] else {
            lastSample[id] = (now, job.received)
            return
        }
        let seconds = now.timeIntervalSince(previous.date)
        guard seconds > 0.3 else { return }
        let instant = max(0, Double(job.received - previous.bytes) / seconds)
        // A little smoothing keeps the number readable instead of jumping.
        let smoothed = speeds[id].map { Int64(Double($0) * 0.6 + instant * 0.4) } ?? Int64(instant)
        speeds[id] = max(0, smoothed)
        lastSample[id] = (now, job.received)
    }

    private var folder: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("CINEMAX", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func run(id: String, title: String, urlString: String, headers: [String: String]) async {
        guard let url = URL(string: urlString) else { return }
        lastSample[id] = (Date(), jobs.first { $0.id == id }?.received ?? 0)
        let sampler = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                guard !Task.isCancelled else { break }
                self?.sample(id)
            }
        }
        let safeName = title
            .replacingOccurrences(of: "/", with: "-")
            .replacingOccurrences(of: ":", with: "-")
        let ext = url.pathExtension.isEmpty ? "mp4" : url.pathExtension
        let destination = folder.appendingPathComponent("\(safeName).\(ext)")

        var attempt = 0
        var finished = false
        EventLog.shared.add("download", "بدأ \(title)")
        while !finished {
            do {
                let total = try await contentLength(url, headers: headers)
                update(id) { entry in entry.total = total }
                if total > 1_048_576 {
                    try await downloadInParts(
                        url: url,
                        total: total,
                        destination: destination,
                        id: id,
                        headers: headers
                    )
                } else {
                    try await downloadWhole(
                        url: url,
                        destination: destination,
                        id: id,
                        headers: headers
                    )
                }
                update(id) { entry in
                    entry.state = "done"
                    entry.received = entry.total
                    entry.filePath = destination.path
                }
                EventLog.shared.add("download", "اكتمل \(title)")
                finished = true
            } catch is CancellationError {
                pauseState(id)
                finished = true
            } catch {
                // URLSession reports a cancelled transfer as its own error rather than
                // as a CancellationError, so a pause is recognised here too.
                if Task.isCancelled {
                    pauseState(id)
                    finished = true
                } else {
                    attempt += 1
                    if attempt > maxRetries {
                        update(id) { entry in entry.state = "failed" }
                        EventLog.shared.add(
                            "download",
                            "فشل \(title) بعد \(attempt) محاولة: \(error.localizedDescription)"
                        )
                        finished = true
                    } else {
                        // A dropped connection is the usual reason a transfer stops, so
                        // it is tried again with a short wait before it is given up on.
                        update(id) { entry in entry.state = "retrying" }
                        speeds[id] = nil
                        lastSample[id] = nil
                        save()
                        EventLog.shared.add("download", "إعادة محاولة \(attempt) لـ \(title)")
                        let seconds = attempt == 1 ? 2 : 6
                        try? await Task.sleep(nanoseconds: UInt64(seconds) * 1_000_000_000)
                        if Task.isCancelled {
                            pauseState(id)
                            finished = true
                        } else {
                            update(id) { entry in entry.state = "running" }
                            save()
                        }
                    }
                }
            }
        }
        speeds[id] = nil
        lastSample[id] = nil
        save()
        if jobs.first(where: { $0.id == id })?.state == "done" {
            notify(title: title)
        }
        sampler.cancel()
        pump()
    }

    /// Marks the transfer as paused rather than failed, keeping its parts on disk.
    private func pauseState(_ id: String) {
        update(id) { entry in
            if entry.state == "running" || entry.state == "retrying" {
                entry.state = "paused"
            }
        }
        save()
    }

    /// Tells the viewer when a file landed, without leaving the app in front.
    private func notify(title: String) {
        guard CinemaxPrefs.notifications else { return }
        let content = UNMutableNotificationContent()
        content.title = "اكتمل التنزيل"
        content.body = title
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { _ in }
    }

    private func contentLength(_ url: URL, headers: [String: String]) async throws -> Int64 {
        var head = URLRequest(url: url)
        head.httpMethod = "HEAD"
        head.timeoutInterval = 20
        for (name, value) in headers {
            head.setValue(value, forHTTPHeaderField: name)
        }
        if let (_, answer) = try? await URLSession.shared.data(for: head),
           let http = answer as? HTTPURLResponse,
           http.statusCode < 400,
           http.expectedContentLength > 0 {
            return http.expectedContentLength
        }
        // A host that refuses HEAD still states the size of the file in its answer
        // to a single byte, so the download does not stop on the method.
        var ranged = URLRequest(url: url)
        ranged.setValue("bytes=0-0", forHTTPHeaderField: "Range")
        ranged.timeoutInterval = 20
        for (name, value) in headers {
            ranged.setValue(value, forHTTPHeaderField: name)
        }
        let (_, response) = try await URLSession.shared.data(for: ranged)
        guard let http = response as? HTTPURLResponse, http.statusCode < 400 else {
            throw CinemaError(message: "تعذر قراءة حجم الملف")
        }
        if let header = http.value(forHTTPHeaderField: "Content-Range"),
           let raw = header.split(separator: "/").last,
           let total = Int64(raw.trimmingCharacters(in: .whitespaces)), total > 0 {
            return total
        }
        return max(0, http.expectedContentLength)
    }

    /// Cuts the file into four ranges that travel on the background session, so
    /// closing the app no longer stops the transfer. The parts live in the job's
    /// own folder, so a paused or failed transfer continues from what already
    /// landed instead of starting the whole file again.
    private func downloadInParts(
        url: URL,
        total: Int64,
        destination: URL,
        id: String,
        headers: [String: String]
    ) async throws {
        let parts = BackgroundTransfers.parts
        let directory = partsDirectory(for: id)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)

        func partURL(_ index: Int) -> URL { directory.appendingPathComponent("part-\(index)") }

        func bounds(_ index: Int) -> (from: Int64, to: Int64) {
            let chunk = max(1, total / Int64(parts))
            let from = Int64(index) * chunk
            let to = index == parts - 1 ? total - 1 : from + chunk - 1
            return (from, to)
        }

        func expected(_ index: Int) -> Int64 {
            let (from, to) = bounds(index)
            return to - from + 1
        }

        func onDisk(_ index: Int) -> Int64 {
            let values = try? partURL(index).resourceValues(forKeys: [.fileSizeKey])
            return Int64(values?.fileSize ?? 0)
        }

        // Whatever a previous run left behind counts before anything is requested.
        var present: Int64 = 0
        var missing: [Int] = []
        for index in 0..<parts {
            if onDisk(index) == expected(index) {
                present += expected(index)
            } else {
                missing.append(index)
            }
        }
        if present > 0 {
            update(id) { entry in entry.received = min(entry.total, present) }
        }

        if !missing.isEmpty {
            try await BackgroundTransfers.shared.begin(
                jobKey: CinemaHash.stable(id),
                url: url,
                headers: headers,
                directory: directory,
                total: total,
                missing: missing,
                alreadyLanded: present,
                report: { [weak self] value in
                    Task { @MainActor in
                        self?.update(id) { entry in entry.received = min(entry.total, value) }
                    }
                }
            )
        }

        // One part at a time into the final file, in chunks, keeps memory flat even
        // for a file far larger than the app's budget.
        if FileManager.default.fileExists(atPath: destination.path) {
            try FileManager.default.removeItem(at: destination)
        }
        FileManager.default.createFile(atPath: destination.path, contents: nil)
        let handle = try FileHandle(forWritingTo: destination)
        for index in 0..<parts {
            let source = partURL(index)
            guard FileManager.default.fileExists(atPath: source.path) else {
                throw CinemaError(message: "جزء من الملف مفقود، أعد التنزيل")
            }
            let reader = try FileHandle(forReadingFrom: source)
            while true {
                let data = reader.readData(ofLength: 1_048_576)
                if data.isEmpty { break }
                try handle.write(contentsOf: data)
            }
            try reader.close()
            try FileManager.default.removeItem(at: source)
        }
        try handle.close()
        try? FileManager.default.removeItem(at: directory)
    }

    private func downloadWhole(
        url: URL,
        destination: URL,
        id: String,
        headers: [String: String]
    ) async throws {
        var request = URLRequest(url: url)
        request.timeoutInterval = 60
        for (name, value) in headers {
            request.setValue(value, forHTTPHeaderField: name)
        }
        let (tmp, response) = try await URLSession.shared.download(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode < 400 else {
            throw CinemaError(message: "تعذر تنزيل الملف")
        }
        if FileManager.default.fileExists(atPath: destination.path) {
            try FileManager.default.removeItem(at: destination)
        }
        try FileManager.default.moveItem(at: tmp, to: destination)
        update(id) { entry in
            entry.received = entry.total
        }
    }

    private func update(_ id: String, change: (inout DownloadJob) -> Void) {
        guard let index = jobs.firstIndex(where: { $0.id == id }) else { return }
        var job = jobs[index]
        change(&job)
        jobs[index] = job
    }

    private func save() {
        if let data = try? JSONEncoder().encode(jobs) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }

    /// Restores the transfers, and answers the ids that were still moving when the
    /// app went away, so the ones iOS kept alive are picked back up instead of
    /// waiting for the viewer to find the resume button.
    @discardableResult
    private func load() -> [String] {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let stored = try? JSONDecoder().decode([DownloadJob].self, from: data) else {
            return []
        }
        let moving = stored.filter { $0.state == "running" || $0.state == "retrying" }
        // Anything that was moving waits as paused until the background session
        // says whether iOS is still fetching it or the parts are all on disk.
        jobs = stored.map { job in
            var copy = job
            if copy.state == "running" || copy.state == "retrying" { copy.state = "paused" }
            return copy
        }
        return moving.map(\.id)
    }
}

/// The downloads tab inside the library.
struct DownloadsView: View {
    @ObservedObject private var manager = DownloadManager.shared
    @State private var playing: CinemaItem?
    @State private var filter = "all"
    @State private var sort = "newest"
    @State private var limit = CinemaxPrefs.downloadLimit

    private var jobs: [DownloadJob] {
        let filtered: [DownloadJob]
        switch filter {
        case "running":
            filtered = manager.jobs.filter { $0.state == "running" || $0.state == "retrying" }
        case "queued": filtered = manager.jobs.filter { $0.state == "queued" }
        case "paused": filtered = manager.jobs.filter { $0.state == "paused" || $0.state == "cancelled" }
        case "done": filtered = manager.jobs.filter { $0.state == "done" }
        default: filtered = manager.jobs
        }
        switch sort {
        case "size": return filtered.sorted { $0.total > $1.total }
        case "name": return filtered.sorted { $0.title.localizedCompare($1.title) == .orderedAscending }
        default: return filtered
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if manager.jobs.isEmpty {
                VStack(spacing: 8) {
                    Text("لا تنزيلات بعد")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(.white)
                    Text("من صفحة أي عمل، اضغط مطوّلًا على المصدر واختر «تنزيل» ليُحفظ على الجهاز.")
                        .font(.system(size: 13))
                        .foregroundStyle(CinemaTheme.muted)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 50)
                .padding(.horizontal, 30)
            } else {
                HStack(spacing: 8) {
                    ForEach(
                        [("all", "الكل"), ("running", "الجارية"), ("queued", "في الانتظار"), ("paused", "المتوقفة"), ("done", "المكتملة")],
                        id: \.0
                    ) { entry in
                        Button {
                            Haptics.select()
                            withAnimation(.easeInOut(duration: 0.15)) { filter = entry.0 }
                        } label: {
                            Text(entry.1)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(filter == entry.0 ? .black : .white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background {
                                    Capsule().fill(
                                        filter == entry.0 ? CinemaTheme.accent : Color.white.opacity(0.08)
                                    )
                                }
                        }
                        .buttonStyle(.plain)
                    }
                    Spacer()
                }

                HStack {
                    Text("\(manager.jobs.count) ملفًا")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                    Spacer()
                    Text(totalLabel)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(CinemaTheme.muted)
                    Menu {
                        ForEach([1, 2, 3, 4], id: \.self) { value in
                            Button {
                                Haptics.select()
                                limit = value
                                CinemaxPrefs.setDownloadLimit(value)
                                manager.pump()
                            } label: {
                                Text(value == 1 ? "تنزيل واحد معًا" : "\(value) تنزيلات معًا")
                            }
                        }
                    } label: {
                        HStack(spacing: 3) {
                            Image(systemName: "gauge.with.dots.needle.33percent")
                                .font(.system(size: 11, weight: .semibold))
                            Text("\(limit)")
                                .font(.system(size: 11, weight: .semibold))
                        }
                        .foregroundStyle(CinemaTheme.muted)
                    }
                    Menu {
                        Button("الأحدث") { sort = "newest" }
                        Button("الأكبر حجمًا") { sort = "size" }
                        Button("الاسم") { sort = "name" }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.accent)
                    }
                    Menu {
                        Button {
                            Haptics.select()
                            for job in manager.jobs where job.state == "running" {
                                manager.pause(job)
                            }
                        } label: {
                            Label("إيقاف الكل", systemImage: "pause.circle")
                        }
                        Button {
                            Haptics.select()
                            for job in manager.jobs
                            where job.state == "paused" || job.state == "failed" || job.state == "cancelled" {
                                manager.resume(job)
                            }
                        } label: {
                            Label("استكمال الكل", systemImage: "play.circle")
                        }
                        Button {
                            Haptics.select()
                            for job in manager.jobs where job.state == "done" {
                                manager.remove(job)
                            }
                        } label: {
                            Label("مسح المكتملة", systemImage: "checkmark.circle")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                    Button {
                        Haptics.tap()
                        for job in manager.jobs { manager.remove(job) }
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(CinemaTheme.favourite)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.bottom, 2)

                ForEach(jobs) { job in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text(job.title)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                            Spacer()
                            Text(statusLabel(job))
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(job.state == "failed" ? CinemaTheme.favourite : CinemaTheme.accent)
                        }
                        ProgressView(value: job.progress)
                            .tint(CinemaTheme.accent)
                        HStack(spacing: 12) {
                            Text(progressText(job))
                                .font(.system(size: 10))
                                .foregroundStyle(CinemaTheme.muted)
                            if job.state == "running" {
                                Text(speedText(job))
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                                Text(etaText(job))
                                    .font(.system(size: 10))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                            Spacer()
                            if job.state == "running" {
                                Button("إيقاف مؤقت") {
                                    Haptics.tap()
                                    manager.pause(job)
                                }
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(.white)
                            }
                            if job.state == "paused" || job.state == "failed" || job.state == "cancelled" {
                                Button("استئناف") {
                                    Haptics.tap()
                                    manager.resume(job)
                                }
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(CinemaTheme.accent)
                            }
                            if job.state == "done", let path = job.filePath {
                                if FileManager.default.fileExists(atPath: path) {
                                    Button("تشغيل") {
                                        Haptics.tap()
                                        // A local file is handed to mpv as a file URL, not
                                        // as a bare path, so a name with spaces still opens.
                                        playing = CinemaItem(
                                            id: URL(fileURLWithPath: path).absoluteString,
                                            type: "movie",
                                            name: job.title,
                                            poster: nil,
                                            background: nil,
                                            releaseInfo: nil,
                                            rating: nil,
                                            overview: nil
                                        )
                                    }
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(CinemaTheme.accent)
                                } else {
                                    Text("الملف مفقود")
                                        .font(.system(size: 10, weight: .semibold))
                                        .foregroundStyle(CinemaTheme.favourite)
                                }
                            }
                            Button {
                                manager.remove(job)
                            } label: {
                                Image(systemName: "trash")
                                    .font(.system(size: 12))
                                    .foregroundStyle(CinemaTheme.favourite)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(14)
                    .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
        }
        .padding(.horizontal, 20)
        .fullScreenCover(item: $playing) { item in
            PlayerScreen(title: item.name, url: item.id)
        }
    }

    private var totalLabel: String {
        let jobs = manager.jobs
        let total = jobs.reduce(Int64(0)) { $0 + $1.total }
        let running = jobs.filter { $0.state == "running" }.count
        let waiting = jobs.filter { $0.state == "queued" }.count
        let size = String(format: "%.0f MB", Double(total) / 1_048_576)
        if running > 0 && waiting > 0 { return "\(size) · \(running) جارٍ · \(waiting) منتظر" }
        if waiting > 0 { return "\(size) · \(waiting) منتظر" }
        return running > 0 ? "\(size) · \(running) جارٍ" : size
    }

    private func statusLabel(_ job: DownloadJob) -> String {
        switch job.state {
        case "done": return "مكتمل"
        case "failed": return "فشل"
        case "paused": return "متوقف"
        case "retrying": return "إعادة المحاولة…"
        case "queued": return "في الانتظار"
        case "cancelled": return "ملغى"
        default: return "\(Int(job.progress * 100))٪"
        }
    }

    /// How much of the file already landed, in the units a viewer reads.
    private func progressText(_ job: DownloadJob) -> String {
        guard job.total > 0 else { return job.sizeLabel }
        let done = Double(job.received) / 1_048_576
        let total = Double(job.total) / 1_048_576
        return String(format: "%.0f من %.0f MB", done, total)
    }

    private func speedText(_ job: DownloadJob) -> String {
        guard let speed = manager.speeds[job.id], speed > 0 else { return "" }
        let megabytes = Double(speed) / 1_048_576
        if megabytes >= 1 { return String(format: "%.1f MB/ث", megabytes) }
        return String(format: "%.0f KB/ث", Double(speed) / 1024)
    }

    private func etaText(_ job: DownloadJob) -> String {
        guard let speed = manager.speeds[job.id], speed > 0, job.total > job.received else { return "" }
        let seconds = Int(Double(job.total - job.received) / Double(speed))
        if seconds < 60 { return "بقي \(seconds) ثانية" }
        let minutes = seconds / 60
        if minutes < 60 { return "بقي \(minutes) دقيقة" }
        return "بقي \(minutes / 60) ساعة و\(minutes % 60) دقيقة"
    }
}
