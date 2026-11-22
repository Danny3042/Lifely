import AppIntents
import SwiftUI
import UIKit
import WidgetKit

// The app writes a snapshot of today into the shared App Group (WidgetPlatform.ios.kt); the widget
// only reads it, apart from mood taps, which it queues there for the app to take in.
private let appGroup = "group.org.example.project.HealthApp"

struct WidgetSnapshot: Codable {
    var day: String
    var todayMinutes: Int
    var goalMinutes: Int
    var sessionsToday: Int
    var streakDays: Int
    var lastMoodValence: Int?
    var lastMoodAtEpochMillis: Int64?

    static func load() -> WidgetSnapshot? {
        guard let text = UserDefaults(suiteName: appGroup)?.string(forKey: "snapshot"),
              let data = text.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    func save() {
        guard let data = try? JSONEncoder().encode(self), let text = String(data: data, encoding: .utf8) else { return }
        UserDefaults(suiteName: appGroup)?.set(text, forKey: "snapshot")
    }

    static let placeholder = WidgetSnapshot(day: dayString(Date()), todayMinutes: 7, goalMinutes: 10, sessionsToday: 2, streakDays: 3, lastMoodValence: 2, lastMoodAtEpochMillis: Int64(Date().timeIntervalSince1970 * 1000))
}

private func dayString(_ date: Date) -> String {
    let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
    return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
}

// MARK: - Colours (the app's greens, light and dark)

private func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
    func ui(_ hex: UInt32) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
    return Color(UIColor { $0.userInterfaceStyle == .dark ? ui(dark) : ui(light) })
}

private enum Palette {
    static let background = dynamic(0xE1F5EE, 0x0E2E27)
    static let text = dynamic(0x04342C, 0xE1F5EE)
    static let secondary = dynamic(0x085041, 0x9FE1CB)
    static let accent = dynamic(0x0F6E56, 0x5DCAA5)
    static let onAccent = dynamic(0xE1F5EE, 0x04342C)
    static let track = dynamic(0x9FE1CB, 0x1F4A41)
    static let flame = dynamic(0x993C1D, 0xF0997B)
}

/// The check-in scale's colours (moodColor in MoodCheckInCard.kt)
private func moodColor(_ valence: Int) -> Color {
    switch valence {
    case ...(-3): return Color(red: 0.36, green: 0.25, blue: 0.85)
    case -2: return Color(red: 0.48, green: 0.35, blue: 0.97)
    case -1: return Color(red: 0.36, green: 0.53, blue: 0.90)
    case 0: return Color(red: 0.23, green: 0.61, blue: 0.86)
    case 1: return Color(red: 0.18, green: 0.70, blue: 0.60)
    case 2: return Color(red: 0.95, green: 0.70, blue: 0.20)
    default: return Color(red: 0.95, green: 0.54, blue: 0.10)
    }
}

private let moodLabels = [-3: "Very unpleasant", -2: "Unpleasant", -1: "Slightly unpleasant", 0: "Neutral", 1: "Slightly pleasant", 2: "Pleasant", 3: "Very pleasant"]
/// The widget's five quick choices (the in-between steps are left to the app)
private let quickMoods = [-3, -2, 0, 2, 3]

// MARK: - Logging a mood from the widget (iOS 17)

@available(iOS 17.0, *)
struct QuickMoodIntent: AppIntent {
    static var title: LocalizedStringResource = "Log Feeling"
    static var isDiscoverable = false

    @Parameter(title: "Feeling") var valence: Int

    init() {}
    init(valence: Int) { self.valence = valence }

    func perform() async throws -> some IntentResult {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let defaults = UserDefaults(suiteName: appGroup)
        // Queue it for the app (WidgetSync takes it in next time the app runs)
        var pending: [[String: Int64]] = []
        if let text = defaults?.string(forKey: "pendingCheckIns"), let data = text.data(using: .utf8),
           let existing = try? JSONDecoder().decode([[String: Int64]].self, from: data) {
            pending = existing
        }
        pending.append(["atEpochMillis": now, "valence": Int64(valence)])
        if let data = try? JSONEncoder().encode(pending), let text = String(data: data, encoding: .utf8) {
            defaults?.set(text, forKey: "pendingCheckIns")
        }
        // Show it straight away
        var snapshot = WidgetSnapshot.load() ?? WidgetSnapshot(day: dayString(Date()), todayMinutes: 0, goalMinutes: 10, sessionsToday: 0, streakDays: 0)
        snapshot.lastMoodValence = valence
        snapshot.lastMoodAtEpochMillis = now
        snapshot.save()
        return .result()
    }
}

// MARK: - Timeline

struct MindfulEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?

    /// Today's numbers, or zero if the snapshot is from an earlier day
    var minutes: Int { isToday ? snapshot?.todayMinutes ?? 0 : 0 }
    var sessions: Int { isToday ? snapshot?.sessionsToday ?? 0 : 0 }
    var goal: Int { max(1, snapshot?.goalMinutes ?? 10) }
    var isToday: Bool { snapshot?.day == dayString(date) }
    /// The streak still stands if the last session was today or yesterday
    var streak: Int {
        guard let s = snapshot else { return 0 }
        if s.day == dayString(date) { return s.streakDays }
        if let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: date), s.day == dayString(yesterday) {
            return s.streakDays
        }
        return 0
    }
    var progress: Double { min(1, Double(minutes) / Double(goal)) }
}

struct MindfulProvider: TimelineProvider {
    func placeholder(in context: Context) -> MindfulEntry {
        MindfulEntry(date: Date(), snapshot: .placeholder)
    }

    func getSnapshot(in context: Context, completion: @escaping (MindfulEntry) -> Void) {
        completion(MindfulEntry(date: Date(), snapshot: context.isPreview ? (WidgetSnapshot.load() ?? .placeholder) : WidgetSnapshot.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MindfulEntry>) -> Void) {
        let now = Date()
        let snapshot = WidgetSnapshot.load()
        // A second entry at midnight starts the new day at zero; the app reloads us whenever it changes
        let midnight = Calendar.current.startOfDay(for: now.addingTimeInterval(86_400))
        completion(Timeline(entries: [MindfulEntry(date: now, snapshot: snapshot), MindfulEntry(date: midnight, snapshot: snapshot)], policy: .atEnd))
    }
}

// MARK: - Views

private let meditateURL = URL(string: "myhealthbuddy://meditate")!
private let checkInURL = URL(string: "myhealthbuddy://checkin")!

private struct Ring: View {
    let progress: Double
    var width: CGFloat = 7

    var body: some View {
        ZStack {
            Circle().stroke(Palette.track, lineWidth: width)
            Circle()
                .trim(from: 0, to: max(0.001, progress))
                .stroke(Palette.accent, style: StrokeStyle(lineWidth: width, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
    }
}

private struct MeditatePill: View {
    let title: String
    var body: some View {
        Label(title, systemImage: "play.fill")
            .font(.caption.weight(.semibold))
            .foregroundStyle(Palette.onAccent)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Capsule().fill(Palette.accent))
    }
}

private struct StreakBadge: View {
    let days: Int
    var body: some View {
        if days > 0 {
            Label("\(days)", systemImage: "flame")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Palette.flame)
        }
    }
}

struct SmallMindfulView: View {
    let entry: MindfulEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Today").font(.caption.weight(.semibold)).foregroundStyle(Palette.accent)
                Spacer()
                StreakBadge(days: entry.streak)
            }
            HStack(spacing: 10) {
                Ring(progress: entry.progress).frame(width: 52, height: 52)
                VStack(alignment: .leading, spacing: 0) {
                    Text("\(entry.minutes)").font(.system(size: 24, weight: .semibold, design: .rounded)).foregroundStyle(Palette.text)
                    Text("of \(entry.goal) min").font(.caption2).foregroundStyle(Palette.secondary)
                }
            }
            Spacer(minLength: 0)
            MeditatePill(title: "Meditate").frame(maxWidth: .infinity)
        }
        .widgetURL(meditateURL)
    }
}

struct MediumMindfulView: View {
    let entry: MindfulEntry

    private var greeting: String {
        switch Calendar.current.component(.hour, from: entry.date) {
        case 5...11: return "Good morning"
        case 12...16: return "Good afternoon"
        case 17...21: return "Good evening"
        default: return "Good night"
        }
    }

    var body: some View {
        HStack(spacing: 14) {
            Link(destination: meditateURL) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(greeting).font(.caption.weight(.semibold)).foregroundStyle(Palette.accent)
                        Spacer()
                        StreakBadge(days: entry.streak)
                    }
                    Spacer(minLength: 0)
                    Text("\(entry.minutes) of \(entry.goal) min")
                        .font(.system(size: 20, weight: .semibold, design: .rounded))
                        .foregroundStyle(Palette.text)
                        .minimumScaleFactor(0.8)
                    Text(statsLine).font(.caption2).foregroundStyle(Palette.secondary)
                    ProgressView(value: entry.progress).tint(Palette.accent)
                    Spacer(minLength: 0)
                    MeditatePill(title: "Meditate")
                }
            }

            Rectangle().fill(Palette.track).frame(width: 0.5)

            VStack(alignment: .leading, spacing: 8) {
                Text("How are you feeling?").font(.caption.weight(.semibold)).foregroundStyle(Palette.accent)
                Spacer(minLength: 0)
                HStack(spacing: 0) {
                    ForEach(quickMoods, id: \.self) { v in MoodButton(valence: v) }
                }
                Spacer(minLength: 0)
                lastCheckIn
            }
        }
    }

    private var statsLine: String {
        let s = entry.sessions == 1 ? "1 session" : "\(entry.sessions) sessions"
        return entry.streak > 0 ? "\(entry.streak) day streak · \(s)" : s
    }

    @ViewBuilder private var lastCheckIn: some View {
        if let v = entry.snapshot?.lastMoodValence, let at = entry.snapshot?.lastMoodAtEpochMillis {
            HStack(spacing: 5) {
                Circle().fill(moodColor(v)).frame(width: 8, height: 8)
                Text("\(moodLabels[v] ?? "Logged") · \(Date(timeIntervalSince1970: Double(at) / 1000), style: .time)")
                    .font(.caption2)
                    .foregroundStyle(Palette.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        } else {
            Text("Tap a colour to check in").font(.caption2).foregroundStyle(Palette.secondary)
        }
    }
}

private struct MoodButton: View {
    let valence: Int

    var body: some View {
        // A 26pt dot in a tap area as wide as its share of the row and 40pt tall
        let dot = Circle().fill(moodColor(valence)).frame(width: 26, height: 26)
            .frame(maxWidth: .infinity, minHeight: 40)
            .contentShape(Rectangle())
            .accessibilityLabel("Log \(moodLabels[valence] ?? "feeling")")
        if #available(iOS 17.0, *) {
            Button(intent: QuickMoodIntent(valence: valence)) { dot }.buttonStyle(.plain)
        } else {
            Link(destination: checkInURL) { dot }
        }
    }
}

struct CircularMindfulView: View {
    let entry: MindfulEntry
    var body: some View {
        Gauge(value: entry.progress) {
            Image(systemName: "figure.mind.and.body")
        } currentValueLabel: {
            Text("\(entry.minutes)")
        }
        .gaugeStyle(.accessoryCircularCapacity)
        .widgetURL(meditateURL)
    }
}

struct RectangularMindfulView: View {
    let entry: MindfulEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Label("Mindful", systemImage: "figure.mind.and.body").font(.headline).widgetAccentable()
            Text("\(entry.minutes) of \(entry.goal) min")
            Text(entry.streak > 0 ? "\(entry.streak) day streak" : "Start a streak today").foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetURL(meditateURL)
    }
}

struct MindfulWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: MindfulEntry

    var body: some View {
        switch family {
        case .systemMedium:
            MediumMindfulView(entry: entry).widgetBackground(Palette.background)
        case .accessoryCircular:
            CircularMindfulView(entry: entry).widgetBackground(.clear)
        case .accessoryRectangular:
            RectangularMindfulView(entry: entry).widgetBackground(.clear)
        case .accessoryInline:
            Text("\(entry.minutes) of \(entry.goal) mindful min").widgetBackground(.clear)
        default:
            SmallMindfulView(entry: entry).widgetBackground(Palette.background)
        }
    }
}

private extension View {
    @ViewBuilder func widgetBackground(_ color: Color) -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            padding().background(color)
        }
    }
}

struct MindfulWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "MindfulWidget", provider: MindfulProvider()) { entry in
            MindfulWidgetView(entry: entry)
        }
        .configurationDisplayName("Mindful minutes")
        .description("Today's meditation, your streak, and a quick check-in.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}
