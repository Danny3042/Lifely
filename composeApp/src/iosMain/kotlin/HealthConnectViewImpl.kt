package utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import platform.rememberSafeAreaInsetsWithTabBar
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun HealthConnectViewImpl(healthKitService: HealthKitService) {
    val healthData = healthKitService.readData().collectAsState(initial = null)

    LaunchedEffect(Unit) {
        healthKitService.requestAuthorization()
    }

    MaterialTheme {
        // Respect safe area insets but cap the top inset so content sits nicely under
        // the native large title without creating too much gap.
        val insets = rememberSafeAreaInsetsWithTabBar()
        val startInset = insets.calculateLeftPadding(LayoutDirection.Ltr)
        val endInset = insets.calculateRightPadding(LayoutDirection.Ltr)
        val rawTop = insets.calculateTopPadding()
        // If the platform didn't provide a top inset (rawTop ~ 0), fallback to a reasonable
        // padding so content won't overlap the native large title. Otherwise keep a small
        // clamped inset to keep spacing tight.
        val defaultFallbackTop = 20.dp
        // If platform didn't report an inset yet, use a small fallback (20.dp). Otherwise keep the
        // reported inset clamped to a small range so greeting sits tight under the nav title.
        val topPadding = if (rawTop <= 2.dp) defaultFallbackTop else rawTop.coerceIn(6.dp, 20.dp)

        Column(
            modifier = Modifier.padding(start = startInset + 16.dp, end = endInset + 16.dp, top = topPadding, bottom = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(
                    listOf(
                        SummaryCardData("Steps", healthData.value?.stepCount?.toString() ?: "0", Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF4CAF50)),
                        SummaryCardData("Sleep (min)", healthData.value?.sleepDurationMinutes?.toString() ?: "0", Icons.Filled.Hotel, Color(0xFF2196F3)),
                        SummaryCardData("Exercise (min)", healthData.value?.exerciseDurationMinutes?.toString() ?: "0", Icons.Filled.FitnessCenter, Color(0xFFFF9800)),
                        SummaryCardData("Distance (m)", healthData.value?.distanceMeters?.toString() ?: "0", Icons.Filled.Place, Color(0xFF9C27B0))
                    )
                ) { card ->
                    SummaryCard(card)
                }
            }
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
        backgroundColor = data.color,
        modifier = Modifier
            .size(width = 140.dp, height = 100.dp),
        elevation = 6.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material.Icon(data.icon, contentDescription = data.title, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(data.value, color = Color.White, style = MaterialTheme.typography.h6)
            Text(data.title, color = Color.White, fontSize = 14.sp)
        }
    }
}