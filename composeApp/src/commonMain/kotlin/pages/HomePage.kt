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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import screens.HealthConnectScreen
import utils.HealthKitService
import utils.RealTimeGreeting
import utils.isAndroid
import platform.PlatformBridge
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
    var hasPermissions by remember { mutableStateOf(false) }

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
            hasPermissions = healthKitService.checkPermissions()
            if (!hasPermissions) {
                hasPermissions = healthKitService.requestAuthorization()
            }
        }
        // simulate loading data for personalization
        loading = true
        delay(700L)
        loading = false
    }

    // Convert platform safe-area (points) to Dp for Compose padding; reading PlatformBridge triggers recomposition.
    val topInsetDp = with(LocalDensity.current) { PlatformBridge.safeAreaTop.toFloat().toDp() }
    val bottomInsetDp = with(LocalDensity.current) { PlatformBridge.safeAreaBottom.toFloat().toDp() }
    // Ensure content sits well below the native top inset (status/notch).
    // Use an inset-based top padding so the greeting never gets clipped. Keep it stable
    // across devices by adding a moderate extra gap to the platform top inset.
    val effectiveTopPadding = if (topInsetDp > 0.dp) (topInsetDp + 84.dp) else 80.dp

    // Wrap content in a Box so we can overlay a FAB anchored bottom-end
    Box(modifier = modifier.fillMaxSize().padding(top = effectiveTopPadding)) {
        // keep the page content in a column inside the Box
        Column(modifier = Modifier.fillMaxSize()) {
            // Top greeting and personalization toggle
            // Greeting area (keep modest inner padding; outer top inset already applied).
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                RealTimeGreeting()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (personalizationEnabled) "Personalized" else "Standard",
                    style = MaterialTheme.typography.bodyMedium
                )
                // The top row no longer shows the add button; the FAB handles adding
                Spacer(modifier = Modifier.size(40.dp))
            }

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

            // Main scrollable content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = (88.dp + bottomInsetDp))
            ) {
                // Quick actions / placeholder (first section) — now personalized
                item {
                    AnimatedSection(visible = !loading) {
                        QuickActionsRow(personalized = personalizationEnabled, onMeditate = onNavigateMeditate, onHabits = onNavigateHabits)
                    }
                    Spacer(modifier = Modifier.height(16.dp)) // increased separation to avoid overlap
                    if (loading) {
                        SkeletonRow()
                    }
                }

                // Stats cards (second section)
                item {
                    AnimatedSection(visible = !loading) {
                        StatsRow(personalized = personalizationEnabled)
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

                // Example recent activities --- replace with real data list
                val recent = listOf("Meditation 10m", "Habit logged", "Session completed")
                items(recent) { act ->
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
                            Text(text = act, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            // Health connect content - show as a compact footer when not available
            if (isAndroid() && !hasPermissions) {
                HealthConnectScreen(healthKitService)
            } else {
                // keep the HealthConnect component available but compact
                HealthConnectScreen(healthKitService)
            }
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
private fun QuickActionsRow(personalized: Boolean, onMeditate: () -> Unit, onHabits: () -> Unit) {
    if (personalized) {
        // Personalized quick actions: resume last session and a suggested habit
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable { onMeditate() },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Resume")
                    Spacer(modifier = Modifier.size(8.dp))
                    Column {
                        Text("Resume")
                        Text("10m guided", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable { /* could open habit suggestion */ onHabits() },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Suggestion")
                    Spacer(modifier = Modifier.size(8.dp))
                    Column {
                        Text("Suggested")
                        Text("Drink water", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    } else {
        // Generic quick actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable { onMeditate() },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Meditate")
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Meditate")
                }
            }
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable { onHabits() },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Habits")
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Habits")
                }
            }
        }
    }
}

@Composable
private fun StatsRow(personalized: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsCard(
            title = if (personalized) "Streak" else "Avg streak",
            value = if (personalized) "7d" else "3d",
            modifier = Modifier.weight(1f).heightIn(min = 72.dp)
        )

        StatsCard(
            title = if (personalized) "Meditation" else "Meditation (avg)",
            value = if (personalized) "42m" else "20m",
            modifier = Modifier.weight(1f).heightIn(min = 72.dp)
        )

        StatsCard(
            title = "Sessions",
            value = if (personalized) "8" else "5",
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
