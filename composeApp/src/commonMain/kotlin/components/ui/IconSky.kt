package components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The app icon's scene as a full-screen backdrop, in both of the icon's versions:
 * - light: a purple-to-peach sky, the sun and three layers of teal hills
 * - dark: a deep purple night sky with stars, a crescent moon and darker teal hills
 * Colourful enough for Liquid Glass to refract.
 *
 * [reveal] (0 → 1) plays the opening: the sky brightens, the hills rise one after another and then
 * the sun (or moon) comes up behind them, while the stars fade in. [phase] (radians, looping) slowly
 * drifts the hills and makes the stars twinkle.
 */
@Composable
fun IconSky(isNight: Boolean, modifier: Modifier = Modifier, reveal: Float = 1f, phase: Float = 0f) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val drift = sin(phase) * 0.35f

        // Sky (colours sampled from AppIcon-Default / AppIcon-Dark)
        val sky = if (isNight) listOf(Color(0xFF281E48), Color(0xFF33234A), Color(0xFF3D274B))
        else listOf(Color(0xFF6C56C4), Color(0xFF9A6FBA), Color(0xFFE9A08F))
        drawRect(Brush.verticalGradient(sky, startY = 0f, endY = h * 0.85f))
        // The opening starts from a dusky sky that lightens as the sun or moon comes up
        val dusk = 1f - stage(reveal, 0f, 0.7f)
        if (dusk > 0f) drawRect(Color(0xFF120E33).copy(alpha = 0.55f * dusk))

        if (isNight) stars(alpha = stage(reveal, 0.2f, 0.9f), phase = phase)

        // Sun or moon: low in the sky, rising behind the hills, so it never sits behind text
        // (the moon sits a little higher, so its whole crescent clears the hills as in the dark icon)
        val restY = if (isNight) h * 0.75f else h * 0.85f
        val c = Offset(w * 0.5f, restY + (1f - stage(reveal, 0.35f, 1f)) * h * 0.3f)
        val r = min(w, h) * (if (isNight) 0.11f else 0.14f)
        if (isNight) moon(c, r) else sun(c, r)

        // Hills rise from below the screen, back to front, then drift very slowly
        val hills = if (isNight) listOf(Color(0xFF45958A), Color(0xFF217368), Color(0xFF124E45))
        else listOf(Color(0xFF7AD5C6), Color(0xFF38AD9A), Color(0xFF1D7A6F))
        hill(hills[0], h * 0.81f + (1f - stage(reveal, 0f, 0.55f)) * h * 0.3f, h * 0.022f, 1.6f, 0.4f + drift)
        hill(hills[1], h * 0.87f + (1f - stage(reveal, 0.1f, 0.65f)) * h * 0.3f, h * 0.02f, 1.3f, 2.1f - drift * 0.7f)
        hill(hills[2], h * 0.93f + (1f - stage(reveal, 0.2f, 0.75f)) * h * 0.3f, h * 0.016f, 1.1f, 4.0f + drift * 0.5f)
    }
}

private fun DrawScope.sun(c: Offset, r: Float) {
    val glow = Color(0xFFFFD9A0)
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
    drawCircle(
        Brush.verticalGradient(listOf(Color(0xFFFFE59F), Color(0xFFFFB26D)), startY = c.y - r, endY = c.y + r),
        r,
        c,
    )
}

/** A crescent like the dark app icon's: a disc with an offset disc taken out of its upper right */
private fun DrawScope.moon(c: Offset, r: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFCFCBFF).copy(alpha = 0.28f), Color.Transparent), c, r * 2.3f),
        r * 2.3f,
        c,
    )
    val disc = Path().apply { addOval(Rect(c, r)) }
    val bite = Path().apply { addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.35f), r * 0.92f)) }
    val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
    drawPath(
        crescent,
        Brush.verticalGradient(listOf(Color(0xFFFAF8FF), Color(0xFFC9C2EE)), startY = c.y - r, endY = c.y + r),
    )
}

/** Fixed pseudo-random stars in the upper sky, each twinkling at its own pace */
private fun DrawScope.stars(alpha: Float, phase: Float) {
    if (alpha <= 0f) return
    val w = size.width
    val h = size.height
    for (i in 0 until 46) {
        // Deterministic positions (a simple hash), spread over the top two thirds
        val x = fract(sin(i * 12.9898f) * 43758.547f) * w
        val y = fract(sin(i * 78.233f) * 12345.679f) * h * 0.66f
        val starSize = 1.2f + fract(sin(i * 3.17f) * 9876.54f) * 2.2f
        val twinkle = 0.55f + 0.45f * sin(phase * (8f + (i % 5) * 3f) + i)
        drawCircle(Color.White.copy(alpha = (alpha * twinkle).coerceIn(0f, 1f)), starSize * density / 2f, Offset(x, y))
    }
}

private fun fract(v: Float): Float = v - kotlin.math.floor(v)

/** Eased progress of one part of the opening, which runs while [t] goes from [from] to [to]. */
private fun stage(t: Float, from: Float, to: Float): Float {
    val x = ((t - from) / (to - from)).coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x) * (1f - x) // ease out
}

private fun DrawScope.hill(color: Color, baseline: Float, amplitude: Float, waves: Float, phase: Float) {
    val w = size.width
    val path = Path().apply {
        moveTo(0f, size.height)
        var x = 0f
        while (x <= w) {
            val t = x / w * 2f * PI.toFloat() * waves + phase
            lineTo(x, baseline + amplitude * sin(t) + amplitude * 0.35f * sin(t * 2.3f + phase * 1.7f))
            x += 6f
        }
        lineTo(w, size.height)
        close()
    }
    drawPath(path, color)
}
