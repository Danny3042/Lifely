import SwiftUI

// Material-like design tokens for SwiftUI on iOS
enum MaterialColor {
    static let primary = Color(red: 0.21, green: 0.39, blue: 0.99) // Material Blue-ish
    static let onPrimary = Color.white
    static let surface = Color(UIColor.systemBackground)
    static let surfaceElevated = Color(UIColor.secondarySystemBackground)
    static let card = Color(UIColor.systemBackground)
    static let muted = Color(UIColor.secondaryLabel)
    static let error = Color(red: 0.87, green: 0.2, blue: 0.2)
}

struct MaterialElevation {
    static var level1: Shadow = Shadow(color: Color.black.opacity(0.08), radius: 4, y: 2)
    static var level2: Shadow = Shadow(color: Color.black.opacity(0.10), radius: 8, y: 4)

    struct Shadow { let color: Color; let radius: CGFloat; let y: CGFloat }
}

// Material Card - elevated rounded rectangle used for small surfaces
struct MaterialCard<Content: View>: View {
    let content: Content
    var elevation: MaterialElevation.Shadow = MaterialElevation.level1
    var cornerRadius: CGFloat = 12
    var padding: CGFloat = 12

    init(elevation: MaterialElevation.Shadow = MaterialElevation.level1, cornerRadius: CGFloat = 12, padding: CGFloat = 12, @ViewBuilder content: () -> Content) {
        self.content = content()
        self.elevation = elevation
        self.cornerRadius = cornerRadius
        self.padding = padding
    }

    var body: some View {
        content
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(MaterialColor.card)
                    .shadow(color: elevation.color, radius: elevation.radius, x: 0, y: elevation.y)
            )
    }
}

// Material FAB with ripple tap effect and dynamic offset
struct MaterialFAB: View {
    var systemIcon: String
    var action: () -> Void
    var backgroundColor: Color = MaterialColor.primary
    var foregroundColor: Color = MaterialColor.onPrimary
    var size: CGFloat = 56
    var elevation: MaterialElevation.Shadow = MaterialElevation.level2
    @State private var pressed = false

    var body: some View {
        Button(action: {
            // simple press animation and action
            withAnimation(.easeOut(duration: 0.12)) {
                pressed = true
            }
            // small delay so the animation is seen before action
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) {
                withAnimation(.easeIn(duration: 0.12)) { pressed = false }
                action()
            }
        }) {
            ZStack {
                Circle()
                    .fill(backgroundColor)
                    .frame(width: size, height: size)
                Image(systemName: systemIcon)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundColor(foregroundColor)
            }
            .scaleEffect(pressed ? 0.96 : 1.0)
            .shadow(color: elevation.color, radius: elevation.radius, x: 0, y: elevation.y)
            .accessibilityLabel("Floating action button")
        }
    }
}

// A compact ripple effect for taps (optional wrapper)
struct RippleTapModifier: ViewModifier {
    @State private var ripple = false
    var color: Color = Color.white.opacity(0.25)
    func body(content: Content) -> some View {
        content
            .overlay(
                Circle()
                    .fill(color)
                    .scaleEffect(ripple ? 1.6 : 0.001)
                    .opacity(ripple ? 0 : 1)
            )
            .onTapGesture {
                ripple = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { ripple = false }
            }
    }
}

extension View {
    func materialRipple(color: Color = Color.white.opacity(0.25)) -> some View {
        self.modifier(RippleTapModifier(color: color))
    }
}

