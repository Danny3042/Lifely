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
        FirebaseApp.configure()
        // Make hosting window backgrounds clear so Compose window underlay can show through
        UIWindow.appearance().backgroundColor = .clear
        
        // Configure navigation bar appearance for proper dark mode support
        let navigationBarAppearance = UINavigationBarAppearance()
        navigationBarAppearance.configureWithDefaultBackground()
        UINavigationBar.appearance().standardAppearance = navigationBarAppearance
        UINavigationBar.appearance().compactAppearance = navigationBarAppearance
        UINavigationBar.appearance().scrollEdgeAppearance = navigationBarAppearance
        
        // Configure tab bar appearance for proper dark mode support
        let tabBarAppearance = UITabBarAppearance()
        tabBarAppearance.configureWithDefaultBackground()
        UITabBar.appearance().standardAppearance = tabBarAppearance
        if #available(iOS 15.0, *) {
            UITabBar.appearance().scrollEdgeAppearance = tabBarAppearance
        }
        
        // Observe Compose dark-mode changes so native bars can match the Compose theme
        NotificationCenter.default.addObserver(forName: NSNotification.Name("ComposeDarkModeChanged"), object: nil, queue: .main) { notification in
            let userInfo = notification.userInfo
            let dark = (userInfo?["dark"] as? Bool) ?? false
            let useSystem = (userInfo?["useSystem"] as? Bool) ?? true

            // Prepare new appearances based on flags
            let newNavAppearance = UINavigationBarAppearance()
            let newTabAppearance = UITabBarAppearance()

            if useSystem {
                // Let the system decide (follow device appearance)
                newNavAppearance.configureWithDefaultBackground()
                newTabAppearance.configureWithDefaultBackground()
                UINavigationBar.appearance().tintColor = nil
                UITabBar.appearance().tintColor = nil
                UITabBar.appearance().unselectedItemTintColor = nil
            } else if dark {
                // Force dark styling for bars
                newNavAppearance.configureWithOpaqueBackground()
                newNavAppearance.backgroundColor = UIColor { trait in
                    return .black
                }
                newNavAppearance.titleTextAttributes = [.foregroundColor: UIColor.white]
                newNavAppearance.largeTitleTextAttributes = [.foregroundColor: UIColor.white]

                newTabAppearance.configureWithOpaqueBackground()
                newTabAppearance.backgroundColor = UIColor.black
                UITabBar.appearance().tintColor = UIColor.systemBlue
                UITabBar.appearance().unselectedItemTintColor = UIColor.lightGray
            } else {
                // Force light styling for bars
                newNavAppearance.configureWithOpaqueBackground()
                newNavAppearance.backgroundColor = UIColor.white
                newNavAppearance.titleTextAttributes = [.foregroundColor: UIColor.black]
                newNavAppearance.largeTitleTextAttributes = [.foregroundColor: UIColor.black]

                newTabAppearance.configureWithOpaqueBackground()
                newTabAppearance.backgroundColor = UIColor.white
                UITabBar.appearance().tintColor = UIColor.systemBlue
                UITabBar.appearance().unselectedItemTintColor = UIColor.gray
            }

            // Apply the computed appearances
            UINavigationBar.appearance().standardAppearance = newNavAppearance
            UINavigationBar.appearance().compactAppearance = newNavAppearance
            UINavigationBar.appearance().scrollEdgeAppearance = newNavAppearance

            UITabBar.appearance().standardAppearance = newTabAppearance
            if #available(iOS 15.0, *) {
                UITabBar.appearance().scrollEdgeAppearance = newTabAppearance
            }

            // Determine the override user interface style for windows
            let overrideStyle: UIUserInterfaceStyle = {
                if useSystem { return .unspecified }
                return dark ? .dark : .light
            }()

            // Force the system to update existing bars and window interface style
            DispatchQueue.main.async {
                for scene in UIApplication.shared.connectedScenes {
                    if let ws = scene as? UIWindowScene {
                        for w in ws.windows {
                            w.overrideUserInterfaceStyle = overrideStyle
                            w.rootViewController?.setNeedsStatusBarAppearanceUpdate()
                            w.rootViewController?.view.setNeedsLayout()
                        }
                    }
                }
            }
        }

        logInitialEvent()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(router)
                .onOpenURL { url in
                    #if canImport(GoogleSignIn)
                    GIDSignIn.sharedInstance.handle(url)
                    #endif
                    UIApplication.shared.endEditing()
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

            DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                self.requestTrackingPermission()
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
    }
}
    extension UIApplication {
        func endEditing() {
            sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        }
    }
