import UIKit
import Foundation

/// ComposeBridge: UIKit helper to observe Compose notifications and update native UI.
/// - Install by calling `ComposeBridge.shared.install(tabBarController:navigationController:)`
///   after you create your root controllers (SceneDelegate / AppCoordinator / AppDelegate).
final class ComposeBridge {
    static let shared = ComposeBridge()

    private(set) weak var tabBarController: UITabBarController?
    private(set) weak var navigationController: UINavigationController?

    private init() {}

    func install(tabBarController: UITabBarController?, navigationController: UINavigationController?) {
        self.tabBarController = tabBarController
        self.navigationController = navigationController

        // Compose -> Native: switch native tab
        NotificationCenter.default.addObserver(forName: NSNotification.Name("SwitchNativeTab"), object: nil, queue: .main) { [weak self] note in
            guard let tab = note.userInfo?["tab"] as? String else { return }
            let index: Int
            switch tab {
            case "HomePage", "Home": index = 0
            case "HabitCoachingPage", "Habits": index = 1
            case "ChatScreen", "Chat": index = 2
            case "meditation", "Meditate": index = 3
            case "profile", "Profile": index = 4
            default: index = 0
            }
            self?.tabBarController?.selectedIndex = index
        }

        // Compose route changed: control tab/nav/back-button visibility
        NotificationCenter.default.addObserver(forName: NSNotification.Name("ComposeRouteChanged"), object: nil, queue: .main) { [weak self] note in
            guard let info = note.userInfo else { return }
            let shouldHideTab = (info["shouldHideTab"] as? Bool) ?? false
            let shouldHideNav = (info["shouldHideNavigationBar"] as? Bool) ?? false
            let shouldShowBackButton = (info["shouldShowBackButton"] as? Bool) ?? false

            // Tab bar visibility
            self?.tabBarController?.tabBar.isHidden = shouldHideTab

            // Navigation bar visibility and back button
            if let nav = self?.navigationController {
                nav.setNavigationBarHidden(shouldHideNav, animated: true)
                if shouldShowBackButton {
                    let backItem = UIBarButtonItem(title: "Back", style: .plain, target: ComposeBridge.shared, action: #selector(ComposeBridge.handleNativeBack(_:)))
                    nav.topViewController?.navigationItem.leftBarButtonItem = backItem
                } else {
                    nav.topViewController?.navigationItem.leftBarButtonItem = nil
                }
            }
        }

        // Convenience: explicit show/hide notifications
        NotificationCenter.default.addObserver(forName: NSNotification.Name("ComposeShowBackButton"), object: nil, queue: .main) { [weak self] _ in
            if let nav = self?.navigationController {
                let backItem = UIBarButtonItem(title: "Back", style: .plain, target: ComposeBridge.shared, action: #selector(ComposeBridge.handleNativeBack(_:)))
                nav.topViewController?.navigationItem.leftBarButtonItem = backItem
            }
        }
        NotificationCenter.default.addObserver(forName: NSNotification.Name("ComposeHideBackButton"), object: nil, queue: .main) { [weak self] _ in
            self?.navigationController?.topViewController?.navigationItem.leftBarButtonItem = nil
        }
    }

    // Forward native back tap to Compose
    @objc func handleNativeBack(_ sender: Any?) {
        NotificationCenter.default.post(name: NSNotification.Name("ComposeBackPressed"), object: nil)
    }
}

