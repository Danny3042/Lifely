package components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A round icon button in each platform's own style: a native Liquid Glass button on iOS 26+
 * (UIKit's glass button configurations; a filled/tinted button on older iOS), and a Material 3
 * small FAB on Android.
 *
 * @param iosSymbol SF Symbol name used on iOS, e.g. "plus".
 * @param icon Material icon used on Android.
 * @param prominent Tinted with the app's primary colour (for the main action) rather than clear glass.
 */
@Composable
expect fun GlassIconButton(
    iosSymbol: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = true,
)
