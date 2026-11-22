package components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A simple question with one or two answers ("Delete your account?").
 * Android shows a Material 3 Expressive dialog; iOS shows the native UIAlertController
 * (Liquid Glass on iOS 26+, with system haptics and VoiceOver).
 *
 * Show it by composing it (`if (show) PlatformAlertDialog(...)`) and hide it from the callbacks.
 * [onDismiss] runs for the cancel button and, on Android, for taps outside the dialog.
 * [icon] is only drawn on Android; iOS alerts don't have icons.
 */
@Composable
expect fun PlatformAlertDialog(
    title: String,
    message: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = "Cancel",
    destructive: Boolean = false,
    icon: ImageVector? = null,
)
