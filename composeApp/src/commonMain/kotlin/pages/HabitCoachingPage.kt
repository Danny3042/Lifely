package pages

import components.ui.bottomBarOverlap
import components.ui.topBarOverlap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import components.ui.CoachPromptCard
import components.ui.RichCoachTipCard
import service.CoachTip
import service.CoachTopic
import service.generateCoachTip
import components.ui.ContentWidth
import components.ui.ListRowCard
import components.ui.PagePadding
import components.ui.QuoteCard
import components.ui.SectionHeader
import components.ui.centeredMaxWidth
import keyboardUtil.hideKeyboard
import kotlinx.coroutines.launch
import service.GenerativeAiService
import sub_pages.CompletedHabitsPageRoute
import utils.HabitRepository

@Composable
fun HabitCoachingPage(navcontroller: NavController) {
    val scope = rememberCoroutineScope()
    var aiTip by remember { mutableStateOf<CoachTip?>(null) }
    // The habit the current tip is about, for "New tip"
    var tipSubject by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var userHabit by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val trackedHabits = HabitRepository.habits

    fun loadTip(subject: String) {
        if (isLoading) return
        scope.launch {
            isLoading = true
            error = null
            try {
                aiTip = generateCoachTip(subject, CoachTopic.Habit)
            } catch (e: Exception) {
                error = service.friendlyGeminiError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun generateTipAndAddHabit() {
        if (userHabit.isBlank() || isLoading) return
        focusManager.clearFocus()
        runCatching { hideKeyboard() }
        val habit = userHabit.trim()
        // Save the habit straight away so it isn't lost if the tip request fails
        HabitRepository.addHabit(habit)
        userHabit = ""
        tipSubject = habit
        loadTip(habit)
    }

    LazyColumn(
        modifier = Modifier.centeredMaxWidth(ContentWidth.Reading).fillMaxSize(),
        contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 16.dp + topBarOverlap(), bottom = 32.dp + bottomBarOverlap()),
    ) {
        item {
            CoachPromptCard(
                title = "Build one habit at a time",
                subtitle = "Tell your coach a habit you want to start and get a practical tip to make it stick.",
                icon = Icons.Outlined.TrackChanges,
                value = userHabit,
                onValueChange = { userHabit = it },
                placeholder = "e.g. Drink more water",
                buttonLabel = "Get tip & add habit",
                isLoading = isLoading,
                onSubmit = ::generateTipAndAddHabit,
            )
        }

        item {
            SectionHeader("Coach's tip")
            val subject = tipSubject
            RichCoachTipCard(
                tip = aiTip,
                isLoading = isLoading,
                error = error,
                emptyText = "Add a habit above and your coach will suggest how to start. Small and consistent beats big and occasional.",
                // The habit was saved when it was typed in; the button confirms it (or re-adds it if ticked off)
                isInHabits = subject != null && subject in HabitRepository.activeHabits,
                onAddToHabits = subject?.let { { HabitRepository.addHabit(it) } },
                onNewTip = subject?.let { { loadTip(it) } },
            )
        }

        item {
            SectionHeader(if (trackedHabits.isEmpty()) "Your habits" else "Your habits · ${trackedHabits.size}")
        }
        if (trackedHabits.isEmpty()) {
            item {
                ListRowCard(
                    title = "No habits yet",
                    subtitle = "Habits you add will appear here. Tick one off when you've done it.",
                    icon = Icons.Outlined.Checklist,
                )
            }
        } else {
            items(trackedHabits.toList(), key = { it }) { habit ->
                ListRowCard(
                    title = habit,
                    subtitle = "Tick when done today",
                    onClick = { HabitRepository.completeHabit(habit) },
                    leading = {
                        Checkbox(checked = false, onCheckedChange = { HabitRepository.completeHabit(habit) })
                    },
                    modifier = Modifier.padding(bottom = 8.dp).animateItem(),
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            ListRowCard(
                title = "Completed habits",
                subtitle = when (val done = HabitRepository.completedHabits.size) {
                    0 -> "Habits you tick off go here"
                    1 -> "1 ticked off · see it here"
                    else -> "$done ticked off · see them here"
                },
                icon = Icons.Outlined.EmojiEvents,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { navcontroller.navigate(CompletedHabitsPageRoute) },
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            QuoteCard("Success is the sum of small efforts, repeated day in and day out.")
        }
    }
}
