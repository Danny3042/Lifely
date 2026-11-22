import SwiftUI
import FirebaseCore
import FirebaseAnalytics
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif
import FirebaseMessaging
import UserNotifications
import AppTrackingTransparency
import Combine

// Simple NativeRouter placed here so the type is available to the App entry point
final class NativeRouter: ObservableObject {
    @Published var selectedTabIndex: Int = 0
    @Published var navigationBarHidden: Bool = false
    @Published var showBackButton: Bool = false

    private var observers: [NSObjectProtocol] = []

    init() {
        // Listen for ComposeRouteChanged
        let o1 = NotificationCenter.default.addObserver(forName: NSNotification.Name("ComposeRouteChanged"), object: nil, queue: .main) { [weak self] note in
            guard let info = note.userInfo else { return }
            if let idx = info["tabIndex"] as? Int {
                self?.selectedTabIndex = idx
            }
            if let hideNav = info["shouldHideNavigationBar"] as? Bool {
                self?.navigationBarHidden = hideNav
            }
            if let show = info["shouldShowBackButton"] as? Bool {
                self?.showBackButton = show
            }
        }
        observers.append(o1)

        let o2 = NotificationCenter.default.addObserver(forName: NSNotification.Name("ComposeShowBackButton"), object: nil, queue: .main) { [weak self] _ in
            self?.showBackButton = true
        }
        observers.append(o2)

        let o3 = NotificationCenter.default.addObserver(forName: NSNotification.Name("ComposeHideBackButton"), object: nil, queue: .main) { [weak self] _ in
            self?.showBackButton = false
        }
        observers.append(o3)

        let o4 = NotificationCenter.default.addObserver(forName: NSNotification.Name("SwitchNativeTab"), object: nil, queue: .main) { [weak self] note in
            if let tab = note.userInfo?["tab"] as? String {
                let idx: Int
                switch tab {
                case "HomePage","Home": idx = 0
                case "HabitCoachingPage","Habits": idx = 1
                case "ChatScreen","Chat": idx = 2
                case "meditation","Meditate": idx = 3
                case "profile","Profile": idx = 4
                default: idx = 0
                }
                self?.selectedTabIndex = idx
            }
        }
        observers.append(o4)

        print("NativeRouter (in iOSApp.swift) initialized and observing notifications")
    }

    func nativeBackTapped() {
        NotificationCenter.default.post(name: NSNotification.Name("ComposeBackPressed"), object: nil)
    }

    deinit {
        for o in observers { NotificationCenter.default.removeObserver(o) }
    }
}

@main
struct iOSApp: App {
    
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    // NativeRouter syncs Compose notifications to SwiftUI. Inject as an environment object.
    @StateObject private var router = NativeRouter()
    
    init() {
        // Pure SwiftUI approach - no UIKit appearance configuration
        // Light/dark mode is applied to the windows by AppAppearance (see below)
        // Make hosting window backgrounds clear so Compose window underlay can show through
        UIWindow.appearance().backgroundColor = .clear
        // Ensure tab bar uses system background so it matches light/dark mode by default
        let tabAppearance = UITabBarAppearance()
        tabAppearance.configureWithDefaultBackground()
        tabAppearance.backgroundColor = UIColor.systemBackground
        UITabBar.appearance().standardAppearance = tabAppearance
        if #available(iOS 15.0, *) {
            UITabBar.appearance().scrollEdgeAppearance = tabAppearance
        }

        // Do not configure Firebase here: defer to AppDelegate after requesting
        // AppTrackingTransparency permission so SDKs that access IDFA do not run
        // before the system prompt and the Info.plist key is present.
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(router)
                .onOpenURL { url in
                    // Widgets: myhealthbuddy://meditate, myhealthbuddy://checkin
                    if WidgetLinks.handle(url) { return }
#if canImport(GoogleSignIn)
                    GIDSignIn.sharedInstance.handle(url)
#endif
                    // Dismiss the keyboard / end editing on the current first responder.
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }
        }
    }
    
    func logInitialEvent() {
        // logging app open event
        Analytics.logEvent(AnalyticsEventAppOpen, parameters: nil)
    }
    
    
    
    
    class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
        
        // Coordinator for Compose <-> Native communication
        private var composeCoordinator: ComposeCoordinator?
        
        func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
            // Meditation countdown in the Dynamic Island / on the Lock Screen
            MeditationLiveActivityController.shared.activate()
            // Home and Lock Screen widgets
            WidgetRefresher.shared.activate()
            
            // Ensure existing windows are transparent
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                for scene in UIApplication.shared.connectedScenes {
                    if let ws = scene as? UIWindowScene {
                        for w in ws.windows {
                            w.backgroundColor = .clear
                        }
                    }
                }
            }
            
            // Initialize and install Compose coordinator observers
            // Note: For SwiftUI TabView apps, we pass nil for both parameters
            // The coordinator will use notifications to communicate with ContentView instead
            composeCoordinator = ComposeCoordinator(tabBarController: nil, navigationController: nil)
            composeCoordinator?.installObservers()
            
            // Setup dark mode observer
            setupDarkModeObserver()

            // Configure Firebase immediately so Auth and other services work
            // Note: We configure first, but delay Analytics events until after tracking permission
            FirebaseApp.configure()

            // Let the app show its own notifications (e.g. "Meditation complete") while it's open too
            UNUserNotificationCenter.current().delegate = self
            
            // Request AppTrackingTransparency permission before logging Analytics events
            // This ensures IDFA-related tracking only happens after user consent
            if #available(iOS 14, *) {
                ATTrackingManager.requestTrackingAuthorization { status in
                    DispatchQueue.main.async {
                        // Log app open now that the user has been prompted for tracking
                        Analytics.logEvent(AnalyticsEventAppOpen, parameters: nil)
                        // Keep a record of the status if needed
                        switch status {
                        case .authorized:
                            // Tracking authorized - Analytics will work normally
                            break
                        case .denied:
                            // Disable analytics collection if tracking denied
                            Analytics.setAnalyticsCollectionEnabled(false)
                        default:
                            break
                        }
                    }
                }
            } else {
                // iOS versions <14: log Analytics event immediately
                Analytics.logEvent(AnalyticsEventAppOpen, parameters: nil)
            }

            return true
        }
        
        
        func application(
            _ app: UIApplication,
            open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]
        ) -> Bool {
            var handled: Bool
            
            // let Google Sign in handle the URL if it is related to Google Sign in
            handled = GIDSignIn.sharedInstance.handle(url)
            if handled {
                return true
            }
            
            // Handle other custom URL types
            // if not handled by this app return false
            return false
        }
        
        
        
        // A notification arriving while the app is open: show it as a banner with sound, as when closed
        func userNotificationCenter(
            _ center: UNUserNotificationCenter,
            willPresent notification: UNNotification,
            withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
        ) {
            completionHandler([.banner, .list, .sound])
        }

        private func requestTrackingPermission() {
            ATTrackingManager.requestTrackingAuthorization { status in
                switch status {
                case .authorized:
                    // Tracking authorized
                    break
                case .denied:
                    // Tracking denied
                    Analytics.setAnalyticsCollectionEnabled(false)
                case .restricted:
                    // Tracking restricted
                    break
                case .notDetermined:
                    // Tracking not determined
                    break
                @unknown default:
                    // Handle unknown status
                    break
                }
            }
            
        }
        
        private func setupDarkModeObserver() {
            // Compose posts this whenever the user changes Appearance (System / Light / Dark).
            // Apply it to the windows straight away so the native tab bar, navigation bar, sheets
            // and the Compose content all switch together, with their normal system styling.
            NotificationCenter.default.addObserver(forName: NSNotification.Name("ComposeDarkModeChanged"), object: nil, queue: .main) { notification in
                let dark = (notification.userInfo?["dark"] as? Bool) ?? false
                let useSystem = (notification.userInfo?["useSystem"] as? Bool) ?? true
                AppAppearance.apply(dark: dark, useSystem: useSystem)
            }
        }
    }
}
extension UIApplication {
    func endEditing() {
        sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

/// Applies the app's Appearance setting (System / Light / Dark) to every window.
/// Setting the window's interface style is the one reliable switch on iOS: system bars, sheets and
/// the hosted Compose view all follow it, and `.unspecified` hands control back to the device.
enum AppAppearance {
    static func apply(dark: Bool, useSystem: Bool) {
        let style: UIUserInterfaceStyle = useSystem ? .unspecified : (dark ? .dark : .light)
        for scene in UIApplication.shared.connectedScenes {
            guard let windowScene = scene as? UIWindowScene else { continue }
            for window in windowScene.windows where window.overrideUserInterfaceStyle != style {
                window.overrideUserInterfaceStyle = style
            }
        }
    }

    /// Re-applies the saved choice (keys written by the Compose side).
    static func applySaved() {
        let defaults = UserDefaults.standard
        let useSystem = (defaults.object(forKey: "useSystemDefault") as? Bool) ?? true
        apply(dark: defaults.bool(forKey: "isDarkMode"), useSystem: useSystem)
    }
}
