import SwiftUI
import ComposeApp
import FirebaseAuth
import HealthKit
import PhotosUI
#if canImport(Charts)
import Charts
#endif

// MARK: - ComposeBridge
/// Singleton bridge to coordinate between Compose and native UIKit/SwiftUI components
final class ComposeBridge {
    static let shared = ComposeBridge()

    private(set) weak var tabBarController: UITabBarController?
    private(set) weak var navigationController: UINavigationController?
    
    // Callbacks for SwiftUI coordination
    var onTabSelectionChange: ((Int) -> Void)?
    var onTabBarVisibilityChange: ((Bool) -> Void)?
    var onNavigationBarVisibilityChange: ((Bool) -> Void)?
    var onBackButtonVisibilityChange: ((Bool) -> Void)?

    private init() {
        setupObservers()
    }

    // Call this to register UIKit controllers (optional, mainly for pure UIKit apps)
    func install(tabBarController: UITabBarController?, navigationController: UINavigationController?) {
        self.tabBarController = tabBarController
        self.navigationController = navigationController
    }

    private func setupObservers() {
        // Listen for tab switching requests from Compose
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("SwitchNativeTab"), 
            object: nil, 
            queue: .main
        ) { [weak self] note in
            guard let tab = note.userInfo?["tab"] as? String else { return }
            let index = self?.tabIndex(for: tab) ?? 0
            
            // Update UIKit tab bar if available
            self?.tabBarController?.selectedIndex = index
            
            // Notify SwiftUI via callback
            self?.onTabSelectionChange?(index)
        }

        // Listen for route changes from Compose
        // Note: We only handle visibility here - back button is handled by SharedComposeHost
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("ComposeRouteChanged"), 
            object: nil, 
            queue: .main
        ) { [weak self] note in
            guard let info = note.userInfo else { return }
            
            let shouldHideTab = (info["shouldHideTab"] as? Bool) ?? false
            let shouldHideNav = (info["shouldHideNavigationBar"] as? Bool) ?? false
            let shouldShowBackButton = (info["shouldShowBackButton"] as? Bool) ?? false

            print("📡 ComposeBridge received ComposeRouteChanged:")
            print("   shouldHideTab: \(shouldHideTab)")
            print("   shouldHideNav: \(shouldHideNav)")
            print("   shouldShowBackButton: \(shouldShowBackButton)")

            // Update UIKit tab bar if available
            if let tabBar = self?.tabBarController?.tabBar {
                UIView.animate(withDuration: 0.3) {
                    tabBar.isHidden = shouldHideTab
                }
            }

            // Update UIKit navigation bar if available (only for pure UIKit apps)
            if let nav = self?.navigationController {
                nav.setNavigationBarHidden(shouldHideNav, animated: true)
                
                if shouldShowBackButton {
                    let backItem = UIBarButtonItem(
                        title: "Back", 
                        style: .plain, 
                        target: self, 
                        action: #selector(self?.handleNativeBack(_:))
                    )
                    nav.topViewController?.navigationItem.leftBarButtonItem = backItem
                } else {
                    nav.topViewController?.navigationItem.leftBarButtonItem = nil
                }
            }
            
            // Notify SwiftUI via callbacks (SharedComposeHost uses these)
            self?.onTabBarVisibilityChange?(shouldHideTab)
            self?.onNavigationBarVisibilityChange?(shouldHideNav)
            self?.onBackButtonVisibilityChange?(shouldShowBackButton)
        }
        
        // Listen for explicit tab bar show/hide requests
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("ShowNativeTabBar"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.tabBarController?.tabBar.isHidden = false
            self?.onTabBarVisibilityChange?(false)
        }
        
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("HideNativeTabBar"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.tabBarController?.tabBar.isHidden = true
            self?.onTabBarVisibilityChange?(true)
        }
        
        // Listen for explicit navigation bar show/hide requests
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("ShowNativeNavigationBar"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.navigationController?.setNavigationBarHidden(false, animated: true)
            self?.onNavigationBarVisibilityChange?(false)
        }
        
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("HideNativeNavigationBar"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.navigationController?.setNavigationBarHidden(true, animated: true)
            self?.onNavigationBarVisibilityChange?(true)
        }
    }

    @objc private func handleNativeBack(_ sender: Any) {
        // Add haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .light)
        impactFeedback.impactOccurred()

        // Post notification to Compose to handle back navigation
        NotificationCenter.default.post(
            name: Notification.Name("ComposeBackPressed"),
            object: nil
        )
    }
    
    private func tabIndex(for route: String) -> Int {
        switch route {
        case "HomePage", "Home": return 0
        case "HabitCoachingPage", "Habits": return 1
        case "ChatScreen", "Chat": return 2
        case "meditation", "Meditate": return 3
        case "profile", "Profile": return 4
        default: return 0
        }
    }
    
    // Helper to programmatically switch tabs
    func switchToTab(_ index: Int) {
        tabBarController?.selectedIndex = index
        onTabSelectionChange?(index)
    }
    
    // Helper to programmatically switch tabs by route name
    func switchToTab(route: String) {
        let index = tabIndex(for: route)
        switchToTab(index)
    }
}

// MARK: - Original Code

// Helper to apply native interface style and update native bar appearances
func applyNativeInterfaceStyle(dark: Bool?, useSystem: Bool) {
    DispatchQueue.main.async {
        let style: UIUserInterfaceStyle = useSystem ? .unspecified : ((dark ?? false) ? .dark : .light)
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
            for window in windowScene.windows {
                window.overrideUserInterfaceStyle = style
            }
        }

        // Update navigation bar appearance to use system background (adapts to dark/light)
        let navAppearance = UINavigationBarAppearance()
        navAppearance.configureWithDefaultBackground()
        UINavigationBar.appearance().standardAppearance = navAppearance
        UINavigationBar.appearance().scrollEdgeAppearance = navAppearance
        UINavigationBar.appearance().tintColor = UIColor.label

        // Update tab bar appearance
        let tabAppearance = UITabBarAppearance()
        tabAppearance.configureWithDefaultBackground()
        UITabBar.appearance().standardAppearance = tabAppearance
        if #available(iOS 15.0, *) {
            UITabBar.appearance().scrollEdgeAppearance = tabAppearance
        }
        UITabBar.appearance().tintColor = UIColor.systemBlue
        UITabBar.appearance().unselectedItemTintColor = UIColor.secondaryLabel
    }
}

// Shared Compose host that uses a single view across all tabs with safe area handling
struct SharedComposeHost: View {
    @Binding var selectedTab: Int
    @Binding var hideTabBar: Bool
    @Binding var hideNavigationBar: Bool
    private let tabRoutes = ["HomePage", "HabitCoachingPage", "ChatScreen", "meditation", "profile"]

    @State private var composeReady: Bool = false
    @State private var lastRequestedRoute: String? = nil
    @State private var observerAdded: Bool = false
    @State private var showBackButton: Bool = false
    @State private var currentRoute: String = ""
    private let composeReadyNotification = Notification.Name("ComposeReady")

    var body: some View {
        ZStack(alignment: .topTrailing) {
            ComposeViewController(onClose: nil)
                .edgesIgnoringSafeArea(.bottom)
                .padding(.top, -20)
        }
        .onAppear {
                    // Only register observer once globally
                    if !observerAdded {
                        observerAdded = true

                        // Listen for ComposeReady
                        NotificationCenter.default.addObserver(
                            forName: composeReadyNotification,
                            object: nil,
                            queue: .main
                        ) { _ in
                            composeReady = true
                            if let queued = lastRequestedRoute {
                                sendRouteWithRetries(route: queued)
                                lastRequestedRoute = nil
                            }
                        }

                        // Listen for route changes from Compose
                        NotificationCenter.default.addObserver(
                            forName: Notification.Name("ComposeRouteChanged"),
                            object: nil,
                            queue: .main
                        ) { notification in
                            print("🔍 SharedComposeHost: ComposeRouteChanged notification received")
                            print("   userInfo: \(notification.userInfo ?? [:])")
                            
                            if let route = notification.userInfo?["route"] as? String {
                                currentRoute = route
                                print("   📍 Current route: \(route)")
                            }

                            // Update back button visibility
                            // Auto-detect: If shouldShowBackButton is not explicitly provided,
                            // infer it based on whether this is a main tab route
                            let isMainTabRoute = tabRoutes.contains(currentRoute)
                            
                            if let shouldShow = notification.userInfo?["shouldShowBackButton"] as? Bool {
                                // Use explicit value if provided
                                print("   ✅ shouldShowBackButton (explicit): \(shouldShow)")
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                    showBackButton = shouldShow
                                }
                                print("   🔘 Back button is now: \(showBackButton ? "VISIBLE" : "HIDDEN")")
                            } else if !currentRoute.isEmpty {
                                // Auto-detect: show back button if NOT on a main tab route
                                let autoDetectedValue = !isMainTabRoute
                                print("   🤖 shouldShowBackButton (auto-detected): \(autoDetectedValue)")
                                print("   📋 isMainTabRoute: \(isMainTabRoute)")
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                    showBackButton = autoDetectedValue
                                }
                                print("   🔘 Back button is now: \(showBackButton ? "VISIBLE" : "HIDDEN")")
                            }

                            // Update tab / navigation bar visibility if Compose provides it
                            if let shouldHideTab = notification.userInfo?["shouldHideTab"] as? Bool {
                                print("   👁️ shouldHideTab: \(shouldHideTab)")
                                DispatchQueue.main.async {
                                    withAnimation(.easeInOut) {
                                        hideTabBar = shouldHideTab
                                    }
                                }
                            }
                            if let shouldHideNav = notification.userInfo?["shouldHideNavigationBar"] as? Bool {
                                print("   🧭 shouldHideNavigationBar: \(shouldHideNav)")
                                DispatchQueue.main.async {
                                    withAnimation(.easeInOut) {
                                        hideNavigationBar = shouldHideNav
                                    }
                                }
                            }
                        }
                    }

                    let route = tabRoutes.indices.contains(selectedTab) ? tabRoutes[selectedTab] : "HomePage"
                    requestRoute(route)
                }
                .onChange(of: selectedTab) { newIndex in
                    print("SharedComposeHost: selectedTab changed to \(newIndex)")
                    guard newIndex >= 0 && newIndex < tabRoutes.count else {
                        print("SharedComposeHost: Invalid tab index \(newIndex)")
                        return
                    }
                    let route = tabRoutes[newIndex]
                    print("SharedComposeHost: Mapped to route: \(route)")
                    
                    // Hide back button when switching to a main tab
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        showBackButton = false
                    }
                    
                    requestRoute(route)
                }
                .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                if showBackButton {
                    Button(action: handleBackButton) {
                        HStack(spacing: 6) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 17, weight: .semibold))
                            Text("Back")
                                .font(.system(size: 17))
                        }
                    }
                    .transition(.opacity.combined(with: .scale))
                }
            }
        }
    }

    private func handleBackButton() {
        // Add haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .light)
        impactFeedback.impactOccurred()

        // Post notification to Compose to handle back navigation
        NotificationCenter.default.post(
            name: Notification.Name("ComposeBackPressed"),
            object: nil
        )
    }

    private func requestRoute(_ route: String) {
        sendRouteWithRetries(route: route)
        if !composeReady {
            lastRequestedRoute = route
        } else {
            lastRequestedRoute = nil
        }
    }

    private func sendRouteWithRetries(route: String) {
        print("SharedComposeHost: sendRouteWithRetries called with route: \(route)")
        
        // First, update PlatformBridge directly to trigger Compose navigation
        let bridge = PlatformBridge.shared
        print("SharedComposeHost: Before update - requestedTabSignal = \(bridge.requestedTabSignal)")
        bridge.requestedTabName = route
        bridge.requestedTabSignal = bridge.requestedTabSignal + 1
        print("SharedComposeHost: After update - requestedTabName = \(bridge.requestedTabName ?? "nil"), requestedTabSignal = \(bridge.requestedTabSignal)")
        
        // Also send via AuthManager for backwards compatibility
        AuthManager.shared.requestNavigateTo(route: route)
        
        // Retry logic to ensure navigation happens
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            bridge.requestedTabName = route
            bridge.requestedTabSignal = bridge.requestedTabSignal + 1
            print("SharedComposeHost: Retry 1 - requestedTabSignal = \(bridge.requestedTabSignal)")
            AuthManager.shared.requestNavigateTo(route: route)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            bridge.requestedTabName = route
            bridge.requestedTabSignal = bridge.requestedTabSignal + 1
            print("SharedComposeHost: Retry 2 - requestedTabSignal = \(bridge.requestedTabSignal)")
            AuthManager.shared.requestNavigateTo(route: route)
        }
    }
}

/// Wrapper to host the Compose MainViewController with singleton pattern and safe area support
struct ComposeViewController: UIViewControllerRepresentable {
    let onClose: (() -> Void)?

    // Singleton Compose ViewController shared across all tabs
    private static var sharedComposeVC: UIViewController?
    private static var isCreating = false
    
    class Coordinator {
        var hasSetupObserver = false
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }
    
    func makeUIViewController(context: Context) -> UIViewController {
        // Prevent multiple simultaneous creations
        if ComposeViewController.isCreating {
            // Return a placeholder
            let placeholder = UIViewController()
            placeholder.view.backgroundColor = .clear
            return placeholder
        }
        
        // Return the shared instance if it exists
        if let existing = ComposeViewController.sharedComposeVC {
            return existing
        }

        // Create new instance only once
        ComposeViewController.isCreating = true
        let composeVC = MainViewControllerKt.MainViewController()
        composeVC.view.backgroundColor = .clear

        // Enable user interaction for scrolling
        composeVC.view.isUserInteractionEnabled = true

        // Store as singleton
        ComposeViewController.sharedComposeVC = composeVC
        ComposeViewController.isCreating = false

        return composeVC
    }

    // Ensure the shared compose VC is visible and in front of other UI layers
    static func ensureSharedVisible() {
        DispatchQueue.main.async {
            guard let vc = ComposeViewController.sharedComposeVC else { return }
            vc.view.isHidden = false
            if let sup = vc.view.superview {
                sup.bringSubviewToFront(vc.view)
            }
        }
    }

    // Allow host to hide/show the shared compose VC when using native UI (used by ContentView when UseNativeTabBar toggles)
    static func setSharedHidden(_ hidden: Bool) {
        DispatchQueue.main.async {
            guard let vc = ComposeViewController.sharedComposeVC else { return }
            vc.view.isHidden = hidden
        }
    }


    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Only send safe area insets once per update cycle
        if !context.coordinator.hasSetupObserver {
            context.coordinator.hasSetupObserver = true
        }

        // Send safe area insets to Compose
        let safeAreaInsets = uiViewController.view.safeAreaInsets
        let insetsInfo: [String: CGFloat] = [
            "top": safeAreaInsets.top,
            "bottom": safeAreaInsets.bottom,
            "leading": safeAreaInsets.left,
            "trailing": safeAreaInsets.right
        ]
        
        NotificationCenter.default.post(
            name: Notification.Name("SafeAreaInsetsChanged"),
            object: nil,
            userInfo: insetsInfo
        )
        // Also attempt typed call into Kotlin-generated bridge if available.
        let bridge = PlatformBridge.shared
        // Swift binding should expose setSafeAreaInsets(top:bottom:leading:trailing:)
        bridge.setSafeAreaInsets(
            top: Double(insetsInfo["top"] ?? 0.0),
            bottom: Double(insetsInfo["bottom"] ?? 0.0),
            leading: Double(insetsInfo["leading"] ?? 0.0),
            trailing: Double(insetsInfo["trailing"] ?? 0.0)
        )
    }
}

/// Root SwiftUI view that uses native TabView with Compose content
struct ContentView: View {
    @EnvironmentObject var router: NativeRouter
    @State private var selectedTab: Int = 0
    @State private var showImagePicker: Bool = false
    @State private var selectedImage: UIImage? = nil
    @StateObject private var settings = AppSettings()
    @State private var authHandle: AuthStateDidChangeListenerHandle? = nil
    @State private var showChartsSheet: Bool = false
    @State private var isSignedIn: Bool = Auth.auth().currentUser != nil
    @State private var composeHidesTabBar: Bool = false
    @State private var composeHidesNavigationBar: Bool = false
    @State private var safeAreaBottom: CGFloat = 0

    // Reference to ComposeBridge for coordination
    private let composeBridge = ComposeBridge.shared

    var body: some View {
        ZStack {
            // Native TabView with shared Compose host in each tab
            TabView(selection: $selectedTab) {
                NavigationView {
                    SharedComposeHost(selectedTab: $selectedTab, hideTabBar: $composeHidesTabBar, hideNavigationBar: $composeHidesNavigationBar)
                        .navigationTitle("Home")
                        .navigationBarTitleDisplayMode(.large)
                        .navigationBarHidden(composeHidesNavigationBar)
                }
                .tabItem {
                    Label("Home", systemImage: "house")
                }
                .tag(0)

                NavigationView {
                    SharedComposeHost(selectedTab: $selectedTab, hideTabBar: $composeHidesTabBar, hideNavigationBar: $composeHidesNavigationBar)
                        .navigationTitle("Habits")
                        .navigationBarTitleDisplayMode(.large)
                        .navigationBarHidden(composeHidesNavigationBar)
                }
                .tabItem {
                    Label("Habits", systemImage: "checkmark.circle")
                }
                .tag(1)

                NavigationView {
                    SharedComposeHost(selectedTab: $selectedTab, hideTabBar: $composeHidesTabBar, hideNavigationBar: $composeHidesNavigationBar)
                        .navigationTitle("Chat")
                        .navigationBarTitleDisplayMode(.large)
                        .navigationBarHidden(composeHidesNavigationBar)
                }
                .tabItem {
                    Label("Chat", systemImage: "message")
                }
                .tag(2)

                NavigationView {
                    SharedComposeHost(selectedTab: $selectedTab, hideTabBar: $composeHidesTabBar, hideNavigationBar: $composeHidesNavigationBar)
                        .navigationTitle("Meditate")
                        .navigationBarTitleDisplayMode(.large)
                        .navigationBarHidden(composeHidesNavigationBar)
                }
                .tabItem {
                    Label("Meditate", systemImage: "apple.meditate.circle.fill")
                }
                .tag(3)

                NavigationView {
                    SharedComposeHost(selectedTab: $selectedTab, hideTabBar: $composeHidesTabBar, hideNavigationBar: $composeHidesNavigationBar)
                        .navigationTitle("Profile")
                        .navigationBarTitleDisplayMode(.large)
                        .navigationBarHidden(composeHidesNavigationBar)
                }
                .tabItem {
                    Label("Profile", systemImage: "person")
                }
                .tag(4)
            }

            // Native back button overlay driven by NativeRouter
            // Only show the overlay when Compose requested a back button AND the native
            // navigation bar is hidden. This avoids duplicates when SwiftUI's NavigationView
            // already shows a back button in the navigation bar.
            if router.showBackButton && router.navigationBarHidden {
                 VStack {
                     HStack(spacing: 8) {
                         Button(action: {
                             // add haptic
                             let impact = UIImpactFeedbackGenerator(style: .light)
                             impact.impactOccurred()
                             router.nativeBackTapped()
                         }) {
                             HStack(spacing: 6) {
                                 Image(systemName: "chevron.left")
                                     .font(.system(size: 17, weight: .semibold))
                                 Text("Back")
                                     .font(.system(size: 17))
                             }
                             .padding(8)
                             .background(.ultraThinMaterial)
                             .cornerRadius(8)
                         }
                         Spacer()
                     }
                     .padding(.top, 12)
                     .padding(.leading, 8)
                     Spacer()
                 }
                 .transition(.opacity.combined(with: .move(edge: .top)))
                 .zIndex(1100)
             }

            // Snackbar overlay
            if settings.showSnackbar {
                VStack {
                    Spacer()
                    Text(settings.snackbarMessage)
                        .padding()
                        .background(Color.black.opacity(0.8))
                        .foregroundColor(.white)
                        .cornerRadius(8)
                        .padding()
                        .padding(.bottom, 100 + safeAreaBottom)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            
            // FAB (Floating Action Button) - only show on home page (tab 0)
            if selectedTab == 0 && isSignedIn && !composeHidesTabBar {
                 VStack {
                     Spacer()
                     HStack {
                         Spacer()
                         Button(action: {
                             // Add haptic feedback
                             let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
                             impactFeedback.impactOccurred()

                             // Post notification that Compose listens to in order to show the add dialog.
                             // Compose registers a listener for "ComposeShowAddDialog" and will set showAddDialog = true.
                             NotificationCenter.default.post(
                                 name: Notification.Name("ComposeShowAddDialog"),
                                 object: nil
                             )
                         }) {
                             Image(systemName: "plus")
                                 .font(.system(size: 24, weight: .semibold))
                                 .foregroundColor(.white)
                                 .frame(width: 56, height: 56)
                                 .background(
                                     Circle()
                                         .fill(Color(red: 0.4, green: 0.2, blue: 0.6))
                                         .shadow(color: Color(red: 0.4, green: 0.2, blue: 0.6).opacity(0.5), radius: 8, x: 0, y: 4)
                                 )
                         }
                         .padding(.trailing, 20)
                         // Raise the FAB well above the tab bar
                         .padding(.bottom, 90)
                         .zIndex(1000)
                         .accessibilityLabel("Add card")
                         .accessibilityAddTraits(.isButton)
                     }
                 }
             }
        }
        .sheet(isPresented: $showImagePicker) {
            ImagePicker(selectedImage: $selectedImage, onImageSelected: { image in
                if let image = image {
                    handleImageSelected(image)
                }
                showImagePicker = false
            })
        }
        .sheet(isPresented: $showChartsSheet) {
            NavigationView {
                Group {
                    if #available(iOS 16.0, *) {
                        ChartView()
                            .navigationTitle("Charts")
                            .navigationBarTitleDisplayMode(.large)
                            .toolbar {
                                ToolbarItem(placement: .navigationBarTrailing) {
                                    Button("Done") {
                                        showChartsSheet = false
                                    }
                                }
                            }
                    } else {
                        VStack {
                            Text("Charts require iOS 16 or newer")
                                .multilineTextAlignment(.center)
                                .padding()
                        }
                        .navigationTitle("Charts")
                        .navigationBarTitleDisplayMode(.large)
                        .toolbar {
                            ToolbarItem(placement: .navigationBarTrailing) {
                                Button("Done") {
                                    showChartsSheet = false
                                }
                            }
                        }
                    }
                }
            }
        }
        .onAppear {
            // Set up ComposeBridge callbacks for SwiftUI coordination
            composeBridge.onTabSelectionChange = { [selectedTab] index in
                if selectedTab != index {
                    withAnimation {
                        self.selectedTab = index
                    }
                }
            }
            
            composeBridge.onTabBarVisibilityChange = { [composeHidesTabBar] hidden in
                if composeHidesTabBar != hidden {
                    withAnimation(.easeInOut) {
                        self.composeHidesTabBar = hidden
                    }
                }
            }
            
            composeBridge.onNavigationBarVisibilityChange = { [composeHidesNavigationBar] hidden in
                if composeHidesNavigationBar != hidden {
                    withAnimation(.easeInOut) {
                        self.composeHidesNavigationBar = hidden
                    }
                }
            }
            
            // Forward back-button visibility changes from ComposeBridge into the SwiftUI NativeRouter
            composeBridge.onBackButtonVisibilityChange = { visible in
                DispatchQueue.main.async {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        // Update both the local SharedComposeHost state and the router so overlay/back-button shows
                        self.composeHidesNavigationBar = false
                        self.composeHidesTabBar = false
                        // Update router directly (EnvironmentObject)
                        self.router.showBackButton = visible
                    }
                }
            }

            // Force update window interface style
            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                windowScene.windows.forEach { window in
                    window.overrideUserInterfaceStyle = .unspecified
                }
            }

            // Tell Compose to hide its tab bar since we're using native SwiftUI TabView
            print("ContentView: posting UseNativeTabBar -> enabled = true")
             NotificationCenter.default.post(
                 name: Notification.Name("UseNativeTabBar"),
                 object: nil,
                 userInfo: ["enabled": true]
             )

            // Note: Compose will detect "UseNativeTabBar" notification; skip direct PlatformBridge assignment

            // Register Firebase auth state listener to show/hide sign-in UI
            if authHandle == nil {
                authHandle = Auth.auth().addStateDidChangeListener { _, user in
                    DispatchQueue.main.async {
                        isSignedIn = (user != nil)
                    }
                }
            }
            
            // 🆕 Listen for tab selection updates from ComposeCoordinator
            NotificationCenter.default.addObserver(
                forName: Notification.Name("UpdateNativeTabSelection"),
                object: nil,
                queue: .main
            ) { notification in
                if let index = notification.userInfo?["index"] as? Int {
                    print("ContentView: Received UpdateNativeTabSelection -> index \(index)")
                    withAnimation {
                        selectedTab = index
                    }
                }
            }

            // Compose event observers (do not force the shared UIViewController to front so SwiftUI overlays like the FAB remain visible)
            NotificationCenter.default.addObserver(forName: Notification.Name("ComposeNavigationChanged"), object: nil, queue: .main) { note in
                // Received navigation change from Compose — we no longer force bringToFront
            }

            NotificationCenter.default.addObserver(forName: Notification.Name("ComposeReady"), object: nil, queue: .main) { _ in
                // Compose became ready. Do not force bringToFront; just handle pending charts
                let pendingAfterReady = UserDefaults.standard.bool(forKey: "OpenNativeChartsPending")
                if pendingAfterReady {
                    print("ContentView: ComposeReady detected and pending OpenNativeCharts -> opening charts")
                    showChartsSheet = true
                    UserDefaults.standard.set(false, forKey: "OpenNativeChartsPending")
                }

                // Re-post UseNativeTabBar on ComposeReady so the Kotlin observer (which may have been registered by then) receives it.
                NotificationCenter.default.post(
                    name: Notification.Name("UseNativeTabBar"),
                    object: nil,
                    userInfo: ["enabled": true]
                )

            }

            // Observe dark mode changes coming from Compose so native bars update automatically
            NotificationCenter.default.addObserver(forName: Notification.Name("ComposeDarkModeChanged"), object: nil, queue: .main) { note in
                if let info = note.userInfo as? [String: Any] {
                    let dark = info["dark"] as? Bool
                    let useSystem = info["useSystem"] as? Bool ?? true
                    applyNativeInterfaceStyle(dark: dark, useSystem: useSystem)
                }
            }

            // Ensure native tab bar visibility matches initial Compose preference
            setNativeTabBarHidden(composeHidesTabBar)

            // Listen for native-host FAB tap to request a new chat (this will be posted by the FAB below)
            NotificationCenter.default.addObserver(forName: Notification.Name("ComposeRequestNewChat"), object: nil, queue: .main) { _ in
                // Forward to Compose via AuthManager or PlatformBridge - we use AuthManager helper
                AuthManager.shared.requestNavigateTo(route: "HeroScreen")
                // Also request Compose to recreate a new chat by notifying PlatformBridge if available
                PlatformBridge.shared.requestedRoute = "newChat"
                PlatformBridge.shared.requestedRouteSignal = PlatformBridge.shared.requestedRouteSignal + 1
            }
            // Observe safe area changes posted by ComposeViewController.updateUIViewController
            NotificationCenter.default.addObserver(forName: Notification.Name("SafeAreaInsetsChanged"), object: nil, queue: .main) { note in
                if let userInfo = note.userInfo as? [String: Any], let bottom = userInfo["bottom"] as? Double {
                    safeAreaBottom = CGFloat(bottom)
                }
            }
            // Observe requests from Kotlin to open native Charts
            NotificationCenter.default.addObserver(forName: Notification.Name("OpenNativeCharts"), object: nil, queue: .main) { _ in
                print("ContentView: received OpenNativeCharts notification")
                // Show the native Charts sheet
                showChartsSheet = true
                // Clear persistent pending flag
                UserDefaults.standard.set(false, forKey: "OpenNativeChartsPending")
            }
            // If the notification was posted before the observer was installed, check persistent flag
            let pending = UserDefaults.standard.bool(forKey: "OpenNativeChartsPending")
            if pending {
                print("ContentView: found pending OpenNativeCharts flag -> opening charts")
                showChartsSheet = true
                UserDefaults.standard.set(false, forKey: "OpenNativeChartsPending")
            }
         }
        .onDisappear {
            if let h = authHandle {
                Auth.auth().removeStateDidChangeListener(h)
                authHandle = nil
            }
        }
        // Keep native UITabBar in sync with Compose's requests
        .onChange(of: composeHidesTabBar) { hidden in
            setNativeTabBarHidden(hidden)
        }
        .onChange(of: composeHidesNavigationBar) { hidden in
            // when Compose wants native nav bar hidden/shown, update the appearance on main thread
            DispatchQueue.main.async {
                // wrapped in animation to avoid jarring changes
                withAnimation(.easeInOut) {
                    // Force update windows so NavigationView picks up the change
                    if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                        for window in scene.windows {
                            window.rootViewController?.navigationController?.setNavigationBarHidden(hidden, animated: true)
                        }
                    }
                }
            }
        }
    }

    // Hide/show UITabBar instances found in the app windows.
    private func setNativeTabBarHidden(_ hidden: Bool) {
        DispatchQueue.main.async {
            guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
            for window in scene.windows {
                // traverse subviews to find UITabBar instances
                func traverse(_ view: UIView) {
                    if let tb = view as? UITabBar {
                        tb.isHidden = hidden
                    }
                    for sub in view.subviews {
                        traverse(sub)
                    }
                }
                traverse(window)
            }
        }
    }
    
    // Handle selected image and send to Compose
    private func handleImageSelected(_ image: UIImage) {
        // Convert UIImage to base64 string
        guard let imageData = image.jpegData(compressionQuality: 0.8) else {
            print("Failed to convert image to data")
            return
        }
        
        let base64String = imageData.base64EncodedString()
        
        // Send to Compose via notification
        NotificationCenter.default.post(
            name: Notification.Name("ImageSelected"),
            object: nil,
            userInfo: ["imageBase64": base64String]
        )
        
        print("✅ Image selected and sent to Compose (base64 length: \(base64String.count))")
    }
}

// VisualEffectBlur helper to get native blur background (uses UIVisualEffectView)
struct VisualEffectBlur: UIViewRepresentable {
    var blurStyle: UIBlurEffect.Style

    func makeUIView(context: Context) -> UIVisualEffectView {
        return UIVisualEffectView(effect: UIBlurEffect(style: blurStyle))
    }

    func updateUIView(_ uiView: UIVisualEffectView, context: Context) {}
}

// ImagePicker wrapper for UIImagePickerController
struct ImagePicker: UIViewControllerRepresentable {
    @Binding var selectedImage: UIImage?
    let onImageSelected: (UIImage?) -> Void
    
    class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: ImagePicker
        
        init(_ parent: ImagePicker) {
            self.parent = parent
        }
        
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let image = info[.originalImage] as? UIImage {
                parent.selectedImage = image
                parent.onImageSelected(image)
            } else {
                parent.onImageSelected(nil)
            }
            picker.dismiss(animated: true)
        }
        
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onImageSelected(nil)
            picker.dismiss(animated: true)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = .photoLibrary
        picker.allowsEditing = false
        return picker
    }
    
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
}

// --- Begin embedded Chart support (moved here so ContentView can always reference ChartView) ---
// Simple strongly-typed point model used by the SwiftUI Chart
struct ChartPoint: Identifiable {
    let id = UUID()
    let date: Date
    let value: Double
}

class ChartDataModel: ObservableObject {
    @Published var points: [ChartPoint] = []
    private var observer: NSObjectProtocol? = nil
    private let healthStore = HKHealthStore()

    init() {
        self.points = Self.sampleData()
        observer = NotificationCenter.default.addObserver(forName: Notification.Name("ChartDataUpdated"), object: nil, queue: .main) { [weak self] note in
            guard let self = self else { return }
            let payloadAny = (note.userInfo?["data"]) ?? note.object
            if let arr = payloadAny as? [[String: Any]] {
                self.update(fromMaps: arr)
            } else if let arrNS = payloadAny as? NSArray {
                self.update(from: arrNS)
            } else if let dict = note.userInfo as? [AnyHashable: Any], let arr = dict["data"] as? NSArray {
                self.update(from: arr)
            }
        }

        // Load real HealthKit data
        loadHealthKitData()
    }

    deinit {
        if let obs = observer { NotificationCenter.default.removeObserver(obs) }
    }

    func loadHealthKitData() {
        guard HKHealthStore.isHealthDataAvailable() else {
            print("HealthKit not available")
            return
        }

        guard let mindfulType = HKObjectType.categoryType(forIdentifier: .mindfulSession) else {
            print("Mindful session type not available")
            return
        }

        let typesToRead: Set<HKObjectType> = [mindfulType]

        healthStore.requestAuthorization(toShare: nil, read: typesToRead) { [weak self] success, error in
            if success {
                self?.fetchMindfulnessData()
            } else {
                print("HealthKit authorization failed: \(error?.localizedDescription ?? "unknown error")")
            }
        }
    }

    private func fetchMindfulnessData() {
        guard let mindfulType = HKObjectType.categoryType(forIdentifier: .mindfulSession) else { return }

        let calendar = Calendar.current
        let now = Date()
        guard let startDate = calendar.date(byAdding: .day, value: -6, to: calendar.startOfDay(for: now)) else { return }

        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: now, options: .strictStartDate)

        let query = HKSampleQuery(sampleType: mindfulType, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: [NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)]) { [weak self] _, samples, error in
            guard let self = self, let samples = samples as? [HKCategorySample], error == nil else {
                print("Error fetching mindfulness data: \(error?.localizedDescription ?? "unknown")")
                return
            }

            // Group samples by day and sum the durations
            var dailyMinutes: [Date: Double] = [:]
            for sample in samples {
                let dayStart = calendar.startOfDay(for: sample.startDate)
                let duration = sample.endDate.timeIntervalSince(sample.startDate) / 60.0 // Convert to minutes
                dailyMinutes[dayStart, default: 0] += duration
            }

            // Create chart points for the last 7 days (even if no data for some days)
            var newPoints: [ChartPoint] = []
            for i in 0..<7 {
                if let day = calendar.date(byAdding: .day, value: -6 + i, to: calendar.startOfDay(for: now)) {
                    let minutes = dailyMinutes[day] ?? 0
                    newPoints.append(ChartPoint(date: day, value: minutes))
                }
            }

            DispatchQueue.main.async {
                self.points = newPoints
            }
        }

        healthStore.execute(query)
    }

    private func update(fromMaps maps: [[String: Any]]) {
        var newPoints: [ChartPoint] = []
        for map in maps {
            if let xAny = map["x"], let yAny = map["y"], let date = Self.dateFrom(xAny), let value = Self.doubleFrom(yAny) {
                newPoints.append(ChartPoint(date: date, value: value))
            }
        }
        newPoints.sort { $0.date < $1.date }
        DispatchQueue.main.async { self.points = newPoints }
    }

    func update(from arr: NSArray) {
        var newPoints: [ChartPoint] = []
        for item in arr {
            if let map = item as? NSDictionary {
                if let x = map["x"] ?? map["timestamp"] ?? map["date"], let yAny = map["y"] ?? map["value"] {
                    if let date = Self.dateFrom(x), let value = Self.doubleFrom(yAny) {
                        newPoints.append(ChartPoint(date: date, value: value))
                        continue
                    }
                }
                if let dateStr = map["date"] as? String, let valueAny = map["value"], let value = Self.doubleFrom(valueAny), let date = Self.dateFrom(dateStr) {
                    newPoints.append(ChartPoint(date: date, value: value))
                    continue
                }
            }
            if let pair = item as? NSArray, pair.count >= 2 {
                let a = pair[0]
                let b = pair[1]
                if let date = Self.dateFrom(a), let value = Self.doubleFrom(b) {
                    newPoints.append(ChartPoint(date: date, value: value))
                    continue
                }
            }
            if let number = item as? NSNumber {
                let date = Date()
                newPoints.append(ChartPoint(date: date, value: number.doubleValue))
            }
        }
        newPoints.sort { $0.date < $1.date }
        DispatchQueue.main.async { self.points = newPoints }
    }

    private static func dateFrom(_ any: Any) -> Date? {
        if let d = any as? Date { return d }
        if let n = any as? NSNumber {
            let val = n.doubleValue
            if val > 1_000_000_000_000 { return Date(timeIntervalSince1970: val / 1000.0) }
            if val > 1_000_000_000 { return Date(timeIntervalSince1970: val) }
            return Date().addingTimeInterval(val)
        }
        if let s = any as? String {
            let iso = ISO8601DateFormatter()
            if let d = iso.date(from: s) { return d }
            if let dbl = Double(s) { return dateFrom(NSNumber(value: dbl)) }
        }
        return nil
    }

    private static func doubleFrom(_ any: Any) -> Double? {
        if let d = any as? Double { return d }
        if let n = any as? NSNumber { return n.doubleValue }
        if let s = any as? String { return Double(s) }
        return nil
    }

    static func sampleData() -> [ChartPoint] {
        let now = Date()
        return (0..<7).map { i in
            ChartPoint(date: Calendar.current.date(byAdding: .day, value: -6 + i, to: now) ?? now, value: Double(arc4random_uniform(80) + 20))
        }
    }
}

#if canImport(Charts)
@available(iOS 16.0, *)
struct ChartView: View {
    @StateObject private var model = ChartDataModel()

    // Helper to convert points into day labels and values
    private func dayLabel(_ date: Date) -> String {
        let df = DateFormatter()
        df.dateFormat = "E"
        return df.string(from: date)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .center) {
                    Text("Key metrics")
                        .font(.title2)
                        .bold()
                    Spacer()
                    Button(action: {
                        model.loadHealthKitData()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.clockwise")
                            Text("Refresh")
                        }
                        .font(.subheadline)
                    }
                }
                .padding(.horizontal)

                // Small metric cards
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        MetricSmallCard(title: "Meditation", value: totalMinutesString() + " min")
                        MetricSmallCard(title: "Sessions", value: String(model.points.filter { $0.value > 0 }.count))
                        MetricSmallCard(title: "Avg min", value: averageString() + " min")
                    }
                    .padding(.horizontal)
                }

                // Large detail card
                MetricDetailCard(title: "Meditation minutes", subtitle: "Last 7 days", points: model.points)
                    .padding(.horizontal)

                // Extra spacing
                Spacer(minLength: 30)
            }
            .padding(.top)
        }
    }

    private func totalMinutesString() -> String {
        let total = model.points.map { $0.value }.reduce(0, +)
        return String(Int(total))
    }

    private func averageString() -> String {
        let vals = model.points.filter { $0.value > 0 }.map { $0.value }
        guard !vals.isEmpty else { return "0" }
        let avg = vals.reduce(0, +) / Double(vals.count)
        return String(Int(avg))
    }
}

@available(iOS 16.0, *)
private struct MetricSmallCard: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline)
                .foregroundColor(.secondary)
            Text(value)
                .font(.title2)
                .bold()
        }
        .padding()
        .frame(width: 140, height: 100)
        .background(.regularMaterial)
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 4)
    }
}

@available(iOS 16.0, *)
private struct MetricDetailCard: View {
    let title: String
    let subtitle: String
    let points: [ChartPoint]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading) {
                    Text(title).font(.headline)
                    Text(subtitle).font(.subheadline).foregroundColor(.secondary)
                }
                Spacer()
                Text(totalString() + " min").font(.title).bold()
            }

            let hasAnyData = points.contains { $0.value > 0 }

            if points.isEmpty {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(UIColor.systemGray6))
                    .frame(height: 160)
                    .overlay(
                        VStack(spacing: 8) {
                            Image(systemName: "chart.bar.xaxis")
                                .font(.system(size: 40))
                                .foregroundColor(.secondary)
                            Text("No data available")
                                .foregroundColor(.secondary)
                            Text("Pull to refresh after recording meditation sessions")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal)
                        }
                    )
            } else if !hasAnyData {
                Chart {
                    ForEach(points) { p in
                        BarMark(x: .value("Date", p.date), y: .value("Value", 0))
                            .foregroundStyle(Color(UIColor.systemGray5))
                            .cornerRadius(6)
                    }
                }
                .chartXAxis { AxisMarks(values: .automatic) { _ in AxisValueLabel(format: .dateTime.weekday(.abbreviated)) } }
                .chartYAxis { AxisMarks(position: .leading) }
                .frame(height: 180)
                .overlay(
                    VStack {
                        Text("No meditation sessions recorded")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                )
            } else {
                Chart {
                    ForEach(points) { p in
                        BarMark(x: .value("Date", p.date), y: .value("Value", p.value))
                            .foregroundStyle(LinearGradient(gradient: Gradient(colors: [Color.accentColor.opacity(0.9), Color.accentColor.opacity(0.3)]), startPoint: .top, endPoint: .bottom))
                            .cornerRadius(6)
                        LineMark(x: .value("Date", p.date), y: .value("Value", p.value))
                            .interpolationMethod(.catmullRom)
                            .foregroundStyle(Color.accentColor)
                    }
                }
                .chartXAxis { AxisMarks(values: .automatic) { _ in AxisValueLabel(format: .dateTime.weekday(.abbreviated)) } }
                .chartYAxis { AxisMarks(position: .leading) }
                .frame(height: 180)
            }
        }
        .padding()
        .background(.regularMaterial)
        .cornerRadius(16)
        .shadow(color: Color.black.opacity(0.04), radius: 8, x: 0, y: 6)
    }

    private func totalString() -> String {
        let total = points.map { $0.value }.reduce(0, +)
        return String(Int(total))
    }
}

#else
@available(iOS 16.0, *)
struct ChartView: View {
    var body: some View { Text("Charts unavailable on this SDK").padding() }
}
#endif
// --- End embedded Chart support ---























































