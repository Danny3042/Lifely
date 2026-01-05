package pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import utils.InsightsViewModel
import utils.isAndroid

const val InsightsPageScreen = "Insights"

fun getStartOfWeek(today: LocalDate): LocalDate {
    val daysFromMonday = (today.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
    return today.minus(daysFromMonday, DateTimeUnit.DAY)
}

@Composable
fun InsightsPage(
    modifier: Modifier = Modifier,
    insightsViewModel: InsightsViewModel? = null
) {
    val insightsViewModel: InsightsViewModel = insightsViewModel ?: if (isAndroid()) viewModel() else InsightsViewModel()

    val sessionsPerDay by insightsViewModel.sessionsPerDay
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val startOfWeek = getStartOfWeek(today)
    val weekDates = List(7) { startOfWeek.plus(it, DateTimeUnit.DAY) }
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val currentDayIndex = (today.dayOfWeek.ordinal + 7 - DayOfWeek.MONDAY.ordinal) % 7
    val scrollState = rememberScrollState()
    var showCardsAnim by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showCardsAnim = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Week Header
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                daysOfWeek.forEachIndexed { index, day ->
                    val date = weekDates[index]
                    val sessions = sessionsPerDay.getOrNull(index) ?: emptyList()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$day\n${date.dayOfMonth} ${date.month.name.take(3)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (index == currentDayIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = when {
                                index == currentDayIndex -> MaterialTheme.colorScheme.secondary
                                sessions.isNotEmpty() -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = sessions.size.toString(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            // Animated Session Cards for each day
            daysOfWeek.forEachIndexed { index, day ->
                val date = weekDates[index]
                val sessions = sessionsPerDay.getOrNull(index) ?: emptyList()
                AnimatedVisibility(
                    visible = showCardsAnim,
                    enter = slideInVertically(initialOffsetY = { 40 * (index + 1) }) + fadeIn()
                ) {
                    if (sessions.isNotEmpty()) {
                        Text(
                            "$day, ${date.dayOfMonth} ${date.month.name.take(3)}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                        sessions.forEach { session ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Time: ${session.time}", style = MaterialTheme.typography.bodyLarge)
                                    Text("${session.duration} min", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}