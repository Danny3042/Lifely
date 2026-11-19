package components

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.danielramzani.HealthCompose.R
import androidx.compose.ui.tooling.preview.Preview

/**
 * Displays a title and content, for use in conveying session details.
 */
fun LazyListScope.sessionDetailsItem(
    @StringRes labelId: Int,
    content: @Composable () -> Unit
) {
    item {
        Text(
            text = stringResource(id = labelId),
            style = MaterialTheme.typography.h5,
            color = MaterialTheme.colors.primary
        )
        content()
    }
}

@Preview
@Composable
fun SessionDetailsItemPreview() {
    MaterialTheme {
        LazyColumn {
            sessionDetailsItem(R.string.total_steps) {
                Text(text = "12345")
            }
        }
    }
}