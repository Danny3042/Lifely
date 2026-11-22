package components.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
actual fun AppleSignInButton(onClick: () -> Unit, modifier: Modifier) {
    // Black on light backgrounds, white on dark, as Apple's button guidelines describe
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (dark) Color.White else Color.Black,
            contentColor = if (dark) Color.Black else Color.White,
        ),
    ) {
        Text("Continue with Apple", style = MaterialTheme.typography.titleSmall)
    }
}
