package components.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A little meditating companion that breathes with you: its body swells and its arms lift as
 * [breath] goes to 1 (breathing in) and settle as it returns to 0 (breathing out). When the timer
 * isn't running it breathes softly on its own so it still looks alive.
 *
 * Wears a sprout leaf by day and a nightcap by night.
 */
@Composable
fun BreathingBuddy(breath: Float, isRunning: Boolean, isNight: Boolean, modifier: Modifier = Modifier) {
    val idle by rememberInfiniteTransition(label = "buddyIdle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idle",
    )
    val b = if (isRunning) breath else idle * 0.25f

    val body = if (isNight) Color(0xFFB8B1F4) else Color(0xFF8FE0C8)
    val bodyShade = if (isNight) Color(0xFF9A92E6) else Color(0xFF6FCDB1)
    val ink = Color(0xFF1E2A44)
    val cheek = Color(0xFFFF8FA3).copy(alpha = 0.55f)

    Canvas(modifier.semantics { contentDescription = if (isRunning) "Breathing buddy breathing with you" else "Breathing buddy" }) {
        val w = size.width
        val h = size.height
        val groundY = h * 0.95f

        // Shadow grows a touch as the body fills
        drawOval(
            Color.Black.copy(alpha = 0.12f),
            topLeft = Offset(w * (0.24f - 0.03f * b), groundY - h * 0.04f),
            size = Size(w * (0.52f + 0.06f * b), h * 0.08f),
        )

        // Body: a soft blob that gets wider and taller on the in-breath, anchored to the ground
        val bodyW = w * (0.6f + 0.1f * b)
        val bodyH = h * (0.54f + 0.12f * b)
        val bodyTop = groundY - bodyH
        val bodyLeft = (w - bodyW) / 2f
        val cx = w / 2f
        val cy = bodyTop + bodyH / 2f

        // Arms: raised up and out on the in-breath, resting by the sides on the out-breath.
        // Each arm is drawn pointing down and rotated outwards (clockwise = towards the left).
        val armAngle = 40f + 95f * b
        val armLength = w * 0.22f
        drawArm(Offset(bodyLeft + bodyW * 0.06f, cy), armAngle, armLength, bodyShade)
        drawArm(Offset(bodyLeft + bodyW * 0.94f, cy), -armAngle, armLength, bodyShade)

        drawOval(body, topLeft = Offset(bodyLeft, bodyTop), size = Size(bodyW, bodyH))
        // Belly highlight
        drawOval(
            Color.White.copy(alpha = 0.3f),
            topLeft = Offset(cx - bodyW * 0.22f, cy + bodyH * 0.02f),
            size = Size(bodyW * 0.44f, bodyH * 0.36f),
        )

        // Face: closed, content eyes, rosy cheeks, small smile
        val eyeY = cy - bodyH * 0.1f
        val eyeDx = bodyW * 0.17f
        val eyeR = bodyW * 0.07f
        val faceStroke = Stroke(width = w * 0.025f, cap = StrokeCap.Round)
        listOf(-1f, 1f).forEach { side ->
            drawArc(ink, 0f, 180f, false, Offset(cx + side * eyeDx - eyeR, eyeY - eyeR), Size(eyeR * 2, eyeR * 2), style = faceStroke)
            drawCircle(cheek, bodyW * 0.07f, Offset(cx + side * eyeDx * 1.55f, eyeY + bodyH * 0.13f))
        }
        val mouthR = bodyW * (0.06f + 0.015f * b)
        drawArc(ink, 20f, 140f, false, Offset(cx - mouthR, eyeY + bodyH * 0.02f), Size(mouthR * 2, mouthR * 2), style = faceStroke)

        if (isNight) drawNightcap(cx, bodyTop, bodyW) else drawSprout(cx, bodyTop, w, b)
    }
}

private fun DrawScope.drawArm(shoulder: Offset, angle: Float, length: Float, color: Color) {
    rotate(degrees = angle, pivot = shoulder) {
        drawLine(color, shoulder, Offset(shoulder.x, shoulder.y + length), strokeWidth = length * 0.42f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSprout(cx: Float, top: Float, w: Float, b: Float) {
    val stem = Color(0xFF3F9E7D)
    val leaf = Color(0xFF5CC48F)
    val stemTop = Offset(cx, top - w * (0.1f + 0.02f * b))
    drawLine(stem, Offset(cx, top + w * 0.02f), stemTop, strokeWidth = w * 0.03f, cap = StrokeCap.Round)
    listOf(-1f, 1f).forEach { side ->
        val path = Path().apply {
            moveTo(stemTop.x, stemTop.y)
            quadraticTo(stemTop.x + side * w * 0.14f, stemTop.y - w * 0.1f, stemTop.x + side * w * 0.2f, stemTop.y - w * 0.02f)
            quadraticTo(stemTop.x + side * w * 0.1f, stemTop.y + w * 0.03f, stemTop.x, stemTop.y)
            close()
        }
        drawPath(path, leaf)
    }
}

private fun DrawScope.drawNightcap(cx: Float, top: Float, bodyW: Float) {
    val cap = Color(0xFF5B4FCF)
    val brim = Color(0xFFF4F1FF)
    val baseY = top + bodyW * 0.12f
    val path = Path().apply {
        moveTo(cx - bodyW * 0.36f, baseY)
        quadraticTo(cx - bodyW * 0.05f, top - bodyW * 0.45f, cx + bodyW * 0.42f, top - bodyW * 0.18f)
        quadraticTo(cx + bodyW * 0.12f, top - bodyW * 0.05f, cx + bodyW * 0.36f, baseY)
        close()
    }
    drawPath(path, cap)
    drawLine(brim, Offset(cx - bodyW * 0.36f, baseY), Offset(cx + bodyW * 0.36f, baseY), strokeWidth = bodyW * 0.1f, cap = StrokeCap.Round)
    drawCircle(brim, bodyW * 0.08f, Offset(cx + bodyW * 0.42f, top - bodyW * 0.18f))
}
