package platform

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSNumber

// Manage safe area values observed from Swift via NotificationCenter or PlatformBridge
object IOSSafeAreaBridge {
    var top by mutableStateOf(0.0)
    var bottom by mutableStateOf(0.0)
    var leading by mutableStateOf(0.0)
    var trailing by mutableStateOf(0.0)

    init {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = "SafeAreaInsetsChanged",
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification: NSNotification? ->
            val userInfo = notification?.userInfo
            top = (userInfo?.get("top") as? NSNumber)?.doubleValue ?: top
            bottom = (userInfo?.get("bottom") as? NSNumber)?.doubleValue ?: bottom
            leading = (userInfo?.get("leading") as? NSNumber)?.doubleValue ?: leading
            trailing = (userInfo?.get("trailing") as? NSNumber)?.doubleValue ?: trailing
        }
    }

    fun currentTop(): Double = if (PlatformBridge.safeAreaTop != 0.0) PlatformBridge.safeAreaTop else top
    fun currentBottom(): Double = if (PlatformBridge.safeAreaBottom != 0.0) PlatformBridge.safeAreaBottom else bottom
    fun currentLeading(): Double = if (PlatformBridge.safeAreaLeading != 0.0) PlatformBridge.safeAreaLeading else leading
    fun currentTrailing(): Double = if (PlatformBridge.safeAreaTrailing != 0.0) PlatformBridge.safeAreaTrailing else trailing
}

@Composable
actual fun rememberSafeAreaInsets(): PaddingValues {
    return PaddingValues(
        start = IOSSafeAreaBridge.currentLeading().dp,
        top = IOSSafeAreaBridge.currentTop().dp,
        end = IOSSafeAreaBridge.currentTrailing().dp,
        bottom = IOSSafeAreaBridge.currentBottom().dp
    )
}

@Composable
actual fun rememberSafeAreaInsetsWithTabBar(): PaddingValues {
    val p = rememberSafeAreaInsets()
    return PaddingValues(
        start = p.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        top = p.calculateTopPadding(),
        end = p.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        bottom = 0.dp
    )
}
