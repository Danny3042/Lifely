package components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.danielramzani.HealthCompose.R
import androidx.compose.ui.tooling.preview.Preview
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Creates a row to represent an [ExerciseSessionRecord]
 */
@Composable
fun ExerciseSessionRow(
    start: ZonedDateTime,
    end: ZonedDateTime,
    uid: String,
    name: String,
    sourceAppName: String,
    sourceAppIcon: Drawable?,
    onDeleteClick: (String) -> Unit = {},
    onDetailsClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ExerciseSessionInfoColumn(
            start = start,
            end = end,
            uid = uid,
            name = name,
            sourceAppName = sourceAppName,
            sourceAppIcon = sourceAppIcon,
            onClick = onDetailsClick
        )
        IconButton(
            onClick = { onDeleteClick(uid) },
        ) {
            Icon(Icons.Default.Delete, stringResource(R.string.delete_button))
        }
    }
}

@Preview
@Composable
fun ExerciseSessionRowPreview() {
    val context = LocalContext.current
   MaterialTheme {
        ExerciseSessionRow(
            ZonedDateTime.now().minusMinutes(30),
            ZonedDateTime.now(),
            UUID.randomUUID().toString(),
            "Running",
            sourceAppName = "My Fitness app",
            sourceAppIcon = context.getDrawable(R.mipmap.ic_launcher)
        )
    }
}