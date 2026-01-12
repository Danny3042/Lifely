import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    // Detect platform in common code and decide whether Compose should render the bottom bar.
    val platform = getPlatform()
    val showBottomBar = platform.name != "iOS"

    // Call the platform implementation and forward the flag so platform-specific code can use it.
    PlatformApp(showBottomBar)

    // Debug / guidance:
    // On iOS, `showBottomBar` will be false so the SwiftUI host should render the TabView.
    // On Android, `showBottomBar` will be true so Compose can render its own bottom navigation.
}

// Platform-specific entry point. Implementations should live in androidMain and iosMain.
expect @Composable fun PlatformApp(showBottomBar: Boolean)
