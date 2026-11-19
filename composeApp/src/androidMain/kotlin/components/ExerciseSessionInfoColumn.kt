package components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.compose.ui.tooling.preview.Preview
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Displays summary information about the [ExerciseSessionRecord]
 */

@Composable
fun ExerciseSessionInfoColumn(
    start: ZonedDateTime,
    end: ZonedDateTime,
    uid: String,
    name: String,
    sourceAppName: String,
    sourceAppIcon: Drawable?,
    onClick: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier.clickable {
            onClick(uid)
        }
    ) {
        Text(
            color = MaterialTheme.colors.primary,
            text = "${start.toLocalTime()} - ${end.toLocalTime()}",
            style = MaterialTheme.typography.caption
        )
        Text(name)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                modifier = Modifier
                    .padding(4.dp, 2.dp)
                    .height(16.dp)
                    .width(16.dp),
                painter = rememberDrawablePainter(drawable = sourceAppIcon),
                contentDescription = "App Icon"
            )
            Text(
                text = sourceAppName,
                fontStyle = FontStyle.Italic
            )
        }
        Text(uid)
    }
}

@Preview
@Composable
fun ExerciseSessionInfoColumnPreview() {
MaterialTheme {
        ExerciseSessionInfoColumn(
            ZonedDateTime.now().minusMinutes(30),
            ZonedDateTime.now(),
            UUID.randomUUID().toString(),
            "Running",
            "My Fitness App",
            null
        )
    }
}