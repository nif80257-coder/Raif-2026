import SwiftUI

struct ContentView: View {
    private let featured = [
        MediaItem(title: "مسارات بعيدة", subtitle: "دراما · 2024", symbol: "sparkles.tv", tint: Color(red: 0.18, green: 0.34, blue: 0.55)),
        MediaItem(title: "ليلة في المدينة", subtitle: "غموض · 2023", symbol: "moon.stars.fill", tint: Color(red: 0.35, green: 0.22, blue: 0.48)),
        MediaItem(title: "خارج الإطار", subtitle: "وثائقي · 2024", symbol: "rectangle.stack.fill", tint: Color(red: 0.50, green: 0.30, blue: 0.18))
    ]

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 24) {
                    header
                    continueWatching
                    section(title: "مختارات لك", items: featured)
                    quickActions
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 32)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("رايف")
            .navigationBarTitleDisplayMode(.inline)
        }
        .tint(.indigo)
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                Text("مساء الخير")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text("ماذا تشاهد اليوم؟")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(.primary)
            }
            Spacer()
            Button(action: {}) {
                Image(systemName: "magnifyingglass")
                    .font(.headline)
                    .frame(width: 44, height: 44)
                    .background(.background, in: Circle())
                    .overlay(Circle().stroke(.quaternary, lineWidth: 1))
            }
            .accessibilityIdentifier("searchButton")
            .accessibilityLabel("بحث")
        }
    }

    private var continueWatching: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("تابع المشاهدة")
                .font(.title3.weight(.bold))
            Button(action: {}) {
                HStack(spacing: 14) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 14)
                            .fill(LinearGradient(colors: [.indigo, .purple], startPoint: .topLeading, endPoint: .bottomTrailing))
                        Image(systemName: "play.fill")
                            .font(.title2)
                            .foregroundStyle(.white)
                    }
                    .frame(width: 112, height: 76)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("الحلقة ٣ · الموسم الأول")
                            .font(.headline)
                        Text("الطريق إلى الشمال")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        ProgressView(value: 0.62)
                            .tint(.indigo)
                    }
                    Spacer(minLength: 0)
                }
                .padding(14)
                .background(.background, in: RoundedRectangle(cornerRadius: 20))
                .shadow(color: .black.opacity(0.06), radius: 12, y: 5)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("continueWatchingCard")
            .accessibilityLabel("متابعة المشاهدة: الطريق إلى الشمال")
        }
    }

    private func section(title: String, items: [MediaItem]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(title).font(.title3.weight(.bold))
                Spacer()
                Button("عرض الكل", action: {})
                    .font(.subheadline.weight(.semibold))
                    .accessibilityLabel("عرض كل \(title)")
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(items) { item in
                        Button(action: {}) {
                            VStack(alignment: .leading, spacing: 8) {
                                ZStack(alignment: .bottomLeading) {
                                    RoundedRectangle(cornerRadius: 16)
                                        .fill(LinearGradient(colors: [item.tint, item.tint.opacity(0.55)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    Image(systemName: item.symbol)
                                        .font(.system(size: 32))
                                        .foregroundStyle(.white.opacity(0.9))
                                        .padding(14)
                                }
                                .frame(width: 142, height: 176)
                                Text(item.title)
                                    .font(.subheadline.weight(.semibold))
                                    .lineLimit(1)
                                Text(item.subtitle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("mediaCard_\(item.title)")
                        .accessibilityLabel("\(item.title)، \(item.subtitle)")
                    }
                }
            }
        }
    }

    private var quickActions: some View {
        HStack(spacing: 12) {
            action(title: "التنزيلات", icon: "arrow.down.circle.fill", identifier: "downloadsButton")
            action(title: "قائمتي", icon: "bookmark.fill", identifier: "watchlistButton")
        }
    }

    private func action(title: String, icon: String, identifier: String) -> some View {
        Button(action: {}) {
            Label(title, systemImage: icon)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15)
                .background(.background, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(.quaternary, lineWidth: 1))
        }
        .accessibilityIdentifier(identifier)
        .accessibilityLabel(title)
    }
}

private struct MediaItem: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let symbol: String
    let tint: Color
}

#Preview {
    ContentView()
}
