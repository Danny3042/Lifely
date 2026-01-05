package components

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import utils.getUUIDString

/**
 * Represents as a Chat message model
 */
@Immutable
sealed interface ChatMessage {

    val id: String

    fun isUserMessage(): Boolean {
        return this is UserChatMessage
    }

    fun isModelMessage(): Boolean {
        return this is ModelChatMessage
    }

    fun isErrorMessage(): Boolean {
        return this is ModelChatMessage.ErrorMessage
    }
}

/**
 * Message send status for optimistic UI
 */
enum class MessageStatus {
    PENDING,
    SENT,
    FAILED
}

/**
 * User prompt message model
 */
@Immutable
data class UserChatMessage(
    val text: String,
    override val id: String = "user-${getUUIDString()}",
    val status: MessageStatus = MessageStatus.SENT,
    val imageBytes: ByteArray? = null,
) : ChatMessage

/**
 * Model's response chat model
 */
@Immutable
sealed interface ModelChatMessage : ChatMessage {

    /**
     * Represents Loading state of a model message
     */
    @Immutable
    data class LoadingModelMessage(
        val textStream: Flow<String>,
        override val id: String = "modelloading-${getUUIDString()}",
    ) : ModelChatMessage

    /**
     * Represents loaded state of a model message
     */
    @Immutable
    data class LoadedModelMessage(
        val text: String,
        override val id: String = "model-${getUUIDString()}",
    ) : ModelChatMessage

    /**
     * Represents error state of a model message
     */
    @Immutable
    data class ErrorMessage(
        val text: String,
        override val id: String = "error-${getUUIDString()}",
    ) : ChatMessage
}