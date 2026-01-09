package tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import pages.ChartsPageScreen
import pages.HomePage
import utils.HealthKitService
import utils.HealthKitServiceImpl
import utils.iOSHealthKitManager
import platform.PlatformBridge

object HomeTab : Tab {

    // initialize directly
    private val healthKitService: HealthKitService = HealthKitServiceImpl(iOSHealthKitManager())

    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Home)

            return remember {
                TabOptions(
                    index = 1u,
                    title = "Home",
                    icon = icon
                )
            }
        }

    @Composable
    override fun Content() {
        HomePage(
            healthKitService,
            onNavigateMeditate = {
                // request the native tab/compose to switch to meditate
                PlatformBridge.requestedTabName = "meditation"
                PlatformBridge.requestedTabSignal = PlatformBridge.requestedTabSignal + 1
            },
            onNavigateHabits = {
                PlatformBridge.requestedTabName = "HabitCoachingPage"
                PlatformBridge.requestedTabSignal = PlatformBridge.requestedTabSignal + 1
            },
            onChartClick = {
                PlatformBridge.requestedRoute = ChartsPageScreen
                PlatformBridge.requestedRouteSignal = PlatformBridge.requestedRouteSignal + 1
            }
        )
    }
}