import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.unit.dp
import components.ui.LocalWindowSize
import components.ui.platformScaffoldInsets
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.NavHostController
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabDisposable
import cafe.adriel.voyager.navigator.tab.TabNavigator
import kotlinx.coroutines.flow.collectLatest
import platform.PlatformBridge
import tabs.ChatTab
import tabs.HabitsTab
import tabs.HomeTab
import tabs.MeditateTab
import tabs.ProfileTab
import utils.HandleBackNavigation
import utils.isAndroid

const val HeroScreen = "HeroScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroScreen(navController: NavHostController, showBottomBar: Boolean = true) {
    HandleBackNavigation(navController)

    // Create tab instances once so we can reliably switch to these exact instances later
    val homeTab = HomeTab
    val habitsTab = HabitsTab(navController)
    val chatTab = ChatTab
    val meditateTab = MeditateTab(navController)
    val profileTab = ProfileTab(navController)

    TabNavigator(
        homeTab,
        tabDisposable = {
            TabDisposable(
                navigator = it,
                tabs = listOf(
                    homeTab,
                    habitsTab,
                    chatTab,
                    meditateTab,
                    profileTab
                )
            )
        }
    ) { tabNavigator ->

        // If a tab was requested before HeroScreen composition, apply it immediately
        LaunchedEffect(Unit) {
            val initial = PlatformBridge.requestedTabName
            if (initial != null) {
                when (initial) {
                    "HomePage", "Home" -> tabNavigator.current = homeTab
                    "HabitCoachingPage", "Habits" -> tabNavigator.current = habitsTab
                    "ChatScreen", "Chat" -> tabNavigator.current = chatTab
                    "meditation", "Meditate" -> tabNavigator.current = meditateTab
                    "profile", "Profile" -> tabNavigator.current = profileTab
                }
                PlatformBridge.requestedTabName = null
            }
        }

        // Observe platform requested tab reactively (no polling)
        LaunchedEffect(Unit) {
            snapshotFlow { PlatformBridge.requestedTabSignal }
                .collectLatest {
                    val requested = PlatformBridge.requestedTabName
                    if (requested != null) {
                        when (requested) {
                            "HomePage", "Home" -> tabNavigator.current = homeTab
                            "HabitCoachingPage", "Habits" -> tabNavigator.current = habitsTab
                            "ChatScreen", "Chat" -> tabNavigator.current = chatTab
                            "meditation", "Meditate" -> tabNavigator.current = meditateTab
                            "profile", "Profile" -> tabNavigator.current = profileTab
                        }
                        // Clear the name after handling so subsequent signals can set it again
                        PlatformBridge.requestedTabName = null
                    }
                }
        }

        // Also react directly to requestedTabName changes (in case signal was missed or set earlier)
        LaunchedEffect(Unit) {
            snapshotFlow { PlatformBridge.requestedTabName }
                .collectLatest { requested ->
                    if (!requested.isNullOrEmpty()) {
                        when (requested) {
                            "HomePage", "Home" -> tabNavigator.current = homeTab
                            "HabitCoachingPage", "Habits" -> tabNavigator.current = habitsTab
                            "ChatScreen", "Chat" -> tabNavigator.current = chatTab
                            "meditation", "Meditate" -> tabNavigator.current = meditateTab
                            "profile", "Profile" -> tabNavigator.current = profileTab
                        }
                        PlatformBridge.requestedTabName = null
                    }
                }
        }

        val windowSize = LocalWindowSize.current
        val tabs = listOf(homeTab, habitsTab, chatTab, meditateTab, profileTab)
        // Phones in portrait get a bottom bar; tablets, foldables and landscape get a side rail
        val useRail = showBottomBar && windowSize.useNavigationRail

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    header = { Spacer(Modifier.height(8.dp)) },
                ) {
                    Spacer(Modifier.weight(1f))
                    tabs.forEach { TabRailItem(it) }
                    Spacer(Modifier.weight(1f))
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                // On iOS the SwiftUI host already pads Compose for every safe-area edge (including the
                // side tab bar, camera and fold on iPhone Duo), so don't apply the side insets twice.
                contentWindowInsets = platformScaffoldInsets(),
                topBar = {
                    // Landscape phones are short on height; the rail already shows the current tab
                    if (isAndroid() && !windowSize.isCompactHeight) {
                        TopAppBar(title = { Text(text = tabNavigator.current.options.title) })
                    }
                },
                content = { innerPadding ->
                    // Keep tab content clear of the Android top bar and bottom navigation bar
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        CurrentTab()
                    }
                },
                bottomBar = {
                    // Show Compose bottom bar only when requested by caller (iOS host will handle native tabs)
                    if (showBottomBar && !useRail) {
                        NavigationBar {
                            tabs.forEach { TabNavigationItem(it) }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun TabRailItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    NavigationRailItem(
        selected = tabNavigator.current.key == tab.key,
        onClick = { tabNavigator.current = tab },
        icon = { Icon(painter = tab.options.icon!!, contentDescription = tab.options.title) },
        label = { Text(text = tab.options.title) },
    )
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    val isSelected = tabNavigator.current.key == tab.key

    NavigationBarItem(
        selected = isSelected,
        onClick = { tabNavigator.current = tab },
        icon = { Icon(painter = tab.options.icon!!, contentDescription = tab.options.title) },
        label = { Text(text = tab.options.title) }
    )
}