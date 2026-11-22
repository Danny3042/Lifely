package sub_pages

import components.ui.bottomBarOverlap
import components.ui.topBarOverlap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import components.ui.PlatformAlertDialog
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import components.ui.ContentWidth
import components.ui.ListRowCard
import components.ui.PagePadding
import components.ui.centeredMaxWidth
import utils.HabitRepository
import utils.isAndroid

const val CompletedHabitsPageRoute = "completed_habits"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedHabitsPage(navController: NavHostController) {
    // Newest first. Reading the SnapshotStateList here keeps the page live as habits are ticked off.
    val completed = HabitRepository.completedHabits.reversed()
    var confirmClearAll by remember { mutableStateOf(false) }
    if (confirmClearAll) {
        PlatformAlertDialog(
            title = "Clear completed habits?",
            message = "This removes all ${completed.size} from your completed list. It can't be undone.",
            confirmText = "Clear all",
            onConfirm = {
                HabitRepository.clearCompletedHabits()
                confirmClearAll = false
            },
            onDismiss = { confirmClearAll = false },
            destructive = true,
        )
    }

    // The Surface gives the page its background and, just as importantly, the right text colour.
    // Without it the list was drawn in the default black, invisible in dark mode.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isAndroid()) {
                TopAppBar(
                    title = { Text("Completed habits") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }

            LazyColumn(
                modifier = Modifier.centeredMaxWidth(ContentWidth.Reading).fillMaxSize(),
                contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 16.dp + topBarOverlap(), bottom = 32.dp + bottomBarOverlap()),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The title is in the navigation bar
                        Spacer(Modifier.weight(1f))
                        if (completed.isNotEmpty()) {
                            TextButton(
                                onClick = { confirmClearAll = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text("Clear all") }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (completed.size) {
                            0 -> "Habits you tick off will collect here."
                            1 -> "1 habit ticked off. A good start."
                            else -> "${completed.size} habits ticked off. Keep it going."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                }

                if (completed.isEmpty()) {
                    item {
                        ListRowCard(
                            title = "Nothing here yet",
                            subtitle = "Tick a habit on the Habits tab when you've done it and it'll move here.",
                            icon = Icons.Outlined.EmojiEvents,
                            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                } else {
                    items(completed, key = { it }) { habit ->
                        ListRowCard(
                            title = habit,
                            subtitle = "Done",
                            icon = Icons.Outlined.CheckCircle,
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            trailing = {
                                // Ticked by mistake? Put it back on the Habits list.
                                IconButton(onClick = { HabitRepository.restoreHabit(habit) }) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.Undo,
                                        contentDescription = "Move $habit back to your habits",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { HabitRepository.deleteCompletedHabit(habit) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete $habit",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            modifier = Modifier.padding(bottom = 8.dp).animateItem(),
                        )
                    }
                }
            }
        }
    }
}
