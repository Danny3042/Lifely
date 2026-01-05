import UIKit
import ComposeApp

// Central holder for the single shared Compose UIViewController instance.
final class SharedComposeHolder {
    static let sharedVC: UIViewController = {
        // Create once on main thread
        if !Thread.isMainThread {
            var vc: UIViewController! = nil
            DispatchQueue.main.sync {
                vc = MainViewControllerKt.MainViewController()
                vc.view.backgroundColor = .clear
                vc.view.isOpaque = false
                print("SharedComposeHolder: created shared VC on main: \(vc)")
            }
            return vc
        } else {
            let vc = MainViewControllerKt.MainViewController()
            vc.view.backgroundColor = .clear
            vc.view.isOpaque = false
            print("SharedComposeHolder: created shared VC: \(vc)")
            return vc
        }
    }()
}

final class ComposeHostAttacher {
    static let shared = ComposeHostAttacher()
    private(set) var attached: Bool = false
    // Keep a strong reference to the host window so it doesn't get released immediately
    private var hostWindow: UIWindow? = nil

    func attachIfNeeded() {
        DispatchQueue.main.async {
            guard !self.attached else { return }
            guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
                print("ComposeHostAttacher: no window scene available")
                return
            }

            // Create a dedicated window for Compose so it's not affected by SwiftUI layout changes
            let window = UIWindow(windowScene: scene)
            window.frame = scene.coordinateSpace.bounds
            window.backgroundColor = .clear
            // Place below normal to let SwiftUI overlays (tab bar, alerts) remain interactive above it
            window.windowLevel = UIWindow.Level(rawValue: UIWindow.Level.normal.rawValue - 1)

            // Attach the shared VC directly as the window's rootViewController so it fills the window exactly
            let shared = SharedComposeHolder.sharedVC

            // Detach from any previous parent
            if let prev = shared.parent {
                shared.willMove(toParent: nil)
                shared.view.removeFromSuperview()
                shared.removeFromParent()
            }

            window.rootViewController = shared
            // Ensure the view fills the window
            shared.view.translatesAutoresizingMaskIntoConstraints = false
            if let rootView = window.rootViewController?.view {
                NSLayoutConstraint.activate([
                    shared.view.topAnchor.constraint(equalTo: rootView.topAnchor),
                    shared.view.bottomAnchor.constraint(equalTo: rootView.bottomAnchor),
                    shared.view.leadingAnchor.constraint(equalTo: rootView.leadingAnchor),
                    shared.view.trailingAnchor.constraint(equalTo: rootView.trailingAnchor)
                ])
            }

            // Neutralize safe area on the shared view so Compose layout doesn't get pushed down by insets
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.01) {
                let safe = window.safeAreaInsets
                print("ComposeHostAttacher: window.safeAreaInsets=\(safe)")
                // Prevent the view from adding its own layout margins from safe area
                shared.view.insetsLayoutMarginsFromSafeArea = false
                // Apply negative extra safe area to cancel system insets
                shared.additionalSafeAreaInsets = UIEdgeInsets(top: -safe.top, left: 0, bottom: -safe.bottom, right: 0)
                print("ComposeHostAttacher: applied additionalSafeAreaInsets=\(shared.additionalSafeAreaInsets)")
            }

             // Show the window (don't make key)
             window.isHidden = false
             // Note: makeKeyAndVisible would steal key window; avoid it in release scenarios
             // window.makeKeyAndVisible()

            // Diagnostic prints to help understand layout/safe area offsets
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                let safe = window.safeAreaInsets
                print("ComposeHostAttacher.attached -> window.frame=\(window.frame) safeAreaInsets=\(safe) sharedFrame=\(shared.view.frame)")
            }

             self.hostWindow = window
             self.attached = true
            print("ComposeHostAttacher: attached shared Compose VC to dedicated window, windowLevel=\(window.windowLevel.rawValue)")

            #if DEBUG
            // Visible debug indicator
            let debugLabel = UILabel()
            debugLabel.text = "ComposeHostWindow"
            debugLabel.font = UIFont.systemFont(ofSize: 10)
            debugLabel.textColor = UIColor.white
            debugLabel.backgroundColor = UIColor.black.withAlphaComponent(0.35)
            debugLabel.translatesAutoresizingMaskIntoConstraints = false
            shared.view.addSubview(debugLabel)
            NSLayoutConstraint.activate([
                debugLabel.leadingAnchor.constraint(equalTo: shared.view.leadingAnchor, constant: 8),
                debugLabel.topAnchor.constraint(equalTo: shared.view.topAnchor, constant: 44)
            ])
            shared.view.bringSubviewToFront(debugLabel)
            #endif

            // Start monitor loop
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                self.monitorAndReattach(times: 40, interval: 0.15)
            }
        }
    }

    private func reattachIfNeeded() {
        DispatchQueue.main.async {
            guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
            if self.hostWindow == nil {
                // Recreate the window host if missing
                self.attached = false
                self.attachIfNeeded()
                return
            }
            let shared = SharedComposeHolder.sharedVC
            if shared.view.superview == nil || shared.parent == nil {
                if let window = self.hostWindow {
                    print("ComposeHostAttacher: reattaching shared VC into host window")
                    // set shared as rootViewController again
                    window.rootViewController = shared
                    // ensure constraints
                    shared.view.translatesAutoresizingMaskIntoConstraints = false
                    if let rootView = window.rootViewController?.view {
                        NSLayoutConstraint.activate([
                            shared.view.topAnchor.constraint(equalTo: rootView.topAnchor),
                            shared.view.bottomAnchor.constraint(equalTo: rootView.bottomAnchor),
                            shared.view.leadingAnchor.constraint(equalTo: rootView.leadingAnchor),
                            shared.view.trailingAnchor.constraint(equalTo: rootView.trailingAnchor)
                        ])
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                            let safe = window.safeAreaInsets
                            print("ComposeHostAttacher.reattach -> window.frame=\(window.frame) safeAreaInsets=\(safe) sharedFrame=\(shared.view.frame)")
                        }
                    }
                }
            }
        }
    }

    private func monitorAndReattach(times: Int, interval: TimeInterval) {
        guard times > 0 else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + interval) {
            let shared = SharedComposeHolder.sharedVC
            let hasSuperview = shared.view.superview != nil
            print("ComposeHostAttacher.monitor -> hasSuperview=\(hasSuperview) parent=\(String(describing: shared.parent)) hostWindow=\(String(describing: self.hostWindow)) sharedFrame=\(shared.view.frame)")
            if !hasSuperview {
                self.reattachIfNeeded()
            }
            self.monitorAndReattach(times: times - 1, interval: interval)
        }
    }
}
