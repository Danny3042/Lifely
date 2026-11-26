package components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow
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
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.markdownColor
import com.mikepenz.markdown.model.markdownTypography
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import components.buddy.BuddyActionCard
import components.buddy.BuddyActions
import components.ui.BreathingBuddy
import components.ui.BuddyExpression

@Composable
fun ChatBubbleItem(
    chatMessage: ChatMessage,
    onRetry: ((id: String, text: String) -> Unit)? = null,
    /** The newest message: only its Buddy animates (thinking, talking, a happy hop when it's done) */
    isLatest: Boolean = false,
    isNight: Boolean = false,
    canSend: Boolean = true,
    /** Sends a message as the user (used by the mood card) */
    onSendMessage: (String) -> Unit = {},
) {
    val isModelMessage = chatMessage.isModelMessage() || chatMessage.isErrorMessage()

    val backgroundColor = when (chatMessage) {
        is ModelChatMessage.ErrorMessage -> MaterialTheme.colorScheme.errorContainer
        is ModelChatMessage.LoadingModelMessage -> MaterialTheme.colorScheme.surfaceContainerLow
        is ModelChatMessage.LoadedModelMessage -> MaterialTheme.colorScheme.surfaceContainerLowest
        is UserChatMessage -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when (chatMessage) {
        is ModelChatMessage.ErrorMessage -> MaterialTheme.colorScheme.onErrorContainer
        is ModelChatMessage.LoadingModelMessage, is ModelChatMessage.LoadedModelMessage ->
            MaterialTheme.colorScheme.onSurface
        is UserChatMessage -> MaterialTheme.colorScheme.onPrimary
    }

    val bubbleShape = if (isModelMessage) {
        RoundedCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 6.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
    }

    val horizontalAlignment = if (isModelMessage) Alignment.Start else Alignment.End

    // A reply still arriving: collected here (collecting is what runs the request), so the Buddy
    // can switch from thinking to talking as soon as the first words come in.
    var streamed by remember(chatMessage.id) { mutableStateOf("") }
    if (chatMessage is ModelChatMessage.LoadingModelMessage) {
        LaunchedEffect(chatMessage.textStream) { chatMessage.textStream.collect { streamed += it } }
    }
    // A short happy hop when a reply finishes
    var justFinished by remember(chatMessage.id) { mutableStateOf(false) }
    if (chatMessage is ModelChatMessage.LoadedModelMessage && isLatest) {
        LaunchedEffect(chatMessage.id) {
            justFinished = true
            kotlinx.coroutines.delay(2_400)
            justFinished = false
        }
    }
    val expression = when {
        !isLatest -> BuddyExpression.Calm
        chatMessage is ModelChatMessage.LoadingModelMessage ->
            if (BuddyActions.clean(streamed).isBlank()) BuddyExpression.Thinking else BuddyExpression.Talking
        chatMessage is ModelChatMessage.LoadedModelMessage && justFinished -> BuddyExpression.Happy
        else -> BuddyExpression.Calm
    }
    // The reply text without its card tags, and the cards it asks for
    val (replyText, actions) = when (chatMessage) {
        is ModelChatMessage.LoadedModelMessage -> remember(chatMessage.text) { BuddyActions.parse(chatMessage.text) }
        else -> "" to emptyList()
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
    if (isModelMessage) {
        BreathingBuddy(
            breath = 0f,
            isRunning = false,
            isNight = isNight,
            expression = expression,
            modifier = Modifier.padding(top = 2.dp).size(40.dp),
        )
        Spacer(Modifier.width(6.dp))
    }
    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text = if (isModelMessage) "Buddy" else "You",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )

        // Place bubble and status indicator in a Row so the status doesn't overlap the card
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints {
                Card(
                    colors = CardDefaults.cardColors(containerColor = backgroundColor, contentColor = contentColor),
                    shape = bubbleShape,
                    modifier = Modifier.widthIn(0.dp, maxWidth * 0.85f),
                ) {
                    when (chatMessage) {
                        is ModelChatMessage.ErrorMessage -> Text(
                            text = chatMessage.text,
                            modifier = Modifier.padding(16.dp),
                        )

                        // Formatted as it streams in. Collecting the stream here is what runs the
                        // request, so it's collected once, straight through (no per-word delay).
                        is ModelChatMessage.LoadingModelMessage -> StreamingReply(
                            text = BuddyActions.clean(streamed),
                            contentColor = contentColor,
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

                        // Gemini answers in Markdown (headings, bold, lists): show it formatted
                        is ModelChatMessage.LoadedModelMessage -> Markdown(
                            content = replyText,
                            colors = markdownColor(text = contentColor),
                            typography = chatMarkdownTypography(),
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
                            color = MaterialTheme.colorScheme.primary
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

        // Cards the Buddy offered with this reply
        if (actions.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.92f),
            ) {
                actions.forEachIndexed { index, action ->
                    BuddyActionCard(
                        action = action,
                        key = "${chatMessage.id}-$index",
                        isNight = isNight,
                        canSend = canSend,
                        onSendMessage = onSendMessage,
                    )
                }
            }
        }
    }
    }
}

/**
 * Text sizes for Markdown inside a chat bubble. The renderer's defaults use display sizes for
 * headings, which swamp a bubble; here headings are title sizes and body text matches the bubbles.
 */
@Composable
private fun chatMarkdownTypography() = markdownTypography(
    h1 = MaterialTheme.typography.titleLarge,
    h2 = MaterialTheme.typography.titleLarge,
    h3 = MaterialTheme.typography.titleMedium,
    h4 = MaterialTheme.typography.titleMedium,
    h5 = MaterialTheme.typography.titleSmall,
    h6 = MaterialTheme.typography.titleSmall,
    text = MaterialTheme.typography.bodyLarge,
    paragraph = MaterialTheme.typography.bodyLarge,
)

/** A reply still arriving: the text so far, formatted, with a pulsing dot while more is on its way. */
@Composable
private fun StreamingReply(text: String, contentColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.animateContentSize()) {
        if (text.isNotBlank()) {
            Markdown(
                content = text,
                colors = markdownColor(text = contentColor),
                typography = chatMarkdownTypography(),
            )
            Spacer(Modifier.height(6.dp))
        }
        val pulse by rememberInfiniteTransition().animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        )
        Box(
            Modifier
                .size(12.dp)
                .scale(pulse)
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
                    ),
                    CircleShape,
                ),
        )
    }
}
