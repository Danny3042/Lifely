package components.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/** Presents a native UIAlertController while this is in the composition. */
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
    val currentConfirm by rememberUpdatedState(onConfirm)
    val currentDismiss by rememberUpdatedState(onDismiss)

    DisposableEffect(title, message, confirmText, dismissText, destructive) {
        var answered = false
        val alert = UIAlertController.alertControllerWithTitle(title, message, UIAlertControllerStyleAlert)
        if (dismissText != null) {
            alert.addAction(UIAlertAction.actionWithTitle(dismissText, UIAlertActionStyleCancel) { _ ->
                answered = true
                currentDismiss()
            })
        }
        val confirm = UIAlertAction.actionWithTitle(
            confirmText,
            if (destructive) UIAlertActionStyleDestructive else UIAlertActionStyleDefault,
        ) { _ ->
            answered = true
            currentConfirm()
        }
        alert.addAction(confirm)
        // Bold the safe choice, as iOS does: confirm for normal alerts, Cancel for destructive ones
        if (!destructive) alert.preferredAction = confirm

        topViewController()?.presentViewController(alert, animated = true, completion = null)

        onDispose {
            // Removed from the composition without an answer (e.g. navigated away): close it
            if (!answered) alert.dismissViewControllerAnimated(true, completion = null)
        }
    }
}

private fun topViewController(): UIViewController? {
    val windows = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
    val window = windows.firstOrNull { it.keyWindow } ?: windows.firstOrNull()
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
