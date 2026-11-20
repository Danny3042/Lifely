package pages

import components.ui.ContentWidth
import components.ui.centeredMaxWidth
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
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import utils.InsightsViewModel
import components.ui.ListRowCard
import components.ui.PagePadding
import components.ui.SectionHeader
import components.ui.StatTile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa

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
    val insightsViewModel: InsightsViewModel = insightsViewModel ?: viewModel { InsightsViewModel() }

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

    val weekMinutes = sessionsPerDay.sumOf { day -> day.sumOf { it.duration } }
    val weekSessions = sessionsPerDay.sumOf { it.size }
    val activeDays = sessionsPerDay.count { it.isNotEmpty() }

    Column(
        modifier = modifier
            .centeredMaxWidth(ContentWidth.Wide)
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = PagePadding, vertical = 16.dp),
    ) {
        Text("This week", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${weekDates.first().day} ${weekDates.first().month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} – " +
                "${weekDates.last().day} ${weekDates.last().month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        // Week strip: one column per day, sized to fit any phone width
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                daysOfWeek.forEachIndexed { index, day ->
                    val date = weekDates[index]
                    val count = sessionsPerDay.getOrNull(index)?.size ?: 0
                    val isToday = index == currentDayIndex
                    val (container, content) = when {
                        count > 0 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                        isToday -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            day.take(1),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = CircleShape, color = container, contentColor = content, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (count > 0) count.toString() else date.day.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Minutes",
                value = "$weekMinutes",
                icon = Icons.Filled.SelfImprovement,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Sessions",
                value = "$weekSessions",
                icon = Icons.Filled.CheckCircle,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Active days",
                value = "$activeDays/7",
                icon = Icons.Filled.CalendarMonth,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader("Sessions")

        if (weekSessions == 0) {
            ListRowCard(
                title = "No sessions yet this week",
                subtitle = "Complete a meditation and it will appear here.",
                icon = Icons.Filled.Spa,
            )
        }

        daysOfWeek.forEachIndexed { index, day ->
            val date = weekDates[index]
            val sessions = sessionsPerDay.getOrNull(index) ?: emptyList()
            AnimatedVisibility(
                visible = showCardsAnim && sessions.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { 40 * (index + 1) }) + fadeIn(),
            ) {
                Column {
                    Text(
                        "$day, ${date.day} ${date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    sessions.forEach { session ->
                        ListRowCard(
                            title = "${session.duration} min meditation",
                            subtitle = "Started at ${session.time}",
                            icon = Icons.Filled.SelfImprovement,
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}