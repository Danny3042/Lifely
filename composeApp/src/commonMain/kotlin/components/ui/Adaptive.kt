package components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import utils.isAndroid

/** Material window width breakpoints: phone, foldable / small tablet, tablet / desktop. */
enum class WindowWidthClass { Compact, Medium, Expanded }

@Immutable
data class WindowSize(
    val width: Dp,
    val height: Dp,
) {
    val widthClass: WindowWidthClass = when {
        width < 600.dp -> WindowWidthClass.Compact
        width < 840.dp -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }

    /** A phone held sideways: little vertical room, so prefer side-by-side layouts. */
    val isCompactHeight: Boolean get() = height < 480.dp

    val isCompactWidth: Boolean get() = widthClass == WindowWidthClass.Compact
    val isExpandedWidth: Boolean get() = widthClass == WindowWidthClass.Expanded

    /** True when there is room for (or a need for) two panes next to each other. */
    val useTwoPane: Boolean get() = isExpandedWidth || (isCompactHeight && width >= 600.dp)

    /** Side navigation rail instead of a bottom bar. */
    val useNavigationRail: Boolean get() = !isCompactWidth || isCompactHeight
}

// A dynamic CompositionLocal (not static): the window size changes at runtime (folding, resizing,
// rotating), and a dynamic local updates every reader directly, even ones inside navigation
// containers (Voyager's TabNavigator) that would otherwise skip recomposition and keep a stale size.
val LocalWindowSize = compositionLocalOf { WindowSize(width = 400.dp, height = 800.dp) }

/** Measures the space the app has and makes it available through [LocalWindowSize]. */
@Composable
fun ProvideWindowSize(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = WindowSize(width = maxWidth, height = maxHeight)
        CompositionLocalProvider(LocalWindowSize provides size) {
            content()
        }
    }
}

/** Common content widths so screens don't stretch edge to edge on tablets. */
object ContentWidth {
    val Form = 480.dp
    val Reading = 640.dp
    val Wide = 840.dp
    val Dashboard = 1200.dp
}

/** Fills the available width up to [maxWidth], centred horizontally. */
fun Modifier.centeredMaxWidth(maxWidth: Dp): Modifier =
    this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth)

/** Box that centres its content and caps it at [maxWidth]. */
@Composable
fun AdaptiveContent(
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxWidth().widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}

/**
 * Window insets for a Scaffold's content. On iOS the SwiftUI host already pads Compose for every
 * safe-area edge (top and bottom bars, plus a side tab bar, the camera and the fold on iPhone Duo),
 * so a Scaffold there must not apply any of them a second time.
 */
@Composable
fun platformScaffoldInsets(): WindowInsets =
    if (isAndroid()) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0)


/**
 * The space taken by system bars (status bar, navigation bar, tab bar, camera and fold) on iOS,
 * where the SwiftUI host reports it. Null on Android, where pages use WindowInsets instead.
 * Pages drawn edge to edge put their background under the bars and pad only their content by this.
 */
val LocalSystemBarsPadding = compositionLocalOf<PaddingValues?> { null }

/** Padding that keeps content clear of the system bars on either platform. */
@Composable
fun Modifier.systemBarsContentPadding(): Modifier {
    val padding = LocalSystemBarsPadding.current
    return if (padding != null) this.padding(padding) else this.windowInsetsPadding(WindowInsets.safeDrawing)
}


/**
 * How much of the bottom of the page the floating tab bar covers. On iOS every page with the tab bar
 * runs underneath it (the bar is translucent glass), so scrolling content adds this at its end to
 * be able to scroll clear of the bar. 0 where nothing overlaps (Android, pages without the tab bar).
 */
val LocalBottomBarOverlap = compositionLocalOf { 0.dp }

/** See [LocalBottomBarOverlap]. */
@Composable
fun bottomBarOverlap(): Dp = LocalBottomBarOverlap.current


/**
 * How much of the top of the page the navigation bar covers. On iOS, pages with the tab bar run
 * underneath the native navigation bar (like a native scroll view), so scrolling content adds this at
 * its start and then scrolls up under the bar, where iOS blurs it. It stays at the bar's expanded
 * (large title) height while the title collapses, so content moves exactly with the finger.
 * 0 on Android and on pages without the bars.
 */
val LocalTopBarOverlap = compositionLocalOf { 0.dp }

/** See [LocalTopBarOverlap]. */
@Composable
fun topBarOverlap(): Dp = LocalTopBarOverlap.current


/**
 * The app's appearance setting and how to change it, for screens that show the Appearance controls
 * outside its own route (the Profile page's detail pane on large screens). Null where not provided.
 */
data class AppearanceControls(
    val isDarkMode: Boolean,
    val onDarkModeToggle: (Boolean) -> Unit,
    val useSystemDefault: Boolean,
    val onUseSystemDefaultToggle: (Boolean) -> Unit,
)

val LocalAppearanceControls = compositionLocalOf<AppearanceControls?> { null }
