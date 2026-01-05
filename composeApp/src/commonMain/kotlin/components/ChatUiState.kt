package components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * UI state model for Chat listing screen
 */
@Stable
interface ChatUiState {
    val messages: List<ChatMessage>
    val canSendMessage: Boolean
}

/**
 * Mutable state implementation for [ChatUiState]
 */
class MutableChatUiState : ChatUiState {
    override val messages = mutableStateListOf<ChatMessage>()

    override var canSendMessage: Boolean by mutableStateOf(true)

    fun addMessage(message: ChatMessage) {
        messages.add(message)
    }

    fun addPendingUserMessage(text: String, tempId: String) {
        messages.add(UserChatMessage(text = text, id = tempId, status = MessageStatus.PENDING))
    }

    fun markUserMessageSent(tempId: String, serverId: String? = null) {
        val idx = messages.indexOfFirst { it is UserChatMessage && it.id == tempId }
        if (idx >= 0) {
            val old = messages[idx] as UserChatMessage
            messages[idx] = old.copy(status = MessageStatus.SENT, id = serverId ?: old.id)
        }
    }

    fun markUserMessageFailed(tempId: String) {
        val idx = messages.indexOfFirst { it is UserChatMessage && it.id == tempId }
        if (idx >= 0) {
            val old = messages[idx] as UserChatMessage
            messages[idx] = old.copy(status = MessageStatus.FAILED)
        }
    }

    fun removeMessage(id: String) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) messages.removeAt(idx)
    }

    fun getMessageById(id: String): ChatMessage? = messages.find { it.id == id }

    fun setLastModelMessageAsLoaded(text: String) {
        updateLastModelMessage { ModelChatMessage.LoadedModelMessage(text) }
    }

    fun setLastMessageAsError(error: String) {
        updateLastModelMessage { ModelChatMessage.ErrorMessage(error) }
    }

    private fun updateLastModelMessage(block: (ModelChatMessage) -> ChatMessage) {
        val lastIdx = messages.indexOfLast { it is ModelChatMessage }
        if (lastIdx >= 0) {
            val lastMessage = messages[lastIdx] as ModelChatMessage
            val newMessage = block(lastMessage)
            messages[lastIdx] = newMessage
        }
    }
}