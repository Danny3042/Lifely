import UIKit
import SwiftUI
import ComposeApp
import FirebaseAuth
import HealthKit
import PhotosUI
#if canImport(Charts)
import Charts
#endif

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
    private let tabRoutes = ["HomePage", "HabitCoachingPage", "ChatScreen", "meditation", "profile"]

    @State private var composeReady: Bool = false
    @State private var lastRequestedRoute: String? = nil
    @State private var observerAdded: Bool = false
    @State private var showBackButton: Bool = false
    @State private var currentRoute: String = ""
    private let composeReadyNotification = Notification.Name("ComposeReady")

    var body: some View {
        GeometryReader { geometry in
            ComposeViewController(onClose: nil)
                .frame(width: geometry.size.width, height: geometry.size.height)
                // Add bottom padding equal to the safe area so Compose content doesn't go under the tab bar
                .padding(.bottom, geometry.safeAreaInsets.bottom)
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
                            if let route = notification.userInfo?["route"] as? String {
                                currentRoute = route

                                // Show back button for sub-pages
                                let mainRoutes = ["HomePage", "HabitCoachingPage", "ChatScreen", "meditation", "profile", "HeroScreen", "Login", "SignUp", "ResetPassword"]

                                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                    showBackButton = !mainRoutes.contains(route)
                                }
                            }
                        }
                    }

                    let route = tabRoutes.indices.contains(selectedTab) ? tabRoutes[selectedTab] : "HomePage"
                    requestRoute(route)
                }
                .onChange(of: selectedTab) { newIndex in
                    guard newIndex >= 0 && newIndex < tabRoutes.count else { return }
                    let route = tabRoutes[newIndex]
                    requestRoute(route)
                }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
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
        AuthManager.shared.requestNavigateTo(route: route)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { 
            AuthManager.shared.requestNavigateTo(route: route) 
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { 
            AuthManager.shared.requestNavigateTo(route: route) 
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { 
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

    var body: some View {
        ZStack {
            // Native TabView with shared Compose host in each tab
            TabView(selection: $selectedTab) {
                NavigationView {
                    SharedComposeHost(selectedTab: $selectedTab)
                        .navigationTitle("Home")
                        .navigationBarTitleDisplayMode(.large)
                        .navigationBarHidden(composeHidesNavigationBar)
                }
                .tabItem {
                    Label("Home", systemImage: "house")
                }
                .tag(0)

                NavigationView {
                    SharedComposeHost(selectedTab: $selectedTab)
                        .navigationTitle("Habits")
                        .navigationBarTitleDisplayMode(.large)
                        .navigationBarHidden(composeHidesNavigationBar)
                }
                .tabItem {
                    Label("Habits", systemImage: "checkmark.circle")
                }
                .tag(1)

                NavigationView {
                    SharedComposeHost(selectedTab: $selectedTab)
                        .navigationTitle("Chat")
                        .navigationBarTitleDisplayMode(.large)
                        .navigationBarHidden(composeHidesNavigationBar)
                }
                .tabItem {
                    Label("Chat", systemImage: "message")
                }
                .tag(2)

                NavigationView {
                    SharedComposeHost(selectedTab: $selectedTab)
                        .navigationTitle("Meditate")
                        .navigationBarTitleDisplayMode(.large)
                        .navigationBarHidden(composeHidesNavigationBar)
                }
                .tabItem {
                    Label("Meditate", systemImage: "apple.meditate.circle.fill")
                }
                .tag(3)

                NavigationView {
                    SharedComposeHost(selectedTab: $selectedTab)
                        .navigationTitle("Profile")
                        .navigationBarTitleDisplayMode(.large)
                        .navigationBarHidden(composeHidesNavigationBar)
                }
                .tabItem {
                    Label("Profile", systemImage: "person")
                }
                .tag(4)
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

            // Native FAB overlay (iOS) - anchored bottom-end above the tab bar
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    Button(action: {
                        // Tell Compose to create a new chat / handle new chat
                        NotificationCenter.default.post(name: Notification.Name("ComposeRequestNewChat"), object: nil)
                    }) {
                        Image(systemName: "plus")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 56, height: 56)
                            .background(Circle().fill(Color(.systemBlue)))
                    }
                    .padding(.trailing, 20)
                    .padding(.bottom, safeAreaBottom + 20)
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
            // Force update window interface style
            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                windowScene.windows.forEach { window in
                    window.overrideUserInterfaceStyle = .unspecified
                }
            }

            // Register Firebase auth state listener to show/hide sign-in UI
            if authHandle == nil {
                authHandle = Auth.auth().addStateDidChangeListener { _, user in
                    DispatchQueue.main.async {
                        isSignedIn = (user != nil)
                    }
                }
            }

            // Compose event observers (do not force the shared UIViewController to front so SwiftUI overlays like the FAB remain visible)
            NotificationCenter.default.addObserver(forName: Notification.Name("ComposeNavigationChanged"), object: nil, queue: .main) { note in
                // Received navigation change from Compose — we no longer force the shared VC to the front
            }

            NotificationCenter.default.addObserver(forName: Notification.Name("ComposeReady"), object: nil, queue: .main) { _ in
                // Compose became ready. Do not force bringToFront; just handle pending charts
                let pendingAfterReady = UserDefaults.standard.bool(forKey: "OpenNativeChartsPending")
                if pendingAfterReady {
                    print("ContentView: ComposeReady detected and pending OpenNativeCharts -> opening charts")
                    showChartsSheet = true
                    UserDefaults.standard.set(false, forKey: "OpenNativeChartsPending")
                }
            }

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




























