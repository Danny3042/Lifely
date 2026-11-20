import SwiftUI
import UIKit
import FirebaseAuth

struct HeroTabView: View {
    @Binding var selectedTab: Int

    // Map native tab index to Compose route names
    private let tabRoutes = [
        "HomePage",           // 0 -> Home
        "HabitCoachingPage",  // 1 -> Habits
        "ChatScreen",         // 2 -> Chat
        "meditation",         // 3 -> Meditation
        "profile"             // 4 -> Profile
    ]

    var body: some View {
        TabView(selection: $selectedTab) {
            TransparentTabContent()
                .onAppear { selectTab(0) }
                .tabItem { Label("Home", systemImage: "house") }
                .tag(0)

            TransparentTabContent()
                .onAppear { selectTab(1) }
                .tabItem { Label("Habits", systemImage: "checklist") }
                .tag(1)

            TransparentTabContent()
                .onAppear { selectTab(2) }
                .tabItem { Label("Chat", systemImage: "message") }
                .tag(2)

            TransparentTabContent()
                .onAppear { selectTab(3) }
                .tabItem { Label("Meditate", systemImage: "person.crop.circle") }
                .tag(3)

            TransparentTabContent()
                .onAppear { selectTab(4) }
                .tabItem { Label("Profile", systemImage: "person") }
                .tag(4)
        }
        .onChange(of: selectedTab) { newIndex in
            selectTab(newIndex)
        }
    }

    private func selectTab(_ index: Int) {
        guard index >= 0 && index < tabRoutes.count else { return }
        let route = tabRoutes[index]
        DispatchQueue.main.async {
            AuthManager.shared.requestNavigateTo(route: route)
        }
    }
}

// Transparent placeholder used in each tab so Compose content underneath is visible.
struct TransparentTabContent: View {
    var body: some View {
        Color.clear
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.clear)
            .contentShape(Rectangle()) // Make entire area tappable but transparent
    }
}

#if DEBUG
struct HeroTabView_Previews: PreviewProvider {
    static var previews: some View {
        HeroTabView(selectedTab: .constant(0))
    }
}
#endif
