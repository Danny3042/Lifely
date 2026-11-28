
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.distinctUntilChanged
import Authentication.LoginScreen
import Authentication.ResetPasswordScreen
import Authentication.SignUpScreen
import Colors.DarkColors
import Colors.HealthAppTheme
import Colors.LightColors
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceStyle
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
import pages.OnboardingScreen
import components.ui.LocalSystemBarsPadding
import components.ui.LocalBottomBarOverlap
import components.ui.LocalTopBarOverlap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import utils.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlatformApp(
    showBottomBar: Boolean,
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    useSystemDefault: Boolean,
    onUseSystemDefaultToggle: (Boolean) -> Unit
) {
    // remember a NavController for Compose navigation on iOS
    val navController = rememberNavController()

    // Know the appearance before the first frame. Swift announces the system style with a
    // notification, but its first one is posted before Compose starts listening, so Compose used
    // to start light and only turn dark on a later post (a light flash at launch, and onboarding
    // stayed light). Read the device's style and the saved Appearance choice directly instead.
    remember {
        try {
            PlatformBridge.systemInterfaceDark =
                UIScreen.mainScreen.traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } catch (_: Throwable) {
        }
    }
    val savedAppearance = remember {
        try {
            val defaults = NSUserDefaults.standardUserDefaults
            val useSystem = if (defaults.objectForKey("useSystemDefault") != null) defaults.boolForKey("useSystemDefault") else true
            useSystem to defaults.boolForKey("isDarkMode")
        } catch (_: Throwable) {
            true to false
        }
    }

    // Let PlatformBridge know whether the native host should show its tab bar
    try {
        PlatformBridge.useNativeTabBar = !showBottomBar
    } catch (_: Throwable) {
    }

    // Compose dark-mode state is provided from common `App` via parameters; treat params as source-of-truth
    // Keep a cached pair to avoid reposting identical dark-mode notifications (prevents flashes)
    var _lastDarkModePair by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }

    // Safe area insets from iOS - these will be updated via PlatformBridge
    var topInset by remember { mutableStateOf(0.0) }
    var bottomInset by remember { mutableStateOf(0.0) }
    // Left/right insets matter on iPhone Duo: the camera, status column, fold and a
    // side-mounted tab bar can all reserve space on one edge only.
    var leftInset by remember { mutableStateOf(0.0) }
    var rightInset by remember { mutableStateOf(0.0) }

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
            leftInset = (userInfo?.objectForKey("leading") as? Double) ?: 0.0
            rightInset = (userInfo?.objectForKey("trailing") as? Double) ?: 0.0
        }
        onDispose {
            // removeObserver expects a non-null observer reference
            NSNotificationCenter.defaultCenter.removeObserver(observer as Any)
        }
    }

    // Observe the native interface style so Compose can follow system appearance when requested.
    // rememberUpdatedState: the observer is registered once, so it must read the latest values.
    val currentUseSystemDefault by rememberUpdatedState(useSystemDefault)
    val currentOnDarkModeToggle by rememberUpdatedState(onDarkModeToggle)
    DisposableEffect(Unit) {
        val styleObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "SystemInterfaceStyleChanged",
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification: NSNotification? ->
            val userInfo = notification?.userInfo as? NSDictionary
            // Swift Boolean may bridge as kotlin.Boolean or NSNumber; handle both
            val darkAny = userInfo?.objectForKey("dark")
            val isDarkFromSystem = when (darkAny) {
                is Boolean -> darkAny
                is NSNumber -> darkAny.boolValue
                else -> false
            }
            // Always record the system style; only change the app's own setting when it follows the system.
            // Ask the saved choice, not the state: Swift sends this at launch, before the saved choice
            // has been restored, when the state still says "follow the system" and a Light choice was
            // being overwritten with the system's Dark.
            try {
                PlatformBridge.systemInterfaceDark = isDarkFromSystem
                val defaults = NSUserDefaults.standardUserDefaults
                val followsSystem = if (defaults.objectForKey("useSystemDefault") != null) {
                    defaults.boolForKey("useSystemDefault")
                } else {
                    currentUseSystemDefault
                }
                if (followsSystem) {
                    currentOnDarkModeToggle(isDarkFromSystem)
                }
            } catch (_: Throwable) {
            }
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(styleObserver as Any)
        }
    }

    // Observe requests from Swift to use the native SwiftUI TabView instead of Compose bottom bar
    DisposableEffect(Unit) {
        val tabObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "UseNativeTabBar",
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification: NSNotification? ->
            val userInfo = notification?.userInfo as? NSDictionary
            val enabledAny = userInfo?.objectForKey("enabled")
            val enabled = when (enabledAny) {
                is Boolean -> enabledAny
                is NSNumber -> enabledAny.boolValue
                else -> true
            }
            try {
                // In the shared PlatformBridge contract, 'useNativeTabBar' shows native tab bar when true
                PlatformBridge.useNativeTabBar = enabled
            } catch (_: Throwable) {
            }
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(tabObserver as Any)
        }
    }

    // Persist and notify when incoming params change. Note: `isDarkMode` and `useSystemDefault` are
    // controlled by common `App`; when they change we save them and post a notification for Swift.
    // Restore the saved Appearance choice once at launch. Until that's done, don't save or announce
    // anything: the defaults App starts with would otherwise overwrite the user's choice.
    var appearanceLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val defaults = NSUserDefaults.standardUserDefaults
            val savedUseSystem = if (defaults.objectForKey("useSystemDefault") != null) defaults.boolForKey("useSystemDefault") else true
            onUseSystemDefaultToggle(savedUseSystem)
            onDarkModeToggle(if (savedUseSystem) PlatformBridge.systemInterfaceDark else defaults.boolForKey("isDarkMode"))
        } catch (_: Throwable) {
        }
        appearanceLoaded = true
    }

    LaunchedEffect(isDarkMode, useSystemDefault, appearanceLoaded) {
        if (!appearanceLoaded) return@LaunchedEffect
        try {
            // Persist to NSUserDefaults directly with keys that Swift ContentView expects
            try {
                println("PlatformIos: Saving dark mode preferences -> isDarkMode: $isDarkMode, useSystemDefault: $useSystemDefault")
                NSUserDefaults.standardUserDefaults.setBool(isDarkMode, forKey = "isDarkMode")
                NSUserDefaults.standardUserDefaults.setBool(useSystemDefault, forKey = "useSystemDefault")
                NSUserDefaults.standardUserDefaults.synchronize()
                println("PlatformIos: Successfully saved to NSUserDefaults")
            } catch (e: Throwable) {
                println("PlatformIos: Failed to save to NSUserDefaults: ${e.message}")
            }
            
            // Also save via SettingsManager for backward compatibility
            try { SettingsManager.saveDarkMode(isDarkMode) } catch (_: Throwable) {}
            try { SettingsManager.saveUseSystemDefault(useSystemDefault) } catch (_: Throwable) {}

            // Post to Swift only when pair actually changes
            val newPair = Pair(isDarkMode, useSystemDefault)
            if (_lastDarkModePair != newPair) {
                _lastDarkModePair = newPair
                println("PlatformIos: Posting ComposeDarkModeChanged notification")
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    val userInfo: Map<Any?, Any?> = mapOf("dark" to isDarkMode, "useSystem" to useSystemDefault)
                    NSNotificationCenter.defaultCenter.postNotificationName(
                        aName = "ComposeDarkModeChanged",
                        `object` = null,
                        userInfo = userInfo
                    )
                }
            } else {
                println("PlatformIos: Dark mode pair unchanged, skipping notification")
            }
        } catch (_: Throwable) {
        }
    }

    // pending route set by native observer; Compose will navigate when this changes
    var pendingRoute by remember { mutableStateOf<String?>(null) }

    // A tab bar tap: close any sub pages (Notifications, About, a meditation…) and show the tab host.
    // Called straight from the native callbacks rather than through pendingRoute: the tab request
    // arrives several times in a row, and setting pendingRoute to the same value again doesn't
    // re-run its effect, so it could get stuck and tab taps stopped closing sub pages.
    fun showTabHost() {
        try {
            // The native host asks for a tab at launch; that mustn't skip first-launch onboarding.
            if (navController.currentBackStackEntry?.destination?.route == OnboardingScreen) return
            val popped = navController.popBackStack("HeroScreen", inclusive = false)
            if (!popped && navController.currentBackStackEntry?.destination?.route != "HeroScreen") {
                navController.navigate("HeroScreen") { launchSingleTop = true }
            }
        } catch (_: Throwable) {
        }
    }

    // If a pending route is set (by the NotificationCenter callback), navigate from Compose context
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute
        if (!route.isNullOrEmpty()) {
            if (route == "HeroScreen") {
                if (navController.currentBackStackEntry?.destination?.route == OnboardingScreen) {
                    pendingRoute = null
                    return@LaunchedEffect
                }
                // A tab bar tap: close any sub pages and go back to the tab host.
                // This must not use saveState/restoreState: popping with saveState and then
                // restoring HeroScreen's state puts the sub page straight back on top.
                try {
                    val popped = navController.popBackStack("HeroScreen", inclusive = false)
                    if (!popped && navController.currentBackStackEntry?.destination?.route != "HeroScreen") {
                        navController.navigate("HeroScreen") { launchSingleTop = true }
                    }
                } catch (_: Throwable) {
                }
                pendingRoute = null
                return@LaunchedEffect
            }
            try {
                navController.navigate(route) {
                    // Ensure we don't create duplicate HeroScreen entries and restore state when possible
                    popUpTo("HeroScreen") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            } catch (_: IllegalArgumentException) {
                // Unknown route from the native side: fall back to HeroScreen
                // (replaces the old "{any}" catch-all destination, which hijacked real routes)
                println("PlatformIos: unknown route requested -> $route, showing HeroScreen")
                try {
                    navController.navigate("HeroScreen") { launchSingleTop = true }
                } catch (_: Throwable) {
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
                         showTabHost()
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
                    showTabHost()

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
        val tabRoutes = setOf("HomePage", "HabitCoachingPage", "ChatScreen", "meditation", "profile", "Home", "Habits", "Chat", "Meditate", "Profile")
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
                    } else if (tabRoutes.contains(route)) {
                        println("PlatformIos: Tab route requested via requestedRoute -> $route")
                        val standardTab = when (route) {
                            "Home" -> "HomePage"
                            "Habits" -> "HabitCoachingPage"
                            "Chat" -> "ChatScreen"
                            "Meditate" -> "meditation"
                            "Profile" -> "profile"
                            else -> route
                        }
                        try {
                            PlatformBridge.requestedTabName = standardTab
                            PlatformBridge.requestedTabSignal = PlatformBridge.requestedTabSignal + 1
                        } catch (_: Throwable) {
                        }
                        showTabHost()
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
                    val routesWithHiddenNav = setOf("Login", "SignUp", "ResetPassword", "Onboarding")
                    
                    val isMain = mainRoutes.contains(destName)
                    val shouldHideNav = routesWithHiddenNav.contains(destName)
                    // Sub pages keep the tab bar (tapping a tab closes them); sign-in and onboarding hide it.
                    val shouldHideTab = shouldHideNav
                    // Show back button on sub-pages (when tab bar is hidden and nav bar is shown)
                    val shouldShowBackButton = !isMain && !shouldHideNav

                    NSOperationQueue.mainQueue.addOperationWithBlock {
                        val userInfo: Map<Any?, Any?> = mapOf(
                            "route" to destName,
                            // The page's own name for the navigation bar ("" keeps the tab's name)
                            "title" to (iosPageTitle(destName) ?: ""),
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

    // Set Compose Material3 colors based on dark mode state - respect 'use system default' when enabled
    // Until the saved choice has been handed to App (one frame), use the copy read above.
    val followSystem = if (appearanceLoaded) useSystemDefault else savedAppearance.first
    val chosenDark = if (appearanceLoaded) isDarkMode else savedAppearance.second
    val effectiveDark = if (followSystem) PlatformBridge.systemInterfaceDark else chosenDark
    // App-wide Material 3 Expressive theme. The Surface goes inside the theme so its content colour
    // (onBackground) is the theme's: any page without its own Surface then gets readable text in
    // dark mode instead of the default black.
    // Pages that draw edge to edge (the meditation sky) go under the status, navigation and tab bars.
    // For those, the app stops padding itself, tells Swift to let the Compose view extend under the
    // bars, and hands the insets to the page so it can keep its controls clear of them.
    val currentEntry by navController.currentBackStackEntryAsState()
    // (the meditation sky, and the onboarding sky)
    val edgeToEdge = currentEntry?.destination?.route.let { it == MEDITATION_PAGE_ROUTE || it == OnboardingScreen }
    LaunchedEffect(edgeToEdge) {
        NSOperationQueue.mainQueue.addOperationWithBlock {
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = "ComposeEdgeToEdge",
                `object` = null,
                userInfo = mapOf<Any?, Any?>("enabled" to edgeToEdge)
            )
        }
    }
    val systemBars = PaddingValues.Absolute(
        left = maxOf(leftInset, PlatformBridge.barLeftInset).dp,
        top = topInset.dp,
        right = maxOf(rightInset, PlatformBridge.barRightInset).dp,
        bottom = bottomInset.dp
    )
    // Every page with the tab bar runs under it at the bottom (Swift extends the Compose view there),
    // so the bottom inset isn't padded; pages pad their scrolling content by it instead
    // (LocalBottomBarOverlap). Screens without the tab bar (sign-in, onboarding) keep the padding.
    val tabBarShown = currentEntry?.destination?.route !in setOf("Login", "SignUp", "ResetPassword", OnboardingScreen)
    // Pages with the bars run under all of them (Swift extends the Compose view to every edge), so the
    // side insets come from the SwiftUI host too: on iPhone Duo the side tab bar, camera and fold.
    val sideLeft = if (tabBarShown) maxOf(leftInset, PlatformBridge.barLeftInset) else leftInset
    val sideRight = if (tabBarShown) maxOf(rightInset, PlatformBridge.barRightInset) else rightInset
    val pagePadding = PaddingValues.Absolute(
        left = sideLeft.dp,
        // Pages with the bars run under the navigation bar too and pad their own content (LocalTopBarOverlap)
        top = if (tabBarShown) 0.dp else topInset.dp,
        right = sideRight.dp,
        bottom = if (tabBarShown) 0.dp else bottomInset.dp
    )

    // Native iOS bars: Swift places a hidden UIScrollView over the page and keeps its offset in step
    // with Compose's scrolling, so iOS itself collapses the large title, draws the scroll edge effect
    // (the blur under the bar, iOS 26+) and minimises the bar (iOS 27). Any scrollable in the app
    // reports through this nested-scroll connection at the root.
    var scrolledPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current.density
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Pages that keep the title fixed (the chat with its sidebar) opt out
                if (!PlatformBridge.linkScrollToBars) return Offset.Zero
                // Pulling down past the top means we're at the top again
                scrolledPx = if (available.y > 0f) 0f else (scrolledPx - consumed.y).coerceAtLeast(0f)
                return Offset.Zero
            }
        }
    }
    // A new page or tab starts at the top
    LaunchedEffect(currentEntry?.destination?.route, PlatformBridge.requestedTabSignal, PlatformBridge.linkScrollToBars) {
        scrolledPx = 0f
    }
    val followsNativeBars = tabBarShown && !edgeToEdge
    LaunchedEffect(followsNativeBars) {
        snapshotFlow { if (followsNativeBars) scrolledPx / density else 0f }
            .distinctUntilChanged()
            .collect { points ->
                NSNotificationCenter.defaultCenter.postNotificationName(
                    aName = "ComposeScrollOffset",
                    `object` = null,
                    userInfo = mapOf<Any?, Any?>("offset" to points.toDouble())
                )
            }
    }
    // Content starts below the bar at its expanded (large title) height and keeps that start while the
    // title collapses or the bar minimises, exactly like a native scroll view's content inset.
    // It's re-read whenever the page is back at the top, or the screen size or page changes.
    // Swift reports how much of the top the status and navigation bars cover.
    val barTopInset = PlatformBridge.topBarInset
    val containerSize = LocalWindowInfo.current.containerSize
    var restTopInset by remember { mutableStateOf(barTopInset) }
    LaunchedEffect(containerSize, currentEntry?.destination?.route) { restTopInset = barTopInset }
    SideEffect {
        if (scrolledPx == 0f && barTopInset > restTopInset) restTopInset = barTopInset
    }
    val topOverlap = if (scrolledPx == 0f) maxOf(barTopInset, restTopInset) else restTopInset

    HealthAppTheme(darkTheme = effectiveDark) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(if (edgeToEdge) Modifier else Modifier.padding(pagePadding)),
            color = MaterialTheme.colorScheme.background
        ) {
          CompositionLocalProvider(
              LocalSystemBarsPadding provides systemBars,
              LocalBottomBarOverlap provides if (tabBarShown && !edgeToEdge) bottomInset.dp else 0.dp,
              LocalTopBarOverlap provides if (followsNativeBars) topOverlap.dp else 0.dp,
          ) {
            Box(Modifier.fillMaxSize().nestedScroll(scrollWatcher)) {
            // Navigation host for Compose — on iOS we hide Compose's bottom bar because
            // the app uses a native SwiftUI TabView. Pass showBottomBar=false.
            YourMainNavHost(
                navController = navController,
                showBottomBar = false,
                isDarkMode = isDarkMode,
                onDarkModeToggle = onDarkModeToggle,
                useSystemDefault = useSystemDefault,
                onUseSystemDefaultToggle = onUseSystemDefaultToggle
            )
            }
          }
        }
    }
}

/** What the iOS navigation bar calls each page pushed inside a tab. Tabs themselves keep the tab's name. */
internal fun iosPageTitle(route: String): String? = when (route) {
    "About" -> "About"
    "Notification" -> "Notifications"
    "Permissions" -> "Permissions"
    "Account" -> "Account"
    "DarkModeSettingsScreen" -> "Appearance"
    "completed_habits" -> "Completed habits"
    "meditation_reflection" -> "Reflection"
    "check_in" -> "Check in"
    "check_in_calendar" -> "Check-in calendar"
    "Insights" -> "Insights"
    "timer" -> "Timer"
    else -> null
}
