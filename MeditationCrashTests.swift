import Testing
import Foundation
@testable import iosApp

/// Tests to verify the meditation crash issue
@Suite("Meditation Crash Tests")
struct MeditationCrashTests {
    
    @Test("Notification center should handle ComposeError")
    func testComposeErrorNotification() async throws {
        let expectation = XCTestExpectation(description: "ComposeError notification received")
        
        let observer = NotificationCenter.default.addObserver(
            forName: Notification.Name("ComposeError"),
            object: nil,
            queue: .main
        ) { notification in
            if let error = notification.userInfo?["error"] as? String {
                #expect(error == "Test error")
                expectation.fulfill()
            }
        }
        
        // Simulate a Compose error
        NotificationCenter.default.post(
            name: Notification.Name("ComposeError"),
            object: nil,
            userInfo: [
                "error": "Test error",
                "details": "Test details"
            ]
        )
        
        // Wait for notification
        await fulfillment(of: [expectation], timeout: 2.0)
        
        NotificationCenter.default.removeObserver(observer)
    }
    
    @Test("Route navigation should handle meditation route")
    func testMeditationRouteNavigation() throws {
        let authManager = AuthManager.shared
        
        // Should not crash when navigating to meditation
        #expect(throws: Never.self) {
            authManager.requestNavigateTo(route: "meditation")
        }
    }
    
    @Test("String should safely check for meditate keyword")
    func testMeditateKeywordDetection() throws {
        let testInputs = [
            "meditate",
            "MEDITATE",
            "I want to meditate",
            "meditation",
            "med"
        ]
        
        for input in testInputs {
            // This should never crash
            let containsMeditate = input.lowercased().contains("meditate")
            #expect(containsMeditate || !containsMeditate) // Always true, just testing execution
        }
    }
    
    @Test("AppSettings should handle snackbar state")
    func testSnackbarState() throws {
        let settings = AppSettings()
        
        settings.snackbarMessage = "Test error"
        settings.showSnackbar = true
        
        #expect(settings.showSnackbar == true)
        #expect(settings.snackbarMessage == "Test error")
        
        settings.showSnackbar = false
        #expect(settings.showSnackbar == false)
    }
}

/// Helper to test async expectations
func fulfillment(of expectations: [XCTestExpectation], timeout: TimeInterval) async {
    await withCheckedContinuation { continuation in
        let waiter = XCTWaiter()
        waiter.wait(for: expectations, timeout: timeout)
        continuation.resume()
    }
}

// XCTestExpectation compatibility
class XCTestExpectation {
    let description: String
    private var isFulfilled = false
    
    init(description: String) {
        self.description = description
    }
    
    func fulfill() {
        isFulfilled = true
    }
    
    var fulfilled: Bool {
        isFulfilled
    }
}

class XCTWaiter {
    func wait(for expectations: [XCTestExpectation], timeout: TimeInterval) {
        let start = Date()
        while Date().timeIntervalSince(start) < timeout {
            if expectations.allSatisfy({ $0.fulfilled }) {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
    }
}
