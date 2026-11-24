import ActivityKit
import ComposeApp
import Foundation

/// Starts and ends the meditation Live Activity when the Compose meditation page says so
/// (it posts "MeditationLiveActivity" from MeditationLiveActivity.ios.kt).
final class MeditationLiveActivityController {
    static let shared = MeditationLiveActivityController()
    private var observers: [NSObjectProtocol] = []

    func activate() {
        guard observers.isEmpty else { return }
        observers.append(NotificationCenter.default.addObserver(
            forName: Notification.Name("MeditationLiveActivity"), object: nil, queue: .main
        ) { [weak self] note in
            guard let info = note.userInfo, let action = info["action"] as? String else { return }
            if action == "start" {
                let endAt = (info["endAt"] as? NSNumber)?.doubleValue ?? Date().timeIntervalSince1970
                let total = (info["total"] as? NSNumber)?.intValue ?? 300
                self?.start(endDate: Date(timeIntervalSince1970: endAt), totalSeconds: total)
            } else {
                self?.end(completed: (info["completed"] as? NSNumber)?.boolValue ?? false)
            }
        })
        // The Stop button on the Live Activity
        observers.append(NotificationCenter.default.addObserver(
            forName: .stopMeditationFromActivity, object: nil, queue: .main
        ) { _ in
            PlatformBridge.shared.meditationStopSignal = PlatformBridge.shared.meditationStopSignal + 1
            self.end(completed: false)
        })
        // A session left over from a crash or a force quit
        end(completed: false)
    }

    private func start(endDate: Date, totalSeconds: Int) {
        guard #available(iOS 16.2, *) else { return }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        Task {
            // One session at a time: a resumed session replaces the paused one
            for activity in Activity<MeditationActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            let state = MeditationActivityAttributes.ContentState(startDate: Date(), endDate: endDate, finished: false)
            let attributes = MeditationActivityAttributes(minutes: max(1, totalSeconds / 60))
            _ = try? Activity.request(
                attributes: attributes,
                content: ActivityContent(state: state, staleDate: endDate),
                pushType: nil
            )
        }
    }

    private func end(completed: Bool) {
        guard #available(iOS 16.2, *) else { return }
        Task {
            for activity in Activity<MeditationActivityAttributes>.activities {
                if completed {
                    // Keep the summary (and its tap-to-check-in link) for 15 min, per the HIG (15–30 min)
                    var state = activity.content.state
                    state.finished = true
                    state.endDate = Date()
                    await activity.end(
                        ActivityContent(state: state, staleDate: nil),
                        dismissalPolicy: .after(Date().addingTimeInterval(15 * 60))
                    )
                } else {
                    await activity.end(nil, dismissalPolicy: .immediate)
                }
            }
        }
    }
}

import UIKit
import WidgetKit

/// Reloads the widgets when the app publishes a new snapshot (WidgetPlatform.ios.kt), and brings them
/// up to date whenever the app comes to the front.
final class WidgetRefresher {
    static let shared = WidgetRefresher()
    private var observers: [NSObjectProtocol] = []

    func activate() {
        guard observers.isEmpty else { return }
        observers.append(NotificationCenter.default.addObserver(
            forName: Notification.Name("WidgetSnapshotChanged"), object: nil, queue: .main
        ) { _ in
            WidgetCenter.shared.reloadAllTimelines()
        })
        observers.append(NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main
        ) { _ in
            // Takes in check-ins logged from the widget while the app was closed
            WidgetSync.shared.onAppActive()
        })
    }
}

/// The widgets' links into the app.
enum WidgetLinks {
    // Called on the main thread (onOpenURL)
    static func handle(_ url: URL) -> Bool {
        guard url.scheme == "myhealthbuddy" else { return false }
        switch url.host {
        case "meditate":
            PlatformBridge.shared.requestedRoute = "meditate_timer"
        case "checkin":
            PlatformBridge.shared.requestedRoute = "check_in"
        case "calendar":
            PlatformBridge.shared.requestedRoute = "check_in_calendar"
        default:
            return true
        }
        PlatformBridge.shared.requestedRouteSignal = PlatformBridge.shared.requestedRouteSignal + 1
        return true
    }
}
