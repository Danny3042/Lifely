package sub_pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import components.MoodCheckInCard
import components.moodColor
import components.ui.ContentWidth
import components.ui.IconBadge
import components.ui.PagePadding
import components.ui.SectionHeader
import components.ui.StatTile
import components.ui.bottomBarOverlap
import components.ui.centeredMaxWidth
import components.ui.topBarOverlap
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant
import utils.HealthData
import utils.healthAccessNeedsRequest
import utils.requestHealthAccess
import utils.HealthKitService
import utils.InsightsViewModel
import utils.MoodCheckInStore
import utils.MoodScale
import utils.isAndroid

const val REFLECTION_PAGE_ROUTE = "meditation_reflection"

/** The same page opened from Home, without a session before it */
const val CHECK_IN_PAGE_ROUTE = "check_in"

/** After a meditation: how it went, a "How are you feeling?" check-in, today's health and a few prompts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionPage(
    healthKitService: HealthKitService,
    onBack: () -> Unit = {},
    // False when opened from Home rather than straight after a meditation
    afterSession: Boolean = true,
    onOpenCalendar: () -> Unit = {},
) {
    val pageTitle = if (afterSession) "Reflection" else "Check in"
    var data by remember { mutableStateOf<HealthData?>(null) }
    // Apple Health is only asked for here, when someone taps Connect (never at launch)
    var healthNeedsRequest by remember { mutableStateOf(false) }
    var healthReload by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        MoodCheckInStore.load()
        healthAccessNeedsRequest { healthNeedsRequest = it }
    }
    LaunchedEffect(healthReload) {
        // it's fine if it errors (no Health permissions)
        try {
            healthKitService.readData().collect { hd -> data = hd }
        } catch (_: Throwable) {}
    }

    // The session just finished is the latest one today
    val insights: InsightsViewModel = viewModel { InsightsViewModel() }
    val sessionsPerDay by insights.sessionsPerDay
    val today = runCatching { Clock.System.todayIn(TimeZone.currentSystemDefault()) }.getOrNull()
    val todayIndex = today?.let { it.dayOfWeek.ordinal } ?: 0 // Monday first
    val todaySessions = sessionsPerDay.getOrNull(todayIndex).orEmpty()
    val lastMinutes = todaySessions.lastOrNull()?.duration
    val todayMinutes = todaySessions.sumOf { it.duration }

    // The check-in's colour glows out across the page, following the card as it scrolls
    var moodValence by remember { mutableStateOf(0f) }
    var moodSaved by remember { mutableStateOf(false) }
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }
    var cardCenter by remember { mutableStateOf(Offset.Unspecified) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      Box(Modifier.fillMaxSize().onGloballyPositioned { pageOrigin = it.positionInRoot() }) {
        MoodGlow(
            valence = moodValence,
            saved = moodSaved,
            center = if (cardCenter.isSpecified) cardCenter - pageOrigin else Offset.Unspecified,
            modifier = Modifier.matchParentSize(),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            if (isAndroid()) {
                TopAppBar(
                    title = { Text(pageTitle) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.Close, contentDescription = "Done")
                        }
                    },
                )
            }

            Column(
                modifier = Modifier
                    .centeredMaxWidth(ContentWidth.Reading)
                    .verticalScroll(rememberScrollState())
                    .padding(top = topBarOverlap(), bottom = bottomBarOverlap())
                    .padding(horizontal = PagePadding, vertical = 16.dp),
            ) {
                // The page's title is in the navigation bar (iOS) or the top app bar (Android)
                Text(
                    if (afterSession) "Take a moment before you move on." else "Take a moment to notice how you feel.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))

                // Session complete
                if (afterSession) Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            icon = Icons.Filled.SelfImprovement,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            size = 52.dp,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Session complete", style = MaterialTheme.typography.titleLarge)
                            Text(
                                when {
                                    lastMinutes != null && todayMinutes > lastMinutes ->
                                        "$lastMinutes min of mindfulness · $todayMinutes min today"
                                    lastMinutes != null -> "$lastMinutes min of mindfulness"
                                    else -> "Nicely done."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                if (afterSession) SectionHeader("Check in") else Spacer(Modifier.height(4.dp))
                MoodCheckInCard(
                    onShapeCenter = { cardCenter = it },
                    onMoodChange = { v, done -> moodValence = v; moodSaved = done },
                )

                // The last few check-ins, so you can see how your feelings move over time
                val recent = MoodCheckInStore.checkIns.take(4)
                if (recent.isNotEmpty()) {
                    SectionHeader(
                        "Recent check-ins",
                        action = {
                            androidx.compose.material3.TextButton(onClick = onOpenCalendar) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Calendar")
                            }
                        },
                    )
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            recent.forEachIndexed { index, checkIn ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(20.dp).background(moodColor(checkIn.valence.toFloat()), CircleShape)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(MoodScale.label(checkIn.valence), style = MaterialTheme.typography.titleSmall)
                                        val details = (checkIn.feelings + checkIn.impacts).joinToString(" · ")
                                        if (details.isNotEmpty()) {
                                            Text(
                                                details,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        whenLabel(checkIn.atEpochMillis),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Today's activity from Apple Health / Health Connect
                SectionHeader("Today in Health")
                val hd = data
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "Steps",
                        value = hd?.stepCount?.toString() ?: "—",
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Sleep",
                        value = hd?.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "—",
                        icon = Icons.Filled.Bedtime,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "Exercise",
                        value = hd?.exerciseDurationMinutes?.let { "$it min" } ?: "—",
                        icon = Icons.Filled.FitnessCenter,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Calories",
                        value = hd?.calories?.toString() ?: "—",
                        icon = Icons.Filled.LocalFireDepartment,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (healthNeedsRequest) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "See your steps, sleep and exercise next to your check-ins, and save your meditations as Mindful Minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        requestHealthAccess {
                            healthNeedsRequest = false
                            healthReload++
                        }
                    }) { Text("Connect Apple Health") }
                } else if (hd == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isAndroid()) "Allow Health Connect access to see your day here."
                        else "Allow Apple Health access to see your day here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // A few things to think about
                SectionHeader("Reflect")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        PromptRow(Icons.Outlined.Air, "What did you notice about your breath?")
                        HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        PromptRow(Icons.Outlined.Mood, "How do you feel now compared with before?")
                        HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        PromptRow(Icons.Outlined.Flag, "What's one intention for the rest of your day?")
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Done", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
      }
    }
}

@Composable
private fun PromptRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = icon,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 40.dp,
        )
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "14:05" for today, otherwise "Mon 14:05". */
private fun whenLabel(epochMillis: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val then = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    val today = Clock.System.now().toLocalDateTime(zone).date
    val time = "${then.hour.toString().padStart(2, '0')}:${then.minute.toString().padStart(2, '0')}"
    if (then.date == today) return time
    val day = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[then.date.dayOfWeek.ordinal]
    return "$day $time"
}

/**
 * A soft light in the mood's colour, radiating from the check-in card across the page. It breathes
 * slowly while you're checking in, swells briefly when you save, then settles to a faint wash.
 */
@Composable
private fun MoodGlow(valence: Float, saved: Boolean, center: Offset, modifier: Modifier = Modifier) {
    val color by animateColorAsState(moodColor(valence), tween(450), label = "glowColor")
    // Brighter the further you are from neutral, like the feeling getting stronger
    val strength = 0.55f + 0.45f * (kotlin.math.abs(valence) / 3f).coerceIn(0f, 1f)
    val intensity by animateFloatAsState(
        if (saved) 0.5f else strength,
        spring(stiffness = 60f),
        label = "glowIntensity",
    )
    // On save the light swells out once
    val swell by animateFloatAsState(if (saved) 1.25f else 1f, tween(900), label = "glowSwell")
    val breathe by rememberInfiniteTransition(label = "glow").animateFloat(
        0.92f, 1.06f, infiniteRepeatable(tween(3_200), RepeatMode.Reverse), label = "glowBreathe",
    )
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Canvas(modifier) {
        if (!center.isSpecified) return@Canvas
        val radius = maxOf(size.width, size.height) * 0.85f * breathe * swell
        val peak = (if (dark) 0.62f else 0.55f) * intensity
        drawRect(
            Brush.radialGradient(
                0f to color.copy(alpha = peak),
                0.25f to color.copy(alpha = peak * 0.7f),
                0.55f to color.copy(alpha = peak * 0.28f),
                0.8f to color.copy(alpha = peak * 0.08f),
                1f to Color.Transparent,
                center = center,
                radius = radius,
            )
        )
    }
}
