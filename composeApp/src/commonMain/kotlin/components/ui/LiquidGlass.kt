package components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * Native Liquid Glass controls for iOS 26+ (UIKit glass button configurations, which iOS draws and
 * animates itself; filled/gray buttons on older iOS). Kept to controls, per Apple's guidance:
 * Liquid Glass is for the layer of buttons and navigation above content, not the content itself.
 * Used by the iOS onboarding; the Android versions are plain Material equivalents (Android's
 * onboarding keeps its own Material layout).
 * Give each one a size through [modifier] (they're native views).
 */

/** A capsule glass button with a text label. [prominent] tints it with the app's primary colour. */
@Composable
expect fun GlassTextButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
)

/** Page dots on a glass platter; tapping or scrubbing them changes the page. */
@Composable
expect fun GlassPageIndicator(
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
