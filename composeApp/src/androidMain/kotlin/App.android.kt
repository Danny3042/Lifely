import Colors.HealthAppTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.compose.rememberNavController
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
import kotlinx.coroutines.flow.collectLatest
import navigation.YourMainNavHost
import platform.PlatformBridge
import utils.SettingsManager

@Composable
actual fun PlatformApp(
    showBottomBar: Boolean,
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    useSystemDefault: Boolean,
    onUseSystemDefaultToggle: (Boolean) -> Unit
) {
                // Restore the saved appearance choice once, then save every change after that.
                // (Saving before restoring would overwrite the user's choice with the defaults.)
                var appearanceLoaded by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    try {
                        onDarkModeToggle(SettingsManager.loadDarkMode())
                        onUseSystemDefaultToggle(SettingsManager.loadUseSystemDefault())
                    } catch (_: Throwable) {
                    }
                    appearanceLoaded = true
                }
                LaunchedEffect(isDarkMode, appearanceLoaded) {
                    if (appearanceLoaded) try { SettingsManager.saveDarkMode(isDarkMode) } catch (_: Throwable) {}
                }
                LaunchedEffect(useSystemDefault, appearanceLoaded) {
                    if (appearanceLoaded) try { SettingsManager.saveUseSystemDefault(useSystemDefault) } catch (_: Throwable) {}
                }

                val darkMode = if (useSystemDefault) isSystemInDarkTheme() else isDarkMode
                HealthAppTheme(darkTheme = darkMode) {
                    val navController = rememberNavController()

                    LaunchedEffect(Unit) {
                        // Observe platform requestedRouteSignal (requests originating from other parts of the app)
                        snapshotFlow { PlatformBridge.requestedRouteSignal }.collectLatest {
                            val route = PlatformBridge.requestedRoute
                            route?.let {
                                try {
                                    navController.navigate(it)
                                } catch (t: Throwable) {
                                    println("Failed to navigate to route from PlatformBridge=$it: $t")
                                }
                                PlatformBridge.requestedRoute = null
                            }
                        }

                        // Provide a notification icon resource id (0 used as fallback; replace with real drawable id)
                        NotifierManager.initialize(NotificationPlatformConfiguration.Android(notificationIconResId = 0))
                    }

                    // Use the centralized nav host and forward dark mode hooks for the settings page
                    YourMainNavHost(
                        navController,
                        showBottomBar,
                        isDarkMode,
                        onDarkModeToggle,
                        useSystemDefault,
                        onUseSystemDefaultToggle
                    )
                }
            }