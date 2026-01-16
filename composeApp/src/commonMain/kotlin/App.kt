import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    // Detect platform in common code and decide whether Compose should render the bottom bar.
    val platform = getPlatform()
    val showBottomBar = platform.name != "iOS"

    // App-wide dark mode state (shared via expect/actual PlatformApp if needed)
    var appDarkMode by remember { mutableStateOf(false) }
    var appUseSystem by remember { mutableStateOf(true) }

    PlatformApp(
        showBottomBar = showBottomBar,
        isDarkMode = appDarkMode,
        onDarkModeToggle = { appDarkMode = it },
        useSystemDefault = appUseSystem,
        onUseSystemDefaultToggle = { appUseSystem = it }
    )

    // Debug / guidance:
    // On iOS, `showBottomBar` will be false so the SwiftUI host should render the TabView.
    // On Android, `showBottomBar` will be true so Compose can render its own bottom navigation.
}

// Platform-specific entry point. Implementations should live in androidMain and iosMain.
@Composable
expect fun PlatformApp(
    showBottomBar: Boolean,
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    useSystemDefault: Boolean,
    onUseSystemDefaultToggle: (Boolean) -> Unit
)