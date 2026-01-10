import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import components.SettingsListItem
import keyboardUtil.hideKeyboard
import keyboardUtil.onDoneHideKeyboardAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import service.GenerativeAiService
import sub_pages.HabitTrackerPage
import utils.HabitRepository
import androidx.navigation.NavController
import sub_pages.CompletedHabitsPageRoute
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager

@Composable
fun HabitTrackerPage(navcontroller: NavController) {
    val scope = rememberCoroutineScope()
    var aiTip by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var userHabit by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val trackedHabits = HabitRepository.habits

    fun generateTipAndAddHabit() {
        if (userHabit.isBlank()) return
        // perform network/habit work, callers should handle focus/keyboard
        scope.launch {
            isLoading = true
            error = null
            try {
                aiTip = GenerativeAiService.instance.getSuggestions(
                    listOf("Give me a practical succinct tip for building the habit: $userHabit")
                )
                HabitRepository.addHabit(userHabit)
                userHabit = ""
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current

        Icon(
            painter = rememberVectorPainter(Icons.Default.School),
            contentDescription = "Habit Coaching",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text("Habit Coaching", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Tip: Start small and be consistent. Focus on one habit at a time for better results.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = userHabit,
            onValueChange = { userHabit = it },
            label = { Text("What habit do you want to build?") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = onDoneHideKeyboardAction(onDone = {
                // submission after keyboard hide
                scope.launch { generateTipAndAddHabit() }
            }),
            trailingIcon = {
                IconButton(
                    onClick = {
                        // ensure keyboard hidden then submit
                        focusManager.clearFocus()
                        hideKeyboard()
                        scope.launch { generateTipAndAddHabit() }
                    },
                    enabled = userHabit.isNotBlank() && !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Habit"
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text("AI Coach", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                when {
                    isLoading -> Text("Loading AI tip...")
                    error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error)
                    aiTip != null -> Text(aiTip!!, style = MaterialTheme.typography.bodyMedium)
                    else -> Text("No tip available.")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                focusManager.clearFocus()
                hideKeyboard()
                scope.launch { generateTipAndAddHabit() }
            },
            enabled = userHabit.isNotBlank() && !isLoading
        ) {
            Text("Get AI Tip & Add Habit")
        }
        Spacer(modifier = Modifier.height(24.dp))

        HabitTrackerPage(habits = trackedHabits,
            onHabitCompleted = { habit -> HabitRepository.addCompletedHabit(habit) })
        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "\"Success is the sum of small efforts, repeated day in and day out.\"",
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
        SettingsListItem(
            title = "Completed Habits",
            onClick = { navcontroller.navigate(CompletedHabitsPageRoute) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Checklist,
                    contentDescription = "Completed Habits Icon"
                )
            }
        )
    }
}