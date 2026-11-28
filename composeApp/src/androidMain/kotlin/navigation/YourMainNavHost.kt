package navigation

import androidx.compose.runtime.CompositionLocalProvider
import components.ui.AppearanceControls
import components.ui.LocalAppearanceControls
import pages.OnboardingRoute
import pages.OnboardingScreen
import pages.rememberStartDestination
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import Authentication.Authentication
import Authentication.LoginScreen
import Authentication.ResetPasswordScreen
import Authentication.SignUpScreen
import HeroScreen
import pages.ChartsPage
import pages.ChartsPageScreen
import pages.HomePageScreen
import pages.InsightsPage
import pages.InsightsPageScreen
import pages.STRESS_MANAGEMENT_PAGE_ROUTE
import pages.StressManagementPage
import pages.Timer
import pages.TimerScreenContent
import sub_pages.AboutPage
import sub_pages.AboutPageScreen
import sub_pages.CompletedHabitsPage
import sub_pages.CompletedHabitsPageRoute
import sub_pages.DarkModeSettingsPage
import sub_pages.DarkModeSettingsPageScreen
import sub_pages.MEDITATION_PAGE_ROUTE
import sub_pages.MeditationPage
import sub_pages.NotificationPage
import sub_pages.NotificationPageScreen
import sub_pages.PermissionsPage
import sub_pages.PermissionsPageScreen
import sub_pages.AccountPage
import sub_pages.AccountPageScreen
import sub_pages.REFLECTION_PAGE_ROUTE
import sub_pages.CHECK_IN_PAGE_ROUTE
import sub_pages.CHECK_IN_CALENDAR_ROUTE
import sub_pages.CheckInCalendarPage
import sub_pages.ReflectionPage
import tabs.HomeTab
import utils.HealthKitServiceImpl
import utils.iOSHealthKitManager
import config.appVersionName
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun YourMainNavHost(navController: NavHostController, showBottomBar: Boolean = true, isDarkMode: Boolean, onDarkModeToggle: (Boolean) -> Unit, useSystemDefault: Boolean, onUseSystemDefaultToggle: (Boolean) -> Unit) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(currentBackStackEntry) {
        val currentRoute = currentBackStackEntry?.destination?.route ?: "HeroScreen"

        val mainTabRoutes = setOf("HomePage", "HabitCoachingPage", "ChatScreen", "meditation", "profile", "HeroScreen")
        val routesWithHiddenNav = setOf("Login", "SignUp", "ResetPassword", "Onboarding")

        // Exact names (or with arguments): "meditation_reflection" starts with "meditation" but is a pushed page
        val isMainTab = currentRoute in mainTabRoutes ||
            mainTabRoutes.any { currentRoute.startsWith("$it/") || currentRoute.startsWith("$it?") }
        val shouldHideTab = !isMainTab
        val shouldHideNav = routesWithHiddenNav.contains(currentRoute)
        val shouldShowBackButton = !isMainTab && !shouldHideNav && navController.previousBackStackEntry != null

        // Android: log for parity with iOS
        println("[YourMainNavHost-Android] route=$currentRoute showBack=$shouldShowBackButton isMain=$isMainTab")
    }

    // First launch opens on onboarding. Draw nothing for the moment it takes to read the flag,
    // so the tab host doesn't flash up first.
    val startDestination = rememberStartDestination() ?: return

    // Lets the Profile page show the Appearance controls in its detail pane on large screens
    CompositionLocalProvider(
        LocalAppearanceControls provides AppearanceControls(
            isDarkMode = isDarkMode,
            onDarkModeToggle = onDarkModeToggle,
            useSystemDefault = useSystemDefault,
            onUseSystemDefaultToggle = onUseSystemDefaultToggle,
        )
    ) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(OnboardingScreen) { OnboardingRoute(navController) }
            composable(LoginScreen) { Authentication().Login(navController) }
            composable("HeroScreen") { HeroScreen(navController, showBottomBar = showBottomBar) }
            composable(SignUpScreen) { Authentication().signUp(navController) }
            composable(ResetPasswordScreen) { Authentication().ResetPassword(navController)}

            composable(HomePageScreen) { HomeTab.Content() }
            composable("HabitCoachingPage") { HomeTab.Content() }
            composable("ChatScreen") { HomeTab.Content() }
            composable("meditation") { HomeTab.Content() }
            composable("profile") { HomeTab.Content() }

            composable(AboutPageScreen) { AboutPage(navController, versionNumber = appVersionName()) }
            composable(NotificationPageScreen) { NotificationPage(navController) }
            composable(PermissionsPageScreen) { PermissionsPage(navController) }
            composable(AccountPageScreen) { AccountPage(navController) }
            composable(DarkModeSettingsPageScreen) {
                DarkModeSettingsPage(
                    isDarkMode = isDarkMode,
                    onDarkModeToggle = onDarkModeToggle,
                    useSystemDefault = useSystemDefault,
                    onUseSystemDefaultToggle = onUseSystemDefaultToggle,
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
                    onOpenCalendar = { navController.navigate(CHECK_IN_CALENDAR_ROUTE) },
                    healthKitService = HealthKitServiceImpl(iOSHealthKitManager()),
                    onBack = { navController.navigate("HeroScreen") { launchSingleTop = true } }
                )
            }
            composable(CHECK_IN_PAGE_ROUTE) {
                ReflectionPage(
                    onOpenCalendar = { navController.navigate(CHECK_IN_CALENDAR_ROUTE) },
                    healthKitService = HealthKitServiceImpl(iOSHealthKitManager()),
                    onBack = { navController.popBackStack() },
                    afterSession = false,
                )
            }
            composable(CHECK_IN_CALENDAR_ROUTE) { CheckInCalendarPage(onBack = { navController.popBackStack() }) }
            composable(STRESS_MANAGEMENT_PAGE_ROUTE) { StressManagementPage(navController) }
            composable(InsightsPageScreen) { InsightsPage(insightsViewModel = viewModel()) }
            composable(ChartsPageScreen) { ChartsPage(navController = navController) }
        }
    }
}
