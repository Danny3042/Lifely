package pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import components.MoodRating
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import utils.fetchSuggestionsFromGemini
import utils.sendMoodRatingToGeminiChat
import kotlin.math.roundToInt

@Serializable
data class Rating(
    val sleepRating: Float,
    val moodRating: Float
)

@Composable
fun ExpandableCard(title: String, onSave: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(
                onClick = { expanded = !expanded }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Expand or collapse content",
                        modifier = Modifier.rotate(if (expanded) 180f else 0f)
                    )
                }
            }
            if (expanded) {
                SliderExample(sliderValue, onSave = { value ->
                    sliderValue = value.toString()
                    onSave(value)
                    // Send ratings to Gemini and fetch suggestions
                    val rating = MoodRating(sliderValue.toFloat())
                    scope.launch {
                        val success = sendMoodRatingToGeminiChat(rating)
                        if (success) {
                            val suggestions = fetchSuggestionsFromGemini()
                            // logging removed
                        }
                    }
                })
            } else {
                Text(text = sliderValue)
            }

        }
       
    }
}

@Composable
fun DescriptionCard() {
    var showDescription by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { showDescription = !showDescription },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Expandable Cards Info", style = MaterialTheme.typography.titleMedium)
            if (showDescription) {
                Text(text = "This app uses expandable cards to rate your sleep and mood. " +
                        "Slide to select a value and hit save to update your ratings.")
            }
        }
    }
}

@Composable
fun SuggestionsCard(suggestions: List<String>, isLoading: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Suggestions", style = MaterialTheme.typography.titleMedium)
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                suggestions.forEach { suggestion ->
                    Text(text = suggestion)
                }
            }
        }
    }
}


@Composable
fun SliderExample(currentValue: String, onSave: (Float) -> Unit) {
    val sliderLabels = (1..10).map { it.toString() } // Labels from 1 to 10
    var sliderPosition by remember { mutableStateOf(
        if (sliderLabels.contains(currentValue)) sliderLabels.indexOf(currentValue).toFloat() else 0f
    )}
    Column {
        Text("1 - Worst, 10 - Best", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = sliderPosition,
            onValueChange = {
                sliderPosition = it
                onSave(sliderLabels[sliderPosition.roundToInt()].toFloat()) // Convert position to value and pass it
            },
            valueRange = 0f..(sliderLabels.size - 1).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.secondary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            ),
            steps = 9 // 10 - 1 = 9 steps for values 1 to 10
        )
        Text(text = sliderLabels[sliderPosition.roundToInt()])
    }
}

@Composable
fun MyButton(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(60.dp)
                .verticalScroll(scrollState)
        ) {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)

            ) {
                Text("Meditation", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}


@Composable
fun AlertDialogExample(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
) {
    AlertDialog(
        onDismissRequest = { onDismissRequest() },
        title = { Text(text = dialogTitle) },
        text = { Text(text = dialogText) },
        confirmButton = {
            Button(
                onClick = {
                    // logging removed
                    onConfirmation()
                    onDismissRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Confirm", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onDismissRequest() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Dismiss")
            }
        }
    )
}
