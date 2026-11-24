package pages
import components.ui.bottomBarOverlap
import components.ui.topBarOverlap
import components.ui.ExpressiveDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.CalendarMonth
import utils.MoodCheckInStore
import utils.MoodScale
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import components.ui.ContentWidth
import components.ui.IconBadge
import components.ui.LocalWindowSize
import components.ui.centeredMaxWidth
import components.ui.ListRowCard
import components.ui.PagePadding
import components.ui.SectionHeader
import components.ui.StatTile
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import platform.PlatformBridge
import platform.registerComposeShowAddDialogListener
import utils.HabitRepository
import utils.HealthKitService
import utils.InsightsViewModel
import utils.RealTimeGreeting
import utils.isAndroid
import kotlin.time.Clock

const val HomePageScreen = "HomePage"


@Composable
fun HomePage(
    healthKitService: HealthKitService,
    modifier: Modifier = Modifier,
    onNavigateMeditate: () -> Unit = {},
    onNavigateHabits: () -> Unit = {},
    onChartClick: () -> Unit = {},
    onCheckIn: () -> Unit = {},
    onCheckInCalendar: () -> Unit = {},
) {
    LaunchedEffect(Unit) { runCatching { MoodCheckInStore.load() } }
    // The loading placeholders show once per app session: replaying them on every return to Home
    // made quick tab switching flash grey blocks
    var loading by remember { mutableStateOf(!homeShownThisSession) }
    // Custom cards added by the + button
    // Shortcuts pinned with the + button, kept on the device
    val shortcuts = remember { mutableStateListOf<HomeShortcut>().apply { addAll(loadHomeShortcuts()) } }
    var showAddDialog by remember { mutableStateOf(false) }

    // Native iOS host posts "ComposeShowAddDialog" to open the add dialog from its own FAB.
    DisposableEffect(Unit) {
        val unregister = registerComposeShowAddDialogListener { showAddDialog = true }
        onDispose { unregister() }
    }

    LaunchedEffect(Unit) {
        // Apple Health access is asked for only when someone chooses to connect it (Reflection page),
        // not at launch, per Apple's HealthKit guidelines
        if (!homeShownThisSession) delay(700L)
        loading = false
        homeShownThisSession = true
    }

    // Permission status (Android only)
    var hasHealthConnectPermissions by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        hasHealthConnectPermissions = if (isAndroid()) {
            try { healthKitService.checkPermissions() } catch (_: Throwable) { false }
        } else null
    }

    // On iOS the page runs under the navigation bar (so the bar's scroll edge effect has content to
    // blur); the list starts below it instead. 0 on Android.
    val topInsetDp = topBarOverlap()
    // Room to scroll the last card clear of the floating tab bar (iOS); 0 on Android
    val bottomInsetDp = bottomBarOverlap()

    val snackbarHostState = remember { SnackbarHostState() }

    // Stats derived from the shared InsightsViewModel (real meditation sessions)
    val insightsViewModel: InsightsViewModel = viewModel { InsightsViewModel() }
    val sessionsPerDay by insightsViewModel.sessionsPerDay
    val totals = List(7) { idx -> sessionsPerDay.getOrNull(idx)?.sumOf { it.duration }?.toFloat() ?: 0f }
    val weekMinutes = totals.sum().toInt()
    val sessionsCount = sessionsPerDay.sumOf { it.size }
    val today = try { Clock.System.todayIn(TimeZone.currentSystemDefault()) } catch (_: Throwable) { null }
    val todayIndex = today?.let { it.dayOfWeek.ordinal } ?: 0 // Monday first
    val todayMinutes = totals.getOrNull(todayIndex)?.toInt() ?: 0
    // Days in a row from the saved history (the week view alone reset the streak every Monday)
    val streakDays = remember(sessionsPerDay) { runCatching { utils.MeditationHistory.streakDays() }.getOrDefault(0) }

    // Recent activity: latest meditation session + completed habits
    data class RecentItem(val id: String, val label: String, val subtitle: String, val autoCompleted: Boolean)
    val recentItems = remember(sessionsPerDay, HabitRepository.completedHabits) {
        val items = mutableListOf<RecentItem>()
        run loop@{
            for (offset in 0 until 7) {
                val idx = (todayIndex - offset + 7) % 7
                val dayList = sessionsPerDay.getOrNull(idx) ?: emptyList()
                if (dayList.isNotEmpty()) {
                    val s = dayList.last()
                    items.add(RecentItem("session_${idx}_${s.time}", "Meditation · ${s.duration} min", "Completed at ${s.time}", true))
                    return@loop
                }
            }
        }
        HabitRepository.completedHabits.forEach { habit ->
            items.add(RecentItem("habit_${habit.hashCode()}", habit, "Habit completed", true))
        }
        items.toList()
    }
    val checkedStates = remember { mutableStateMapOf<String, Boolean>() }

    if (showAddDialog) {
        AddCardDialog(
            pinned = shortcuts.toSet(),
            onDismiss = { showAddDialog = false },
            onAdd = { shortcut ->
                if (shortcut !in shortcuts) shortcuts.add(shortcut)
                saveHomeShortcuts(shortcuts)
                showAddDialog = false
            }
        )
    }

    val windowSize = LocalWindowSize.current
    val twoPane = windowSize.useTwoPane
    val listPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 8.dp + topInsetDp, bottom = 96.dp + bottomInsetDp)

    // Greeting, today's progress, stats and the weekly trend
    val summarySection: LazyListScope.() -> Unit = {
            // Greeting + date
            item {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
                    RealTimeGreeting()
                    if (today != null) {
                        Text(
                            text = "${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${today.day} ${
                                today.month.name.lowercase().replaceFirstChar { it.uppercase() }
                            }",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Hero: today's mindfulness + primary call to action
            item {
                if (loading) SkeletonBlock(height = 196.dp) else Entrance {
                    TodayHeroCard(todayMinutes = todayMinutes, onStart = onNavigateMeditate)
                }
            }

            // Stat tiles
            item {
                Spacer(Modifier.height(12.dp))
                if (loading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(3) { SkeletonBlock(height = 132.dp, modifier = Modifier.weight(1f)) }
                    }
                } else Entrance {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            label = "Day streak",
                            value = "$streakDays",
                            icon = Icons.Filled.LocalFireDepartment,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Min this week",
                            value = "$weekMinutes",
                            icon = Icons.Filled.SelfImprovement,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateMeditate,
                        )
                        StatTile(
                            label = "Sessions",
                            value = "$sessionsCount",
                            icon = Icons.Filled.CheckCircle,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // How are you feeling: opens the check-in without a meditation first
            item {
                Spacer(Modifier.height(12.dp))
                if (!loading) Entrance {
                    val last = MoodCheckInStore.checkIns.firstOrNull()
                    ListRowCard(
                        title = "How are you feeling?",
                        subtitle = if (last != null) "Last check-in: ${MoodScale.label(last.valence)}" else "Log your mood in a few seconds",
                        icon = Icons.Filled.Mood,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onCheckIn,
                        trailing = {
                            // Past check-ins on a calendar
                            androidx.compose.material3.IconButton(onClick = onCheckInCalendar) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = "Check-in calendar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }

            // Weekly trend
            item {
                SectionHeader("Your week")
                if (loading) SkeletonBlock(height = 112.dp) else Entrance {
                    WeekTrendCard(totals = totals, onClick = onChartClick)
                }
            }
    }

    // Shortcuts and recent activity
    val activitySection: LazyListScope.() -> Unit = {
            // User-added shortcut cards
            if (shortcuts.isNotEmpty()) {
                item { SectionHeader("Shortcuts") }
                items(shortcuts.toList(), key = { it.name }) { shortcut ->
                    val action = when (shortcut) {
                        HomeShortcut.Meditate -> onNavigateMeditate
                        HomeShortcut.Habits -> onNavigateHabits
                        HomeShortcut.Trends -> onChartClick
                        HomeShortcut.CheckIn -> onCheckIn
                        HomeShortcut.Calendar -> onCheckInCalendar
                    }
                    ListRowCard(
                        title = shortcut.title,
                        subtitle = shortcut.subtitle,
                        icon = shortcut.icon,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = action,
                        trailing = {
                            androidx.compose.material3.IconButton(onClick = {
                                shortcuts.remove(shortcut)
                                saveHomeShortcuts(shortcuts)
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove ${shortcut.title} shortcut",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            // Recent activity
            item { SectionHeader("Recent activity") }
            if (recentItems.isEmpty()) {
                item {
                    ListRowCard(
                        title = "Nothing yet today",
                        subtitle = "Finish a meditation or habit and it will show up here.",
                        icon = Icons.Filled.Spa,
                    )
                }
            } else {
                items(recentItems, key = { it.id }) { item ->
                    if (!checkedStates.containsKey(item.id)) checkedStates[item.id] = item.autoCompleted
                    ListRowCard(
                        title = item.label,
                        subtitle = item.subtitle,
                        icon = Icons.Filled.CheckCircle,
                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        trailing = {
                            Checkbox(
                                checked = checkedStates[item.id] == true,
                                onCheckedChange = { checked -> if (!item.autoCompleted) checkedStates[item.id] = checked },
                                enabled = !item.autoCompleted,
                            )
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (twoPane) {
            // Wide screens and landscape: two independently scrolling columns
            Row(
                modifier = Modifier.centeredMaxWidth(ContentWidth.Dashboard).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxSize(), contentPadding = listPadding) {
                    summarySection()
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 16.dp + topInsetDp, bottom = 96.dp + bottomInsetDp),
                ) {
                    activitySection()
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.centeredMaxWidth(ContentWidth.Reading).fillMaxSize(),
                contentPadding = listPadding,
            ) {
                summarySection()
                activitySection()
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        // Compose FAB on Android only; iOS renders a native FAB that calls back into Compose.
        if (isAndroid()) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = PagePadding, bottom = PagePadding)
                    .zIndex(200f),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add card")
            }
        }
    }

    LaunchedEffect(hasHealthConnectPermissions) {
        if (hasHealthConnectPermissions == true) {
            try { snackbarHostState.showSnackbar("Health Connect enabled") } catch (_: Throwable) {}
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TodayHeroCard(todayMinutes: Int, onStart: () -> Unit) {
    // Daily mindfulness goal, picked during onboarding.
    var goalMinutes by remember { mutableStateOf(utils.DEFAULT_DAILY_GOAL_MINUTES) }
    LaunchedEffect(Unit) {
        goalMinutes = runCatching { utils.SettingsManager.loadDailyGoalMinutes() }.getOrDefault(goalMinutes).coerceAtLeast(1)
    }
    val progress = (todayMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Today", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$todayMinutes of $goalMinutes min",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = if (progress >= 1f) "Daily goal reached — nice work" else "Mindful minutes toward your goal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                IconBadge(
                    icon = Icons.Filled.Spa,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    size = 56.dp,
                )
            }
            Spacer(Modifier.height(20.dp))
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start meditation", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WeekTrendCard(totals: List<Float>, onClick: () -> Unit) {
    val max = totals.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val normalised = totals.map { it / max }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mindful minutes", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Last 7 days · tap for charts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (totals.all { it == 0f }) {
                    Text(
                        "No sessions yet — your trend will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Sparkline(values = normalised, modifier = Modifier.fillMaxWidth().height(48.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open charts", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddCardDialog(pinned: Set<HomeShortcut>, onDismiss: () -> Unit, onAdd: (HomeShortcut) -> Unit) {
    val allPinned = pinned.size == HomeShortcut.entries.size
    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = "Add a shortcut",
        icon = Icons.Filled.Add,
        supportingText = if (allPinned) "Every shortcut is already on Home. Remove one with its ✕." else "Pin a shortcut to your Home page. Remove it any time with its ✕.",
        dismissText = "Close",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeShortcut.entries.forEach { shortcut ->
                val isPinned = shortcut in pinned
                val onClick: (() -> Unit)? = if (isPinned) null else { { onAdd(shortcut) } }
                val check: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = if (isPinned) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = "Already on Home", tint = MaterialTheme.colorScheme.primary) }
                } else null
                ListRowCard(
                    title = shortcut.title,
                    subtitle = if (isPinned) "On Home" else shortcut.subtitle,
                    icon = shortcut.icon,
                    onClick = onClick,
                    trailing = check,
                )
            }
        }
    }
}

/** Home has been on screen since the app started (see `loading` in HomePage). */
private var homeShownThisSession = false

/** The shortcuts the + button can pin to Home. */
enum class HomeShortcut(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Meditate("Meditate", "Start a session", Icons.Filled.PlayArrow),
    CheckIn("Check in", "How are you feeling?", Icons.Filled.Mood),
    Calendar("Check-in calendar", "Your past check-ins", Icons.Filled.CalendarMonth),
    Habits("Habits", "Open your habits", Icons.AutoMirrored.Filled.List),
    Trends("Trends", "See your week", Icons.Filled.Insights),
}

private const val HOME_SHORTCUTS_FILE = "home_shortcuts.txt"

private fun loadHomeShortcuts(): List<HomeShortcut> =
    runCatching { platform.readTextFile(HOME_SHORTCUTS_FILE) }.getOrDefault("")
        .split(",").mapNotNull { name -> HomeShortcut.entries.firstOrNull { it.name == name.trim() } }.distinct()

private fun saveHomeShortcuts(shortcuts: List<HomeShortcut>) {
    runCatching { platform.writeTextFile(HOME_SHORTCUTS_FILE, shortcuts.joinToString(",") { it.name }) }
}

/** Fade + slide entrance used for the Home sections once data has loaded. */
@Composable
private fun Entrance(content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 6 },
    ) { content() }
}

@Composable
private fun SkeletonBlock(height: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
    )
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, strokeWidth: Dp = 3.dp) {
    val color = MaterialTheme.colorScheme.primary
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height - strokePx
        val step = if (values.size > 1) w / (values.size - 1) else w
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = strokePx / 2 + h - (v.coerceIn(0f, 1f) * h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = strokePx, cap = StrokeCap.Round))
    }
}
