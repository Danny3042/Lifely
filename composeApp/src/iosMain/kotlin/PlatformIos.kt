import Authentication.Authentication
import Authentication.LoginScreen
import Authentication.ResetPasswordScreen
import Authentication.SignUpScreen
import Colors.DarkColors
import Colors.LightColors
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import config.VERSION_NUMBER
import kotlinx.coroutines.flow.collectLatest
import pages.ChartsPageScreen
import pages.HomePageScreen
import pages.InsightsPage
import pages.InsightsPageScreen
import pages.STRESS_MANAGEMENT_PAGE_ROUTE
import pages.StressManagementPage
import pages.TimerScreenContent
import platform.ChartBridge
import platform.Foundation.NSDictionary
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.PlatformBridge
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
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
import tabs.HomeTab
import utils.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlatformApp() {
    // remember a NavController for Compose navigation on iOS
    val navController = rememberNavController()

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
            if (observer != null) {
                NSNotificationCenter.defaultCenter.removeObserver(observer as Any)
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            isDarkMode = SettingsManager.loadDarkMode()
            useSystemDefault = SettingsManager.loadUseSystemDefault()
        } catch (e: Throwable) {
            isDarkMode = false
            useSystemDefault = true
        }
    }

    LaunchedEffect(isDarkMode) {
        try {
            SettingsManager.saveDarkMode(isDarkMode)
        } catch (e: Throwable) {
        }
    }

    LaunchedEffect(useSystemDefault) {
        try {
            SettingsManager.saveUseSystemDefault(useSystemDefault)
        } catch (e: Throwable) {
        }
    }

    // Notify iOS (Swift) when dark mode or 'use system default' changes so native bars can update
    LaunchedEffect(isDarkMode, useSystemDefault) {
        try {
            NSOperationQueue.mainQueue.addOperationWithBlock {
                val userInfo = mapOf("dark" to isDarkMode, "useSystem" to useSystemDefault)
                NSNotificationCenter.defaultCenter.postNotificationName(
                    aName = "ComposeDarkModeChanged",
                    `object` = null,
                    userInfo = userInfo as Map<Any?, *>
                )
            }
        } catch (e: Throwable) {
        }
    }

    LaunchedEffect(Unit) {
        try {
            println("PlatformIos: initializing NotifierManager for iOS")
            NotifierManager.initialize(NotificationPlatformConfiguration.Ios())
            println("PlatformIos: NotifierManager initialized")
        } catch (e: Throwable) {
            println("PlatformIos: NotifierManager.initialize failed: ${e.message}")
        }
    }

    // pending route set by native observer; Compose will navigate when this changes
    var pendingRoute by remember { mutableStateOf<String?>(null) }

    // If a pending route is set (by the NotificationCenter callback), navigate from Compose context
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute
        if (!route.isNullOrEmpty()) {
            try {
                navController.navigate(route)
            } catch (e: Throwable) {
            }
            pendingRoute = null
        }
    }

    // Observe native navigation requests from AuthManager (Swift) via NotificationCenter
    DisposableEffect(navController) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "AuthManagerNavigateToRoute",
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification: NSNotification? ->
            val userInfo = notification?.userInfo as? NSDictionary
            val route = (userInfo?.objectForKey("route") as? String)
            if (!route.isNullOrEmpty()) {
                try {
                    // If the route looks like a tab request, set the PlatformBridge requestedTab and request navigation
                    val tabRoutes = setOf("HomePage", "HabitCoachingPage", "ChatScreen", "meditation", "profile", "Home", "Habits", "Chat", "Meditate", "Profile")
                    if (tabRoutes.contains(route)) {
                        // Write requested tab name and increment signal so Compose observers detect repeated clicks
                        PlatformBridge.requestedTabName = route
                        PlatformBridge.requestedTabSignal = PlatformBridge.requestedTabSignal + 1
                        pendingRoute = "HeroScreen"
                    } else {
                        pendingRoute = route
                    }
                } catch (e: Throwable) {
                }
            }
        }
        onDispose {
            if (observer != null) {
                NSNotificationCenter.defaultCenter.removeObserver(observer as Any)
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
                        NSOperationQueue.mainQueue.addOperationWithBlock {
                            NSNotificationCenter.defaultCenter.postNotificationName(
                                aName = "OpenNativeCharts",
                                `object` = null,
                                userInfo = null
                            )
                        }

                        // Publish sample chart data immediately so native Swift Charts can display something
                        try {
                            ChartBridge.publishSample()
                        } catch (e: Throwable) {
                        }

                        // Clear the requestedRoute so it can be requested again later
                        PlatformBridge.requestedRoute = null
                    } else {
                        pendingRoute = route
                        PlatformBridge.requestedRoute = null
                    }
                } catch (e: Throwable) {
                }
            }
        }
    }

    // Strong readiness signal: post ComposeReady once NavController reaches HeroScreen
    DisposableEffect(navController) {
        var posted = false
        val listener = NavController.OnDestinationChangedListener { controller, destination, arguments ->
            try {
                val destName = destination.route ?: destination.toString()

                // Notify iOS about the current route so it can show/hide back button
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    val userInfo = mapOf("route" to destName)
                    NSNotificationCenter.defaultCenter.postNotificationName(
                        aName = "ComposeRouteChanged",
                        `object` = null,
                        userInfo = userInfo as Map<Any?, *>
                    )
                }
                
                if (!posted && destName == "HeroScreen") {
                    posted = true
                    NSOperationQueue.mainQueue.addOperationWithBlock {
                        NSNotificationCenter.defaultCenter.postNotificationName(
                            aName = "ComposeReady",
                            `object` = null
                        )
                    }
                }
            } catch (e: Throwable) {
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
    
    // Listen for back button press from iOS
    DisposableEffect(navController) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "ComposeBackPressed",
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification: NSNotification? ->
            try {
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                } else {
                }
            } catch (e: Throwable) {
            }
        }
        onDispose {
            if (observer != null) {
                NSNotificationCenter.defaultCenter.removeObserver(observer as Any)
            }
        }
    }

    // Render the shared Compose NavHost on iOS so the Compose MainViewController shows the full app
    // Compute effective dark mode (use system default unless overridden)
    val darkModeEffective = if (useSystemDefault) isSystemInDarkTheme() else isDarkMode
    val colors = if (darkModeEffective) DarkColors else LightColors

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navControllerLocal = navController
            
            // Apply padding for safe area insets to prevent content from being hidden
            // Do not apply global top/bottom safe-area padding here because individual screens
            // already call rememberSafeAreaInsetsWithTabBar() and handle their own insets.
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                NavHost(navController = navControllerLocal, startDestination = LoginScreen) {
                    composable(LoginScreen) { Authentication().Login(navControllerLocal) }
                    composable("HeroScreen") { HeroScreen(navControllerLocal, showBottomBar = false) }
                    composable(SignUpScreen) { Authentication().signUp(navControllerLocal) }
                    composable(ResetPasswordScreen) { Authentication().ResetPassword(navControllerLocal) }
                    composable(HomePageScreen) { HomeTab.Content() }
                    composable(DarkModeSettingsPageScreen) {
                        DarkModeSettingsPage(
                            isDarkMode = isDarkMode,
                            onDarkModeToggle = { checked: Boolean -> isDarkMode = checked },
                            useSystemDefault = useSystemDefault,
                            onUseSystemDefaultToggle = { use: Boolean -> useSystemDefault = use },
                            navController = navControllerLocal
                        )
                    }
                    composable(InsightsPageScreen) { InsightsPage() }
                    composable(STRESS_MANAGEMENT_PAGE_ROUTE) { StressManagementPage(navControllerLocal) }
                    composable(MEDITATION_PAGE_ROUTE) { MeditationPage(onBack = { navControllerLocal.popBackStack() }, onNavigateToInsights = { navControllerLocal.navigate(InsightsPageScreen) }) }
                    composable(CompletedHabitsPageRoute) { CompletedHabitsPage(navControllerLocal) }
                    composable(NotificationPageScreen) { NotificationPage(navControllerLocal) }
                    composable(AboutPageScreen) { AboutPage(navControllerLocal, versionNumber = VERSION_NUMBER) }
                    composable("TimerScreen") { TimerScreenContent(onBack = { navControllerLocal.popBackStack() }) }
                }
            }
        }
    }
}