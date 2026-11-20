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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISwitch
import platform.darwin.NSObject

/**
 * Hosts a real UISwitch so iOS draws it: on iOS 26+ the thumb turns into Liquid Glass
 * while it's pressed or dragged, with the system haptics and VoiceOver behaviour.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
) {
    val tint = MaterialTheme.colorScheme.primary
    val currentOnChange by rememberUpdatedState(onCheckedChange)
    val target = remember { SwitchTarget() }
    target.onChange = { currentOnChange?.invoke(it) }
    // The system decides the switch size (it changed in iOS 26), so ask it rather than hard-coding
    val size = remember { UISwitch().intrinsicContentSize.useContents { DpSize(width.dp, height.dp) } }

    UIKitView(
        factory = {
            UISwitch().apply {
                setOn(checked, animated = false)
                addTarget(target, NSSelectorFromString("valueChanged:"), UIControlEventValueChanged)
            }
        },
        modifier = modifier.size(size),
        update = { sw ->
            if (sw.on != checked) sw.setOn(checked, animated = true)
            sw.enabled = enabled
            sw.userInteractionEnabled = onCheckedChange != null
            sw.onTintColor = tint.toUIColor()
        },
        properties = UIKitInteropProperties(
            // Touches go straight to the switch, so dragging the glass thumb feels immediate
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
            // Draw above Compose so the switch's transparent edges and glass show the card behind it
            placedAsOverlay = true,
        ),
    )
}

private class SwitchTarget : NSObject() {
    var onChange: (Boolean) -> Unit = {}

    @ObjCAction
    fun valueChanged(sender: UISwitch) {
        onChange(sender.on)
    }
}

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())
