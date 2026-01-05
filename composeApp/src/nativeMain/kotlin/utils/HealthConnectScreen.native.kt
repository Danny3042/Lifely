package screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import utils.HealthKitService
import platform.rememberSafeAreaInsetsWithTabBar
import utils.RealTimeGreeting
import platform.PlatformBridge
import pages.ChartsPageScreen

// Health data model
data class ComposeAppHealthData(
    val stepCount: Int = 0,
    val sleepDurationMinutes: Int = 0,
    val calories: Int = 0,
    val habitStreak: Int = 0
)

@Composable
actual fun HealthConnectScreen(healthKitService: HealthKitService) {
    val healthData = healthKitService.readData().collectAsState(initial = null)

    LaunchedEffect(Unit) {
        healthKitService.requestAuthorization()
    }

    // Convert HealthData to ComposeAppHealthData
    val composeHealthData = healthData.value?.let { data ->
        ComposeAppHealthData(
            stepCount = data.stepCount ?: 0,
            sleepDurationMinutes = data.sleepDurationMinutes ?: 0,
            calories = data.calories ?: 0,
            habitStreak = 0  // Habit streak is app-specific, not from HealthKit
        )
    }

    MaterialTheme {
        val insets = rememberSafeAreaInsetsWithTabBar()
        Column(modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(insets)
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Show the shared RealTimeGreeting inside the content area (this ensures a single large greeting)
            RealTimeGreeting()
            Spacer(Modifier.height(16.dp))

            // Summary cards row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(
                    listOf(
                        SummaryCardData("Steps", composeHealthData?.stepCount?.toString() ?: "0",
                            Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF4CAF50)),
                        SummaryCardData(
                            "Sleep",
                            formatDuration(composeHealthData?.sleepDurationMinutes),
                            Icons.Filled.Hotel,
                            Color(0xFF2196F3)
                        ),
                        SummaryCardData("Calories Burned", composeHealthData?.calories?.toString() ?: "0", Icons.Filled.LocalFireDepartment, Color(0xFF9C27B0))
                    )
                ) { card ->
                    SummaryCard(card)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Quick Actions / Shortcuts
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(
                    listOf(
                        ShortcutData("Charts", Icons.Filled.PieChart, Color(0xFF607D8B)),
                        ShortcutData("Insights", Icons.Filled.PieChart, Color(0xFF795548)),
                        ShortcutData("Habits", Icons.Filled.PieChart, Color(0xFF3F51B5))
                    )
                ) { shortcut ->
                    ShortcutCard(shortcut) {
                        // Use PlatformBridge to request navigation
                        if (shortcut.title == "Charts") {
                            PlatformBridge.requestedRoute = ChartsPageScreen
                            PlatformBridge.requestedRouteSignal += 1
                        } else if (shortcut.title == "Habits") {
                            // Request the Habits tab in HeroScreen (Compose)
                            PlatformBridge.requestedTabName = "HabitCoachingPage"
                            PlatformBridge.requestedTabSignal += 1
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Insights section
            Text("Insights", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // Habit Insights - derive simple insights from healthData if available
            HabitInsights(healthData = composeHealthData)

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("You slept ${composeHealthData?.sleepDurationMinutes ?: 0} minutes last night! Keep up the good work.", fontSize = 16.sp)
                }
            }
        }
    }
}

// Shortcut UI

data class ShortcutData(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

@Composable
fun ShortcutCard(data: ShortcutData, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(width = 120.dp, height = 100.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = data.color),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(data.icon, contentDescription = data.title, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(data.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// Habit insights

@Composable
fun HabitInsights(healthData: ComposeAppHealthData?) {
    // ComposeAppHealthData is the health data type containing step count, calories, sleep, and habit streak
    Column {
        // Example derived insights - use real fields if available
        val steps = healthData?.stepCount ?: 0
        val calories = healthData?.calories ?: 0
        val sleepMinutes = healthData?.sleepDurationMinutes ?: 0

        HabitInsightItem("Average steps today", steps.toString())
        HabitInsightItem("Calories burned", calories.toString())
        HabitInsightItem("Sleep last night", formatDuration(sleepMinutes))

        // Placeholder for habit-specific insights (e.g., streaks)
        HabitInsightItem("Current habit streak", healthData?.habitStreak?.toString() ?: "0")
    }
}

@Composable
fun HabitInsightItem(title: String, value: String) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

data class SummaryCardData(
    val title: String,
    val value: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

@Composable
fun SummaryCard(data: SummaryCardData) {
    Card(
        colors = CardDefaults.cardColors(containerColor = data.color),
        modifier = Modifier
            .size(width = 140.dp, height = 100.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Icon(data.icon, contentDescription = data.title, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(data.value, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(data.title, color = Color.White, fontSize = 14.sp)
        }
    }
}

fun formatDuration(totalMinutes: Int?): String {
    val total = totalMinutes ?: 0
    val hours = total / 60
    val minutes = total % 60
    return "${hours}h ${minutes}m"
}