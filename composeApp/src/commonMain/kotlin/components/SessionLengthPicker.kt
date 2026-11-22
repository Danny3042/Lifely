package components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val MAX_MINUTES = 120

/**
 * Any session length: tap − / + for a minute at a time, or drag for big changes. (The common
 * lengths are one tap on the meditation page itself.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionLengthPicker(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (totalMinutes: Int) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(initialMinutes.coerceIn(1, MAX_MINUTES)) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Session length", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilledTonalIconButton(
                        onClick = { minutes = (minutes - 1).coerceAtLeast(1) },
                        enabled = minutes > 1,
                        modifier = Modifier.size(56.dp),
                    ) { Icon(Icons.Filled.Remove, contentDescription = "One minute less") }

                    Text(
                        "$minutes min",
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "$minutes minutes" },
                    )

                    FilledTonalIconButton(
                        onClick = { minutes = (minutes + 1).coerceAtMost(MAX_MINUTES) },
                        enabled = minutes < MAX_MINUTES,
                        modifier = Modifier.size(56.dp),
                    ) { Icon(Icons.Filled.Add, contentDescription = "One minute more") }
                }

                Spacer(Modifier.height(16.dp))
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.roundToInt().coerceIn(1, MAX_MINUTES) },
                    valueRange = 1f..MAX_MINUTES.toFloat(),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("1 min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("2 h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(minutes) }) { Text("Set") }
                }
            }
        }
    }
}
