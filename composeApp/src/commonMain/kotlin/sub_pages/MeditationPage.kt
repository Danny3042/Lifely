package sub_pages

import components.ui.ContentWidth
import components.ui.LocalWindowSize
import components.ui.centeredMaxWidth
import components.ui.DaySky
import components.ui.NightSky
import components.ui.SkyScene
import components.ui.systemBarsContentPadding
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import utils.SettingsManager
import service.SessionNotifications
import service.MEDITATION_FINISHED_NOTIFICATION
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import components.SessionLengthPicker
import components.ui.BreathingBuddy
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.TimeZone
import utils.Session
import androidx.lifecycle.viewmodel.compose.viewModel
import utils.InsightsViewModel
import kotlinx.datetime.toLocalDateTime

// Must not be (or start with) "meditation": that's the Meditate tab's route, and the iOS host
// matches tab routes by prefix, so sharing it made this page count as a tab with no back button.
const val MEDITATION_PAGE_ROUTE = "meditate_timer"
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MeditationPage(
    onBack: (() -> Unit)? = null,
    onNavigateToInsights: (() -> Unit)? = null,
    onMeditationComplete: (() -> Unit)? = null,
    insightsViewModelParam: InsightsViewModel? = null
) {
    // Use provided view model if given; otherwise use platform-appropriate creation.
    val insightsViewModel: InsightsViewModel = insightsViewModelParam ?: viewModel { InsightsViewModel() }

    var totalTime by remember { mutableStateOf(5 * 60) }
    var timeLeft by remember { mutableStateOf(totalTime) }
    val progress = timeLeft / totalTime.toFloat()
    var isRunning by remember { mutableStateOf(false) }
    // Stopping, resetting or changing the length ends the session: its notification is withdrawn.
    // (A session that finishes keeps it, so it still arrives on time.)
    fun stopSession() {
        isRunning = false
        SessionNotifications.cancel(MEDITATION_FINISHED_NOTIFICATION)
    }
    // Leaving the page mid-session ends it too
    val runningNow by rememberUpdatedState(isRunning)
    DisposableEffect(Unit) {
        onDispose { if (runningNow) SessionNotifications.cancel(MEDITATION_FINISHED_NOTIFICATION) }
    }
    var showEditDialog by remember { mutableStateOf(false) }


    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        // Count down to a fixed finish time rather than counting ticks, so the timer is still right
        // after the app has been in the background (where ticks stop).
        val endAt = Clock.System.now().toEpochMilliseconds() + timeLeft * 1000L
        // Hand the "finished" notification to the system now, so it arrives on time even if the app
        // is in the background or the phone is locked by then
        launch {
            val wanted = runCatching { SettingsManager.loadNotificationsEnabled() }.getOrDefault(true)
            if (wanted && runCatching { SessionNotifications.ensurePermission() }.getOrDefault(false)) {
                val seconds = ((endAt - Clock.System.now().toEpochMilliseconds()) / 1000).toInt()
                SessionNotifications.schedule(
                    id = MEDITATION_FINISHED_NOTIFICATION,
                    afterSeconds = seconds,
                    title = "Meditation complete",
                    body = "Your session has ended. Take a moment to notice how you feel.",
                )
            }
        }
        while (true) {
            val remainingMs = endAt - Clock.System.now().toEpochMilliseconds()
            timeLeft = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
            if (timeLeft == 0) break
            // Wake just after the next whole second
            delay((remainingMs % 1000).coerceAtLeast(1) + 5)
        }
        if (timeLeft == 0 && isRunning) {
            isRunning = false
            // Add session to shared state
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            insightsViewModel.addSession(
                Session(
                    time = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}",
                    duration = totalTime / 60
                )
            )
            // The "finished" notification was scheduled with the system when the session started
            // Prefer explicit meditation-complete navigation (Reflection page).
            // Call the completion handler if provided; otherwise fall back to navigating to insights.
            if (onMeditationComplete != null) {
                try {
                    onMeditationComplete.invoke()
                } catch (_: Throwable) {
                    // If the completion handler fails, fallback to insights navigation
                    onNavigateToInsights?.invoke()
                }
            } else {
                onNavigateToInsights?.invoke()
            }
        }
    }

    if (showEditDialog) {
        SessionLengthPicker(
            initialMinutes = totalTime / 60,
            onDismiss = { showEditDialog = false },
            onConfirm = { minutes ->
                totalTime = minutes * 60
                timeLeft = totalTime
                showEditDialog = false
                stopSession()
            }
        )
    }

    val hours = timeLeft / 3600
    val minutes = (timeLeft % 3600) / 60
    val seconds = timeLeft % 60
    val timeText = (if (hours > 0) "$hours:" else "") +
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    // Daytime sky in light mode, night sky in dark mode
    val isNight = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val sky = if (isNight) NightSky else DaySky
    val elapsed = if (totalTime > 0) 1f - timeLeft / totalTime.toFloat() else 0f

    // Breathing guide, counted from the session's own clock so every session starts on a breath in:
    // 4 s in, 4 s out. The glow behind the timer swells and settles over each 4 s half.
    val secondsIn = totalTime - timeLeft
    val breathingIn = secondsIn % 8 < 4
    val breath by animateFloatAsState(
        targetValue = if (isRunning && breathingIn) 1f else 0f,
        animationSpec = tween(4_000, easing = FastOutSlowInEasing),
        label = "breath",
    )

    // iOS shows its own native back button in the navigation bar; only Android needs one drawn here
    val showBackArrow = onBack != null && utils.isAndroid()

    val windowSize = LocalWindowSize.current
    val sideBySide = windowSize.useTwoPane
    // Shrink the ring when height is tight (landscape phones) so everything fits
    val ringSize = if (windowSize.isCompactHeight) (windowSize.height - 72.dp).coerceIn(160.dp, 280.dp) else 280.dp

    val header: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Meditate", style = MaterialTheme.typography.headlineLarge, color = sky.onSky)
            AnimatedContent(
                targetState = when {
                    !isRunning -> if (isNight) "Settle in under the stars" else "Find a sunny, comfortable spot"
                    breathingIn -> "Breathe in…"
                    else -> "…and breathe out"
                },
                transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
                label = "breathCue",
            ) { cue ->
                Text(cue, style = MaterialTheme.typography.bodyLarge, color = sky.onSkyVariant)
            }
        }
    }

    val ring: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
            // Soft glow that swells as you breathe in and settles as you breathe out
            Box(
                Modifier
                    .fillMaxSize()
                    .scale(0.86f + 0.2f * breath)
                    .background(
                        Brush.radialGradient(listOf(sky.accent.copy(alpha = 0.35f), Color.Transparent)),
                        CircleShape,
                    ),
            )
            // Frosted disc so the time stays readable when the sun or moon passes behind it
            Box(Modifier.fillMaxSize(0.86f).background(sky.glass, CircleShape))
            val ringStroke = with(LocalDensity.current) { Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round) }
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = sky.accent,
                trackColor = sky.track,
                stroke = ringStroke,
                trackStroke = ringStroke,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BreathingBuddy(
                    breath = breath,
                    isRunning = isRunning,
                    isNight = isNight,
                    modifier = Modifier.size(if (ringSize < 220.dp) 56.dp else 96.dp),
                )
                AnimatedContent(
                    targetState = timeText,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                ) { text ->
                    Text(
                        text,
                        style = if (ringSize < 220.dp) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                        color = sky.onSky,
                    )
                }
                Text(
                    text = if (isRunning) "remaining" else "${totalTime / 60} min session",
                    style = MaterialTheme.typography.labelLarge,
                    color = sky.onSkyVariant,
                )
            }
        }
    }

    // Controls: reset · play/stop · duration
    val controls: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = {
                    stopSession()
                    timeLeft = totalTime
                },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = sky.glass, contentColor = sky.onSky),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(28.dp))
            }

            LargeFloatingActionButton(
                onClick = {
                    if (isRunning) stopSession() else if (timeLeft > 0) isRunning = true
                },
                // Red while running so Stop is unmistakable
                containerColor = if (isRunning) Color(0xFFE5484D) else sky.accent,
                contentColor = if (isRunning) Color.White else sky.onAccent,
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Stop" else "Start",
                    modifier = Modifier.size(40.dp),
                )
            }

            FilledTonalIconButton(
                onClick = { showEditDialog = true },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = sky.glass, contentColor = sky.onSky),
            ) {
                Icon(Icons.Default.Timer, contentDescription = "Set duration", modifier = Modifier.size(28.dp))
            }
        }
    }

    val tip: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = sky.glass),
        ) {
            Text(
                text = "Take a deep breath and relax. Focus on your breathing; when your mind wanders, gently bring it back.",
                style = MaterialTheme.typography.bodyLarge,
                color = sky.onSky,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = sky.skyMid, contentColor = sky.onSky) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The sky fills the whole screen, under the status, navigation and tab bars...
            SkyScene(isNight = isNight, elapsed = elapsed)
          // ...while the timer and controls stay clear of them.
          Box(modifier = Modifier.fillMaxSize().systemBarsContentPadding()) {
            if (sideBySide) {
                // Landscape / tablet: timer on the left, title, controls and tip on the right
                Row(
                    modifier = Modifier
                        .centeredMaxWidth(ContentWidth.Dashboard)
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { ring() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = ContentWidth.Form)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        header()
                        controls()
                        tip()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .centeredMaxWidth(ContentWidth.Reading)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showBackArrow) Spacer(Modifier.height(48.dp))
                    Spacer(Modifier.height(8.dp))
                    header()
                    Spacer(Modifier.height(40.dp))
                    ring()
                    Spacer(Modifier.height(40.dp))
                    controls()
                    Spacer(Modifier.height(40.dp))
                    tip()
                }
            }

            if (showBackArrow) {
                IconButton(
                    onClick = { onBack?.invoke() },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = sky.onSky)
                }
            }
          }
        }
    }
}