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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
actual fun PlatformApp() {
    var isDarkMode by remember { mutableStateOf(false) }
    var useSystemDefault by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isDarkMode = SettingsManager.loadDarkMode()
        useSystemDefault = SettingsManager.loadUseSystemDefault()
    }

    LaunchedEffect(isDarkMode) { SettingsManager.saveDarkMode(isDarkMode) }
    LaunchedEffect(useSystemDefault) { SettingsManager.saveUseSystemDefault(useSystemDefault) }

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


        NavHost(navController, startDestination = LoginScreen) {
            composable(LoginScreen) { Authentication().Login(navController) }
            composable("HeroScreen") { HeroScreen(navController) }
            composable(SignUpScreen) { Authentication().signUp(navController) }
            composable(ResetPasswordScreen) { Authentication().ResetPassword(navController)}
            composable(HomePageScreen) { HomeTab.Content() }
            composable("profile") { ProfileTab(navController).Content() }
            composable(AboutPageScreen) { AboutPage(navController, versionNumber = VERSION_NUMBER) }
            composable(NotificationPageScreen) { NotificationPage(navController) }
            composable(DarkModeSettingsPageScreen) {
                DarkModeSettingsPage(
                    isDarkMode = isDarkMode,
                    onDarkModeToggle = { isDarkMode = it },
                    useSystemDefault = useSystemDefault,
                    onUseSystemDefaultToggle = { useSystemDefault = it },
                    navController = navController
                )
            }
            composable(Timer) { TimerScreenContent(onBack = { navController.popBackStack() }) }
            composable(CompletedHabitsPageRoute) { CompletedHabitsPage(navController) }
            composable(MEDITATION_PAGE_ROUTE) {
                MeditationPage(
                    onBack = { navController.popBackStack() },
                    onNavigateToInsights = { navController.navigate(InsightsPageScreen) },
                    onMeditationComplete = { navController.navigate(REFLECTION_PAGE_ROUTE) }
                )
            }
            composable(REFLECTION_PAGE_ROUTE) {
                ReflectionPage(
                    healthKitService = HealthKitServiceImpl(iOSHealthKitManager()),
                    onBack = { navController.navigate("HeroScreen") { launchSingleTop = true } }
                )
            }
            composable(STRESS_MANAGEMENT_PAGE_ROUTE) {
                StressManagementPage(navController)
            }
            composable(InsightsPageScreen) { InsightsPage(insightsViewModel = viewModel()) }
            // New charts page
            composable(ChartsPageScreen) { ChartsPage(navController = navController) }
        }
    }
}