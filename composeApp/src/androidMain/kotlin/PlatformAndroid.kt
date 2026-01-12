// No package: must match the expect declaration in commonMain

import Authentication.Authentication
import Authentication.LoginScreen
import Authentication.ResetPasswordScreen
import Authentication.SignUpScreen
import Colors.DarkColors
import Colors.LightColors
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import navigation.YourMainNavHost
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
import config.VERSION_NUMBER
import pages.ChartsPage
import pages.ChartsPageScreen
import pages.HomePageScreen
import pages.InsightsPage
import pages.InsightsPageScreen
import pages.STRESS_MANAGEMENT_PAGE_ROUTE
import pages.StressManagementPage
import sub_pages.AboutPage
import sub_pages.AboutPageScreen
import sub_pages.CompletedHabitsPage
import sub_pages.CompletedHabitsPageRoute
import sub_pages.MEDITATION_PAGE_ROUTE
import sub_pages.MeditationPage
import sub_pages.NotificationPage
import sub_pages.NotificationPageScreen
import sub_pages.DarkModeSettingsPage
import sub_pages.DarkModeSettingsPageScreen
import tabs.HomeTab
import tabs.ProfileTab
import utils.SettingsManager
import utils.HealthKitServiceImpl
import utils.iOSHealthKitManager
import sub_pages.REFLECTION_PAGE_ROUTE
import sub_pages.ReflectionPage
import platform.PlatformBridge
import kotlinx.coroutines.flow.collectLatest
import pages.Timer
import pages.TimerScreenContent

// Android actual implementation uses Material design
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
            navController = navController,
            showBottomBar = showBottomBar,
            isDarkMode = isDarkMode,
            onDarkModeToggle = onDarkModeToggle,
            useSystemDefault = useSystemDefault,
            onUseSystemDefaultToggle = onUseSystemDefaultToggle
        )
     }
 }
