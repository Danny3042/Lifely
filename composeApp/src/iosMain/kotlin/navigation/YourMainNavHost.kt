import pages.OnboardingRoute
import androidx.compose.runtime.CompositionLocalProvider
import components.ui.AppearanceControls
import components.ui.LocalAppearanceControls
import pages.OnboardingScreen
import pages.rememberStartDestination
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSNotificationCenter

import Authentication.Authentication
import Authentication.LoginScreen
import Authentication.ResetPasswordScreen
import Authentication.SignUpScreen
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
fun YourMainNavHost(
    navController: NavHostController,
    showBottomBar: Boolean = true,
    isDarkMode: Boolean = false,
    onDarkModeToggle: (Boolean) -> Unit = {},
    useSystemDefault: Boolean = true,
    onUseSystemDefaultToggle: (Boolean) -> Unit = {}
) {
    // Observe current back stack entry for route changes
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(currentBackStackEntry) {
        // No destination yet (the start screen is still loading): report nothing. Assuming
        // "HeroScreen" here showed the tab and navigation bars for a moment before onboarding.
        val currentRoute = currentBackStackEntry?.destination?.route ?: return@LaunchedEffect

        // Define which routes are main tabs (these won't show back button)
        val mainTabRoutes = setOf(
            "HomePage",
            "HabitCoachingPage",
            "ChatScreen",
            "meditation",
            "profile",
            "HeroScreen"
        )

        val routesWithHiddenNav = setOf("Login", "SignUp", "ResetPassword", "Onboarding")

        // Exact names (or with arguments): "meditation_reflection" starts with "meditation" but is a
        // pushed page, and matching by prefix hid its back button
        val isMainTab = currentRoute in mainTabRoutes ||
            mainTabRoutes.any { currentRoute.startsWith("$it/") || currentRoute.startsWith("$it?") }
        val shouldHideNav = routesWithHiddenNav.contains(currentRoute)
        // Sub pages keep the tab bar (tapping a tab closes them); sign-in and onboarding hide it.
        val shouldHideTab = shouldHideNav
        val shouldShowBackButton = !isMainTab && !shouldHideNav && navController.previousBackStackEntry != null

        // Post notification to SwiftUI on main thread
        try {
            NSOperationQueue.mainQueue.addOperationWithBlock {
                val userInfo: Map<Any?, Any?> = mapOf(
                    "route" to currentRoute,
                    // The page's own name for the navigation bar ("" keeps the tab's name)
                    "title" to (iosPageTitle(currentRoute) ?: ""),
                    "shouldShowBackButton" to shouldShowBackButton,
                    "shouldHideTab" to shouldHideTab,
                    "shouldHideNavigationBar" to shouldHideNav
                )

                NSNotificationCenter.defaultCenter.postNotificationName(
                    aName = "ComposeRouteChanged",
                    `object` = null,
                    userInfo = userInfo
                )

                try {
                    if (shouldShowBackButton) {
                        NSNotificationCenter.defaultCenter.postNotificationName(
                            aName = "ComposeShowBackButton",
                            `object` = null,
                            userInfo = mapOf("route" to currentRoute)
                        )
                    } else {
                        NSNotificationCenter.defaultCenter.postNotificationName(
                            aName = "ComposeHideBackButton",
                            `object` = null,
                            userInfo = mapOf("route" to currentRoute)
                        )
                    }
                } catch (_: Throwable) {
                }

                if (currentRoute == "HeroScreen") {
                    NSNotificationCenter.defaultCenter.postNotificationName(
                        aName = "ComposeReady",
                        `object` = null
                    )
                }
            }
        } catch (_: Throwable) {
        }

        println("📍 Navigation changed to: $currentRoute")
        println("   Show back button: $shouldShowBackButton")
        println("   Is main tab: $isMainTab")
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
