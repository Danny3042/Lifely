import AppIntents
import ComposeApp
import Foundation

// Siri, Shortcuts, Spotlight and the Action button. The routes and the check-in store are the
// Compose app's: meditation ("meditate_timer"), check-in ("check_in") and calendar ("check_in_calendar").

/// Opens a Compose page by its route.
@MainActor
private func openRoute(_ route: String) {
    PlatformBridge.shared.requestedRoute = route
    PlatformBridge.shared.requestedRouteSignal = PlatformBridge.shared.requestedRouteSignal + 1
}

struct StartMeditationIntent: AppIntent {
    static var title: LocalizedStringResource = "Start Meditation"
    static var description = IntentDescription("Opens MyHealthBuddy and starts a meditation session.")
    static var openAppWhenRun: Bool = true

    @Parameter(title: "Minutes", default: 5, inclusiveRange: (1, 180))
    var minutes: Int

    static var parameterSummary: some ParameterSummary {
        Summary("Meditate for \(\.$minutes) minutes")
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        PlatformBridge.shared.meditationStartMinutes = KotlinInt(int: Int32(minutes))
        openRoute("meditate_timer")
        return .result()
    }
}

struct CheckInIntent: AppIntent {
    static var title: LocalizedStringResource = "Check In"
    static var description = IntentDescription("Opens the check-in so you can note how you're feeling.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        openRoute("check_in")
        return .result()
    }
}

struct OpenCheckInCalendarIntent: AppIntent {
    static var title: LocalizedStringResource = "Show Check-in Calendar"
    static var description = IntentDescription("Opens the calendar of your past check-ins.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        openRoute("check_in_calendar")
        return .result()
    }
}

/// The seven steps of the check-in scale, from very unpleasant to very pleasant
enum MoodLevel: Int, AppEnum {
    case veryUnpleasant = -3, unpleasant = -2, slightlyUnpleasant = -1, neutral = 0
    case slightlyPleasant = 1, pleasant = 2, veryPleasant = 3

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Feeling"
    static var caseDisplayRepresentations: [MoodLevel: DisplayRepresentation] = [
        .veryUnpleasant: "Very unpleasant",
        .unpleasant: "Unpleasant",
        .slightlyUnpleasant: "Slightly unpleasant",
        .neutral: "Neutral",
        .slightlyPleasant: "Slightly pleasant",
        .pleasant: "Pleasant",
        .veryPleasant: "Very pleasant",
    ]
}

/// Logs a check-in without opening the app ("Hey Siri, log my mood in MyHealthBuddy")
struct LogMoodIntent: AppIntent {
    static var title: LocalizedStringResource = "Log How I Feel"
    static var description = IntentDescription("Saves a check-in of how pleasant or unpleasant you feel right now.")

    @Parameter(title: "Feeling", requestValueDialog: "How are you feeling right now?")
    var mood: MoodLevel

    static var parameterSummary: some ParameterSummary {
        Summary("Log feeling \(\.$mood)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let store = MoodCheckInStore.shared
        store.load()
        store.add(checkIn: MoodCheckIn(
            atEpochMillis: Int64(Date().timeIntervalSince1970 * 1000),
            valence: Int32(mood.rawValue),
            feelings: [],
            impacts: []
        ), refreshWidgets: true)
        let label = MoodLevel.caseDisplayRepresentations[mood].map { String(localized: $0.title) } ?? "your check-in"
        return .result(dialog: "Logged \(label). Noticing how you feel is part of the practice.")
    }
}

struct HealthBuddyShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: StartMeditationIntent(),
            phrases: [
                "Start a meditation in \(.applicationName)",
                "Meditate with \(.applicationName)",
            ]
        )
        AppShortcut(
            intent: LogMoodIntent(),
            phrases: [
                "Log my mood in \(.applicationName)",
                "I'm feeling \(\.$mood) in \(.applicationName)",
            ]
        )
        AppShortcut(
            intent: CheckInIntent(),
            phrases: ["Check in with \(.applicationName)"]
        )
        AppShortcut(
            intent: OpenCheckInCalendarIntent(),
            phrases: ["Show my check-ins in \(.applicationName)"]
        )
    }
}
