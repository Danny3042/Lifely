package navigation

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
import sub_pages.REFLECTION_PAGE_ROUTE
import sub_pages.ReflectionPage
import tabs.HomeTab
import utils.HealthKitServiceImpl
import utils.iOSHealthKitManager
import config.VERSION_NUMBER
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun YourMainNavHost(navController: NavHostController, showBottomBar: Boolean = true) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(currentBackStackEntry) {
        val currentRoute = currentBackStackEntry?.destination?.route ?: "HeroScreen"

        val mainTabRoutes = setOf("HomePage", "HabitCoachingPage", "ChatScreen", "meditation", "profile", "HeroScreen")
        val routesWithHiddenNav = setOf("Login", "SignUp", "ResetPassword")

        val isMainTab = mainTabRoutes.any { currentRoute.startsWith(it) }
        val shouldHideTab = !isMainTab
        val shouldHideNav = routesWithHiddenNav.contains(currentRoute)
        val shouldShowBackButton = !isMainTab && !shouldHideNav && navController.previousBackStackEntry != null

        // Android: log for parity with iOS
        println("[YourMainNavHost-Android] route=$currentRoute showBack=$shouldShowBackButton isMain=$isMainTab")
    }

    NavHost(navController = navController, startDestination = "HeroScreen") {
        composable(LoginScreen) { Authentication().Login(navController) }
        composable("HeroScreen") { HeroScreen(navController, showBottomBar = showBottomBar) }
        composable(SignUpScreen) { Authentication().signUp(navController) }
        composable(ResetPasswordScreen) { Authentication().ResetPassword(navController)}

        composable(HomePageScreen) { HomeTab.Content() }
        composable("HabitCoachingPage") { HomeTab.Content() }
        composable("ChatScreen") { HomeTab.Content() }
        composable("meditation") { HomeTab.Content() }
        composable("profile") { HomeTab.Content() }

        composable(AboutPageScreen) { AboutPage(navController, versionNumber = VERSION_NUMBER) }
        composable(NotificationPageScreen) { NotificationPage(navController) }
        composable(DarkModeSettingsPageScreen) {
            DarkModeSettingsPage(
                isDarkMode = false,
                onDarkModeToggle = {},
                useSystemDefault = true,
                onUseSystemDefaultToggle = {},
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
        composable(STRESS_MANAGEMENT_PAGE_ROUTE) { StressManagementPage(navController) }
        composable(InsightsPageScreen) { InsightsPage(insightsViewModel = viewModel()) }
        composable(ChartsPageScreen) { ChartsPage(navController = navController) }

        composable("{any}") { backStackEntry ->
            val route = backStackEntry.arguments?.getString("any")
            LaunchedEffect(route) {
                if (!route.isNullOrEmpty()) {
                    navController.navigate("HeroScreen") {
                        popUpTo("HeroScreen") { inclusive = true }
                    }
                }
            }
        }
    }
}
