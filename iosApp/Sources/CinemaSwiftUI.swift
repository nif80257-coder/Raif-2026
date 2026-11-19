import SwiftUI
import UIKit
import CinemaxKit

// MARK: - Theme

/// The house palette: an OLED-black canvas, one orchid accent, and glass surfaces
/// drawn with the system's own materials so they match iOS everywhere.
enum CinemaTheme {
    static let canvas = Color.black
    static let surface = Color(red: 0.067, green: 0.067, blue: 0.075)
    static let elevated = Color(red: 0.106, green: 0.106, blue: 0.118)
    static let accent = Color(red: 0.757, green: 0.451, blue: 0.902)
    static let favourite = Color(red: 1.0, green: 0.357, blue: 0.557)
    static let muted = Color(white: 0.58)
    static let hairline = Color.white.opacity(0.08)
    static let posterRadius: CGFloat = 14
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
    static func tap() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
    static func select() {
        UISelectionFeedbackGenerator().selectionChanged()
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
struct StreamsPayload: Codable { let streams: [StreamChoice] }

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
}

struct StreamChoice: Identifiable, Codable, Hashable {
    let url: String
    let addon: String
    let title: String
    let quality: String?

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
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[StreamChoice], Error>) in
            bridge.loadStreams(itemId: id, type: type, onDone: { json in
                do {
                    continuation.resume(returning: try self.decode(StreamsPayload.self, json).streams)
                } catch {
                    continuation.resume(throwing: error)
                }
            }, onError: { message in
                continuation.resume(throwing: CinemaError(message: message))
            })
        }
    }

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

    func toggleFavorite(_ item: CinemaItem) -> Bool {
        guard let data = try? JSONEncoder().encode(item), let json = String(data: data, encoding: .utf8) else {
            return false
        }
        var result = false
        let semaphore = DispatchSemaphore(value: 0)
        bridge.toggleFavorite(itemJson: json, onDone: { state in
            result = state.boolValue
            semaphore.signal()
        })
        _ = semaphore.wait(timeout: .now() + 1)
        return result
    }

    func useClassicInterface() {
        bridge.switchInterface(classic: true)
    }
}

// MARK: - Images

/// A small memory cache in front of URLSession: no third-party loader, and the
/// same image is never downloaded twice while scrolling.
final class CinemaImageStore {
    static let shared = CinemaImageStore()
    private let cache = NSCache<NSURL, UIImage>()
    private let session = URLSession(configuration: .default)

    init() {
        cache.countLimit = 160
    }

    func cached(_ url: URL) -> UIImage? {
        cache.object(forKey: url as NSURL)
    }

    func image(for url: URL) async -> UIImage? {
        if let cached = cached(url) { return cached }
        do {
            let (data, _) = try await session.data(from: url)
            guard let image = UIImage(data: data) else { return nil }
            cache.setObject(image, forKey: url as NSURL)
            return image
        } catch {
            return nil
        }
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
            image = await CinemaImageStore.shared.image(for: url)
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
            .animation(.easeInOut(duration: 1.1), value: palette)
            .onAppear {
                withAnimation(.easeInOut(duration: 1.6)) { appear = true }
            }
        }
        .allowsHitTesting(false)
    }
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

    @State private var index = 0

    var body: some View {
        TabView(selection: $index) {
            ForEach(Array(items.prefix(6).enumerated()), id: \.element.id) { offset, item in
                HeroSlide(item: item, onOpen: { onOpen(item) }, onPalette: onPalette)
                    .tag(offset)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .frame(height: 440)
        .onChange(of: index) { value in
            guard items.indices.contains(value) else { return }
            Task {
                let colors = await PaletteSampler.colors(for: items[value].posterURL)
                if !colors.isEmpty { onPalette(colors) }
            }
        }
    }
}

private struct HeroSlide: View {
    let item: CinemaItem
    let onOpen: () -> Void
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
                        Image(systemName: tab.icon)
                            .font(.system(size: 17, weight: .semibold))
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
    @Published var shelves: [Shelf] = []
    @Published var continueWatching: [ProgressItem] = []
    @Published var favorites: Set<String> = []
    @Published var loading = true
    @Published var error: String?
    @Published var filter = "all"

    func load() async {
        loading = true
        error = nil
        do {
            async let shelvesTask = CinemaAPI.shared.shelves()
            async let progressTask = CinemaAPI.shared.progress()
            async let favoritesTask = CinemaAPI.shared.favorites()
            let loaded = try await shelvesTask
            continueWatching = await progressTask
            favorites = Set(await favoritesTask.map(\.id))
            shelves = loaded
            if loaded.isEmpty {
                error = "لا كتالوجات بعد. افتح الإعدادات ← الواجهة الكلاسيكية وأضف إضافة، أو ثبّت المصادر الجاهزة."
            }
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    func toggle(_ item: CinemaItem) {
        if CinemaAPI.shared.toggleFavorite(item) {
            favorites.insert(item.id)
        } else {
            favorites.remove(item.id)
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
        switch filter {
        case "movie": return shelves.filter { $0.items.contains { $0.type == "movie" } }
        case "series": return shelves.filter { $0.items.contains { $0.type == "series" } }
        case "channel": return shelves.filter { $0.items.contains { $0.type == "channel" || $0.type == "tv" } }
        default: return shelves
        }
    }
}

struct HomeView: View {
    @Binding var selection: Int
    @StateObject private var model = HomeModel()
    @State private var glow: [Color] = [CinemaTheme.accent]
    @State private var detailItem: CinemaItem?

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
                } else {
                    HeroCarousel(items: model.heroItems, onOpen: { detailItem = $0 }, onPalette: { glow = $0 })
                        .frame(height: 440)
                    if !model.continueWatching.isEmpty {
                        SectionHeader(title: "أكمل المشاهدة")
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(spacing: 12) {
                                ForEach(model.continueWatching) { entry in
                                    ContinueCard(entry: entry) { detailItem = entry.item }
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    }
                    ForEach(model.visibleShelves) { shelf in
                        SectionHeader(title: shelf.title)
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(alignment: .top, spacing: 12) {
                                ForEach(shelf.items) { item in
                                    PosterCard(
                                        item: item,
                                        progress: model.continueWatching.first { $0.item.id == item.id }?.fraction ?? 0,
                                        saved: model.favorites.contains(item.id),
                                        onOpen: { detailItem = item }
                                    )
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
        .refreshable { await model.load() }
        .task { await model.load() }
        .fullScreenCover(item: $detailItem) { item in
            DetailView(item: item, onFavorite: { model.toggle(item) })
        }
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text("CINEMAX")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(3)
                    .foregroundStyle(CinemaTheme.muted)
                Text("الرئيسية")
                    .font(.system(size: 34, weight: .heavy))
                    .foregroundStyle(.white)
            }
            Spacer()
            Text(CinemaAPI.shared.versionLabel)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(CinemaTheme.muted)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(.ultraThinMaterial, in: Capsule())
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 14)
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
    }
}

// MARK: - Player

/// The libmpv surface as a SwiftUI view. The same controller the classic player
/// uses, so MKV, HEVC, AVI and embedded subtitle tracks all work here too.
struct MPVSurface: UIViewControllerRepresentable {
    let url: String
    @Binding var controller: MPVViewController?

    func makeUIViewController(context: Context) -> MPVViewController {
        let player = MPVViewController()
        player.load(urlString: url, headersJson: "{}", startMs: 0)
        DispatchQueue.main.async { controller = player }
        return player
    }

    func updateUIViewController(_ uiViewController: MPVViewController, context: Context) {}
}

struct PlayerScreen: View {
    let title: String
    let url: String

    @Environment(\.dismiss) private var dismiss
    @State private var player: MPVViewController?
    @State private var playing = true
    @State private var position: Double = 0
    @State private var duration: Double = 0
    @State private var ticker: Timer?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            MPVSurface(url: url, controller: $player)
                .ignoresSafeArea()
            VStack {
                HStack(spacing: 12) {
                    CircleButton(system: "xmark") { dismiss() }
                    Text(title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)

                Spacer()

                VStack(spacing: 10) {
                    Slider(
                        value: Binding(
                            get: { position },
                            set: { value in
                                position = value
                                player?.seekTo(Int64(value * 1000))
                            }
                        ),
                        in: 0...max(1, duration)
                    )
                    .tint(CinemaTheme.accent)

                    HStack {
                        Button {
                            playing.toggle()
                            if playing { player?.playPlayback() } else { player?.pausePlayback() }
                        } label: {
                            Image(systemName: playing ? "pause.fill" : "play.fill")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 44, height: 44)
                        }
                        Spacer()
                        Text("\(clock(position)) / \(clock(duration))")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(CinemaTheme.muted)
                    }
                }
                .padding(18)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                .padding(.horizontal, 16)
                .padding(.bottom, 18)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear(perform: startTicker)
        .onDisappear { ticker?.invalidate() }
    }

    private func startTicker() {
        ticker?.invalidate()
        ticker = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
            Task { @MainActor in
                guard let player else { return }
                player.refreshState()
                position = Double(player.positionMs) / 1000
                duration = max(1, Double(player.durationMs) / 1000)
                if player.ended { dismiss() }
            }
        }
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

// MARK: - Detail

@MainActor
final class DetailModel: ObservableObject {
    @Published var detail: DetailPayload?
    @Published var loading = true
    @Published var streams: [StreamChoice] = []
    @Published var loadingStreams = false
    @Published var error: String?

    func load(item: CinemaItem) async {
        loading = true
        do {
            detail = try await CinemaAPI.shared.detail(for: item)
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
        await loadStreams(id: item.id, type: item.type)
    }

    func loadStreams(id: String, type: String) async {
        loadingStreams = true
        streams = (try? await CinemaAPI.shared.streams(for: id, type: type)) ?? []
        loadingStreams = false
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

    private var playlist: [CinemaItem] {
        guard let detail = model.detail, !detail.episodes.isEmpty else { return [] }
        guard let season else { return detail.episodes }
        return detail.episodes.filter { $0.id.split(separator: ":").count >= 3 && Int($0.id.split(separator: ":")[1]) == season }
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
                    Spacer(minLength: 60)
                }
            }
            topBar
        }
        .task { await model.load(item: item) }
        .fullScreenCover(item: $playing) { choice in
            PlayerScreen(title: item.name, url: choice.url)
        }
    }

    private var topBar: some View {
        HStack {
            CircleButton(system: "chevron.down") { dismiss() }
            Spacer()
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
                    if let first = model.streams.first { playing = first }
                } label: {
                    Label("مشاهدة", systemImage: "play.fill")
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

            if let genres = model.detail?.genres, !genres.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(genres.prefix(8), id: \.self) { genre in
                            CinemaPill(text: genre, tint: CinemaTheme.muted)
                        }
                    }
                }
            }

            if let overview = model.detail?.item.overview ?? item.overview, !overview.isEmpty {
                Text(overview)
                    .font(.system(size: 14))
                    .lineSpacing(5)
                    .foregroundStyle(.white.opacity(0.9))
                    .lineLimit(6)
            }
        }
        .padding(.horizontal, 20)
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
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(playlist) { entry in
                        Button {
                            Haptics.tap()
                            episode = entry
                            Task {
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
                                Text(entry.name)
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                            }
                            .frame(width: 168)
                        }
                        .buttonStyle(CinemaPressStyle())
                    }
                }
                .padding(.horizontal, 20)
            }
        }
    }

    private var sources: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: episode == nil ? "مصادر المشاهدة" : "مصادر \(episode?.name ?? "")")
            if model.loadingStreams {
                HStack(spacing: 8) {
                    ProgressView().tint(CinemaTheme.accent)
                    Text("جارٍ جلب المصادر…")
                        .font(.system(size: 13))
                        .foregroundStyle(CinemaTheme.muted)
                }
                .padding(.horizontal, 20)
            } else if model.streams.isEmpty {
                Text("لا يوجد رابط مباشر لهذا العنوان من إضافاتك.")
                    .font(.system(size: 13))
                    .foregroundStyle(CinemaTheme.muted)
                    .padding(.horizontal, 20)
            } else {
                VStack(spacing: 8) {
                    ForEach(model.streams) { stream in
                        Button {
                            playing = stream
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
                                if let quality = stream.quality {
                                    CinemaPill(text: quality)
                                }
                                Image(systemName: "play.circle.fill")
                                    .font(.system(size: 22))
                                    .foregroundStyle(CinemaTheme.accent)
                            }
                            .padding(14)
                            .background(CinemaTheme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .strokeBorder(CinemaTheme.hairline, lineWidth: 1)
                            )
                        }
                        .buttonStyle(CinemaPressStyle())
                    }
                }
                .padding(.horizontal, 20)
            }
        }
    }
}

// MARK: - Search

@MainActor
final class SearchModel: ObservableObject {
    @Published var results: [CinemaItem] = []
    @Published var loading = false
    @Published var error: String?

    func run(_ query: String) async {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.count >= 2 else {
            results = []
            return
        }
        loading = true
        error = nil
        do {
            let shelves = try await CinemaAPI.shared.search(text)
            results = shelves.flatMap(\.items)
        } catch {
            self.error = error.localizedDescription
            results = []
        }
        loading = false
    }
}

struct SearchView: View {
    @State private var query = ""
    @StateObject private var model = SearchModel()
    @State private var detailItem: CinemaItem?
    @State private var searchTask: Task<Void, Never>?

    private let columns = [GridItem(.adaptive(minimum: 112), spacing: 12)]

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
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(.ultraThinMaterial, in: Capsule())
                    .overlay(Capsule().strokeBorder(CinemaTheme.hairline, lineWidth: 1))
                    .padding(.horizontal, 20)

                    if model.loading {
                        ProgressView().tint(CinemaTheme.accent).padding(30)
                    } else if let error = model.error {
                        Text(error).font(.system(size: 13)).foregroundStyle(CinemaTheme.muted).padding(20)
                    } else if model.results.isEmpty {
                        VStack(spacing: 10) {
                            Image(systemName: "sparkle.magnifyingglass")
                                .font(.system(size: 30, weight: .light))
                                .foregroundStyle(CinemaTheme.accent)
                            Text("اكتب حرفين على الأقل للبحث في كل إضافاتك")
                                .font(.system(size: 14))
                                .foregroundStyle(CinemaTheme.muted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 60)
                    } else {
                        LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                            ForEach(model.results) { item in
                                PosterCard(item: item, width: 112, onOpen: { detailItem = item })
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                    Spacer(minLength: 120)
                }
                .padding(.top, 8)
            }
            .background(CinemaTheme.canvas.ignoresSafeArea())
            .navigationTitle("البحث")
            .onChange(of: query) { value in
                searchTask?.cancel()
                searchTask = Task {
                    try? await Task.sleep(nanoseconds: 550_000_000)
                    guard !Task.isCancelled else { return }
                    await model.run(value)
                }
            }
        }
        .fullScreenCover(item: $detailItem) { DetailView(item: $0) }
    }
}

// MARK: - Library

@MainActor
final class LibraryModel: ObservableObject {
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
    @StateObject private var model = LibraryModel()
    @State private var detailItem: CinemaItem?

    private let columns = [GridItem(.adaptive(minimum: 112), spacing: 12)]

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    Picker("", selection: $model.segment) {
                        Text("المحفوظة (\(model.favorites.count))").tag(0)
                        Text("قيد المشاهدة (\(model.progress.count))").tag(1)
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, 20)

                    if model.segment == 0 {
                        if model.favorites.isEmpty {
                            empty("لا شيء محفوظ بعد", "اضغط ♥ على أي عنوان ليظهر هنا.")
                        } else {
                            LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                                ForEach(model.favorites) { item in
                                    PosterCard(item: item, width: 112, saved: true, onOpen: { detailItem = item })
                                }
                            }
                            .padding(.horizontal, 20)
                        }
                    } else {
                        if model.progress.isEmpty {
                            empty("لا شيء قيد المشاهدة", "أي فيلم تبدأه يظهر هنا مع موضع التوقف.")
                        } else {
                            LazyVStack(spacing: 12) {
                                ForEach(model.progress) { entry in
                                    ContinueCard(entry: entry) { detailItem = entry.item }
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

struct SettingsView: View {
    @State private var classic = CinemaxAppleBridge().isClassicInterfaceSelected()

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("CINEMAX")
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

                    card(title: "الواجهة") {
                        Toggle(isOn: $classic) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("الواجهة الكلاسيكية")
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(.white)
                                Text("إدارة الإضافات والخيارات المتقدمة في الواجهة السابقة")
                                    .font(.system(size: 11))
                                    .foregroundStyle(CinemaTheme.muted)
                            }
                        }
                        .tint(CinemaTheme.accent)
                        .onChange(of: classic) { value in
                            CinemaxAppleBridge().switchInterface(classic: value)
                        }
                    }

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

struct CinemaRootView: View {
    @State private var selection = 0

    var body: some View {
        ZStack(alignment: .bottom) {
            CinemaTheme.canvas.ignoresSafeArea()
            Group {
                switch selection {
                case 0: HomeView(selection: $selection)
                case 1: SearchView()
                case 2: LibraryView()
                default: SettingsView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            FloatingTabBar(selection: $selection)
        }
        .preferredColorScheme(.dark)
        .environment(\.layoutDirection, .rightToLeft)
        .tint(CinemaTheme.accent)
    }
}
