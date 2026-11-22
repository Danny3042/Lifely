package utils

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun RealTimeGreeting(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Clock.System.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Clock.System.now()
            delay(60_000L)
        }
    }

    val hour = now.toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        // Late at night and the small hours
        else -> "Good night"
    }

    Text(
        text = greeting,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.displaySmall,
        modifier = modifier.fillMaxWidth()
    )
}
