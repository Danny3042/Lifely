package components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A simplified MyHealthBuddy app icon, drawn in code: the sky, the sun and two rolling hills.
 * Like the real icon it switches to night in dark mode (darker sky and hills, the moon instead of
 * the sun). Shapes come from the app icon's own artwork (iosApp/AppIcon.icon), on a 1024 grid.
 */
@Composable
fun AppLogo(
    size: Dp = 88.dp,
    modifier: Modifier = Modifier,
    dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
) {
    val shapes = remember { LogoShapes() }
    val palette = if (dark) NightPalette else DayPalette
    Canvas(
        modifier = modifier
            .size(size)
            // The rounding of an app icon
            .clip(RoundedCornerShape(percent = 23))
            .semantics { contentDescription = "MyHealthBuddy" },
    ) {
        val s = this.size.minDimension / 1024f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            // Sky
            drawRect(Brush.verticalGradient(palette.sky, startY = 0f, endY = 1024f), size = androidx.compose.ui.geometry.Size(1024f, 1024f))
            // Sun or moon
            if (dark) {
                drawPath(shapes.moon, Brush.verticalGradient(palette.light, startY = 305f, endY = 635f))
            } else {
                drawCircle(Brush.verticalGradient(palette.light, startY = 280f, endY = 660f), radius = 190f, center = androidx.compose.ui.geometry.Offset(512f, 470f))
            }
            // Hills, back then front
            drawPath(shapes.backHill, Brush.verticalGradient(palette.backHill, startY = 540f, endY = 1024f))
            drawPath(shapes.frontHill, Brush.verticalGradient(palette.frontHill, startY = 660f, endY = 1024f))
        }
    }
}

private class LogoShapes {
    private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

    val backHill = path("M-320 650 L0 620 C180 520 360 540 512 600 C680 664 860 560 1024 600 L1344 640 L1344 1344 L-320 1344 Z")
    val frontHill = path("M-320 740 L0 720 C200 640 380 660 560 720 C740 780 900 700 1024 720 L1344 735 L1344 1344 L-320 1344 Z")

    /** A crescent: a full moon with an offset circle cut away. */
    val moon: Path = Path().apply {
        val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center = androidx.compose.ui.geometry.Offset(500f, 470f), radius = 165f)) }
        val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center = androidx.compose.ui.geometry.Offset(590f, 410f), radius = 140f)) }
        op(full, bite, PathOperation.Difference)
    }
}

private class LogoPalette(
    val sky: List<Color>,
    val light: List<Color>,
    val backHill: List<Color>,
    val frontHill: List<Color>,
)

// Colours from the app icon's artwork
private val DayPalette = LogoPalette(
    sky = listOf(Color(0xFF5B4FCF), Color(0xFFF2A08F)),
    light = listOf(Color(0xFFFFE9A3), Color(0xFFFF9F5A)),
    backHill = listOf(Color(0xFF7FD8C9), Color(0xFF2E9C8E)),
    frontHill = listOf(Color(0xFF1E7E73), Color(0xFF0B3D3E)),
)

private val NightPalette = LogoPalette(
    sky = listOf(Color(0xFF1C1846), Color(0xFF5A3550)),
    light = listOf(Color(0xFFFFFDF4), Color(0xFFC9CEE8)),
    backHill = listOf(Color(0xFF4A9C91), Color(0xFF1D5B54)),
    frontHill = listOf(Color(0xFF135249), Color(0xFF061F1E)),
)
