package components.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import service.weather.WeatherKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Colours for everything drawn on top of a [SkyScene], so text and controls stay readable. */
@Immutable
data class SkyPalette(
    val skyTop: Color,
    val skyMid: Color,
    val skyBottom: Color,
    val hillFar: Color,
    val hillMid: Color,
    val hillNear: Color,
    val bodyCore: Color,
    val bodyGlow: Color,
    /** Main text and icons on the sky. */
    val onSky: Color,
    /** Secondary text on the sky. */
    val onSkyVariant: Color,
    /** Timer progress and the main button. */
    val accent: Color,
    val onAccent: Color,
    /** Progress ring track. */
    val track: Color,
    /** Translucent panels and secondary buttons. */
    val glass: Color,
)

val DaySky = SkyPalette(
    skyTop = Color(0xFF6FBFF2),
    skyMid = Color(0xFFB9E3FF),
    skyBottom = Color(0xFFFFE6C7),
    hillFar = Color(0xFFB9E5CB),
    hillMid = Color(0xFF88D0AA),
    hillNear = Color(0xFF52AE8D),
    bodyCore = Color(0xFFFFCF5C),
    bodyGlow = Color(0xFFFFE08A),
    onSky = Color(0xFF0F2E4D),
    onSkyVariant = Color(0xFF2D5574),
    accent = Color(0xFFFF9F1C),
    onAccent = Color.White,
    track = Color.White.copy(alpha = 0.5f),
    glass = Color.White.copy(alpha = 0.4f),
)

val NightSky = SkyPalette(
    skyTop = Color(0xFF060A22),
    skyMid = Color(0xFF131A46),
    skyBottom = Color(0xFF30265F),
    hillFar = Color(0xFF282D5E),
    hillMid = Color(0xFF1C2049),
    hillNear = Color(0xFF111433),
    bodyCore = Color(0xFFF6F2DC),
    bodyGlow = Color(0xFFCFCBFF),
    onSky = Color(0xFFF4F2FF),
    onSkyVariant = Color(0xFFBDB9E4),
    accent = Color(0xFFD9D3FF),
    onAccent = Color(0xFF1B1F47),
    track = Color.White.copy(alpha = 0.14f),
    glass = Color(0xFF0B0F2E).copy(alpha = 0.5f),
)

private class Speck(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

/** A falling particle: x, start height and fall speed (whole loops per 60 s clock, so the loop is seamless). */
private class Fall(val x: Float, val y: Float, val size: Float, val phase: Float, val loops: Int)

/**
 * An animated sky: a sunny day with clouds and birds, or a starry night with a moon, shooting stars
 * and fireflies. The sun or moon travels from left to right across the sky as [elapsed] goes 0 → 1.
 * [weather], when known, adds rain, snow, cloud cover, fog or a windier drift on top (kept subtle).
 */
@Composable
fun SkyScene(isNight: Boolean, elapsed: Float, modifier: Modifier = Modifier, weather: WeatherKind? = null) {
    val palette = if (isNight) NightSky else DaySky
    val time = rememberInfiniteTransition(label = "sky")
    // One slow 60 s loop drives clouds, birds, stars, fireflies and shooting stars.
    val clock by time.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "clock",
    )
    val pulse by time.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val arc by animateFloatAsState(elapsed.coerceIn(0f, 1f), tween(900), label = "arc")

    val stars = remember {
        val r = Random(42)
        List(80) { Speck(r.nextFloat(), r.nextFloat() * 0.72f, 0.6f + r.nextFloat() * 1.6f, r.nextFloat() * 6.28f, 2f + r.nextFloat() * 6f) }
    }
    val fireflies = remember {
        val r = Random(7)
        List(8) { Speck(r.nextFloat(), 0.8f + r.nextFloat() * 0.17f, 1f + r.nextFloat() * 0.8f, r.nextFloat() * 6.28f, 3f + r.nextFloat() * 4f) }
    }

    val drops = remember {
        val r = Random(11)
        List(150) { Fall(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 0.8f, r.nextFloat(), 70 + r.nextInt(50)) }
    }
    val flakes = remember {
        val r = Random(23)
        List(70) { Fall(r.nextFloat(), r.nextFloat(), 0.7f + r.nextFloat() * 1.3f, r.nextFloat() * 6.28f, 4 + r.nextInt(5)) }
    }
    val dropCount = when (weather) {
        WeatherKind.Drizzle -> 40
        WeatherKind.Rain -> 90
        WeatherKind.HeavyRain, WeatherKind.Thunder -> 150
        else -> 0
    }
    val overcastClouds = when (weather) {
        WeatherKind.PartlyCloudy -> 2
        WeatherKind.Cloudy, WeatherKind.Drizzle, WeatherKind.Rain, WeatherKind.HeavyRain, WeatherKind.Thunder -> 5
        WeatherKind.Fog, WeatherKind.Windy -> 3
        else -> 0
    }
    // How much of the sun or moon shows through the cover
    val haze = when (weather) {
        WeatherKind.Cloudy, WeatherKind.Drizzle, WeatherKind.Rain, WeatherKind.HeavyRain, WeatherKind.Thunder -> 0.55f
        WeatherKind.Fog -> 0.4f
        WeatherKind.PartlyCloudy -> 0.15f
        else -> 0f
    }
    val cloudSpeed = if (weather == WeatherKind.Windy) 3 else 1

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.8f
        val unit = min(w, h)

        drawRect(Brush.verticalGradient(listOf(palette.skyTop, palette.skyMid, palette.skyBottom)))

        if (isNight) {
            stars.forEach { s ->
                val twinkle = 0.35f + 0.65f * abs(sin(clock * 2f * PI.toFloat() * s.speed + s.phase))
                drawCircle(Color.White.copy(alpha = twinkle), radius = s.size * density, center = Offset(s.x * w, s.y * horizon))
            }
            drawShootingStar(clock, w, h)
        }

        // Sun or moon: rises just above the hills on the left, peaks mid-session, sets on the right.
        val radius = unit * 0.075f
        val center = Offset(
            x = w * (0.14f + 0.72f * arc),
            y = horizon * 0.9f - sin(PI.toFloat() * arc) * horizon * 0.62f,
        )
        val glowRadius = radius * (3.2f + 0.35f * pulse)
        drawCircle(
            Brush.radialGradient(listOf(palette.bodyGlow.copy(alpha = 0.6f), Color.Transparent), center, glowRadius),
            radius = glowRadius,
            center = center,
        )
        if (isNight) {
            drawMoon(center, radius, palette)
        } else {
            drawSun(center, radius, clock, palette)
        }
        if (haze > 0f) {
            drawCircle(
                Brush.radialGradient(listOf(palette.skyMid.copy(alpha = haze), Color.Transparent), center, glowRadius),
                radius = glowRadius,
                center = center,
            )
        }
        if (!isNight) {
            drawClouds(clock, w, h, cloudSpeed)
            if (weather == null || weather == WeatherKind.Clear || weather == WeatherKind.PartlyCloudy) drawBirds(clock, w, h)
        }
        if (overcastClouds > 0) {
            val cover = if (isNight) Color(0xFF3B4075).copy(alpha = 0.75f) else Color(0xFFD5DCE6).copy(alpha = 0.85f)
            drawOvercast(clock, w, h, cover, overcastClouds, cloudSpeed)
        }

        drawHill(palette.hillFar, horizon - h * 0.02f, h * 0.035f, 1.3f, 0.6f)
        drawHill(palette.hillMid, horizon + h * 0.045f, h * 0.03f, 1.8f, 2.1f)
        drawHill(palette.hillNear, horizon + h * 0.11f, h * 0.025f, 1.1f, 4.0f)

        if (weather == WeatherKind.Fog) {
            val fog = if (isNight) Color(0xFF8E93C8) else Color(0xFFEFF3F7)
            val top = horizon - h * 0.14f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, fog.copy(alpha = 0.5f + 0.08f * pulse), fog.copy(alpha = 0.35f), Color.Transparent),
                    startY = top,
                    endY = horizon + h * 0.2f,
                ),
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(w, horizon + h * 0.2f - top),
            )
        }

        if (dropCount > 0) {
            val streak = if (isNight) Color(0xFFCFCBFF) else Color(0xFF2D5574)
            val alpha = if (weather == WeatherKind.Drizzle) 0.22f else 0.32f
            val len = unit * (if (weather == WeatherKind.Drizzle) 0.025f else 0.045f)
            val width = (if (weather == WeatherKind.HeavyRain || weather == WeatherKind.Thunder) 1.6f else 1.2f) * density
            for (i in 0 until dropCount) {
                val d = drops[i]
                val y = ((d.y + clock * d.loops) % 1f) * (h + len) - len
                val x = d.x * w + (y / h) * len * 0.5f
                drawLine(
                    streak.copy(alpha = alpha * d.size.coerceAtMost(1f)),
                    start = Offset(x, y),
                    end = Offset(x - len * 0.18f, y + len * d.size),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
        }
        if (weather == WeatherKind.Thunder) {
            // A soft flash every ~10 s, on every other beat
            val beat = clock * 6f
            val phase = beat % 1f
            if (beat.toInt() % 2 == 0 && phase < 0.05f) {
                drawRect(Color.White.copy(alpha = 0.16f * (1f - phase / 0.05f)))
            }
        }
        if (weather == WeatherKind.Snow) {
            val flake = if (isNight) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.95f)
            flakes.forEach { f ->
                val y = ((f.y + clock * f.loops) % 1f) * (h + 20f) - 10f
                val x = f.x * w + sin(clock * 2f * PI.toFloat() * f.loops + f.phase) * 12f * density
                drawCircle(flake, radius = f.size * 1.6f * density, center = Offset(x, y))
            }
        }

        if (isNight) {
            fireflies.forEach { f ->
                val t = clock * 2f * PI.toFloat() * f.speed + f.phase
                val glow = 0.15f + 0.85f * abs(sin(t * 0.7f))
                val c = Offset(f.x * w + sin(t) * 14f * density, f.y * h + sin(t * 1.3f) * 8f * density)
                drawCircle(Color(0xFFFFF3A6).copy(alpha = glow * 0.25f), radius = f.size * 3.5f * density, center = c)
                drawCircle(Color(0xFFFFF7C2).copy(alpha = glow), radius = f.size * density, center = c)
            }
        }
    }
}

private fun DrawScope.drawSun(center: Offset, radius: Float, clock: Float, palette: SkyPalette) {
    rotate(degrees = clock * 360f, pivot = center) {
        repeat(12) { i ->
            rotate(degrees = i * 30f, pivot = center) {
                drawLine(
                    palette.bodyCore.copy(alpha = 0.7f),
                    start = Offset(center.x, center.y - radius * 1.35f),
                    end = Offset(center.x, center.y - radius * 1.75f),
                    strokeWidth = radius * 0.14f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
    drawCircle(palette.bodyCore, radius, center)
    drawCircle(Color.White.copy(alpha = 0.35f), radius * 0.55f, Offset(center.x - radius * 0.25f, center.y - radius * 0.25f))
}

private fun DrawScope.drawMoon(center: Offset, radius: Float, palette: SkyPalette) {
    drawCircle(palette.bodyCore, radius, center)
    val crater = Color(0xFFD9D3B8)
    drawCircle(crater, radius * 0.22f, Offset(center.x - radius * 0.3f, center.y - radius * 0.2f))
    drawCircle(crater, radius * 0.14f, Offset(center.x + radius * 0.35f, center.y + radius * 0.25f))
    drawCircle(crater, radius * 0.1f, Offset(center.x + radius * 0.05f, center.y + radius * 0.45f))
}

private fun DrawScope.drawCloudBlob(c: Offset, s: Float, color: Color) {
    drawCircle(color, s, c)
    drawCircle(color, s * 0.8f, Offset(c.x - s * 1.0f, c.y + s * 0.25f))
    drawCircle(color, s * 0.85f, Offset(c.x + s * 1.05f, c.y + s * 0.2f))
    drawCircle(color, s * 0.65f, Offset(c.x + s * 0.4f, c.y - s * 0.55f))
}

/** A bigger, greyer layer of cloud for overcast skies; [speedMul] must be a whole number to keep the loop seamless. */
private fun DrawScope.drawOvercast(clock: Float, w: Float, h: Float, color: Color, count: Int, speedMul: Int) {
    val clouds = listOf(
        floatArrayOf(0.10f, 0.10f, 1.5f, 1f),
        floatArrayOf(0.40f, 0.20f, 1.8f, 2f),
        floatArrayOf(0.70f, 0.07f, 1.4f, 1f),
        floatArrayOf(0.90f, 0.28f, 1.6f, 2f),
        floatArrayOf(0.25f, 0.34f, 1.3f, 1f),
    )
    val span = w * 1.6f
    for (i in 0 until count.coerceAtMost(clouds.size)) {
        val (start, y, scale, speed) = clouds[i]
        val x = ((start + clock * speed * speedMul) % 1f) * span - w * 0.3f
        drawCloudBlob(Offset(x, h * y), min(w, h) * 0.09f * scale, color)
    }
}

private fun DrawScope.drawClouds(clock: Float, w: Float, h: Float, speedMul: Int = 1) {
    // (start position, height, scale, speed): each cloud drifts right and wraps round.
    val clouds = listOf(
        floatArrayOf(0.0f, 0.12f, 1.0f, 2f),
        floatArrayOf(0.45f, 0.24f, 0.7f, 3f),
        floatArrayOf(0.75f, 0.06f, 0.55f, 4f),
    )
    val span = w * 1.4f
    clouds.forEach { (start, y, scale, speed) ->
        val x = ((start + clock * speed * speedMul) % 1f) * span - w * 0.2f
        val s = min(w, h) * 0.09f * scale
        val white = Color.White.copy(alpha = 0.85f)
        val c = Offset(x, h * y)
        drawCircle(white, s, c)
        drawCircle(white, s * 0.8f, Offset(c.x - s * 1.0f, c.y + s * 0.25f))
        drawCircle(white, s * 0.85f, Offset(c.x + s * 1.05f, c.y + s * 0.2f))
        drawCircle(white, s * 0.65f, Offset(c.x + s * 0.4f, c.y - s * 0.55f))
    }
}

private fun DrawScope.drawBirds(clock: Float, w: Float, h: Float) {
    val stroke = Stroke(width = 2f * density, cap = StrokeCap.Round)
    listOf(0f to 0.18f, 0.06f to 0.2f, 0.03f to 0.155f).forEach { (offset, y) ->
        val x = ((clock * 3f + offset) % 1f) * w * 1.3f - w * 0.15f
        val flap = sin(clock * 2f * PI.toFloat() * 90f + offset * 40f) * 4f * density
        val span = 9f * density
        val cy = h * y
        val path = Path().apply {
            moveTo(x - span, cy - flap)
            quadraticTo(x - span / 2, cy - span / 2, x, cy)
            quadraticTo(x + span / 2, cy - span / 2, x + span, cy - flap)
        }
        drawPath(path, Color(0xFF2D5574).copy(alpha = 0.7f), style = stroke)
    }
}

private fun DrawScope.drawShootingStar(clock: Float, w: Float, h: Float) {
    // One every ~7.5 s, visible for a short moment.
    val phase = (clock * 8f) % 1f
    if (phase > 0.12f) return
    val t = phase / 0.12f
    val seed = (clock * 8f).toInt()
    val startX = w * (0.1f + (seed * 0.37f) % 0.5f)
    val startY = h * (0.05f + (seed * 0.23f) % 0.2f)
    val head = Offset(startX + w * 0.45f * t, startY + h * 0.18f * t)
    val tail = Offset(head.x - w * 0.12f, head.y - h * 0.048f)
    val alpha = 1f - t
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = alpha)), tail, head),
        start = tail,
        end = head,
        strokeWidth = 2.5f * density,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawHill(color: Color, baseline: Float, amplitude: Float, waves: Float, phase: Float) {
    val w = size.width
    val path = Path().apply {
        moveTo(0f, size.height)
        var x = 0f
        while (x <= w) {
            lineTo(x, baseline - amplitude * sin(x / w * 2f * PI.toFloat() * waves + phase))
            x += 6f
        }
        lineTo(w, baseline - amplitude * sin(2f * PI.toFloat() * waves + phase))
        lineTo(w, size.height)
        close()
    }
    drawPath(path, color)
}
