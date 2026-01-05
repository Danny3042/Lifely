package screens

import Health.HealthConnectUtils
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import pages.ChartsPageScreen
import platform.PlatformBridge
import utils.HabitStorage
import utils.HealthKitService
import utils.RealTimeGreeting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun HealthConnectScreen(healthKitService: HealthKitService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val interval: Long = 7

    var steps by remember { mutableStateOf("0") }
    var mins by remember { mutableStateOf("0") }
    var sleepDuration by remember { mutableStateOf("00:00") }
    var showHealthConnectInstallPopup by remember { mutableStateOf(false) }

    val requestPermissions =
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(HealthConnectUtils.PERMISSIONS)) {
                scope.launch {
                    steps = HealthConnectUtils.readStepsForInterval(interval).last().metricValue
                    sleepDuration = HealthConnectUtils.readSleepSessionsForInterval(interval).last().metricValue
                    mins = HealthConnectUtils.readMinsForInterval(interval).last().metricValue
                }
            } else {
                Toast.makeText(context, "Permissions are rejected", Toast.LENGTH_SHORT).show()
            }
        }

    // initialize habit storage for Android
    LaunchedEffect(Unit) {
        HabitStorage.init(context)
    }

    LaunchedEffect(key1 = true) {
        when (HealthConnectUtils.checkForHealthConnectInstalled(context)) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                Toast.makeText(
                    context,
                    "Health Connect client is not available for this device",
                    Toast.LENGTH_SHORT
                ).show()
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                showHealthConnectInstallPopup = true
            }
            HealthConnectClient.SDK_AVAILABLE -> {
                if (HealthConnectUtils.checkPermissions()) {
                    steps = HealthConnectUtils.readStepsForInterval(interval)[0].metricValue
                    sleepDuration = HealthConnectUtils.readSleepSessionsForInterval(interval).last().metricValue
                    mins = HealthConnectUtils.readMinsForInterval(interval).last().metricValue
                } else {
                    requestPermissions.launch(HealthConnectUtils.PERMISSIONS)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Connect", fontSize = 32.sp, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Gray),
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                            .align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Reuse the shared greeting
                        RealTimeGreeting()

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(
                                listOf(
                                    SummaryCardData("Steps", steps,
                                        Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF4CAF50)),
                                    SummaryCardData("Sleep", formatDuration(sleepDuration), Icons.Filled.Hotel, Color(0xFF2196F3)),
                                    SummaryCardData("Active min", mins, Icons.Filled.FitnessCenter, Color(0xFFFF9800))
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
                                    if (shortcut.title == "Charts") {
                                        PlatformBridge.requestedRoute = ChartsPageScreen
                                        PlatformBridge.requestedRouteSignal += 1
                                    } else if (shortcut.title == "Habits") {
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

                        // For charts we will show session durations (meditation durations) from InsightsViewModel later
                        HabitInsights(stepsString = steps, caloriesString = "0", sleepString = sleepDuration, streak = "0")

                        Spacer(Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("You slept $sleepDuration last night! Keep up the good work.", fontSize = 16.sp)
                            }
                        }
                    }

                    if (showHealthConnectInstallPopup) {
                        AlertDialog(
                            onDismissRequest = { showHealthConnectInstallPopup = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    showHealthConnectInstallPopup = false
                                    val uriString =
                                        "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW).apply {
                                            setPackage("com.android.vending")
                                            data = uriString.toUri()
                                            putExtra("overlay", true)
                                            putExtra("callerId", this.`package`)
                                        }
                                    )
                                }) {
                                    Text("Install")
                                }
                            },
                            title = {
                                Text(text = "Alert")
                            },
                            text = {
                                Text(text = "Health Connect is not installed")
                            }
                        )
                    }
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
fun HabitInsights(stepsString: String, caloriesString: String, sleepString: String, streak: String) {
    Column {
        val steps = stepsString.toIntOrNull() ?: 0
        val calories = caloriesString.toIntOrNull() ?: 0
        val sleepMinutes = sleepString.toIntOrNull() ?: 0

        HabitInsightItem("Average steps today", steps.toString())
        HabitInsightItem("Calories burned", calories.toString())
        HabitInsightItem("Sleep last night", formatDuration(sleepMinutes.toString()))

        HabitInsightItem("Current habit streak", streak)
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
            Icon(data.icon, contentDescription = data.title, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(data.value, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(data.title, color = Color.White, fontSize = 14.sp)
        }
    }
}

fun formatDuration(totalMinutes: String): String {
    val total = totalMinutes.toIntOrNull() ?: 0
    val hours = total / 60
    val mins = total % 60
    return "${hours}h ${mins}m"
}