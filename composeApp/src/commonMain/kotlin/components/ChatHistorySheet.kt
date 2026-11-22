package components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import components.ui.ListRowCard
import components.ui.PagePadding
import components.ui.bottomBarOverlap
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Past chats: tap one to reopen it, or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistorySheet(
    chats: List<SavedChatSummary>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = PagePadding)) {
            Text("Past chats", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Chats are kept here when you start a new one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        if (chats.isEmpty()) {
            ListRowCard(
                title = "No past chats yet",
                subtitle = "Start a new chat with + and this one will be saved here.",
                icon = Icons.Outlined.History,
                modifier = Modifier.padding(horizontal = PagePadding).padding(bottom = 32.dp + bottomBarOverlap()),
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                contentPadding = PaddingValues(start = PagePadding, end = PagePadding, bottom = 32.dp + bottomBarOverlap()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chats, key = { it.id }) { chat ->
                    ListRowCard(
                        title = chat.title,
                        subtitle = "${friendlyDate(chat.updatedAt)} · ${chat.messageCount} messages",
                        icon = Icons.AutoMirrored.Outlined.Chat,
                        onClick = { onOpen(chat.id) },
                        trailing = {
                            IconButton(onClick = { onDelete(chat.id) }) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete chat \"${chat.title}\"",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    )
                }
            }
        }
    }
}

/** "Today 14:05", "Yesterday 09:30" or "12 Nov". */
internal fun friendlyDate(epochMillis: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val then = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    val today = Clock.System.now().toLocalDateTime(zone).date
    val time = "${then.hour.toString().padStart(2, '0')}:${then.minute.toString().padStart(2, '0')}"
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return when {
        then.date == today -> "Today $time"
        then.date.toEpochDays() == today.toEpochDays() - 1 -> "Yesterday $time"
        else -> "${then.date.day} ${months[then.date.month.ordinal]}"
    }
}
