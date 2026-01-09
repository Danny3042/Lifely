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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
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
        else -> "Good evening"
    }

    // Use a high-contrast color on Android to ensure visibility; otherwise use theme onSurface
    val textColor = if (isAndroid()) Color(0xFF000000) else MaterialTheme.colorScheme.onSurface

    Text(
        text = greeting,
        color = textColor,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = modifier.fillMaxWidth()
    )
}
