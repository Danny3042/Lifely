package pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import screens.HealthConnectScreen
import utils.HealthConnectChecker
import utils.HealthKitService
import utils.RealTimeGreeting
import utils.isAndroid

const val HomePageScreen = "HomePage"

@Composable
fun HomePage(healthKitService: HealthKitService) {
    var isAvailableResult by remember { mutableStateOf(Result.success(false)) }
    var showSleepScreen by remember { mutableStateOf(false) }
    var isAuthorizedResult by remember { mutableStateOf<Result<Boolean>?>(null) }
    var isRevokeSupported by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isAndroid()) {
            hasPermissions = healthKitService.checkPermissions()
            if (!hasPermissions) {
                hasPermissions = healthKitService.requestAuthorization()
            }
        }
    }

    val healthConnectAvailability = HealthConnectChecker.checkHealthConnectAvailability()

    Column {
        // Health connect content (includes greeting inside the content area on each platform)
        if (isAndroid() && isAuthorizedResult?.getOrNull() != true) {
            HealthConnectScreen(healthKitService)
        } else {
            HealthConnectScreen(healthKitService)
        }
    }
}