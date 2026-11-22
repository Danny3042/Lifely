package components.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.AuthenticationServices.ASAuthorizationAppleIDButton
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyle
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeContinue
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventTouchUpInside
import platform.darwin.NSObject

/** Apple's own Sign in with Apple button, so it always matches Apple's current design and wording. */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun AppleSignInButton(onClick: () -> Unit, modifier: Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val currentOnClick by rememberUpdatedState(onClick)
    val target = remember { AppleButtonTarget() }
    target.onTap = { currentOnClick() }

    // The button's style is fixed when it's created, so make a new one when light/dark changes
    key(dark) {
        UIKitView(
            factory = {
                ASAuthorizationAppleIDButton(
                    authorizationButtonType = ASAuthorizationAppleIDButtonTypeContinue,
                    authorizationButtonStyle = if (dark) ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleWhite else ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleBlack,
                ).apply {
                    cornerRadius = 26.0
                    addTarget(target, NSSelectorFromString("tapped"), UIControlEventTouchUpInside)
                }
            },
            modifier = modifier.height(52.dp),
            properties = UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = true,
                placedAsOverlay = true,
            ),
        )
    }
}

private class AppleButtonTarget : NSObject() {
    var onTap: () -> Unit = {}

    @ObjCAction
    fun tapped() {
        onTap()
    }
}
