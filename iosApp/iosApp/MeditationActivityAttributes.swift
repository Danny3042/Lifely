import ActivityKit
import AppIntents
import Foundation

/// A running meditation, shown as a Live Activity: in the Dynamic Island on iPhones that have one,
/// and on the Lock Screen (and as a banner) everywhere. Shared by the app and the widget extension.
@available(iOS 16.1, *)
struct MeditationActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        /// When the session started (or resumed) and when it finishes; the countdown runs between them
        var startDate: Date
        var endDate: Date
        /// True once the session has run to the end
        var finished: Bool
    }

    /// Length of the whole session in minutes
    var minutes: Int
}

/// The Live Activity's Stop button. It runs in the app, which stops the session.
@available(iOS 17.0, *)
struct StopMeditationIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Stop Meditation"
    static var description = IntentDescription("Ends the meditation that's running.")

    func perform() async throws -> some IntentResult {
        await MainActor.run {
            NotificationCenter.default.post(name: .stopMeditationFromActivity, object: nil)
        }
        return .result()
    }
}

extension Notification.Name {
    static let stopMeditationFromActivity = Notification.Name("StopMeditationFromActivity")
}
