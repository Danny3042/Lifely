package pages

import TimerViewModel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.tooling.preview.Preview
import keyboardUtil.hideKeyboard
import keyboardUtil.onDoneHideKeyboardAction
import utils.isAndroid
import androidx.lifecycle.viewmodel.compose.viewModel

const val Timer = "timer"
@Composable
fun CircularTimer(
    timerValue: Long,
    totalTime: Long,
    modifier: Modifier = Modifier
) {
    val progress = timerValue.toFloat() / totalTime.toFloat()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(200.dp)) {
        // Draw the background circle
        drawCircle(
            color = onSurfaceColor,
            radius = size.minDimension / 2,
            center = center,
            style = Stroke(width = 8.dp.toPx())
        )
        // Draw the progress arc
        drawArc(
            color = primaryColor,
            startAngle = -90f,
            sweepAngle = 360 * progress,
            useCenter = false,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, size.height),
            style = Stroke(width = 8.dp.toPx())
        )
    }
    Text(
        text = timerValue.formatTime(),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreenContent(onBack: () -> Unit ) {
    val timerViewModel = viewModel { TimerViewModel() }
    val timerValue by timerViewModel.timer.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (isAndroid()) {
                TopAppBar(
                    title = { Text("Timer View") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { // Handle navigation icon press
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) {
        TimerScreen(
            timerValue = timerValue,
            onStartClick = { timerViewModel.startTimer() },
            onPauseClick = { timerViewModel.pauseTimer() },
            onStopClick = { timerViewModel.stopTimer() },
            onSetClick = { showDialog = true }
        )
        SetTimerDialog(
            showDialog = showDialog,
            onConfirm =  { time -> timerViewModel.setTimer(time) },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun TimerScreen(
    timerValue: Long,
    totalTime: Long = 60, // Default total time (1 minute)
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onSetClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularTimer(timerValue = timerValue, totalTime = totalTime)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            OutlinedButton(onClick = onStartClick) {
                Text(text = "Start")
            }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(onClick = onPauseClick) {
                Text(text = "Pause")
            }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(onClick = onStopClick) {
                Text(text = "Stop")
            }
        }
        Button(onClick = onSetClick) {
            Text(text = "Set Timer")
        }
    }
}

private fun Long.formatTime(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return "$hours:$minutes:$seconds"
}

@Composable
fun SetTimerDialog(
    showDialog: Boolean,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        var input by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Set Timer") },
            text = {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Enter time in seconds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = onDoneHideKeyboardAction(onDone = {})
                )
            },
            confirmButton = {
                Button(onClick = {
                    hideKeyboard()
                    onConfirm(input.toLongOrNull() ?: 0L)
                    onDismiss()
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview
@Composable
fun TimeViewPreview() {
    val navController = rememberNavController()
    TimerScreenContent(onBack = { navController.popBackStack() })
}