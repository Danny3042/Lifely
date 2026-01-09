package sub_pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import utils.HealthData
import utils.HealthKitService
import utils.InsightsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import utils.isAndroid

const val REFLECTION_PAGE_ROUTE = "meditation_reflection"

@Composable
fun ReflectionPage(
    healthKitService: HealthKitService,
    onBack: () -> Unit = {}
) {
    var data by remember { mutableStateOf<HealthData?>(null) }
    LaunchedEffect(Unit) {
        // collect once; it's fine if it errors (no Health permissions)
        try {
            healthKitService.readData().collect { hd -> data = hd }
        } catch (_: Throwable) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Reflection", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Great job — your session is complete.\nTake a moment to reflect on how that felt.")
        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Session Insights", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                val hd = data
                Text("Calories: ${hd?.calories ?: "—"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Exercise minutes: ${hd?.exerciseDurationMinutes ?: "—"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Steps (24h): ${hd?.stepCount ?: "—"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Distance (m): ${hd?.distanceMeters ?: "—"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sleep (min): ${hd?.sleepDurationMinutes ?: "—"}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Reflection prompts:")
        Spacer(modifier = Modifier.height(8.dp))
        Text("- What did you notice about your breath?")
        Spacer(modifier = Modifier.height(6.dp))
        Text("- How do you feel now compared to before the session?")
        Spacer(modifier = Modifier.height(6.dp))
        Text("- Any intentions for the rest of your day?")

        Spacer(modifier = Modifier.height(24.dp))
        // Back to Home button
        Button(onClick = { onBack() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back to Home")
        }

        Spacer(modifier = Modifier.height(12.dp))
        // Test button: log a sample session so we can verify Charts update
        val insightsViewModel: InsightsViewModel = if (isAndroid()) viewModel() else InsightsViewModel()
        Button(onClick = {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val time = "${now.hour.toString().padStart(2,'0')}:${now.minute.toString().padStart(2,'0')}"
            val session = utils.Session(time = time, duration = 5)
            insightsViewModel.addSession(session)
            println("ReflectionPage: test session added: $session")
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Log test session (5m)")
        }

    }
}
