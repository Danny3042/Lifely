package components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import components.ui.bottomBarOverlap
import components.ui.topBarOverlap

/**
 * The chat list shown beside the conversation on large screens (tablets, unfolded phones), in place
 * of the Past chats sheet: start a new chat, or open or delete a past one. The open chat is
 * highlighted; a brand-new chat that hasn't been saved yet shows as "New chat" at the top.
 */
@Composable
fun ChatSidebar(
    chats: List<SavedChatSummary>,
    activeChatId: String,
    currentChatHasMessages: Boolean,
    onNewChat: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp + topBarOverlap(),
                bottom = 16.dp + bottomBarOverlap(),
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Chats", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = onNewChat,
                        // A new chat is already open when the current one is empty
                        enabled = currentChatHasMessages,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New chat")
                    }
                }
            }
            val openChatIsSaved = chats.any { it.id == activeChatId }
            if (!openChatIsSaved) {
                item(key = "current") {
                    SidebarChatRow(
                        title = if (currentChatHasMessages) "Current chat" else "New chat",
                        subtitle = if (currentChatHasMessages) "In progress" else "Ask anything to get started",
                        selected = true,
                        onClick = {},
                        onDelete = null,
                    )
                }
            }
            items(chats, key = { it.id }) { chat ->
                SidebarChatRow(
                    title = chat.title,
                    subtitle = "${friendlyDate(chat.updatedAt)} · ${chat.messageCount} messages",
                    selected = chat.id == activeChatId,
                    onClick = { onOpen(chat.id) },
                    onDelete = { onDelete(chat.id) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (chats.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Past chats appear here when you start a new one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarChatRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete chat \"$title\"",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
