import Foundation
import SwiftUI
import UIKit
import FirebaseCore
import FirebaseAuth
import AuthenticationServices
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

// Lightweight shim AuthManager: keeps the same public API so existing call sites compile,
// but delegates navigation/auth responsibility to the shared Compose UI on iOS.
final class AuthManager: NSObject, ObservableObject {
    static let shared = AuthManager()

    private override init() {}

    struct Notifications {
        // Compose-side navigation request. Compose (PlatformIos.kt) listens for this.
        static let navigate = Notification.Name("AuthManagerNavigateToRoute")
        static let didSignOut = Notification.Name("AuthManagerDidSignOut")
    }

    private func postNavigateTo(route: String) {
        NotificationCenter.default.post(name: Notifications.navigate, object: nil, userInfo: ["route": route])
    }

    // MARK: - Navigation helpers (these make the Compose auth UI appear)
    func requestShowLogin() {
        postNavigateTo(route: "Login")
    }

    func requestShowSignUp() {
        postNavigateTo(route: "SignUp")
    }

    func requestShowResetPassword() {
        postNavigateTo(route: "ResetPassword")
    }

    // Public helper to request navigation to any Compose route
    func requestNavigateTo(route: String) {
        postNavigateTo(route: route)
    }

    // MARK: - Auth operations (shimbed)
    // These methods intentionally do not perform native authentication. Instead they
    // trigger the Compose auth flow and complete with a clear error so callers know
    // the operation is handled by the shared Compose UI.

    private func composeHandledError() -> NSError {
        return NSError(domain: "AuthManager", code: -2, userInfo: [NSLocalizedDescriptionKey: "Authentication delegated to Compose UI on iOS"])
    }

    func signIn(email: String, password: String, completion: @escaping (Result<User, Error>) -> Void) {
        // Show Compose login and return a delegated error
        postNavigateTo(route: "Login")
        completion(.failure(composeHandledError()))
    }

    func createUser(email: String, password: String, completion: @escaping (Result<User, Error>) -> Void) {
        postNavigateTo(route: "SignUp")
        completion(.failure(composeHandledError()))
    }

    func sendPasswordReset(email: String, completion: @escaping (Result<Void, Error>) -> Void) {
        postNavigateTo(route: "ResetPassword")
        completion(.failure(composeHandledError()))
    }

    // Sign out: attempt native Firebase sign-out if Firebase is configured; still post a didSignOut notification.
    func signOut() throws {
        do {
            try Auth.auth().signOut()
        } catch {
            // rethrow so callers can handle native sign-out errors
            throw error
        }
        NotificationCenter.default.post(name: Notifications.didSignOut, object: nil)
    }

    // MARK: - Federated sign-in shims
    // We no-op these flows but surface the same method signatures so call sites compile.
    private var appleSignInCompletion: ((Result<User, Error>) -> Void)?
    private var currentNonce: String?

    func startSignInWithAppleFlow(presentationAnchor: ASPresentationAnchor, completion: @escaping (Result<User, Error>) -> Void) {
        // Let Compose handle Apple sign-in UI; notify Compose to show Login so user can pick Apple there.
        postNavigateTo(route: "Login")
        completion(.failure(composeHandledError()))
    }

    #if canImport(GoogleSignIn) || canImport(GoogleSignInSwift)
    func signInWithGoogle(presenting viewController: UIViewController, completion: @escaping (Result<User, Error>) -> Void) {
        // Forward to Compose UI
        postNavigateTo(route: "Login")
        completion(.failure(composeHandledError()))
    }
    #endif
}
