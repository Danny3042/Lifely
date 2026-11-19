package components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import keyboardUtil.hideKeyboard
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

data class CalendarUiModel(
    val selectedDate: Date,
    val visibleDates: List<Date>,
    val events: List<Event> = listOf(),
    val ratings: Map<LocalDate, Rating> = emptyMap()
) {
    val startDate: Date = visibleDates.first()
    val endDate: Date = visibleDates.last()

    data class Date(
        val date: LocalDate,
        val isSelected: Boolean,
        val isToday: Boolean
    ) {
        val day: String = date.dayOfWeek.name.take(3)
    }
}

data class Event(
    val date: LocalDate,
    val title: String
)

data class Rating(
    val sleepRating: Int,
    val moodRating: Int
)

@Serializable
data class MoodRating(
    val rating: Float
)

class CalendarDataSource {
    val today: LocalDate
        get() = Clock.System.todayIn(TimeZone.currentSystemDefault())

    var events: MutableList<Event> = mutableListOf()

    fun getData(startDate: LocalDate = today, lastSelectedDate: LocalDate): CalendarUiModel {
        val firstDayOfWeek = startDate
        val endDayOfWeek = firstDayOfWeek.plus(7, DateTimeUnit.DAY)
        val visibleDates = getDatesBetween(firstDayOfWeek, endDayOfWeek)
        return toUiModel(visibleDates, lastSelectedDate)
    }
    fun saveRatings(
        date: LocalDate,
        sleepRating: Int,
        moodRating: Int
    ): CalendarUiModel {
        val newRatings = mutableMapOf<LocalDate, Rating>()
        newRatings[date] = Rating(sleepRating, moodRating)
        return CalendarUiModel(
            selectedDate = toItemUiModel(date, true),
            visibleDates = getDatesBetween(date.minus(3, DateTimeUnit.DAY), date.plus(3, DateTimeUnit.DAY))
                .map { toItemUiModel(it, it == date) },
            ratings = newRatings
        )
    }

    fun loadRatings(date: LocalDate): Rating? {
        return events.find { it.date == date }?.let {
            Rating(
                sleepRating = it.title.split(",")[0].toInt(),
                moodRating = it.title.split(",")[1].toInt()
            )
        }
    }

    fun addEvent(event: Event) {
        events.add(event)
    }

    private fun getDatesBetween(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
        return generateSequence(startDate) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= endDate }
            .toList()
    }

    private fun toUiModel(dateList: List<LocalDate>, lastSelectedDate: LocalDate): CalendarUiModel {
        return CalendarUiModel(
            selectedDate = toItemUiModel(lastSelectedDate, true),
            visibleDates = dateList.map { toItemUiModel(it, it == lastSelectedDate) },
        )
    }

    private fun toItemUiModel(date: LocalDate, isSelected: Boolean) = CalendarUiModel.Date(
        isSelected = isSelected,
        isToday = date == today,
        date = date,
    )
}

@Composable
fun Header(
    data: CalendarUiModel,
    onPrevClickListener: (LocalDate) -> Unit,
    onNextClickListener: (LocalDate) -> Unit,
    onTodayClickListener: () -> Unit,
) {
    Row {
        Text(
            text = if (data.selectedDate.isToday) {
                "Today"
            } else {
                data.selectedDate.date.toString()
            },
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
        )
        IconButton(onClick = { onTodayClickListener() }) {
            Icon(
                imageVector = Icons.Filled.Today,
                contentDescription = "Today"
            )
        }
        IconButton(onClick = { onPrevClickListener(data.startDate.date) }) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Previous"
            )
        }
        IconButton(onClick = { onNextClickListener(data.endDate.date) }) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Next"
            )
        }
    }
}

@Composable
fun EventInputDialog(
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDismissRequest() },
        title = { Text("New Event") },
        text = {
            TextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Event Title") }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    // hide keyboard when confirming
                    hideKeyboard()
                    onConfirm(input)
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(
                onClick = { onDismissRequest() }
            ) {
                Text("Cancel")
            }
        }
    )
}

// this card updates the rating for mood and sleep
@Composable
fun RatingCard(rating: Rating, onDelete: () -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }

    Card(
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    offsetX += dragAmount
                    if (offsetX > 300) { // Adjust threshold as needed
                        onDelete()
                    }
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Sleep Rating: ${rating.sleepRating}", style = MaterialTheme.typography.titleMedium)
            Text(text = "Mood Rating: ${rating.moodRating}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// Updates the content to remove the rating from the data source permanently
@Composable
fun Content(
    calendarUiModel: CalendarUiModel,
    selectedDate: LocalDate,
    onDateClickListener: (CalendarUiModel.Date) -> Unit,
    onUpdateCalendarUiModel: (CalendarUiModel) -> Unit
) {
    Column {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp)
        ) {
            items(items = calendarUiModel.visibleDates) { date ->
                ContentItem(
                    date = date,
                    isSelected = date.date == selectedDate,
                    onClickListener = onDateClickListener
                )
            }
        }
        calendarUiModel.ratings[calendarUiModel.selectedDate.date]?.let { rating ->
            RatingCard(
                rating = rating,
                onDelete = {
                    val newRatings = calendarUiModel.ratings.toMutableMap()
                    newRatings.remove(calendarUiModel.selectedDate.date)
                    onUpdateCalendarUiModel(calendarUiModel.copy(ratings = newRatings))
                }
            )
        }
    }
}

@Composable
fun RatingsDialog(
    sleepRating: Int,
    moodRating: Int,
    onSleepRatingChange: (Int) -> Unit,
    onMoodRatingChange: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Ratings") },
        text = {
            Column {
                Text("Sleep Rating")
                Slider(value = sleepRating.toFloat(), onValueChange = { onSleepRatingChange(it.toInt()) }, valueRange = 0f..10f)
                Text("Mood Rating")
                Slider(value = moodRating.toFloat(), onValueChange = { onMoodRatingChange(it.toInt()) }, valueRange = 0f..10f)
            }
        },
        confirmButton = {
            Button(onClick = onSubmit) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AdviceTypeDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Health Advice Type") },
        text = {
            Column {
                listOf("General", "Sleep", "Nutrition", "Stress").forEach { type ->
                    Button(onClick = { onSelect(type) }, modifier = Modifier.padding(4.dp)) {
                        Text(type)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
fun ContentItem(
    date: CalendarUiModel.Date,
    isSelected: Boolean,
    onClickListener: (CalendarUiModel.Date) -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .clickable { onClickListener(date) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            }
        )
    ) {
        Column(
            modifier = Modifier
                .width(50.dp)
                .height(50.dp)
                .padding(4.dp)
        ) {
            Text(
                text = date.day,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = date.date.dayOfMonth.toString(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun InfoCard(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Navigation Instructions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Text(
                text = "1. Swipe left or right on a rating card to delete it.\n" +
                        "2. Click on a date to view or add ratings.\n" +
                        "3. Click on the floating action button to add a new rating.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}