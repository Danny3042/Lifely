package Colors

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** iOS has no wallpaper-based colour; the brand scheme is used. */
@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null
