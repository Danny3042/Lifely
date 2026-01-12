# Tab Navigation Debugging Guide

## Changes Made

I've added extensive debug logging to help diagnose why tab navigation isn't working. Additionally, a new `ComposeCoordinator` class has been added to manage bidirectional communication between Compose and native SwiftUI/UIKit.

### New Coordinator Architecture

The app now includes a `ComposeCoordinator` that:
- Listens for `SwitchNativeTab` notifications from Compose to update native tab selection
- Listens for `ComposeRouteChanged` notifications to control tab bar and navigation bar visibility
- Forwards native back button presses to Compose via `ComposeBackPressed` notification
- Is initialized in `AppDelegate.didFinishLaunchingWithOptions`

Here's what to do:

## Step 1: Run the App and Check Console Output

1. Build and run your app in Xcode
2. Open the Xcode Console (View → Debug Area → Activate Console)
3. Click on different tabs in the tab bar
4. Look for these log messages:

### Expected Swift Logs (from ContentView.swift):
```
SharedComposeHost: selectedTab changed to X
SharedComposeHost: Mapped to route: [route name]
SharedComposeHost: sendRouteWithRetries called with route: [route name]
SharedComposeHost: Before update - requestedTabSignal = X
SharedComposeHost: After update - requestedTabName = [route name], requestedTabSignal = X+1
```

### Expected Kotlin Logs (from PlatformIos.kt):
```
PlatformIos: requestedTabSignal changed to X, tabName = [tab name]
PlatformIos: Tab clicked -> [tab name], navigating to [route]
PlatformIos: Current destination = [current route]
PlatformIos: Navigation complete, new destination = [new route]
```

## Step 2: Diagnose Based on What You See

### Case A: No Swift logs appear when clicking tabs
**Problem**: Tab changes aren't being detected
**Solution**: 
- Check that `selectedTab` binding is properly connected to TabView
- Verify TabView is using `selection: $selectedTab`

### Case B: Swift logs appear but no Kotlin logs
**Problem**: PlatformBridge isn't working
**Possible causes**:
1. PlatformBridge.shared might not be properly initialized
2. The Kotlin code might not be observing the changes

**Solution**: Add this to verify PlatformBridge in Swift:
```swift
let bridge = PlatformBridge.shared
print("Bridge type: \(type(of: bridge))")
print("Can access requestedTabSignal: \(bridge.responds(to: #selector(getter: PlatformBridge.requestedTabSignal)))")
```

### Case C: Both Swift and Kotlin logs appear but navigation doesn't happen
**Problem**: Navigation issue in Compose
**Possible causes**:
1. Routes don't match the composable destinations
2. HeroScreen is intercepting navigation
3. NavController isn't properly initialized

**Solution**: Check if the route names match exactly:
- Print what `HomePageScreen` constant equals
- Verify composable routes in NavHost match

### Case D: You see "Unknown tab name" in logs
**Problem**: Route mapping is incorrect
**Solution**: The tab names from Swift don't match the `when` statement in Kotlin

## Step 3: Additional Debugging Steps

### Check Route Constants
Add this at the top of `PlatformApp`:
```kotlin
LaunchedEffect(Unit) {
    println("=== Route Constants ===")
    println("HomePageScreen = $HomePageScreen")
    println("HomePageScreen constant type = ${HomePageScreen::class.simpleName}")
}
```

### Check if Composables Are Registered
The NavHost should have these composables:
- `composable(HomePageScreen)` 
- `composable("HabitCoachingPage")`
- `composable("ChatScreen")`
- `composable("meditation")`
- `composable("profile")`

### Verify Tab Route Array Matches
In `ContentView.swift`, the `tabRoutes` array should be:
```swift
private let tabRoutes = ["HomePage", "HabitCoachingPage", "ChatScreen", "meditation", "profile"]
```

Tab indices:
- 0 = HomePage
- 1 = HabitCoachingPage  
- 2 = ChatScreen
- 3 = meditation
- 4 = profile

## Step 4: Quick Fix Attempts

### Option 1: Simplify Navigation
Try this simpler navigation approach in the Kotlin tab observer:

```kotlin
if (route != null) {
    println("PlatformIos: Navigating to $route")
    navController.navigate(route)
}
```

### Option 2: Use the New ComposeCoordinator
The `ComposeCoordinator` is now installed in `AppDelegate` and listens for:
- `SwitchNativeTab` - Compose can post this with a `tab` parameter to switch native tabs
- `ComposeRouteChanged` - Compose posts this with appearance control parameters

To test if the coordinator is working, check your Kotlin code to ensure it's posting these notifications:

**From Kotlin to switch tabs:**
```kotlin
NSNotificationCenter.defaultCenter.postNotificationName(
    name = "SwitchNativeTab",
    `object` = null,
    userInfo = mapOf("tab" to "HomePage")
)
```

**From Kotlin to control appearance:**
```kotlin
NSNotificationCenter.defaultCenter.postNotificationName(
    name = "ComposeRouteChanged",
    `object` = null,
    userInfo = mapOf(
        "route" to currentRoute,
        "shouldHideTab" to false,
        "shouldHideNavigationBar" to false,
        "shouldShowBackButton" to false
    )
)
```

### Option 3: Debug ComposeCoordinator
Add print statements to verify the coordinator is receiving notifications. In `ComposeCoordinator.swift`, you can add:

```swift
print("ComposeCoordinator: Received SwitchNativeTab for tab: \(tab)")
print("ComposeCoordinator: Switching to index: \(index)")
```

### Option 4: Use Notification Method Instead (Legacy)
If PlatformBridge doesn't work, use notifications directly:

In Swift:
```swift
NotificationCenter.default.post(
    name: Notification.Name("NavigateToTab"),
    object: nil,
    userInfo: ["route": route]
)
```

In Kotlin (add new DisposableEffect):
```kotlin
DisposableEffect(navController) {
    val observer = NSNotificationCenter.defaultCenter.addObserverForName(
        name = "NavigateToTab",
        `object` = null,
        queue = NSOperationQueue.mainQueue
    ) { notification: NSNotification? ->
        val userInfo = notification?.userInfo as? NSDictionary
        val route = userInfo?.objectForKey("route") as? String
        if (route != null) {
            navController.navigate(route)
        }
    }
    onDispose {
        NSNotificationCenter.defaultCenter.removeObserver(observer as Any)
    }
}
```

## What to Report Back

Please share:
1. Which logs you see (Swift only, Kotlin only, both, or neither)
2. Any error messages
3. What happens when you click a tab (nothing, wrong page, crash, etc.)
4. The value of `HomePageScreen` constant if you can print it

This will help me provide a more targeted fix!
