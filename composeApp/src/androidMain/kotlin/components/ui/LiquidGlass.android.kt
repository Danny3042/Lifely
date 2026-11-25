package components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Material equivalents. Android's onboarding keeps its own Material layout and doesn't use these.

@Composable
actual fun GlassTextButton(title: String, onClick: () -> Unit, modifier: Modifier, prominent: Boolean) {
    if (prominent) Button(onClick = onClick, modifier = modifier) { Text(title) }
    else FilledTonalButton(onClick = onClick, modifier = modifier) { Text(title) }
}

@Composable
actual fun GlassPageIndicator(count: Int, current: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.height(8.dp).width(if (i == current) 24.dp else 8.dp)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}
