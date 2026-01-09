package utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun HealthConnectPermissionButton(
    healthKitService: HealthKitService,
    modifier: Modifier,
    onGranted: () -> Unit
) {
    // No-op on iOS — HealthKit handled separately on iOS.
}
