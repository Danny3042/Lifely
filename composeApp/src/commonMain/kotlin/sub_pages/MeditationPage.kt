package sub_pages

import components.ui.ContentWidth
import components.ui.LocalWindowSize
import components.ui.centeredMaxWidth
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
import com.mmk.kmpnotifier.notification.NotifierManager
import utils.SettingsManager
import components.TimeEditDialog
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.TimeZone
import utils.Session
import androidx.lifecycle.viewmodel.compose.viewModel
import utils.InsightsViewModel
import kotlinx.datetime.toLocalDateTime

const val MEDITATION_PAGE_ROUTE = "meditation"
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
    // NotifierManager may not be initialized yet on iOS startup; obtain safely.
    val notifier = remember {
        runCatching { NotifierManager.getLocalNotifier() }.getOrNull()
    }
    var showEditDialog by remember { mutableStateOf(false) }


    LaunchedEffect(isRunning) {
        while (isRunning && timeLeft > 0) {
            delay(1.seconds)
            timeLeft -= 1
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
            // Send notification only if user has enabled notifications
            runCatching {
                val enabled = SettingsManager.loadNotificationsEnabled()
                if (enabled) {
                    notifier?.notify(
                        title = "Meditation Timer Finished",
                        body = "Your meditation session has ended. Take a moment to reflect.",
                    )
                }
            }
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
        TimeEditDialog(
            initialMinutes = totalTime / 60,
            initialSeconds = totalTime % 60,
            onDismiss = { showEditDialog = false },
            onConfirm = { minutes, seconds ->
                totalTime = minutes * 60 + seconds
                timeLeft = totalTime
                showEditDialog = false
                isRunning = false
            }
        )
    }

    val minutes = timeLeft / 60
    val seconds = timeLeft % 60
    val timeText = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    val windowSize = LocalWindowSize.current
    val sideBySide = windowSize.useTwoPane
    // Shrink the ring when height is tight (landscape phones) so everything fits
    val ringSize = if (windowSize.isCompactHeight) (windowSize.height - 72.dp).coerceIn(160.dp, 280.dp) else 280.dp

    val header: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Meditate", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = if (isRunning) "Breathe in… and out" else "Find a comfortable position",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val ring: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
            val ringStroke = with(LocalDensity.current) { Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round) }
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
                stroke = ringStroke,
                trackStroke = ringStroke,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = timeText,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                ) { text ->
                    Text(
                        text,
                        style = if (ringSize < 220.dp) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
                    )
                }
                Text(
                    text = if (isRunning) "remaining" else "${totalTime / 60} min session",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    isRunning = false
                    timeLeft = totalTime
                },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(28.dp))
            }

            LargeFloatingActionButton(
                onClick = {
                    if (isRunning) isRunning = false else if (timeLeft > 0) isRunning = true
                },
                containerColor = if (isRunning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (isRunning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimary,
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
            ) {
                Icon(Icons.Default.Timer, contentDescription = "Set duration", modifier = Modifier.size(28.dp))
            }
        }
    }

    val tip: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Text(
                text = "Take a deep breath and relax. Focus on your breathing; when your mind wanders, gently bring it back.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    if (onBack != null) Spacer(Modifier.height(48.dp))
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

            if (onBack != null) {
                IconButton(
                    onClick = { onBack.invoke() },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }
    }
}