package components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive dialog used for the app's custom dialogs (timers, calendar, shortcuts).
 * Built on BasicAlertDialog: a large rounded surface, an optional tonal icon badge, a headline,
 * your content, and a filled confirm button with a text button beside it.
 *
 * For a plain "are you sure?" question use [PlatformAlertDialog] instead, which is native on iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingText: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissText: String? = "Cancel",
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = colors.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                val centred = icon != null
                if (icon != null) {
                    IconBadge(
                        icon = icon,
                        containerColor = if (destructive) colors.errorContainer else colors.primaryContainer,
                        contentColor = if (destructive) colors.onErrorContainer else colors.onPrimaryContainer,
                        size = 56.dp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = if (centred) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (supportingText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = if (centred) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (content != null) {
                    Spacer(Modifier.height(20.dp))
                    content()
                }
                val hasConfirm = confirmText != null && onConfirm != null
                if (hasConfirm || dismissText != null) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissText != null) {
                            TextButton(onClick = onDismissRequest) { Text(dismissText) }
                        }
                        if (hasConfirm) {
                            Button(
                                onClick = onConfirm!!,
                                enabled = confirmEnabled,
                                colors = if (destructive) {
                                    ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                            ) { Text(confirmText!!) }
                        }
                    }
                }
            }
        }
    }
}
