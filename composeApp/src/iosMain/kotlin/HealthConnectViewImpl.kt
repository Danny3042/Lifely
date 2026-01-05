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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HealthConnectViewImpl(healthKitService: HealthKitService) {
    val healthData = healthKitService.readData().collectAsState(initial = null)

    LaunchedEffect(Unit) {
        healthKitService.requestAuthorization()
    }

    MaterialTheme {
        Column(modifier = Modifier.padding(24.dp)) {
            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(
                    listOf(
                        SummaryCardData("Steps", healthData.value?.stepCount?.toString() ?: "0", Icons.Filled.DirectionsWalk, Color(0xFF4CAF50)),
                        SummaryCardData("Sleep (min)", healthData.value?.sleepDurationMinutes?.toString() ?: "0", Icons.Filled.Hotel, Color(0xFF2196F3)),
                        SummaryCardData("Exercise (min)", healthData.value?.exerciseDurationMinutes?.toString() ?: "0", Icons.Filled.FitnessCenter, Color(0xFFFF9800)),
                        SummaryCardData("Distance (m)", healthData.value?.distanceMeters?.toString() ?: "0", Icons.Filled.Place, Color(0xFF9C27B0))
                    )
                ) { card ->
                    SummaryCard(card)
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Insights", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("You slept ${healthData.value?.sleepDurationMinutes ?: 0} minutes last night! Keep up the good work.", fontSize = 16.sp)
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