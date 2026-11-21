package components
import components.ui.ExpressiveDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.input.KeyboardType


import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*

import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import keyboardUtil.hideKeyboard
import keyboardUtil.onDoneHideKeyboardAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun TimeEditDialog(
    initialMinutes: Int,
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int, seconds: Int) -> Unit
) {
    var minutesText by remember { mutableStateOf(initialMinutes.toString()) }
    var secondsText by remember { mutableStateOf(initialSeconds.toString()) }

    val minutes = minutesText.toIntOrNull() ?: 0
    val seconds = secondsText.toIntOrNull() ?: 0
    val valid = seconds in 0..59 && (minutes > 0 || seconds > 0)

    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = "Edit timer",
        icon = Icons.Outlined.Timer,
        supportingText = "How long should this session last?",
        confirmText = "Save",
        confirmEnabled = valid,
        onConfirm = {
            hideKeyboard()
            onConfirm(minutes, seconds)
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Minutes") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = onDoneHideKeyboardAction(onDone = { })
            )
            OutlinedTextField(
                value = secondsText,
                onValueChange = { secondsText = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Seconds") },
                singleLine = true,
                isError = seconds > 59,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = onDoneHideKeyboardAction(onDone = { })
            )
        }
    }
}