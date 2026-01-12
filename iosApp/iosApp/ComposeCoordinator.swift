import Foundation
import UIKit
import SwiftUI

/// Coordinator for managing bidirectional communication between Compose and native SwiftUI/UIKit
/// This installs observers for Compose -> native navigation and appearance changes.
class ComposeCoordinator {
    
    // MARK: - Properties
    
    /// Reference to the UITabBarController if using UIKit tabs
    weak var tabBarController: UITabBarController?
    
    /// Reference to the UINavigationController if using UIKit navigation
    weak var navigationController: UINavigationController?
    
    // For SwiftUI TabView support - binding to selectedTab state
    private var selectedTabBinding: Binding<Int>?
    
    // MARK: - Initializer
    
    init(tabBarController: UITabBarController? = nil, navigationController: UINavigationController? = nil) {
        self.tabBarController = tabBarController
        self.navigationController = navigationController
    }
    
    /// Alternative initializer for SwiftUI apps with tab binding
    init(selectedTabBinding: Binding<Int>? = nil) {
        self.selectedTabBinding = selectedTabBinding
    }
    
    // MARK: - Setup Observers
    
    /// Install all Compose observers for bidirectional communication
    func installObservers() {
        print("🔷 ComposeCoordinator: Installing observers")
        setupTabSwitchObserver()
        setupRouteChangedObserver()
        print("✅ ComposeCoordinator: Observers installed")
    }
    
    // MARK: - Private Observer Setup
    
    /// 1) Observe Compose -> native tab changes
    private func setupTabSwitchObserver() {
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("SwitchNativeTab"),
            object: nil,
            queue: .main
        ) { [weak self] notification in
            print("🔷 ComposeCoordinator: Received SwitchNativeTab")
            
            guard let userInfo = notification.userInfo,
                  let tab = userInfo["tab"] as? String else {
                print("❌ ComposeCoordinator: Missing tab parameter")
                return
            }
            
            print("🔷 ComposeCoordinator: Tab name = \(tab)")
            
            // Map Kotlin tab names to native index (update mapping to match your tab order)
            let index: Int
            switch tab {
            case "HomePage", "Home":
                index = 0
            case "HabitCoachingPage", "Habits":
                index = 1
            case "ChatScreen", "Chat":
                index = 2
            case "meditation", "Meditate":
                index = 3
            case "profile", "Profile":
                index = 4
            default:
                print("⚠️ ComposeCoordinator: Unknown tab '\(tab)', defaulting to 0")
                index = 0
            }
            
            print("🔷 ComposeCoordinator: Mapped to index \(index)")
            
            DispatchQueue.main.async {
                if let tbc = self?.tabBarController {
                    print("✅ ComposeCoordinator: Updating UITabBarController to index \(index)")
                    tbc.selectedIndex = index
                } else {
                    // If using SwiftUI TabView, post notification for ContentView to observe
                    print("✅ ComposeCoordinator: Posting UpdateNativeTabSelection for index \(index)")
                    NotificationCenter.default.post(
                        name: Notification.Name("UpdateNativeTabSelection"),
                        object: nil,
                        userInfo: ["index": index]
                    )
                }
            }
        }
    }
    
    /// 2) Observe ComposeRouteChanged so we can hide/show native tab bar and nav bar/back button
    private func setupRouteChangedObserver() {
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("ComposeRouteChanged"),
            object: nil,
            queue: .main
        ) { [weak self] notification in
            print("🔷 ComposeCoordinator: Received ComposeRouteChanged")
            
            guard let userInfo = notification.userInfo else {
                print("⚠️ ComposeCoordinator: No userInfo in ComposeRouteChanged")
                return
            }
            
            let route = userInfo["route"] as? String ?? "unknown"
            let shouldHideTab = (userInfo["shouldHideTab"] as? Bool) ?? false
            let shouldHideNav = (userInfo["shouldHideNavigationBar"] as? Bool) ?? false
            let shouldShowBackButton = (userInfo["shouldShowBackButton"] as? Bool) ?? false
            
            print("🔷 ComposeCoordinator: route=\(route), hideTab=\(shouldHideTab), hideNav=\(shouldHideNav), showBack=\(shouldShowBackButton)")
            
            DispatchQueue.main.async {
                // Update UITabBar visibility
                if let tbc = self?.tabBarController {
                    print("✅ ComposeCoordinator: Setting tabBar.isHidden = \(shouldHideTab)")
                    tbc.tabBar.isHidden = shouldHideTab
                } else {
                    // If using SwiftUI TabView, post notification for ContentView to observe
                    NotificationCenter.default.post(
                        name: Notification.Name("UpdateTabBarVisibility"),
                        object: nil,
                        userInfo: ["hidden": shouldHideTab]
                    )
                }
                
                // Update Navigation bar visibility and back button
                if let nav = self?.navigationController {
                    print("✅ ComposeCoordinator: Setting navigationBar hidden = \(shouldHideNav)")
                    nav.setNavigationBarHidden(shouldHideNav, animated: true)
                    
                    // Control back button visibility
                    if shouldShowBackButton {
                        print("✅ ComposeCoordinator: Adding back button")
                        let backItem = UIBarButtonItem(
                            title: "Back",
                            style: .plain,
                            target: self,
                            action: #selector(self?.handleNativeBack)
                        )
                        nav.topViewController?.navigationItem.leftBarButtonItem = backItem
                    } else {
                        nav.topViewController?.navigationItem.leftBarButtonItem = nil
                    }
                } else {
                    // If using SwiftUI NavigationStack, post notifications for ContentView to observe
                    // (ContentView already handles this directly, so this is redundant but harmless)
                    NotificationCenter.default.post(
                        name: Notification.Name("UpdateNavigationBarVisibility"),
                        object: nil,
                        userInfo: ["hidden": shouldHideNav, "showBack": shouldShowBackButton]
                    )
                }
            }
        }
    }
    
    // MARK: - Native Back Handler
    
    /// Handle native back button press
    @objc private func handleNativeBack() {
        print("🔷 ComposeCoordinator: Back button tapped, posting ComposeBackPressed")
        // Forward the back action to Compose
        NotificationCenter.default.post(
            name: NSNotification.Name("ComposeBackPressed"),
            object: nil
        )
    }
}
// MARK: - Standalone Function for Direct Installation (Alternative API)

/// Install Compose observers using a simple function call
/// This is an alternative to using the ComposeCoordinator class
func installComposeObservers(tabBarController: UITabBarController?, navigationController: UINavigationController?) {
    print("🔷 installComposeObservers: Setting up observers")
    
    // Observe Compose -> native tab changes
    NotificationCenter.default.addObserver(forName: NSNotification.Name("SwitchNativeTab"), object: nil, queue: .main) { notification in
        print("🔷 SwitchNativeTab received")
        guard let userInfo = notification.userInfo,
              let tab = userInfo["tab"] as? String else {
            print("❌ Missing tab parameter")
            return
        }
        
        print("📤 Tab: \(tab)")

        let index: Int
        switch tab {
        case "HomePage", "Home": index = 0
        case "HabitCoachingPage", "Habits": index = 1
        case "ChatScreen", "Chat": index = 2
        case "meditation", "Meditate": index = 3
        case "profile", "Profile": index = 4
        default: index = 0
        }
        
        print("✅ Switching to index: \(index)")

        DispatchQueue.main.async {
            if let tbc = tabBarController {
                tbc.selectedIndex = index
            } else {
                // Post for SwiftUI
                NotificationCenter.default.post(
                    name: Notification.Name("UpdateNativeTabSelection"),
                    object: nil,
                    userInfo: ["index": index]
                )
            }
        }
    }

    // Observe ComposeRouteChanged to show/hide tab bar, nav bar and back button
    NotificationCenter.default.addObserver(forName: NSNotification.Name("ComposeRouteChanged"), object: nil, queue: .main) { notification in
        print("🔷 ComposeRouteChanged received")
        guard let userInfo = notification.userInfo else { return }
        let shouldHideTab = (userInfo["shouldHideTab"] as? Bool) ?? false
        let shouldHideNav = (userInfo["shouldHideNavigationBar"] as? Bool) ?? false
        let shouldShowBackButton = (userInfo["shouldShowBackButton"] as? Bool) ?? false

        print("📤 hideTab: \(shouldHideTab), hideNav: \(shouldHideNav), showBack: \(shouldShowBackButton)")

        DispatchQueue.main.async {
            // Tab bar visibility
            if let tbc = tabBarController {
                print("✅ Setting tabBar.isHidden = \(shouldHideTab)")
                tbc.tabBar.isHidden = shouldHideTab
            }

            // Navigation bar visibility
            if let nav = navigationController {
                print("✅ Setting navigationBar hidden = \(shouldHideNav)")
                nav.setNavigationBarHidden(shouldHideNav, animated: true)

                if shouldShowBackButton {
                    print("✅ Adding back button")
                    // Create back button with closure-based action
                    let backItem = UIBarButtonItem(title: "Back", style: .plain, target: nil, action: nil)
                    let tapRecognizer = UITapGestureRecognizer(target: nil, action: nil)
                    
                    // Use a helper class to hold the action
                    let helper = BackButtonHelper()
                    let action = #selector(BackButtonHelper.handleBackTap)
                    backItem.target = helper
                    backItem.action = action
                    
                    nav.topViewController?.navigationItem.leftBarButtonItem = backItem
                    // Note: In production, you'd need to retain the helper object
                } else {
                    nav.topViewController?.navigationItem.leftBarButtonItem = nil
                }
            }
        }
    }
    
    print("✅ installComposeObservers: Complete")
}

// Helper class for handling back button taps
private class BackButtonHelper: NSObject {
    @objc func handleBackTap() {
        print("🔙 Back button tapped, posting ComposeBackPressed")
        NotificationCenter.default.post(name: NSNotification.Name("ComposeBackPressed"), object: nil)
    }
}

