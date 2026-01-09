package utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Show a Health Connect permission request CTA when invoked. Platform-specific.
 * On Android this will launch the permission flow; on iOS it's a no-op.
 */
@Composable
expect fun HealthConnectPermissionButton(
    healthKitService: HealthKitService,
    modifier: Modifier = Modifier,
    onGranted: () -> Unit = {}
)
