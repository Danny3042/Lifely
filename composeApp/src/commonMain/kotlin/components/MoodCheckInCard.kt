package components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Clock
import utils.MoodCheckIn
import utils.MoodCheckInStore
import utils.MoodScale

/**
 * "How are you feeling right now?" — a check-in in the spirit of State of Mind in Apple's Health app.
 * Slide from very unpleasant to very pleasant (the shape and colour follow), pick the words that fit
 * and what's affecting you most, then save it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodCheckInCard(
    modifier: Modifier = Modifier,
    // Tells the page how you feel as you go, so it can glow in the same colour
    onMoodChange: (valence: Float, saved: Boolean) -> Unit = { _, _ -> },
    // Where the mood shape sits on screen (root coordinates), so the glow can come from it
    onShapeCenter: (androidx.compose.ui.geometry.Offset) -> Unit = {},
) {
    var valence by remember { mutableStateOf(0f) }
    val level = valence.roundToInt()
    val feelings = remember { mutableStateListOf<String>() }
    val impacts = remember { mutableStateListOf<String>() }
    var saved by remember { mutableStateOf(false) }
    // The words and impacts are optional: folded away so a check-in is a slide and a tap
    var showDetails by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(valence, saved) { onMoodChange(valence, saved) }

    val color by animateColorAsState(moodColor(valence), label = "moodColor")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
    ) {
        AnimatedContent(targetState = saved, label = "checkIn") { done ->
            if (done) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Check-in saved", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Noticing how you feel is part of the practice.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = {
                        saved = false
                        valence = 0f
                        feelings.clear()
                        impacts.clear()
                        showDetails = false
                    }) { Text("Check in again") }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("How are you feeling right now?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose how pleasant or unpleasant this moment feels.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MoodShape(
                            valence = valence,
                            color = color,
                            modifier = Modifier.size(140.dp).onGloballyPositioned { onShapeCenter(it.boundsInRoot().center) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        MoodScale.label(level),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Slider(
                        value = valence,
                        onValueChange = { new ->
                            valence = new
                            // Keep only words that still fit the new feeling
                            val fits = MoodScale.feelingsFor(new.roundToInt())
                            feelings.retainAll { it in fits }
                        },
                        valueRange = -3f..3f,
                        steps = 5,
                        // Screen readers say the feeling ("Pleasant"), not a number
                        modifier = Modifier.semantics {
                            contentDescription = "How pleasant or unpleasant you feel"
                            stateDescription = MoodScale.label(level)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = color,
                            activeTrackColor = color,
                            inactiveTrackColor = color.copy(alpha = 0.25f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                        ),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text("Very unpleasant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("Very pleasant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            MoodCheckInStore.add(
                                MoodCheckIn(
                                    atEpochMillis = Clock.System.now().toEpochMilliseconds(),
                                    valence = level,
                                    feelings = feelings.toList(),
                                    impacts = impacts.toList(),
                                )
                            )
                            saved = true
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                    ) { Text("Save check-in") }

                    // Optional details, after Save so they never stand in its way
                    val chosen = feelings.size + impacts.size
                    androidx.compose.material3.TextButton(
                        onClick = { showDetails = !showDetails },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            when {
                                showDetails -> "Hide details"
                                chosen > 0 -> "Details · $chosen chosen"
                                else -> "Add details (optional)"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = showDetails) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            Text("What best describes this feeling?", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            ChipGroup(options = MoodScale.feelingsFor(level), selected = feelings, color = color)

                            Spacer(Modifier.height(16.dp))
                            Text("What's having the biggest impact on you?", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            ChipGroup(options = MoodScale.impacts, selected = impacts, color = color)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(options: List<String>, selected: MutableList<String>, color: Color) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isOn = option in selected
            FilterChip(
                selected = isOn,
                onClick = { if (isOn) selected.remove(option) else selected.add(option) },
                label = { Text(option) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.22f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

/** Purple (unpleasant) through calm blue (neutral) to warm orange (pleasant). */
fun moodColor(valence: Float): Color {
    // One colour per step, like Health's State of Mind. Blending straight from blue to orange runs
    // through grey, so each step blends only into its neighbour.
    val stops = listOf(
        Color(0xFF5B3FD9), // very unpleasant
        Color(0xFF7A5AF8),
        Color(0xFF5B86E5),
        Color(0xFF3A9BDC), // neutral
        Color(0xFF2FB39A),
        Color(0xFFF2B233),
        Color(0xFFF28A1A), // very pleasant
    )
    val position = (valence.coerceIn(-3f, 3f) + 3f)
    val index = position.toInt().coerceAtMost(stops.size - 2)
    return lerp(stops[index], stops[index + 1], position - index)
}

/**
 * The feeling as a shape: sharp spikes when unpleasant, a smooth circle when neutral, soft petals
 * when pleasant. It turns slowly and breathes a little.
 */
@Composable
fun MoodShape(valence: Float, color: Color, modifier: Modifier = Modifier) {
    val v by animateFloatAsState(valence, spring(stiffness = 120f), label = "valence")
    val transition = rememberInfiniteTransition(label = "mood")
    val turn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "turn")
    val breathe by transition.animateFloat(0.96f, 1.02f, infiniteRepeatable(tween(2_400), RepeatMode.Reverse), label = "breathe")

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.78f * breathe
        val t = (v / 3f).coerceIn(-1f, 1f)
        val lobes = if (t < 0) 12 else 7
        val path = Path()
        val steps = 180
        for (i in 0..steps) {
            val angle = (i.toFloat() / steps) * 2f * PI.toFloat()
            val wave = cos(lobes * angle)
            val shape = if (t < 0) {
                // Spikes: narrow peaks pointing out
                max(0f, wave).pow(6) * 0.30f * -t - 0.06f * -t
            } else {
                // Petals: soft round bumps
                ((wave + 1f) / 2f).pow(1.5f) * 0.16f * t
            }
            val r = radius * (1f + shape)
            val point = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        // A soft glow behind, for depth
        drawCircle(color.copy(alpha = 0.12f * (1f - abs(t) * 0.3f)), radius = radius * 1.18f, center = center)
        rotate(turn, center) {
            drawPath(
                path,
                Brush.radialGradient(
                    listOf(lerp(color, Color.White, 0.45f), color),
                    center = center,
                    radius = radius * 1.2f,
                ),
            )
        }
    }
}
