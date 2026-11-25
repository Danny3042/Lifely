package components.buddy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import components.ui.BreathingBuddy
import components.ui.BuddyExpression
import kotlinx.coroutines.delay
import platform.PlatformBridge
import sub_pages.MEDITATION_PAGE_ROUTE
import utils.HabitRepository
import utils.MoodCheckIn
import utils.MoodCheckInStore
import utils.MoodScale
import kotlin.time.Clock

/**
 * What's been done with each card this session (a breathing exercise finished, a mood logged),
 * kept outside the list items so scrolling a card away and back doesn't reset it.
 */
private object BuddyCardMemory {
    val done = mutableStateMapOf<String, String>()
}

/** The card for one [BuddyAction] under a Buddy reply. [key] identifies it (message id + index). */
@Composable
fun BuddyActionCard(
    action: BuddyAction,
    key: String,
    isNight: Boolean,
    canSend: Boolean,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (action) {
        is BuddyAction.Breathe -> BreatheCard(action.seconds, key, isNight, modifier)
        is BuddyAction.Meditate -> MeditateCard(action.minutes, modifier)
        is BuddyAction.AddHabit -> HabitCard(action.name, modifier)
        BuddyAction.LogMood -> MoodCard(key, canSend, onSendMessage, modifier)
    }
}

@Composable
private fun CardShell(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

/** Breathe along with the Buddy: in for 4 seconds, out for 6, for the length Gemini suggested. */
@Composable
private fun BreatheCard(seconds: Int, key: String, isNight: Boolean, modifier: Modifier) {
    var running by remember { mutableStateOf(false) }
    val finished = BuddyCardMemory.done[key] != null
    val breath = remember { Animatable(0f) }
    var phase by remember { mutableStateOf("Breathe in") }
    var left by remember { mutableIntStateOf(seconds) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val cycles = ((seconds + 9) / 10).coerceAtLeast(1)
        left = cycles * 10
        repeat(cycles) {
            phase = "Breathe in"
            breath.animateTo(1f, tween(4_000, easing = FastOutSlowInEasing))
            phase = "Breathe out"
            breath.animateTo(0f, tween(6_000, easing = FastOutSlowInEasing))
        }
        running = false
        BuddyCardMemory.done[key] = "breathed"
    }
    // The countdown ticks every second alongside the breaths
    LaunchedEffect(running) {
        while (running && left > 0) {
            delay(1_000)
            left--
        }
    }

    CardShell(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BreathingBuddy(
                breath = breath.value,
                isRunning = running,
                isNight = isNight,
                expression = if (finished && !running) BuddyExpression.Happy else BuddyExpression.Calm,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(targetState = Triple(running, finished, phase), label = "breathe") { (isRunning, isDone, p) ->
                    Column {
                        Text(
                            when {
                                isRunning -> "$p…"
                                isDone -> "Nicely done"
                                else -> "Breathe with Buddy"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when {
                                isRunning -> "Follow Buddy's belly · ${left}s left"
                                isDone -> "That's ${lengthLabel(seconds)} of calm."
                                else -> "${lengthLabel(seconds)} · in for 4, out for 6"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (!running) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { running = true }) { Text(if (finished) "Again" else "Start") }
            }
        }
    }
}

private fun lengthLabel(seconds: Int): String =
    if (seconds % 60 == 0) "${seconds / 60} min" else if (seconds > 60) "${seconds / 60} min ${seconds % 60}s" else "${seconds}s"

/** Starts the meditation timer at the suggested length. */
@Composable
private fun MeditateCard(minutes: Int, modifier: Modifier) {
    CardShell(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon { Icon(Icons.Outlined.SelfImprovement, contentDescription = null) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$minutes-minute meditation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("The timer starts straight away", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                PlatformBridge.meditationStartMinutes = minutes
                PlatformBridge.requestedRoute = MEDITATION_PAGE_ROUTE
                PlatformBridge.requestedRouteSignal = PlatformBridge.requestedRouteSignal + 1
            }) { Text("Start") }
        }
    }
}

/** Adds a habit the Buddy suggested to the habit list. */
@Composable
private fun HabitCard(name: String, modifier: Modifier) {
    val added = HabitRepository.activeHabits.any { it.equals(name, ignoreCase = true) } ||
        HabitRepository.completedHabits.any { it.equals(name, ignoreCase = true) }
    CardShell(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon { Icon(if (added) Icons.Filled.Check else Icons.Outlined.TaskAlt, contentDescription = null) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (added) "In your habits" else "A small habit to try", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            if (added) {
                TextButton(onClick = {
                    PlatformBridge.requestedTabName = "HabitCoachingPage"
                    PlatformBridge.requestedTabSignal = PlatformBridge.requestedTabSignal + 1
                }) { Text("View") }
            } else {
                FilledTonalButton(onClick = { HabitRepository.addHabit(name) }) { Text("Add") }
            }
        }
    }
}

private data class MoodOption(val valence: Int, val word: String, val color: Color)

private val moodOptions = listOf(
    // Five quick choices on the same -3..3 scale as the full check-in (State of Mind)
    MoodOption(-2, "Rough", Color(0xFF7E8CE0)),
    MoodOption(-1, "Low", Color(0xFF9DB4E8)),
    MoodOption(0, "Okay", Color(0xFFB9D9D0)),
    MoodOption(2, "Good", Color(0xFF8FE0C8)),
    MoodOption(3, "Great", Color(0xFFFFD27A)),
)

/** Logs a quick mood check-in (saved like the Home check-in) and tells the Buddy how you feel. */
@Composable
private fun MoodCard(key: String, canSend: Boolean, onSendMessage: (String) -> Unit, modifier: Modifier) {
    val logged = BuddyCardMemory.done[key]
    CardShell(modifier) {
        Column {
            Text(
                if (logged != null) "Logged: $logged" else "How are you feeling right now?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                moodOptions.forEach { option ->
                    val label = MoodScale.label(option.valence)
                    val selected = logged == label
                    val dim = logged != null && !selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(enabled = logged == null, role = Role.Button) {
                                MoodCheckInStore.add(MoodCheckIn(Clock.System.now().toEpochMilliseconds(), option.valence))
                                BuddyCardMemory.done[key] = label
                                if (canSend) onSendMessage("I'm feeling ${option.word.lowercase()} right now.")
                            }
                            .padding(4.dp)
                            .semantics { contentDescription = label },
                    ) {
                        Box(
                            Modifier
                                .size(if (selected) 40.dp else 34.dp)
                                .background(option.color.copy(alpha = if (dim) 0.35f else 1f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF1E2A44), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.size(4.dp))
                        Text(
                            option.word,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (dim) 0.5f else 1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardIcon(content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
