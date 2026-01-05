package components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh

@Composable
fun ChatBubbleItem(chatMessage: ChatMessage, onRetry: ((id: String, text: String) -> Unit)? = null) {
    val isModelMessage = chatMessage.isModelMessage() || chatMessage.isErrorMessage()

    val backgroundColor = when (chatMessage) {
        is ModelChatMessage.ErrorMessage -> MaterialTheme.colorScheme.errorContainer
        is ModelChatMessage.LoadingModelMessage, is ModelChatMessage.LoadedModelMessage ->
            MaterialTheme.colorScheme.primaryContainer
        is UserChatMessage -> MaterialTheme.colorScheme.tertiaryContainer
    }

    val bubbleShape = if (isModelMessage) {
        RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
    }

    val horizontalAlignment = if (isModelMessage) Alignment.Start else Alignment.End

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth(),
    ) {
        Text(
            text = if (chatMessage.isModelMessage()) "AI" else "You",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        // Place bubble and status indicator in a Row so the status doesn't overlap the card
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints {
                Card(
                    colors = CardDefaults.cardColors(containerColor = backgroundColor),
                    shape = bubbleShape,
                    modifier = Modifier.widthIn(0.dp, maxWidth * 0.9f),
                ) {
                    when (chatMessage) {
                        is ModelChatMessage.ErrorMessage -> Text(
                            text = chatMessage.text,
                            modifier = Modifier.padding(16.dp),
                        )

                        is ModelChatMessage.LoadingModelMessage -> LoadingText(
                            stream = chatMessage.textStream,
                            modifier = Modifier.padding(16.dp),
                        )

                        is UserChatMessage -> Column(modifier = Modifier.padding(0.dp)) {
                            // Render image placeholder if there is an attachment
                            chatMessage.imageBytes?.let {
                                // Simple placeholder: show a small box with text. Converting bytes to ImageBitmap
                                // is platform-dependent; keep a lightweight placeholder here.
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("[Image]", modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                            }

                            Text(
                                text = chatMessage.text,
                                modifier = Modifier.padding(16.dp),
                            )
                        }

                        is ModelChatMessage.LoadedModelMessage -> Text(
                            text = chatMessage.text,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            // Status indicators for user messages: pending spinner or retry button
            if (chatMessage is UserChatMessage) {
                when (chatMessage.status) {
                    MessageStatus.PENDING -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    MessageStatus.FAILED -> {
                        IconButton(onClick = {
                            onRetry?.invoke(chatMessage.id, chatMessage.text)
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Retry")
                        }
                    }
                    else -> { /* no indicator for SENT */ }
                }
            }
        }
    }
}