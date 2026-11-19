package tabs

import pages.HabitCoachingPage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import utils.HealthKitService
import utils.HealthKitServiceImpl
import utils.iOSHealthKitManager
import androidx.navigation.NavHostController

class HabitsTab(private val navcontroller: NavHostController) : Tab {

    private val healthKitService: HealthKitService = HealthKitServiceImpl(iOSHealthKitManager())
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.TrackChanges)

            return remember {
                TabOptions(
                    index = 2u,
                    title = "Habits",
                    icon = icon
                )
            }
        }

    @Composable
    override fun Content() {
        HabitCoachingPage(navcontroller)
    }
}
