package components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
actual fun PlatformAlertDialog(
    title: String,
    message: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String?,
    destructive: Boolean,
    icon: ImageVector?,
) {
    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = icon,
        supportingText = message,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
        destructive = destructive,
    )
}
