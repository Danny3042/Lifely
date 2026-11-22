package sub_pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import components.moodColor
import components.ui.ContentWidth
import components.ui.LocalWindowSize
import components.ui.PagePadding
import components.ui.SectionHeader
import components.ui.bottomBarOverlap
import components.ui.centeredMaxWidth
import components.ui.topBarOverlap
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import utils.MoodCheckIn
import utils.MoodCheckInStore
import utils.MoodScale
import utils.isAndroid

const val CHECK_IN_CALENDAR_ROUTE = "check_in_calendar"

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val weekdayInitials = listOf("M", "T", "W", "T", "F", "S", "S")
private val weekdayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/**
 * Check-ins on a calendar: each day is coloured by how you felt on average, so a month shows at a
 * glance. Pick a day to see its check-ins. Side by side on wide screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInCalendarPage(onBack: () -> Unit = {}) {
    LaunchedEffect(Unit) { runCatching { MoodCheckInStore.load() } }

    val zone = TimeZone.currentSystemDefault()
    val today = remember { Clock.System.now().toLocalDateTime(zone).date }
    var month by remember { mutableStateOf(LocalDate(today.year, today.month, 1)) }
    var selected by remember { mutableStateOf(today) }

    // Check-ins grouped by day
    val byDay: Map<LocalDate, List<MoodCheckIn>> = MoodCheckInStore.checkIns.groupBy {
        Instant.fromEpochMilliseconds(it.atEpochMillis).toLocalDateTime(zone).date
    }

    val twoPane = LocalWindowSize.current.useTwoPane

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            if (isAndroid()) {
                TopAppBar(
                    title = { Text("Check-in calendar") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }

            val calendar: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier) {
                    MonthSummary(month = month, byDay = byDay)
                    Spacer(Modifier.height(16.dp))
                    MonthCalendar(
                        month = month,
                        today = today,
                        selected = selected,
                        byDay = byDay,
                        onPrevious = { month = month.minus(1, DateTimeUnit.MONTH) },
                        onNext = { month = month.plus(1, DateTimeUnit.MONTH) },
                        onToday = { month = LocalDate(today.year, today.month, 1); selected = today },
                        onSelect = { selected = it },
                    )
                    Spacer(Modifier.height(12.dp))
                    MoodLegend()
                }
            }
            val details: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier) {
                    SectionHeader(dayTitle(selected, today))
                    DayCheckIns(byDay[selected].orEmpty())
                }
            }

            if (twoPane) {
                Row(
                    modifier = Modifier.centeredMaxWidth(ContentWidth.Dashboard).fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    calendar(
                        Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(top = topBarOverlap(), bottom = bottomBarOverlap())
                            .padding(start = PagePadding, top = 16.dp, bottom = 16.dp)
                    )
                    details(
                        Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(top = topBarOverlap(), bottom = bottomBarOverlap())
                            .padding(end = PagePadding, top = 16.dp, bottom = 16.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.centeredMaxWidth(ContentWidth.Reading)
                        .verticalScroll(rememberScrollState())
                        .padding(top = topBarOverlap(), bottom = bottomBarOverlap())
                        .padding(horizontal = PagePadding, vertical = 16.dp),
                ) {
                    calendar(Modifier.fillMaxWidth())
                    details(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** How many check-ins this month, and the overall feeling. */
@Composable
private fun MonthSummary(month: LocalDate, byDay: Map<LocalDate, List<MoodCheckIn>>) {
    val inMonth = byDay.filterKeys { it.year == month.year && it.month == month.month }.values.flatten()
    val text = if (inMonth.isEmpty()) {
        "No check-ins this month yet."
    } else {
        val average = inMonth.map { it.valence }.average()
        val days = byDay.keys.count { it.year == month.year && it.month == month.month }
        "${inMonth.size} check-in${if (inMonth.size == 1) "" else "s"} on $days day${if (days == 1) "" else "s"} · mostly ${MoodScale.label(average.roundToInt()).lowercase()}"
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MonthCalendar(
    month: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    byDay: Map<LocalDate, List<MoodCheckIn>>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${monthNames[month.month.ordinal]} ${month.year}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                val onCurrentMonth = month.year == today.year && month.month == today.month
                if (!onCurrentMonth) TextButton(onClick = onToday) { Text("Today") }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                IconButton(onClick = onNext, enabled = !onCurrentMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                weekdayInitials.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(260)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(260)))
                        .togetherWith(slideOutHorizontally(tween(200)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200)))
                },
                label = "month",
            ) { shown ->
                val first = shown
                val nextMonth = first.plus(1, DateTimeUnit.MONTH)
                val daysInMonth = (nextMonth.toEpochDays() - first.toEpochDays()).toInt()
                val leading = first.dayOfWeek.ordinal // Monday first
                val cells = leading + daysInMonth
                val rows = (cells + 6) / 7
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until rows) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0 until 7) {
                                val dayNumber = row * 7 + col - leading + 1
                                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                    if (dayNumber in 1..daysInMonth) {
                                        val date = LocalDate(first.year, first.month, dayNumber)
                                        DayCell(
                                            day = dayNumber,
                                            spokenDate = "${weekdayNames[date.dayOfWeek.ordinal]} $dayNumber ${monthNames[first.month.ordinal]}",
                                            checkIns = byDay[date].orEmpty(),
                                            isToday = date == today,
                                            isSelected = date == selected,
                                            isFuture = date > today,
                                            onClick = { onSelect(date) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    spokenDate: String,
    checkIns: List<MoodCheckIn>,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
) {
    val average = checkIns.takeIf { it.isNotEmpty() }?.map { it.valence }?.average()?.toFloat()
    val fill = average?.let { moodColor(it) }
    val ring = when {
        isSelected -> MaterialTheme.colorScheme.onSurface
        isToday -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill.copy(alpha = 0.85f)) else Modifier)
            .border(if (isSelected) 2.dp else 1.5.dp, ring, CircleShape)
            .clickable(enabled = !isFuture, onClick = onClick)
            // Read as "Sunday 22 November, Pleasant, 2 check-ins" rather than just "22"
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(spokenDate)
                    if (isToday) append(", today")
                    if (average != null) {
                        append(", mostly ").append(MoodScale.label(kotlin.math.round(average).toInt()).lowercase())
                        append(", ").append(checkIns.size).append(if (checkIns.size == 1) " check-in" else " check-ins")
                    } else if (!isFuture) {
                        append(", no check-ins")
                    }
                }
                selected = isSelected
                role = Role.Button
                if (!isFuture) onClick { onClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                fill != null -> Color.White
                isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        // More than one check-in that day
        if (checkIns.size > 1) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(4.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
            )
        }
    }
}

/** The colour scale, from very unpleasant to very pleasant. */
@Composable
private fun MoodLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text("Unpleasant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (v in -3..3) {
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(moodColor(v.toFloat())))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("Pleasant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayCheckIns(checkIns: List<MoodCheckIn>) {
    if (checkIns.isEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "No check-ins on this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
        return
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Earliest first, through the day
            checkIns.sortedBy { it.atEpochMillis }.forEachIndexed { index, checkIn ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier.padding(top = 2.dp).size(24.dp).background(moodColor(checkIn.valence.toFloat()), CircleShape)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(MoodScale.label(checkIn.valence), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                timeOf(checkIn.atEpochMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (checkIn.feelings.isNotEmpty()) {
                            Text(
                                checkIn.feelings.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (checkIn.impacts.isNotEmpty()) {
                            Text(
                                "Affected by " + checkIn.impacts.joinToString(", ").lowercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun timeOf(epochMillis: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

private fun dayTitle(date: LocalDate, today: LocalDate): String {
    val weekday = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[date.dayOfWeek.ordinal]
    val label = "$weekday ${date.day} ${monthNames[date.month.ordinal]}"
    return when (date) {
        today -> "Today · $label"
        today.minus(1, DateTimeUnit.DAY) -> "Yesterday · $label"
        else -> label
    }
}
