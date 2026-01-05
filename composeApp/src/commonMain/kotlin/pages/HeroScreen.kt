
import androidx.compose.foundation.layout.RowScope
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

        Scaffold(
            topBar = {
                if (isAndroid()) {
                    TopAppBar(title = { Text(text = tabNavigator.current.options.title) })
                }
            },
            content = {
                CurrentTab()
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        TabNavigationItem(homeTab)
                        TabNavigationItem(habitsTab)
                        TabNavigationItem(chatTab)
                        TabNavigationItem(meditateTab)
                        TabNavigationItem(profileTab)
                    }
                }
            }
        )
    }
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