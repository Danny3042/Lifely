import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

// The app's greens (the meditation page and icon)
private let deepGreen = Color(red: 0.17, green: 0.42, blue: 0.37)
private let mint = Color(red: 0.62, green: 0.95, blue: 0.87)

/// A running meditation: a countdown in the Dynamic Island, and a card on the Lock Screen (which
/// is also what iPhones without a Dynamic Island show as a banner).
struct MeditationLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: MeditationActivityAttributes.self) { context in
            LockScreenView(context: context)
                .activityBackgroundTint(deepGreen.opacity(0.85))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label {
                        Text(context.state.finished ? "Done" : "Meditating")
                            .font(.subheadline.weight(.semibold))
                    } icon: {
                        Image(systemName: "figure.mind.and.body")
                            .foregroundStyle(mint)
                    }
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Countdown(state: context.state)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(mint)
                        .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 10) {
                        SessionProgress(state: context.state)
                        HStack {
                            Text(context.state.finished
                                 ? "\(context.attributes.minutes) min of mindfulness"
                                 : "Breathe in… and out")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            Spacer()
                            StopButton(finished: context.state.finished)
                        }
                    }
                    .padding(.horizontal, 4)
                }
            } compactLeading: {
                Image(systemName: context.state.finished ? "checkmark.circle.fill" : "figure.mind.and.body")
                    .foregroundStyle(mint)
            } compactTrailing: {
                Countdown(state: context.state)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(mint)
                    .frame(maxWidth: 44)
            } minimal: {
                if context.state.finished {
                    Image(systemName: "checkmark").foregroundStyle(mint)
                } else {
                    ProgressView(
                        timerInterval: context.state.startDate...context.state.endDate,
                        countsDown: true,
                        label: { EmptyView() },
                        currentValueLabel: { Image(systemName: "figure.mind.and.body").font(.system(size: 9)) }
                    )
                    .progressViewStyle(.circular)
                    .tint(mint)
                }
            }
            .keylineTint(mint)
        }
    }
}

private struct LockScreenView: View {
    let context: ActivityViewContext<MeditationActivityAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 12) {
                ZStack {
                    Circle().fill(mint.opacity(0.25))
                    Image(systemName: context.state.finished ? "checkmark" : "figure.mind.and.body")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(mint)
                }
                .frame(width: 44, height: 44)

                VStack(alignment: .leading, spacing: 2) {
                    Text(context.state.finished ? "Session complete" : "Meditating")
                        .font(.headline)
                        .foregroundStyle(.white)
                    Text(context.state.finished
                         ? "\(context.attributes.minutes) min of mindfulness. Notice how you feel."
                         : "\(context.attributes.minutes) min session · breathe in… and out")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.75))
                }
                Spacer(minLength: 8)
                if !context.state.finished {
                    Countdown(state: context.state)
                        .font(.system(size: 34, weight: .semibold, design: .rounded))
                        .foregroundStyle(.white)
                }
            }
            if !context.state.finished {
                HStack(spacing: 12) {
                    SessionProgress(state: context.state)
                    StopButton(finished: false)
                }
            }
        }
        .padding(16)
    }
}

/// Counts down by itself: the system updates it every second without the app running.
private struct Countdown: View {
    let state: MeditationActivityAttributes.ContentState

    var body: some View {
        if state.finished {
            Text("Done")
        } else {
            Text(timerInterval: state.startDate...state.endDate, countsDown: true)
                .monospacedDigit()
                .multilineTextAlignment(.trailing)
        }
    }
}

private struct SessionProgress: View {
    let state: MeditationActivityAttributes.ContentState

    var body: some View {
        if state.finished {
            ProgressView(value: 1).tint(mint)
        } else {
            ProgressView(timerInterval: state.startDate...state.endDate, countsDown: false, label: { EmptyView() }, currentValueLabel: { EmptyView() })
                .tint(mint)
        }
    }
}

/// Stops the session from the Lock Screen or Dynamic Island (iOS 17 and later)
private struct StopButton: View {
    let finished: Bool

    var body: some View {
        if #available(iOS 17.0, *), !finished {
            Button(intent: StopMeditationIntent()) {
                Label("Stop", systemImage: "stop.fill")
                    .font(.caption.weight(.semibold))
            }
            .tint(Color(red: 0.90, green: 0.28, blue: 0.30))
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
        }
    }
}
