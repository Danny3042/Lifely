# Fix for NotifierFactory Crash

## Problem Identified ✅

The crash is happening because `kmpnotifier` library is not initialized:
```
kotlin.IllegalStateException: NotifierFactory is not initialized. 
Please, initialize NotifierFactory by calling #initialize method
```

This happens when `MeditationPage` tries to access `NotifierManager.getLocalNotifier()`.

## Solution

### Step 1: Initialize NotifierFactory on iOS

In your iOS code, add initialization in `iOSApp.swift`:

```swift
import SwiftUI
import FirebaseCore
import FirebaseAnalytics
import ComposeApp  // Your Compose module
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif
import FirebaseMessaging
import UserNotifications
import AppTrackingTransparency

@main
struct iOSApp: App {
    
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    
    init() {
        FirebaseApp.configure()
        
        // ⭐ ADD THIS: Initialize KMP Notifier
        NotifierManagerKt.initializeNotifier()
        
        // Make hosting window backgrounds clear so Compose window underlay can show through
        UIWindow.appearance().backgroundColor = .clear
        
        // ... rest of your init code
    }
    
    // ... rest of your code
}
```

### Step 2: Alternative - Initialize in Kotlin (Better Approach)

If the above doesn't work or you want platform-agnostic initialization, add this to your Kotlin code:

**In `iosMain/kotlin/MainViewController.kt` or similar:**

```kotlin
// iosMain
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotifierConfiguration
import platform.UIKit.UIViewController

actual fun getPlatformName(): String = "iOS"

// Add this initialization function
fun initializeNotifierForIOS() {
    try {
        NotifierManager.initialize(
            configuration = NotifierConfiguration.Companion.Enabled
        )
        println("✅ NotifierManager initialized successfully")
    } catch (e: Exception) {
        println("⚠️ Failed to initialize NotifierManager: ${e.message}")
    }
}

fun MainViewController(): UIViewController {
    // Initialize notifier before creating the view
    initializeNotifierForIOS()
    
    return ComposeUIViewController {
        App()
    }
}
```

### Step 3: Make MeditationPage Handle Uninitialized Notifier

**In your `MeditationPage.kt` (or wherever MeditationPage is defined):**

Add a safe wrapper around the notifier access:

```kotlin
@Composable
fun MeditationPage(
    onBack: (() -> Unit)? = null,
    onSessionComplete: (() -> Unit)? = null,
    insightsViewModel: InsightsViewModel? = null
) {
    // Safe notifier access
    val notifier = remember {
        try {
            NotifierManager.getLocalNotifier()
        } catch (e: IllegalStateException) {
            println("⚠️ NotifierManager not initialized: ${e.message}")
            null
        }
    }
    
    // Rest of your composable...
    
    // When you need to show a notification:
    fun showNotification(title: String, message: String) {
        try {
            notifier?.notify(title, message) ?: run {
                println("⚠️ Cannot show notification - notifier not initialized")
            }
        } catch (e: Exception) {
            println("⚠️ Notification failed: ${e.message}")
        }
    }
    
    // Your UI code...
}
```

### Step 4: Quick Fix - Remove Notifier from MeditationPage

If you don't need notifications in the meditation page, simply remove the notifier usage:

**Find this code in your MeditationPage.kt:**
```kotlin
// REMOVE or comment out:
val notifier = NotifierManager.getLocalNotifier()
```

And replace any calls to `notifier.notify()` with:
```kotlin
// Instead of:
notifier.notify("Title", "Message")

// Use platform-specific bridge or just log:
println("Would show notification: Title - Message")
// OR
PlatformBridge.showToast("Message")
```

## Testing the Fix

After applying one of the solutions above:

1. Clean and rebuild your project:
   ```bash
   ./gradlew clean
   ```

2. Run the app and navigate to meditation tab

3. Type "meditate" in the message bar

4. Should work without crashing! ✅

## Recommended Approach

**I recommend Step 3** (making MeditationPage handle uninitialized notifier) because:
- It's defensive programming
- Won't crash if initialization fails
- Easy to implement
- Doesn't require changes to multiple places

## Complete MeditationPage Fix Example

Here's a complete example of how to fix your MeditationPage:

```kotlin
import com.mmk.kmpnotifier.notification.NotifierManager
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*

@Composable
fun MeditationPage(
    onBack: (() -> Unit)? = null,
    onSessionComplete: (() -> Unit)? = null,
    insightsViewModel: InsightsViewModel? = null
) {
    println("🔍 MeditationPage: Starting composition")
    
    // Safe notifier initialization
    var notifier by remember { mutableStateOf<Any?>(null) }
    var notifierError by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        try {
            // Try to initialize if not already done
            try {
                NotifierManager.initialize(
                    com.mmk.kmpnotifier.notification.configuration.NotifierConfiguration.Companion.Enabled
                )
            } catch (e: Exception) {
                // Already initialized, that's fine
            }
            
            // Get the notifier
            notifier = NotifierManager.getLocalNotifier()
            println("✅ MeditationPage: Notifier ready")
        } catch (e: Exception) {
            notifierError = e.message
            println("⚠️ MeditationPage: Notifier not available - ${e.message}")
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Meditation", style = MaterialTheme.typography.headlineMedium)
        
        // Show warning if notifier failed
        if (notifierError != null) {
            Text(
                "Notifications disabled: $notifierError",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        // Rest of your UI...
        
        Button(onClick = {
            // Safe notification
            try {
                (notifier as? com.mmk.kmpnotifier.notification.Notifier)?.notify(
                    "Meditation Complete",
                    "Great job! You've completed your session."
                ) ?: println("Notification skipped - notifier not available")
            } catch (e: Exception) {
                println("Notification failed: ${e.message}")
            }
        }) {
            Text("Complete Session")
        }
    }
}
```

## Additional Fix: Add Gradle Dependency Check

Make sure you have the notifier dependency properly configured:

**In `composeApp/build.gradle.kts`:**

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Make sure this is present
            implementation("io.github.mirzemehdi:kmpnotifier:1.0.0") // or latest version
        }
        
        iosMain.dependencies {
            // iOS-specific notifier deps if needed
        }
    }
}
```

## Expected Console Output After Fix

When it works correctly, you should see:
```
✅ NotifierManager initialized successfully
🔍 MeditationPage: Starting composition  
✅ MeditationPage: Notifier ready
```

Instead of the crash! 🎉

## If You Still Get Crashes

If the issue persists:

1. **Check if you actually need notifications** - if not, remove the notifier completely
2. **Verify dependency versions** - make sure kmpnotifier is compatible with your Kotlin/Compose versions
3. **Check for platform-specific requirements** - iOS might need specific permissions in Info.plist

Let me know if you need help with any of these steps!
