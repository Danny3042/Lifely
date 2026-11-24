import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

// Brand colours: the app's mint, and the gold of the sun in the app icon.
private let mint = Color(red: 0.62, green: 0.95, blue: 0.87)
private let sunGold = Color(red: 1.00, green: 0.78, blue: 0.45)
private let deepGreen = Color(red: 0.09, green: 0.29, blue: 0.26)
private let stopRed = Color(red: 0.96, green: 0.36, blue: 0.36)

private typealias SessionState = MeditationActivityAttributes.ContentState

/// Where a tap goes: the running session, or straight to the check-in once it's finished.
/// Every presentation uses the same link, so compact leading and trailing open the same screen.
private func link(for state: SessionState) -> URL {
    URL(string: state.finished ? "myhealthbuddy://checkin" : "myhealthbuddy://meditate")!
}

/// A running meditation, designed to Apple's Live Activities guidelines:
/// - compact: progress ring snug against the camera + a medium-weight countdown, read as one piece
/// - limited width (iOS 27, e.g. landscape): the ring alone, fitting a 28pt circle; trailing left empty
/// - minimal: the live progress ring, not a static logo
/// - expanded: the compact layout enlarged (ring leading, countdown trailing) wrapped around the camera,
///   with one control (Stop) below
/// - Lock Screen: its own layout (not a notification), 14pt margins, height shrinks once finished
struct MeditationLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: MeditationActivityAttributes.self) { context in
            LockScreenView(context: context)
                .activityBackgroundTint(deepGreen)
                .activitySystemActionForegroundColor(mint)
                .widgetURL(link(for: context.state))
        } dynamicIsland: { context in
            let state = context.state
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    SessionRing(state: state, lineWidth: 5, glyph: .title3)
                        .frame(width: 52, height: 52)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Countdown(state: state, minutes: context.attributes.minutes)
                        .font(.system(size: 40, weight: .semibold, design: .rounded))
                        .foregroundStyle(state.finished ? sunGold : mint)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(alignment: .center, spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(state.finished ? "Session complete" : "Meditating")
                                .font(.headline)
                                .foregroundStyle(.white)
                            Text(state.finished
                                 ? "Tap to notice how you feel"
                                 : "\(context.attributes.minutes)-min session")
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(.white.opacity(0.7))
                        }
                        Spacer(minLength: 8)
                        StopButton(finished: state.finished)
                    }
                    .padding(.top, 4)
                }
            } compactLeading: {
                // Snug against the camera: no extra padding
                SessionRing(state: state, lineWidth: 3, glyph: .caption2)
                    .frame(width: 24, height: 24)
            } compactTrailing: {
                CompactTrailing(state: state, minutes: context.attributes.minutes)
            } minimal: {
                SessionRing(state: state, lineWidth: 3, glyph: .caption2)
                    .frame(width: 24, height: 24)
            }
            .keylineTint(mint)
            .widgetURL(link(for: state))
        }
    }
}

// MARK: - Lock Screen (also the banner on iPhones without a Dynamic Island, and StandBy at 2x)

private struct LockScreenView: View {
    let context: ActivityViewContext<MeditationActivityAttributes>

    var body: some View {
        let state = context.state
        HStack(alignment: .center, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text(state.finished ? "SESSION COMPLETE" : "MEDITATING")
                    .font(.caption.weight(.bold))
                    .tracking(1.2)
                    .foregroundStyle(state.finished ? sunGold : mint)
                Countdown(state: state, minutes: context.attributes.minutes)
                    .font(.system(size: 46, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(state.finished ? "Tap to notice how you feel" : "Breathe in… and out")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white.opacity(0.75))
            }
            Spacer(minLength: 8)
            VStack(spacing: 10) {
                SessionRing(state: state, lineWidth: 6, glyph: .title2)
                    .frame(width: 64, height: 64)
                StopButton(finished: state.finished)
            }
        }
        .padding(14) // standard Lock Screen margin
    }
}

// MARK: - Pieces

/// Countdown that the system advances every second without the app running.
/// When the session ends it changes to the session length, with a content transition.
private struct Countdown: View {
    let state: SessionState
    let minutes: Int

    var body: some View {
        Group {
            if state.finished {
                Text("\(minutes) min")
            } else {
                Text(timerInterval: state.startDate...state.endDate, countsDown: true)
                    .monospacedDigit()
                    .multilineTextAlignment(.trailing)
            }
        }
        .contentTransition(.opacity)
    }
}

/// Live progress ring with the app's leaf, filling as the session runs; gold with a tick when done.
/// Used for compact leading, minimal, expanded and Lock Screen so the activity reads the same everywhere.
private struct SessionRing: View {
    let state: SessionState
    let lineWidth: CGFloat
    let glyph: Font

    var body: some View {
        if state.finished {
            ZStack {
                Circle().stroke(sunGold, lineWidth: lineWidth)
                Image(systemName: "checkmark")
                    .font(glyph.weight(.bold))
                    .foregroundStyle(sunGold)
            }
            .padding(lineWidth / 2)
            .transition(.scale.combined(with: .opacity))
        } else {
            ProgressView(
                timerInterval: state.startDate...state.endDate,
                countsDown: false,
                label: { EmptyView() },
                currentValueLabel: {
                    Image(systemName: "leaf.fill")
                        .font(glyph.weight(.bold))
                        .foregroundStyle(mint)
                }
            )
            .progressViewStyle(.circular)
            .tint(mint)
        }
    }
}

/// Compact trailing: a medium-weight countdown. In the limited-width Dynamic Island (iOS 27)
/// it is left empty so the system hides it and the ring alone fits the 28pt circle.
private struct CompactTrailing: View {
    let state: SessionState
    let minutes: Int

    var body: some View {
        if #available(iOS 27.0, *) {
            LimitedWidthAware(state: state, minutes: minutes)
        } else {
            CompactCountdown(state: state, minutes: minutes)
        }
    }
}

@available(iOS 27.0, *)
private struct LimitedWidthAware: View {
    @Environment(\.isDynamicIslandLimitedInWidth) private var limited
    let state: SessionState
    let minutes: Int

    var body: some View {
        if limited {
            EmptyView()
        } else {
            CompactCountdown(state: state, minutes: minutes)
        }
    }
}

private struct CompactCountdown: View {
    let state: SessionState
    let minutes: Int

    var body: some View {
        Countdown(state: state, minutes: minutes)
            .font(.system(size: 15, weight: .semibold, design: .rounded))
            .foregroundStyle(state.finished ? sunGold : mint)
            .frame(width: 44, alignment: .trailing) // keeps it narrow and balanced with the ring
    }
}

/// The single control: stop the session (iOS 17 and later). Hidden once finished.
private struct StopButton: View {
    let finished: Bool

    var body: some View {
        if #available(iOS 17.0, *), !finished {
            Button(intent: StopMeditationIntent()) {
                Label("Stop", systemImage: "stop.fill")
                    .font(.subheadline.weight(.semibold))
            }
            .tint(stopRed)
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
        }
    }
}
