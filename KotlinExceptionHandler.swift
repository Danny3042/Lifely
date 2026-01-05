import Foundation
import SwiftUI
import ComposeApp

/// Extension to help debug and handle Kotlin crashes
extension Notification.Name {
    static let composeError = Notification.Name("ComposeError")
    static let composeCrash = Notification.Name("ComposeCrash")
    static let notifierInitFailed = Notification.Name("NotifierInitFailed")
}

/// Helper to catch and report Kotlin exceptions
class KotlinExceptionHandler {
    static let shared = KotlinExceptionHandler()
    
    private init() {}
    
    func setupGlobalHandler() {
        // Listen for uncaught Kotlin exceptions
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("KotlinException"),
            object: nil,
            queue: .main
        ) { notification in
            if let error = notification.userInfo?["exception"] as? String {
                self.handleKotlinException(error)
            }
        }
    }
    
    func handleKotlinException(_ error: String) {
        print("⚠️ Kotlin Exception Caught: \(error)")
        
        // Check for specific errors
        if error.contains("NotifierFactory is not initialized") {
            handleNotifierError()
        }
        
        // Post to iOS error handler
        NotificationCenter.default.post(
            name: .composeError,
            object: nil,
            userInfo: [
                "error": "App Error",
                "details": error
            ]
        )
    }
    
    private func handleNotifierError() {
        print("🔔 Attempting to initialize NotifierFactory...")
        
        // Try to call Kotlin initialization if available
        // This assumes you've exposed an initialization function
        // NotifierManagerKt.doInit() // Uncomment if you expose this
        
        NotificationCenter.default.post(
            name: .notifierInitFailed,
            object: nil,
            userInfo: [
                "error": "NotifierFactory not initialized",
                "solution": "Please check NOTIFIER_FIX.md"
            ]
        )
    }
}

/// Wrapper for safe Compose navigation
class SafeComposeNavigator {
    static func navigateToRoute(_ route: String) {
        do {
            AuthManager.shared.requestNavigateTo(route: route)
            print("✅ Safe navigation to: \(route)")
        } catch {
            print("❌ Navigation failed: \(error.localizedDescription)")
            NotificationCenter.default.post(
                name: .composeError,
                object: nil,
                userInfo: [
                    "error": "Navigation Error",
                    "details": "Failed to navigate to \(route): \(error.localizedDescription)"
                ]
            )
        }
    }
}

/// Debug helper to log app state
class AppStateLogger {
    static func logAppState() {
        print("""
        
        ========== APP STATE ==========
        Date: \(Date())
        iOS Version: \(UIDevice.current.systemVersion)
        Device: \(UIDevice.current.model)
        Memory: \(ProcessInfo.processInfo.physicalMemory / 1_000_000) MB
        ===============================
        
        """)
    }
    
    static func logComposableState(name: String) {
        print("📱 Composable State: \(name) - \(Date())")
    }
}

/// Extension to add crash recovery to ContentView
extension View {
    func withCrashRecovery() -> some View {
        self.onAppear {
            KotlinExceptionHandler.shared.setupGlobalHandler()
        }
    }
}
