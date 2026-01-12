import Authentication.Authentication
import Authentication.LoginScreen
import Authentication.ResetPasswordScreen
import Authentication.SignUpScreen
import Colors.DarkColors
import Colors.LightColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import config.VERSION_NUMBER
import kotlinx.coroutines.flow.collectLatest
import pages.ChartsPage
import pages.ChartsPageScreen
import pages.HomePageScreen
import pages.InsightsPage
import pages.InsightsPageScreen
import pages.STRESS_MANAGEMENT_PAGE_ROUTE
import pages.StressManagementPage
import pages.Timer
import pages.TimerScreenContent
import platform.ChartBridge
import platform.Foundation.NSDictionary
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.PlatformBridge
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
import utils.HealthKitServiceImpl
import utils.iOSHealthKitManager
import tabs.HomeTab
import utils.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlatformApp(showBottomBar: Boolean) {
    // remember a NavController for Compose navigation on iOS
    val navController = rememberNavController()

    // Let PlatformBridge know whether the native host should show its tab bar
    try {
        PlatformBridge.useNativeTabBar = !showBottomBar
    } catch (_: Throwable) {
    }

    // Dark mode state (iOS) - load/save using SettingsManager similar to Android
    var isDarkMode by remember { mutableStateOf(false) }
    var useSystemDefault by remember { mutableStateOf(true) }
    
    // Safe area insets from iOS - these will be updated via PlatformBridge
    var topInset by remember { mutableStateOf(0.0) }
    var bottomInset by remember { mutableStateOf(0.0) }

    // Listen for safe area changes from iOS
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "SafeAreaInsetsChanged",
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification: NSNotification? ->
            val userInfo = notification?.userInfo as? NSDictionary
            topInset = (userInfo?.objectForKey("top") as? Double) ?: 0.0
            bottomInset = (userInfo?.objectForKey("bottom") as? Double) ?: 0.0
        }
        onDispose {
            // removeObserver expects a non-null observer reference
            NSNotificationCenter.defaultCenter.removeObserver(observer as Any)
        }
    }

    LaunchedEffect(Unit) {
        try {
            isDarkMode = SettingsManager.loadDarkMode()
            useSystemDefault = SettingsManager.loadUseSystemDefault()
        } catch (_: Throwable) {
            isDarkMode = false
            useSystemDefault = true
        }
    }

    LaunchedEffect(isDarkMode) {
        try {
            SettingsManager.saveDarkMode(isDarkMode)
        } catch (_: Throwable) {
        }
    }

    LaunchedEffect(useSystemDefault) {
        try {
            SettingsManager.saveUseSystemDefault(useSystemDefault)
        } catch (_: Throwable) {
        }
    }

    // Notify iOS (Swift) when dark mode or 'use system default' changes so native bars can update
    LaunchedEffect(isDarkMode, useSystemDefault) {
        try {
            NSOperationQueue.mainQueue.addOperationWithBlock {
                val userInfo: Map<Any?, Any?> = mapOf("dark" to isDarkMode, "useSystem" to useSystemDefault)
                NSNotificationCenter.defaultCenter.postNotificationName(
                    aName = "ComposeDarkModeChanged",
                    `object` = null,
                    userInfo = userInfo
                )
            }
        } catch (_: Throwable) {
        }
    }

    // pending route set by native observer; Compose will navigate when this changes
    var pendingRoute by remember { mutableStateOf<String?>(null) }

    // If a pending route is set (by the NotificationCenter callback), navigate from Compose context
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute
        if (!route.isNullOrEmpty()) {
            try {
                navController.navigate(route) {
                    // Ensure we don't create duplicate HeroScreen entries and restore state when possible
                    popUpTo("HeroScreen") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            } catch (_: Throwable) {
            }
            // clear pendingRoute so future identical requests will re-trigger navigation if needed
            pendingRoute = null
        }
    }

    // Observe native navigation requests from AuthManager (Swift) via NotificationCenter
    DisposableEffect(navController) {
        @Suppress("UNUSED_VARIABLE")
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
             name = "AuthManagerNavigateToRoute",
             `object` = null,
             queue = NSOperationQueue.mainQueue
         ) { notification: NSNotification? ->
             val userInfo = notification?.userInfo as? NSDictionary
             val route = (userInfo?.objectForKey("route") as? String)
             if (!route.isNullOrEmpty()) {
                 try {
                     // Tab routes that should be handled by the tab navigation system
                     val tabRoutes = setOf("HomePage", "HabitCoachingPage", "ChatScreen", "meditation", "profile", "Home", "Habits", "Chat", "Meditate", "Profile")
                     if (tabRoutes.contains(route)) {
                         println("PlatformIos: Tab route requested via AuthManager -> $route")
                         // Map to a standard tab name and signal the Compose side to switch tabs
                         val standardTab = when (route) {
                             "Home" -> "HomePage"
                             "Habits" -> "HabitCoachingPage"
                             "Chat" -> "ChatScreen"
                             "Meditate" -> "meditation"
                             "Profile" -> "profile"
                             else -> route
                         }
                         // Request the Compose tab switch via PlatformBridge and ensure HeroScreen is shown
                         try {
                            PlatformBridge.requestedTabName = standardTab
                            PlatformBridge.requestedTabSignal = PlatformBridge.requestedTabSignal + 1
                         } catch (_: Throwable) {
                         }
                         // Ensure we end up on the HeroScreen where the TabNavigator lives
                         pendingRoute = "HeroScreen"
                     } else {
                         // Non-tab routes, navigate normally
                         pendingRoute = route
                     }
                 } catch (_: Throwable) {
                 }
             }
         }
         onDispose {
             NSNotificationCenter.defaultCenter.removeObserver(observer as Any)
         }
    }

    // Observe tab bar clicks from iOS
    LaunchedEffect(Unit) {
        snapshotFlow { PlatformBridge.requestedTabSignal }.collectLatest { signal ->
            val tabName = PlatformBridge.requestedTabName
            println("PlatformIos: requestedTabSignal changed to $signal, tabName = $tabName")
            
            if (!tabName.isNullOrEmpty()) {
                try {
                    // Ensure app is showing HeroScreen so the Compose TabNavigator can switch tabs
                    val current = navController.currentBackStackEntry?.destination?.route
                    if (current != "HeroScreen") {
                        // Set pendingRoute so the existing pending-route handler navigates to HeroScreen
                        pendingRoute = "HeroScreen"
                    }

                    // Also post a notification so the native Swift TabView can switch selection to match
                    NSOperationQueue.mainQueue.addOperationWithBlock {
                        val userInfo: Map<Any?, Any?> = mapOf("tab" to tabName)
                        NSNotificationCenter.defaultCenter.postNotificationName(
                            aName = "SwitchNativeTab",
                            `object` = null,
                            userInfo = userInfo
                        )
                    }
                } catch (e: Throwable) {
                    println("PlatformIos: Failed to navigate to tab $tabName: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
     }

    // Also observe PlatformBridge.requestedRouteSignal (requests from Compose UI/native host)
    LaunchedEffect(Unit) {
        snapshotFlow { PlatformBridge.requestedRouteSignal }.collectLatest {
            val route = PlatformBridge.requestedRoute
            if (!route.isNullOrEmpty()) {
                // If route requests the ChartsPage on iOS, ask native Swift to open the native Charts view
                try {
                    if (route == ChartsPageScreen) {
                        println("PlatformIos: Charts requested from Compose -> posting OpenNativeCharts")
                        NSOperationQueue.mainQueue.addOperationWithBlock {
                            NSNotificationCenter.defaultCenter.postNotificationName(
                                aName = "OpenNativeCharts",
                                `object` = null,
                                userInfo = null
                            )
                        }

                        // Also set a persistent flag so Swift can pick it up if it missed the notification
                        try {
                            NSUserDefaults.standardUserDefaults.setBool(true, forKey = "OpenNativeChartsPending")
                        } catch (_: Throwable) {
                            println("PlatformIos: failed to set OpenNativeChartsPending flag")
                        }

                        // Publish sample chart data immediately so native Swift Charts can display something
                        try {
                            ChartBridge.publishSample()
                        } catch (_: Throwable) {
                            println("PlatformIos: ChartBridge.publishSample failed")
                        }

                        // Clear the requestedRoute so it can be requested again later
                        PlatformBridge.requestedRoute = null
                    } else {
                        println("PlatformIos: route requested -> $route")
                        pendingRoute = route
                        PlatformBridge.requestedRoute = null
                    }
                } catch (_: Throwable) {
                    println("PlatformIos: failed handling requestedRoute")
                }
            }
        }
    }

    // Observe route changes via snapshotFlow - platform-agnostic approach
    LaunchedEffect(navController) {
        snapshotFlow { navController.currentBackStackEntry?.destination?.route ?: navController.currentBackStackEntry?.destination?.toString() }
            .collectLatest { destName ->
                try {
                    if (destName == null) return@collectLatest

                    // Decide which routes are main (show tab bar) vs sub-pages
                    // Routes where we want to show the tab bar
                    val mainRoutes = setOf("HomePage", "HabitCoachingPage", "ChatScreen", "meditation", "profile", "HeroScreen")
                    // Routes where we want to hide the navigation bar
                    val routesWithHiddenNav = setOf("Login", "SignUp", "ResetPassword")
                    
                    val isMain = mainRoutes.contains(destName)
                    val shouldHideTab = !isMain
                    val shouldHideNav = routesWithHiddenNav.contains(destName)
                    // Show back button on sub-pages (when tab bar is hidden and nav bar is shown)
                    val shouldShowBackButton = !isMain && !shouldHideNav

                    NSOperationQueue.mainQueue.addOperationWithBlock {
                        val userInfo: Map<Any?, Any?> = mapOf(
                            "route" to destName, 
                            "shouldHideTab" to shouldHideTab, 
                            "shouldHideNavigationBar" to shouldHideNav,
                            "shouldShowBackButton" to shouldShowBackButton
                        )
                        NSNotificationCenter.defaultCenter.postNotificationName(
                            aName = "ComposeRouteChanged",
                            `object` = null,
                            userInfo = userInfo
                        )

                        // Also post a dedicated show/hide back button notification for convenience
                        try {
                            if (shouldShowBackButton) {
                                NSNotificationCenter.defaultCenter.postNotificationName(
                                    aName = "ComposeShowBackButton",
                                    `object` = null,
                                    userInfo = mapOf("route" to destName)
                                )
                            } else {
                                NSNotificationCenter.defaultCenter.postNotificationName(
                                    aName = "ComposeHideBackButton",
                                    `object` = null,
                                    userInfo = mapOf("route" to destName)
                                )
                            }
                        } catch (_: Throwable) {
                        }
                    }

                    if (destName == "HeroScreen") {
                        NSOperationQueue.mainQueue.addOperationWithBlock {
                            NSNotificationCenter.defaultCenter.postNotificationName(
                                aName = "ComposeReady",
                                `object` = null
                            )
                        }
                    }
                } catch (_: Throwable) {
                }
            }
    }

    // Listen for back button press from iOS
    DisposableEffect(navController) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
             name = "ComposeBackPressed",
             `object` = null,
             queue = NSOperationQueue.mainQueue
        ) { _: NSNotification? ->
            try {
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                }
            } catch (_: Throwable) {
            }
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer as Any)
        }
    }

    // Removed PlatformBridge.useNativeTabBar writes because the binding isn't available reliably here.

    // Set initial Compose Material3 colors based on dark mode state
    val colors = if (isDarkMode) DarkColors else LightColors

    // Main app surface with safe area insets applied
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = topInset.dp,
                bottom = bottomInset.dp
            ),
        color = colors.background
    ) {
        // Apply MaterialTheme with dynamic colors
        MaterialTheme(
            colorScheme = colors
        ) {
            // Navigation host for Compose — on iOS we hide Compose's bottom bar because
            // the app uses a native SwiftUI TabView. Pass showBottomBar=false.
            YourMainNavHost(navController = navController, showBottomBar = false)
        }
     }
 }
