package pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import utils.HealthKitService
import utils.RealTimeGreeting
import utils.isAndroid
import platform.PlatformBridge
import androidx.lifecycle.viewmodel.compose.viewModel
import utils.InsightsViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import utils.HabitRepository
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.zIndex
import platform.registerComposeShowAddDialogListener

const val HomePageScreen = "HomePage"

@Composable
fun HomePage(
    healthKitService: HealthKitService,
    modifier: Modifier = Modifier,
    onNavigateMeditate: () -> Unit = {},
    onNavigateHabits: () -> Unit = {},
    onChartClick: () -> Unit = {}
) {
    // personalization toggle and loading state (toggle replaced by add button)
    var personalizationEnabled by remember { mutableStateOf(true) } // retained for backward compatibility
    var loading by remember { mutableStateOf(true) }
    // Custom cards added by the + button
    val customCards = remember { mutableStateListOf<Pair<Int, String>>() }
    var nextCardId by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Register platform listener: when native host posts "ComposeShowAddDialog" the onShow lambda will
    // set showAddDialog = true so the Compose add dialog appears.
    DisposableEffect(Unit) {
        val unregister = registerComposeShowAddDialogListener {
            showAddDialog = true
        }
        onDispose { unregister() }
    }

    LaunchedEffect(Unit) {
        if (!isAndroid()) {
            // Trigger any platform-specific checks; don't store results (HealthConnect removed)
            try {
                healthKitService.checkPermissions()
                healthKitService.requestAuthorization()
            } catch (_: Throwable) {
                // ignore - host may not support HealthKit
            }
        }
        // simulate loading data for personalization
        loading = true
        delay(700L)
        loading = false
    }

    // Permission status state (Android only)
    var hasHealthConnectPermissions by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        if (isAndroid()) {
            try {
                hasHealthConnectPermissions = healthKitService.checkPermissions()
            } catch (_: Throwable) {
                hasHealthConnectPermissions = false
            }
        } else {
            hasHealthConnectPermissions = null
        }
    }

    // Convert platform safe-area (points) to Dp for Compose padding; reading PlatformBridge triggers recomposition.
    val topInsetDp = with(LocalDensity.current) { PlatformBridge.safeAreaTop.toFloat().toDp() }
    val bottomInsetDp = with(LocalDensity.current) { PlatformBridge.safeAreaBottom.toFloat().toDp() }
    // Keep content below the native top inset (status/notch). Use a small extra gap so the greeting
    // doesn't get clipped but isn't pushed too far below the nav bar on iOS.
    val extraTopGap = 8.dp
    val effectiveTopPadding = if (topInsetDp > 0.dp) (topInsetDp + extraTopGap) else 16.dp

    // Snackbar for permission confirmation
    val snackbarHostState = remember { SnackbarHostState() }

    // Wrap content in a Box so we can overlay a FAB anchored bottom-end
    Box(modifier = modifier.fillMaxSize().padding(top = effectiveTopPadding)) {
        // Snackbar host overlays the page; placed inside Box so it floats above content.
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        // Health data states (populated on Android via HealthConnect)
        // Stats derived from shared InsightsViewModel so they reflect real meditation sessions
        val insightsViewModel: InsightsViewModel = if (isAndroid()) viewModel() else InsightsViewModel()
        val sessionsPerDay by insightsViewModel.sessionsPerDay

        // compute totals per day (last 7 days) and aggregated stats
        val totals = List(7) { idx ->
            sessionsPerDay.getOrNull(idx)?.sumOf { it.duration }?.toFloat() ?: 0f
        }
        val meditationMinutes = totals.sum().toInt()
        val sessionsCount = sessionsPerDay.sumOf { it.size }

        // compute streak: consecutive days up to today with at least one session
        val todayIndex = try {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            (today.dayOfWeek.ordinal + 6) % 7
        } catch (e: Throwable) { 0 }
        var streakDays = 0
        for (offset in 0 until 7) {
            val idx = (todayIndex - offset + 7) % 7
            val daySessions = sessionsPerDay.getOrNull(idx) ?: emptyList()
            if (daySessions.isNotEmpty()) streakDays++ else break
        }

        // keep the page content in a column inside the Box
        Column(modifier = Modifier.fillMaxSize()) {
            // Main scrollable content — greeting and the top row are now part of the LazyColumn
            // so they will scroll together with the rest of the page content.

            // Add-card dialog: choose type of card to add
            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Add card") },
                    text = {
                        Column {
                            Text("Choose a card to add to your Home page:")
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = {
                                customCards.add(nextCardId to "Meditation shortcut")
                                nextCardId += 1
                                showAddDialog = false
                            }) { Text("Meditation shortcut") }
                            TextButton(onClick = {
                                customCards.add(nextCardId to "Habit shortcut")
                                nextCardId += 1
                                showAddDialog = false
                            }) { Text("Habit shortcut") }
                            TextButton(onClick = {
                                customCards.add(nextCardId to "Charts shortcut")
                                nextCardId += 1
                                showAddDialog = false
                            }) { Text("Charts shortcut") }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAddDialog = false }) { Text("Close") }
                    }
                )
            }

            // remember per-item checked states so checkboxes persist while composable is active
            val checkedStates = remember { mutableStateMapOf<String, Boolean>() }

            // Build recent activity list from sessions and completed habits
            data class RecentItem(val id: String, val label: String, val autoCompleted: Boolean)

            val recentItems = remember(sessionsPerDay, HabitRepository.completedHabits) {
                val items = mutableListOf<RecentItem>()

                // Add the most recent session (search last 7 days)
                run loop@{
                    for (offset in 0 until 7) {
                        val idx = ( (Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.ordinal + 6) % 7 - offset + 7) % 7
                        val dayList = sessionsPerDay.getOrNull(idx) ?: emptyList()
                        if (dayList.isNotEmpty()) {
                            val s = dayList.last()
                            items.add(RecentItem(id = "session_${idx}_${s.time}", label = "Meditation ${s.duration}m", autoCompleted = true))
                            return@loop
                        }
                    }

                }

                // Add completed habits
                HabitRepository.completedHabits.forEach { habit ->
                    items.add(RecentItem(id = "habit_${habit.hashCode()}", label = habit, autoCompleted = true))
                }

                // If no items, keep a friendly placeholder
                if (items.isEmpty()) items.add(RecentItem(id = "none", label = "No recent activity", autoCompleted = false))

                items.toList()
            }

            // Main scrollable content — greeting is now the first item so it scrolls with the page
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = (88.dp + bottomInsetDp))
            ) {
                // Greeting at top (now scrolls)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .heightIn(min = 80.dp)
                    ) {
                        RealTimeGreeting()
                    }
                }

                // Row with personalization label below the greeting (now scrolls)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // The top row no longer shows the add button; the FAB handles adding
                        Spacer(modifier = Modifier.size(40.dp))
                    }
                }

                // Quick actions / placeholder (second section) — now personalized
                item {
                    AnimatedSection(visible = !loading) {
                        QuickActionsRow(personalized = personalizationEnabled, onMeditate = onNavigateMeditate, onSecondAction = onChartClick)
                    }
                    Spacer(modifier = Modifier.height(16.dp)) // increased separation to avoid overlap
                    if (loading) {
                        SkeletonRow()
                    }
                }

                // Stats cards (third section)
                item {
                    AnimatedSection(visible = !loading) {
                        StatsRow(personalized = personalizationEnabled,
                            streakDays = streakDays,
                            steps = null,
                            meditationMinutes = meditationMinutes,
                            sessions = sessionsCount,
                            onMeditate = onNavigateMeditate)
                    }
                    Spacer(modifier = Modifier.height(28.dp)) // increased separation to avoid overlap with charts
                    if (loading) {
                        SkeletonStats()
                    }
                }

                // Charts card as its own item so it does not visually merge with stats
                item {
                    AnimatedSection(visible = !loading) {
                        ChartsCard(onChartClick = onChartClick)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Custom cards inserted by the user via the + button
                if (customCards.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Your cards", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    items(customCards) { (_, title) ->
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { /* could navigate based on type */ },
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    title.contains("Meditation", ignoreCase = true) -> {
                                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Meditation", tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.size(12.dp))
                                        Column { Text(title); Text("Start a session", style = MaterialTheme.typography.bodySmall) }
                                    }
                                    title.contains("Habit", ignoreCase = true) -> {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Habit", tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.size(12.dp))
                                        Column { Text(title); Text("Open habits", style = MaterialTheme.typography.bodySmall) }
                                    }
                                    title.contains("Charts", ignoreCase = true) -> {
                                        MiniSparkline(values = listOf(0.1f,0.5f,0.35f,0.6f,0.9f), modifier = Modifier.size(width = 120.dp, height = 44.dp))
                                        Spacer(modifier = Modifier.size(12.dp))
                                        Column { Text(title); Text("Mini chart preview", style = MaterialTheme.typography.bodySmall) }
                                    }
                                    else -> {
                                        Box(modifier = Modifier.size(44.dp).background(Color(0xFFECE7F3), shape = RoundedCornerShape(8.dp)))
                                        Spacer(modifier = Modifier.size(12.dp))
                                        Text(text = title)
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Recent activity header
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Recent activity",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                items(recentItems) { item ->
                    // initialize default state if missing; autoCompleted items are marked checked
                    if (!checkedStates.containsKey(item.id)) checkedStates[item.id] = item.autoCompleted

                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { /* navigate */ },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(44.dp).background(Color(0xFFE0E0E0), shape = RoundedCornerShape(8.dp)))
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(text = item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = checkedStates[item.id] == true,
                                onCheckedChange = { checked -> if (!item.autoCompleted) checkedStates[item.id] = checked },
                                enabled = !item.autoCompleted,
                                colors = CheckboxDefaults.colors(checkmarkColor = MaterialTheme.colorScheme.onPrimary)
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            // HealthConnectScreen intentionally removed per user request; keep Home content focused on the Home page cards and recent activity.
        }

        // Place the FAB near bottom-end but use a platform-aware upward offset so it
        // clears the native bottom nav without being positioned too high on iOS.
        // Clamp the bottom inset to a reasonable range (0..48dp) to avoid cases where
        // the reported inset is unexpectedly large and pushes the FAB too far up.
        val safeBottomInset = bottomInsetDp.coerceIn(0.dp, 48.dp)
        val fabVerticalOffset = if (isAndroid()) (bottomInsetDp + 120.dp) else (safeBottomInset + 12.dp)

        // Only show Compose FAB on Android. On iOS the host (SwiftUI) should render a native FAB
        // and invoke the equivalent action on the Compose side via your chosen bridge.
        if (isAndroid()) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 12.dp)
                    .offset(y = -fabVerticalOffset)
                    .zIndex(200f)
                    .size(64.dp)
                    .shadow(18.dp, shape = CircleShape)
                    .border(width = 2.dp, color = Color.White, shape = CircleShape),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add card")
            }
        } else {
            // iOS path: Do not render a Compose FAB here to avoid layout overlap issues.
            // Implement a native SwiftUI FAB anchored above the native tab bar in the
            // iOS host (ContentView.swift). The SwiftUI FAB should call into the
            // platform bridge (or your chosen callback) to trigger the same add dialog
            // behavior inside Compose.
        }
    }

    // Show a confirmation snackbar when permissions become granted
    LaunchedEffect(hasHealthConnectPermissions) {
        if (hasHealthConnectPermissions == true) {
            try {
                snackbarHostState.showSnackbar("Health Connect enabled")
            } catch (_: Throwable) {
                // ignore
            }
        }
    }
}

@Composable
private fun AnimatedSection(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 450)) + slideInVertically(tween(durationMillis = 450, easing = FastOutSlowInEasing))
    ) {
        content()
    }
}

@Composable
private fun QuickActionsRow(personalized: Boolean, onMeditate: () -> Unit, onSecondAction: () -> Unit) {
    if (personalized) {
        // Personalized quick actions: resume last session and a suggested habit (now insights)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp), // extra vertical breathing room
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp)
                    .clickable { onMeditate() },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Resume")
                    Spacer(modifier = Modifier.size(16.dp))
                    Column {
                        Text("Resume")
                        Text("10m guided", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp)
                    .clickable { /* insights */ onSecondAction() },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Insights")
                    Spacer(modifier = Modifier.size(16.dp))
                    Column {
                        Text("Insights")
                        Text("View trends", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    } else {
        // Generic quick actions
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp)
                    .clickable { onMeditate() },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Meditate")
                    Spacer(modifier = Modifier.size(16.dp))
                    Text("Meditate")
                }
            }
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp)
                    .clickable { onSecondAction() },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Insights")
                    Spacer(modifier = Modifier.size(16.dp))
                    Text("Insights")
                }
            }
        }
    }
}

@Composable
private fun StatsRow(personalized: Boolean) {
    // Provide default values; overload to accept live health data
    StatsRow(personalized, streakDays = null, steps = null, meditationMinutes = null, sessions = null)
}

@Composable
private fun StatsRow(personalized: Boolean, streakDays: Int?, steps: Int?, meditationMinutes: Int?, sessions: Int?, onMeditate: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val streakValue = streakDays?.let { "${it}d" } ?: if (personalized) "7d" else "3d"
        StatsCard(
            title = if (personalized) "Streak" else "Avg streak",
            value = streakValue,
            modifier = Modifier.weight(1f).heightIn(min = 72.dp)
        )

        StatsCard(
            title = if (personalized) "Meditation" else "Meditation (avg)",
            value = meditationMinutes?.let { "${it}m" } ?: if (personalized) "42m" else "20m",
            modifier = Modifier.weight(1f).heightIn(min = 72.dp).clickable { onMeditate() }
        )

        StatsCard(
            title = "Sessions",
            value = sessions?.toString() ?: if (personalized) "8" else "5",
            modifier = Modifier.weight(1f).heightIn(min = 72.dp)
        )
    }
}

@Composable
private fun ChartsCard(onChartClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onChartClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // mini sparkline preview
            MiniSparkline(
                values = listOf(0.2f, 0.4f, 0.3f, 0.6f, 0.5f, 0.8f, 0.7f),
                modifier = Modifier.size(width = 100.dp, height = 44.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text("View charts", style = MaterialTheme.typography.bodyMedium)
                Text("Tap to open key metrics", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatsCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(vertical = 6.dp), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth().heightIn(min = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.size(6.dp))
            // allow value to wrap / expand so numbers are not clipped
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// Simple skeleton shimmer using an infinite transition alpha pulse
@Composable
private fun SkeletonRow() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
            },
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Box(modifier = Modifier
            .fillMaxWidth(0.48f)
            .height(56.dp)
            .alpha(alpha)
            .background(Color(0xFFE0E0E0), shape = RoundedCornerShape(12.dp)))
        Spacer(modifier = Modifier.size(8.dp))
        Box(modifier = Modifier
            .fillMaxWidth(0.48f)
            .height(56.dp)
            .alpha(alpha)
            .background(Color(0xFFE0E0E0), shape = RoundedCornerShape(12.dp)))
    }
}

@Composable
private fun SkeletonStats() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f).height(72.dp).alpha(alpha).background(Color(0xFFE0E0E0), shape = RoundedCornerShape(12.dp)))
        Box(modifier = Modifier.weight(1f).height(72.dp).alpha(alpha).background(Color(0xFFE0E0E0), shape = RoundedCornerShape(12.dp)))
        Box(modifier = Modifier.weight(1f).height(72.dp).alpha(alpha).background(Color(0xFFE0E0E0), shape = RoundedCornerShape(12.dp)))
    }
}

@Composable
private fun MiniSparkline(values: List<Float>, modifier: Modifier = Modifier, strokeWidth: Dp = 2.dp) {
    // Capture composable-only values outside the draw scope
    val color = MaterialTheme.colorScheme.primary
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }

    // Expect values between 0..1; draw a simple sparkline
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val step = if (values.size > 1) w / (values.size - 1) else w
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v.coerceIn(0f, 1f) * h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = strokePx, cap = StrokeCap.Round))
    }
}
