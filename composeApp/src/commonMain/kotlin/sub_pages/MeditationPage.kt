package sub_pages

import components.ui.ContentWidth
import utils.saveMindfulMinutes
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import utils.SettingsManager
import service.SessionNotifications
import service.MeditationLiveActivity
import platform.PlatformBridge
import service.MEDITATION_FINISHED_NOTIFICATION
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import components.SessionLengthPicker
import components.ui.BreathingBuddy
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import tts.AmbientSound
import tts.AmbientSounds
import tts.GuideAudio
import service.weather.BuddyWeather
import service.weather.WeatherAttributionLink
import service.weather.WeatherKind
import components.ui.PagePadding
import components.ui.bottomBarOverlap
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import tts.GuideVoice
import tts.GuideVoices
import tts.guideScript
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MeditationPage(
    onBack: (() -> Unit)? = null,
    onNavigateToInsights: (() -> Unit)? = null,
    onMeditationComplete: (() -> Unit)? = null,
    insightsViewModelParam: InsightsViewModel? = null
) {
    // Use provided view model if given; otherwise use platform-appropriate creation.
    val insightsViewModel: InsightsViewModel = insightsViewModelParam ?: viewModel { InsightsViewModel() }

    // Opens at the length you used last time
    var totalTime by remember { mutableStateOf(loadPreferredMinutes() * 60) }
    var timeLeft by remember { mutableStateOf(totalTime) }
    val progress = timeLeft / totalTime.toFloat()
    var isRunning by remember { mutableStateOf(false) }
    // Stopping, resetting or changing the length ends the session: its notification is withdrawn.
    // (A session that finishes keeps it, so it still arrives on time.)
    fun stopSession() {
        isRunning = false
        GuideAudio.stop()
        SessionNotifications.cancel(MEDITATION_FINISHED_NOTIFICATION)
        MeditationLiveActivity.end(completed = false)
    }
    // Leaving the page mid-session ends it too
    val runningNow by rememberUpdatedState(isRunning)
    DisposableEffect(Unit) {
        onDispose {
            // The ambience fades out on its own after the page has gone (it also covers a finished session)
            AmbientSound.fadeOut(1500)
            if (runningNow) {
                GuideAudio.stop()
                SessionNotifications.cancel(MEDITATION_FINISHED_NOTIFICATION)
                MeditationLiveActivity.end(completed = false)
            }
        }
    }
    // "Start a meditation" from Siri or Shortcuts: set the length and go
    val startRequest = PlatformBridge.meditationStartMinutes
    LaunchedEffect(startRequest) {
        val minutes = startRequest ?: return@LaunchedEffect
        PlatformBridge.meditationStartMinutes = null
        if (isRunning) stopSession()
        totalTime = minutes.coerceIn(1, 180) * 60
        timeLeft = totalTime
        isRunning = true
    }
    // The Stop button on the Live Activity
    val stopSignal = PlatformBridge.meditationStopSignal
    val initialStopSignal = remember { stopSignal }
    LaunchedEffect(stopSignal) {
        if (stopSignal != initialStopSignal && isRunning) stopSession()
    }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }

    // Weather (only when the person shares it): shapes the sky, the wording and the suggested sound
    LaunchedEffect(Unit) { BuddyWeather.refresh() }
    val weather = BuddyWeather.current
    val suggestedSound = AmbientSounds.suggestionFor(weather)
    // Until you choose something yourself, the sound that suits the weather is preselected
    val soundId = if (AmbientSounds.hasChosen) AmbientSounds.selectedId else suggestedSound
    var ambienceMuted by remember { mutableStateOf(false) }
    val ambientScope = rememberCoroutineScope()
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Premium candidate: mixing several sounds, or more than the basic set, would go here.
    // The ambient sound fades in when a session starts (and out if it's muted)...
    LaunchedEffect(isRunning, soundId, ambienceMuted) {
        if (!isRunning) return@LaunchedEffect
        previewJob?.cancel()
        if (soundId == null || ambienceMuted) {
            AmbientSound.fadeOut(800)
        } else {
            val wav = AmbientSounds.load(soundId)
            if (wav != null) AmbientSound.play(soundId, wav, AmbientSounds.volume)
        }
    }
    // ...and fades out when the session ends, stops or pauses
    LaunchedEffect(isRunning) {
        if (!isRunning) AmbientSound.fadeOut(2000)
    }
    // The volume slider is live
    LaunchedEffect(AmbientSounds.volume) {
        if (AmbientSound.playingId != null) AmbientSound.setVolume(AmbientSounds.volume)
    }


    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        // Count down to a fixed finish time rather than counting ticks, so the timer is still right
        // after the app has been in the background (where ticks stop).
        val endAt = Clock.System.now().toEpochMilliseconds() + timeLeft * 1000L
        // The countdown in the Dynamic Island / on the Lock Screen
        MeditationLiveActivity.start(endAtEpochMillis = endAt, totalSeconds = totalTime)
        // Hand the "finished" notification to the system now, so it arrives on time even if the app
        // is in the background or the phone is locked by then
        launch {
            val wanted = runCatching { SettingsManager.loadNotificationsEnabled() }.getOrDefault(true)
            if (wanted && runCatching { SessionNotifications.ensurePermission() }.getOrDefault(false)) {
                val seconds = ((endAt - Clock.System.now().toEpochMilliseconds()) / 1000).toInt()
                SessionNotifications.schedule(
                    id = MEDITATION_FINISHED_NOTIFICATION,
                    afterSeconds = seconds,
                    title = "Meditation Complete",
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
            MeditationLiveActivity.end(completed = true)
            // Add session to shared state
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            insightsViewModel.addSession(
                Session(
                    time = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}",
                    duration = totalTime / 60
                )
            )
            // Also save it to Apple Health as Mindful Minutes, if the person allowed that
            // Use when the timer actually ended, not when the app came back to the foreground
            saveMindfulMinutes(endAt - totalTime * 1000L, endAt)
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

    // Spoken guidance: the chosen AI voice welcomes you, checks in through the session and closes it.
    // Every line is prepared as the session starts (and kept on the phone for next time), then
    // played on schedule; the line being spoken is shown as a caption.
    var caption by remember { mutableStateOf<String?>(null) }
    val guideVoice = GuideVoices.selected
    LaunchedEffect(isRunning, guideVoice) {
        caption = null
        val voice = guideVoice
        if (!isRunning || voice == null) return@LaunchedEffect
        val total = totalTime
        val elapsedNow = total - timeLeft
        val startedAt = Clock.System.now().toEpochMilliseconds() - elapsedNow * 1000L
        val cues = guideScript(total, BuddyWeather.current?.kind).filter { it.atSecond >= elapsedNow - 1 }
        val gate = Semaphore(3)
        val files = cues.map { cue -> async { gate.withPermit { GuideVoices.audioFor(voice, cue.text) } } }
        try {
            cues.forEachIndexed { i, cue ->
                val wait = startedAt + cue.atSecond * 1000L - Clock.System.now().toEpochMilliseconds()
                if (wait > 0) delay(wait)
                // The first line may still be generating on a first run: give it a little longer
                val file = withTimeoutOrNull(if (i == 0) 15_000 else 6_000) { files[i].await() } ?: return@forEachIndexed
                caption = cue.text
                GuideAudio.play(file)
            }
        } finally {
            // Stopped early: silence the guide. A session that finishes lets its closing words play out.
            if (timeLeft > 0) GuideAudio.stop()
        }
    }
    val guideScope = rememberCoroutineScope()
    var previewing by remember { mutableStateOf<String?>(null) }
    var voiceProblem by remember { mutableStateOf(false) }

    if (showEditDialog) {
        SessionLengthPicker(
            initialMinutes = totalTime / 60,
            onDismiss = { showEditDialog = false },
            onConfirm = { minutes ->
                totalTime = minutes * 60
                timeLeft = totalTime
                showEditDialog = false
                stopSession()
                savePreferredMinutes(minutes)
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

    val header: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Meditate", style = MaterialTheme.typography.headlineLarge, color = sky.onSky)
            AnimatedContent(
                targetState = when {
                    !isRunning -> when (weather?.kind) {
                        WeatherKind.Drizzle, WeatherKind.Rain, WeatherKind.HeavyRain, WeatherKind.Thunder -> "Rain outside. Settle in and listen."
                        WeatherKind.Snow -> "Snow outside. Get cosy."
                        WeatherKind.Windy -> "Windy out there. Find a still spot inside."
                        else -> if (isNight) "Settle in under the stars" else "Find a sunny, comfortable spot"
                    }
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

    val ring: @Composable (Dp) -> Unit = { ringSize ->
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

            if (isRunning && soundId != null) {
                // Mute or unmute the ambient sound mid-session
                FilledTonalIconButton(
                    onClick = { ambienceMuted = !ambienceMuted },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = sky.glass, contentColor = sky.onSky),
                ) {
                    Icon(
                        if (ambienceMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (ambienceMuted) "Unmute ${AmbientSounds.name(soundId)}" else "Mute ${AmbientSounds.name(soundId)}",
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                FilledTonalIconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = sky.glass, contentColor = sky.onSky),
                ) {
                    Icon(Icons.Default.Timer, contentDescription = "Set duration", modifier = Modifier.size(28.dp))
                }
            }
        }
    }

    // One tappable summary of the session (length, sound, guide); it opens the "Your session" sheet.
    val summaryRow: @Composable () -> Unit = {
        AnimatedVisibility(visible = !isRunning, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(sky.glass)
                    .clickable { showSheet = true }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SummaryPart(Icons.Default.Timer, "${totalTime / 60} min", sky.onSky)
                SummaryPart(soundIcon(soundId), AmbientSounds.name(soundId), sky.onSky)
                SummaryPart(Icons.Default.RecordVoiceOver, GuideVoices.selected?.name ?: "No guide", sky.onSky)
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
                // While a voice guides the session, the card shows what it's saying
                text = caption?.takeIf { isRunning } ?: "Take a deep breath and relax. Focus on your breathing; when your mind wanders, gently bring it back.",
                style = MaterialTheme.typography.bodyLarge,
                color = sky.onSky,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                previewJob?.cancel()
                if (!isRunning) AmbientSound.fadeOut(600)
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PagePadding)
                    .padding(bottom = 24.dp + bottomBarOverlap()),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Text("Your session", style = MaterialTheme.typography.headlineSmall)

                // 1) Length
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Length", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QUICK_LENGTHS.forEach { mins ->
                            FilterChip(
                                selected = totalTime == mins * 60,
                                onClick = {
                                    stopSession()
                                    totalTime = mins * 60
                                    timeLeft = totalTime
                                    savePreferredMinutes(mins)
                                },
                                label = { Text("$mins min", style = MaterialTheme.typography.labelLarge) },
                                modifier = Modifier.heightIn(min = 40.dp),
                                shape = CircleShape,
                            )
                        }
                    }
                }

                // 2) Sound
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sound", style = MaterialTheme.typography.titleSmall)
                    val options = listOf<String?>(null) + AmbientSounds.all.map { it.id }
                    FlowRow(
                        maxItemsInEachRow = 4,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        options.forEach { id ->
                            SoundTile(
                                icon = soundIcon(id),
                                label = AmbientSounds.name(id).let { if (id == null) "None" else it },
                                selected = soundId == id,
                                suggested = id != null && id == suggestedSound,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    AmbientSounds.select(id)
                                    previewJob?.cancel()
                                    if (id == null) {
                                        if (!isRunning) AmbientSound.fadeOut(500)
                                    } else if (!isRunning) {
                                        // A short taste of it, so you can hear what you're picking
                                        previewJob = ambientScope.launch {
                                            val wav = AmbientSounds.load(id) ?: return@launch
                                            AmbientSound.play(id, wav, AmbientSounds.volume)
                                            delay(3_000)
                                            if (AmbientSound.playingId == id) AmbientSound.fadeOut(1_000)
                                        }
                                    }
                                },
                            )
                        }
                        // An empty slot so the last row's tiles stay the same size as the others
                        Spacer(Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = AmbientSounds.volume,
                            onValueChange = { AmbientSounds.updateVolume(it) },
                            onValueChangeFinished = { AmbientSounds.saveVolume() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (weather != null) {
                        if (suggestedSound != null) {
                            Text(
                                "Suggested for now: ${AmbientSounds.name(suggestedSound)}. ${AmbientSounds.reasonFor(weather)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        WeatherAttributionLink(weather)
                    }
                }

                // 3) Guide voice. Premium candidate: extra voices beyond the first few.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Guide voice", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        (listOf<GuideVoice?>(null) + GuideVoices.all).forEach { voice ->
                            FilterChip(
                                selected = GuideVoices.selectedId == voice?.id,
                                onClick = {
                                    GuideVoices.select(voice?.id)
                                    GuideAudio.stop()
                                    voiceProblem = false
                                    if (voice != null) {
                                        previewing = voice.id
                                        guideScope.launch {
                                            val file = GuideVoices.audioFor(voice, GuideVoices.sampleLine(voice))
                                            if (previewing == voice.id) {
                                                previewing = null
                                                if (file != null) GuideAudio.play(file) else voiceProblem = true
                                            }
                                        }
                                    } else {
                                        previewing = null
                                    }
                                },
                                label = { Text(voice?.name ?: "Off", style = MaterialTheme.typography.labelLarge) },
                                leadingIcon = if (previewing != null && previewing == voice?.id) {
                                    { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) }
                                } else null,
                                modifier = Modifier.heightIn(min = 40.dp),
                                shape = CircleShape,
                            )
                        }
                    }
                    Text(
                        when {
                            voiceProblem -> "Couldn't load that voice. Check your connection and try again."
                            GuideVoices.selected != null -> "${GuideVoices.selected!!.character} voice guides your session"
                            else -> "Silent session"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 4) Done
                Button(onClick = {
                    showSheet = false
                    previewJob?.cancel()
                    if (!isRunning) AmbientSound.fadeOut(600)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = sky.skyMid, contentColor = sky.onSky) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The sky fills the whole screen, under the status, navigation and tab bars...
            SkyScene(isNight = isNight, elapsed = elapsed, weather = weather?.kind)
          // ...while the timer and controls stay clear of them.
          BoxWithConstraints(modifier = Modifier.fillMaxSize().systemBarsContentPadding()) {
            if (sideBySide) {
                // Landscape / tablet: timer on the left, title, controls and tip on the right
                val ringSize = minOf(maxWidth / 2 - 48.dp, maxHeight - 32.dp, 300.dp).coerceAtLeast(160.dp)
                Row(
                    modifier = Modifier
                        .centeredMaxWidth(ContentWidth.Dashboard)
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { ring(ringSize) }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = ContentWidth.Form)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        header()
                        controls()
                        summaryRow()
                        tip()
                    }
                }
            } else {
                // Everything you need to start a session fits on one screen; the tip sits below the fold.
                val reserved = 290.dp + (if (showBackArrow) 48.dp else 0.dp)
                val ringSize = minOf(maxWidth - 48.dp, maxHeight - reserved, 300.dp).coerceAtLeast(160.dp)
                Column(
                    modifier = Modifier
                        .centeredMaxWidth(ContentWidth.Reading)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showBackArrow) Spacer(Modifier.height(48.dp))
                    Spacer(Modifier.height(4.dp))
                    header()
                    Spacer(Modifier.height(16.dp))
                    ring(ringSize)
                    Spacer(Modifier.height(16.dp))
                    controls()
                    Spacer(Modifier.height(14.dp))
                    summaryRow()
                    Spacer(Modifier.height(24.dp))
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

private fun soundIcon(id: String?): ImageVector = when (id) {
    "rain" -> Icons.Default.WaterDrop
    "wind" -> Icons.Default.Air
    "waves" -> Icons.Default.Waves
    "stream" -> Icons.Default.Opacity
    "fire" -> Icons.Default.LocalFireDepartment
    "night" -> Icons.Default.NightsStay
    else -> Icons.Default.Block
}

@Composable
private fun SummaryPart(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A sound in the picker: icon over name, with a small "Suggested" note when it suits the weather. */
@Composable
private fun SoundTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    suggested: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content)
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        if (suggested) {
            Text("Suggested for now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/** One-tap session lengths on the meditation page */
private val QUICK_LENGTHS = listOf(1, 3, 5, 10, 15, 20)
private const val PREFERRED_LENGTH_FILE = "meditation_length.txt"

private fun loadPreferredMinutes(): Int =
    runCatching { platform.readTextFile(PREFERRED_LENGTH_FILE).trim().toInt() }.getOrNull()?.coerceIn(1, 120) ?: 5

private fun savePreferredMinutes(minutes: Int) {
    runCatching { platform.writeTextFile(PREFERRED_LENGTH_FILE, minutes.toString()) }
}
