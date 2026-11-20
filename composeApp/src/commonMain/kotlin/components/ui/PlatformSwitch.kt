package components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A switch that uses each platform's own control:
 * Material 3 on Android, and the native UISwitch on iOS (Liquid Glass thumb on iOS 26+).
 *
 * Pass `onCheckedChange = null` when a parent (for example a toggleable row) handles taps.
 */
@Composable
expect fun PlatformSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)
