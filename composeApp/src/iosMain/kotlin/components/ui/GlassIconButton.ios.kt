package components.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIDevice
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightSemibold
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

/** A real UIButton, so iOS draws the Liquid Glass (and its touch response) itself. */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun GlassIconButton(
    iosSymbol: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    prominent: Boolean,
) {
    val tint = MaterialTheme.colorScheme.primary
    val currentOnClick by rememberUpdatedState(onClick)
    val target = remember { TapTarget() }
    target.onTap = { currentOnClick() }

    UIKitView(
        factory = {
            UIButton.buttonWithType(UIButtonTypeSystem).apply {
                configuration = glassConfiguration(prominent, iosSymbol)
                addTarget(target, NSSelectorFromString("tapped:"), UIControlEventTouchUpInside)
            }
        },
        modifier = modifier.size(44.dp),
        update = { button ->
            button.tintColor = tint.toUIColor()
            button.accessibilityLabel = contentDescription
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
            // Above Compose so the glass shows (and refracts) what's behind it
            placedAsOverlay = true,
        ),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun glassConfiguration(prominent: Boolean, symbol: String): UIButtonConfiguration {
    val major = UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 0
    val config = if (major >= 26) {
        if (prominent) UIButtonConfiguration.prominentGlassButtonConfiguration() else UIButtonConfiguration.glassButtonConfiguration()
    } else {
        if (prominent) UIButtonConfiguration.filledButtonConfiguration() else UIButtonConfiguration.tintedButtonConfiguration()
    }
    config.image = UIImage.systemImageNamed(symbol)
    config.preferredSymbolConfigurationForImage =
        UIImageSymbolConfiguration.configurationWithPointSize(17.0, UIImageSymbolWeightSemibold)
    config.cornerStyle = UIButtonConfigurationCornerStyleCapsule
    return config
}

private class TapTarget : NSObject() {
    var onTap: () -> Unit = {}

    @ObjCAction
    fun tapped(sender: UIButton) {
        onTap()
    }
}

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())
