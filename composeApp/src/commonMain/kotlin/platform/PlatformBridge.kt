package platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Mutable holder for platform requests. HeroScreen will observe this to switch tabs when requested by native iOS.
object PlatformBridge {
    // Title of tab requested (e.g., "Habits", "Home", "Chat", "Meditate", "Profile").
    var requestedTabName: String? by mutableStateOf(null)
    // Incrementing signal to force observers to see repeated requests for the same tab.
    var requestedTabSignal: Long by mutableStateOf(0L)

    // New: request a navigation route from platform nav host (e.g., "ChartsPage")
    var requestedRoute: String? by mutableStateOf(null)
    var requestedRouteSignal: Long by mutableStateOf(0L)

    // Safe area insets in point units — updated by Swift via the generated ObjC/Swift bridge.
    var safeAreaTop: Double by mutableStateOf(0.0)
    var safeAreaBottom: Double by mutableStateOf(0.0)
    var safeAreaLeading: Double by mutableStateOf(0.0)
    var safeAreaTrailing: Double by mutableStateOf(0.0)

    // When set to true, the native host (iOS) will show a native UITabBar and the Compose UI should hide its bottom navigation.
    var useNativeTabBar: Boolean by mutableStateOf(false)

    // The current system interface dark-mode reported by the native host (iOS).
    // Updated by PlatformIos when Swift posts SystemInterfaceStyleChanged.
    var systemInterfaceDark: Boolean by mutableStateOf(false)

    // Called from Swift/ObjC bindings (ComposeAppPlatformBridge.shared.setSafeAreaInsets)
    fun setSafeAreaInsets(top: Double, bottom: Double, leading: Double, trailing: Double) {
        safeAreaTop = top
        safeAreaBottom = bottom
        safeAreaLeading = leading
        safeAreaTrailing = trailing
    }
}
