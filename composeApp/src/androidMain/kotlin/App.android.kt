import Colors.DarkColors
import Colors.LightColors
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
                // Persist incoming state and observe changes to save
                LaunchedEffect(isDarkMode) { try { SettingsManager.saveDarkMode(isDarkMode) } catch (_: Throwable) {} }
                LaunchedEffect(useSystemDefault) { try { SettingsManager.saveUseSystemDefault(useSystemDefault) } catch (_: Throwable) {} }

                val darkMode = if (useSystemDefault) isSystemInDarkTheme() else isDarkMode
                val colors = if (darkMode) DarkColors else LightColors

                MaterialTheme(colorScheme = colors) {
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