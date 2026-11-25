package components.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIDevice
import platform.UIKit.UIPageControl
import platform.UIKit.UIPageControlBackgroundStyleProminent
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

private val iosMajor: Int
    get() = UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 0

/** Liquid Glass on iOS 26+, the closest classic style before that. */
@OptIn(ExperimentalForeignApi::class)
private fun glassConfig(prominent: Boolean): UIButtonConfiguration =
    if (iosMajor >= 26) {
        if (prominent) UIButtonConfiguration.prominentGlassButtonConfiguration()
        else UIButtonConfiguration.glassButtonConfiguration()
    } else {
        if (prominent) UIButtonConfiguration.filledButtonConfiguration()
        else UIButtonConfiguration.grayButtonConfiguration()
    }

private fun Color.toGlassUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())

private class GlassTapTarget : NSObject() {
    var onTap: () -> Unit = {}

    @ObjCAction
    fun tapped(sender: UIButton) {
        onTap()
    }
}

/**
 * A real UIButton above the Compose layer, so iOS draws the glass (refracting what's behind it) and
 * its touch response.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun NativeGlassButton(
    configure: () -> UIButtonConfiguration,
    accessibility: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val tint = MaterialTheme.colorScheme.primary
    val currentOnClick by rememberUpdatedState(onClick)
    val target = remember { GlassTapTarget() }
    target.onTap = { currentOnClick() }

    UIKitView(
        factory = {
            UIButton.buttonWithType(UIButtonTypeSystem).apply {
                addTarget(target, NSSelectorFromString("tapped:"), UIControlEventTouchUpInside)
            }
        },
        modifier = modifier,
        update = { button ->
            button.configuration = configure()
            button.tintColor = tint.toGlassUIColor()
            button.accessibilityLabel = accessibility
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
            placedAsOverlay = true,
        ),
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GlassTextButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier,
    prominent: Boolean,
) {
    NativeGlassButton(
        configure = {
            glassConfig(prominent).apply {
                this.title = title
                cornerStyle = UIButtonConfigurationCornerStyleCapsule
                baseForegroundColor = UIColor.whiteColor
            }
        },
        accessibility = title,
        onClick = onClick,
        modifier = modifier,
    )
}

private class PageChangeTarget : NSObject() {
    var onChange: (Int) -> Unit = {}

    @ObjCAction
    fun changed(sender: UIPageControl) {
        onChange(sender.currentPage.toInt())
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun GlassPageIndicator(
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val target = remember { PageChangeTarget() }
    target.onChange = { currentOnSelect(it) }

    UIKitView(
        factory = {
            UIPageControl().apply {
                // The platter behind the dots (Liquid Glass on iOS 26)
                backgroundStyle = UIPageControlBackgroundStyleProminent
                addTarget(target, NSSelectorFromString("changed:"), UIControlEventValueChanged)
            }
        },
        modifier = modifier.height(28.dp).width((count * 18 + 44).dp),
        update = { control ->
            control.numberOfPages = count.toLong()
            control.currentPage = current.toLong()
            control.pageIndicatorTintColor = UIColor.whiteColor.colorWithAlphaComponent(0.45)
            control.currentPageIndicatorTintColor = UIColor.whiteColor
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
            placedAsOverlay = true,
        ),
    )
}
