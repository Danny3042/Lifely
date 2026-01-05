// SafeAreaHelper.kt
// Add this to your Compose shared module (commonMain/iosMain)

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.darwin.NSObject

/**
 * Observable safe area insets for iOS
 */
class SafeAreaInsetsManager {
    var insets by mutableStateOf(PaddingValues(0.dp))
        private set
    
    private var observer: NSObject? = null
    
    init {
        observeSafeAreaChanges()
    }
    
    private fun observeSafeAreaChanges() {
        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "SafeAreaInsetsChanged",
            `object` = null,
            queue = null
        ) { notification: NSNotification? ->
            notification?.userInfo?.let { userInfo ->
                val top = (userInfo["top"] as? Double)?.toFloat() ?: 0f
                val bottom = (userInfo["bottom"] as? Double)?.toFloat() ?: 0f
                val leading = (userInfo["leading"] as? Double)?.toFloat() ?: 0f
                val trailing = (userInfo["trailing"] as? Double)?.toFloat() ?: 0f
                
                insets = PaddingValues(
                    start = leading.dp,
                    top = top.dp,
                    end = trailing.dp,
                    bottom = bottom.dp
                )
            }
        }
    }
    
    fun cleanup() {
        observer?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
            observer = null
        }
    }
    
    companion object {
        val shared = SafeAreaInsetsManager()
    }
}

/**
 * Composable function to get current safe area insets
 */
@Composable
fun rememberSafeAreaInsets(): PaddingValues {
    return SafeAreaInsetsManager.shared.insets
}

/**
 * Get safe area insets adjusted for tab bar
 * Use this when you have a tab bar at the bottom
 */
@Composable
fun rememberSafeAreaInsetsWithTabBar(): PaddingValues {
    val insets = rememberSafeAreaInsets()
    // Tab bar is typically around 49-83 points tall (including safe area)
    // Since iOS handles the tab bar, we mainly need to worry about the top
    return PaddingValues(
        start = insets.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        top = insets.calculateTopPadding(),
        end = insets.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        bottom = 0.dp // Tab bar already provides bottom spacing
    )
}
