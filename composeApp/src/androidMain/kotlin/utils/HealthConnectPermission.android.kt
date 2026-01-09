package utils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
actual fun HealthConnectPermissionButton(healthKitService: HealthKitService, modifier: Modifier, onGranted: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        // If granted contains all required permissions, notify the caller
        if (granted.containsAll(Health.HealthConnectUtils.PERMISSIONS)) {
            onGranted()
        }
    }

    Button(onClick = {
        // First check if the SDK is available and permissions needed; then launch the permission flow.
        CoroutineScope(Dispatchers.Main).launch {
            val available = Health.HealthConnectUtils.checkForHealthConnectInstalled(context)
            if (available == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {
                // launch the permission request
                launcher.launch(Health.HealthConnectUtils.PERMISSIONS)
            } else {
                // open Play Store link
                // fallback logic could be placed here
            }
        }
    }, modifier = modifier) {
        Text("Enable Health Connect")
    }
}
