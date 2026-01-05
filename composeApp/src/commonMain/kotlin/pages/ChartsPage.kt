package pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.lifecycle.viewmodel.compose.viewModel
import utils.SimpleLineChart
import utils.InsightsViewModel
import utils.isAndroid

const val ChartsPageScreen = "ChartsPage"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsPage(
    modifier: Modifier = Modifier,
    insightsViewModelParam: InsightsViewModel? = null,
    navController: NavHostController? = null
) {
    val insightsViewModel: InsightsViewModel = insightsViewModelParam ?: if (isAndroid()) viewModel() else InsightsViewModel()

    val sessionsPerDay by insightsViewModel.sessionsPerDay

    // compute totals per day for the last 7 days (index 0 = Monday in existing code)
    val totals = remember(sessionsPerDay) {
        List(7) { idx ->
            sessionsPerDay.getOrNull(idx)?.sumOf { it.duration }?.toFloat() ?: 0f
        }
    }

    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
            // On Android show a TopAppBar with a native back button when navController is provided
            if (isAndroid() && navController != null) {
                TopAppBar(
                    title = { Text("Key metrics", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            } else {
                Text("Key metrics", style = MaterialTheme.typography.titleLarge)
            }

            // Small metric cards in a horizontal row (like Fitbit)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                items(
                    listOf(
                        MetricModel("Meditation", totals.sum().toInt(), totals),
                        MetricModel("Sessions", totals.count { it > 0f }, totals),
                        MetricModel("Avg min", if (totals.count { it > 0f } > 0) (totals.sum() / totals.count { it > 0f }).toInt() else 0, totals)
                    )
                ) { item ->
                    MetricCard(model = item)
                }
            }

            // Larger detailed card for meditation minutes across the week
            Text("\nMeditation this week", style = MaterialTheme.typography.titleMedium)
            MetricDetailCard(title = "Meditation minutes", subtitle = "Last 7 days", values = totals)
        }
    }
}

// small data model for cards

data class MetricModel(val title: String, val value: Int, val values: List<Float>)

@Composable
fun MetricCard(model: MetricModel) {
    Card(
        modifier = Modifier
            .height(120.dp)
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.SpaceBetween) {
            Text(model.title, style = MaterialTheme.typography.bodyMedium)
            if (model.values.sum() == 0f) {
                Text("No data", style = MaterialTheme.typography.headlineSmall)
            } else {
                Text(text = model.value.toString(), style = MaterialTheme.typography.headlineSmall)
            }
            // tiny sparkline (non-animated)
            SimpleLineChart(values = model.values, modifier = Modifier.fillMaxWidth().height(48.dp), animateReveal = false, smooth = true)
        }
    }
}

@Composable
fun MetricDetailCard(title: String, subtitle: String, values: List<Float>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                Text(values.sum().toInt().toString(), style = MaterialTheme.typography.headlineSmall)
            }
            SimpleLineChart(values = values, modifier = Modifier.fillMaxWidth().height(160.dp), smooth = true)

            // day labels (Mon..Sun)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M","T","W","T","F","S","S").forEach { d ->
                    Text(d, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}