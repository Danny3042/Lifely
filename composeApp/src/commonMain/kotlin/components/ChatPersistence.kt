package components

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.writeTextFile
import platform.readTextFile
import platform.saveAttachmentFile
import platform.readAttachmentFile

@Serializable
internal data class PersistedUserMessage(
    val id: String,
    val text: String,
    val status: String,
    val attachmentFile: String? = null
)

@Serializable
internal data class PersistedModelMessage(
    val id: String,
    val text: String,
    val type: String // "loaded" or "error"
)

@Serializable
internal data class PersistedChatState(
    val messages: List<String>, // JSON strings of either PersistedUserMessage or PersistedModelMessage tagged
    /** Id of the conversation (also its id in the history once saved there). */
    val chatId: String? = null,
)

/** A past conversation kept in the chat history. */
@Serializable
internal data class PersistedSavedChat(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<String>,
)

@Serializable
internal data class PersistedChatHistory(val chats: List<PersistedSavedChat> = emptyList())

/** What the history list shows for a past chat. */
data class SavedChatSummary(val id: String, val title: String, val updatedAt: Long, val messageCount: Int)

private val json = Json { encodeDefaults = true; prettyPrint = false }

private const val CHAT_STATE_FILE = "chat_state.json"
private const val CHAT_HISTORY_FILE = "chat_history.json"
private const val MAX_SAVED_CHATS = 50

suspend fun saveChatState(messages: List<ChatMessage>, chatId: String? = null) {
    writeTextFile(CHAT_STATE_FILE, json.encodeToString(PersistedChatState(messages = encodeMessages(messages), chatId = chatId)))
}

/** The current conversation and its id (null for a chat saved before ids existed). */
suspend fun loadChatStateWithId(): Pair<List<ChatMessage>, String?> {
    val content = try { readTextFile(CHAT_STATE_FILE) } catch (e: Exception) { null } ?: return emptyList<ChatMessage>() to null
    val state = try { json.decodeFromString<PersistedChatState>(content) } catch (e: Exception) { return emptyList<ChatMessage>() to null }
    return decodeMessages(state.messages) to state.chatId
}

// ---- Chat history ----

private suspend fun readHistory(): PersistedChatHistory {
    val content = try { readTextFile(CHAT_HISTORY_FILE) } catch (e: Exception) { null } ?: return PersistedChatHistory()
    return try { json.decodeFromString<PersistedChatHistory>(content) } catch (e: Exception) { PersistedChatHistory() }
}

private suspend fun writeHistory(history: PersistedChatHistory) {
    writeTextFile(CHAT_HISTORY_FILE, json.encodeToString(history))
}

/** Past chats, most recent first. */
suspend fun loadChatHistory(): List<SavedChatSummary> =
    readHistory().chats.sortedByDescending { it.updatedAt }.map { SavedChatSummary(it.id, it.title, it.updatedAt, it.messages.size) }

/** Saves (or updates) a conversation in the history. Conversations with no question in them are skipped. */
suspend fun saveChatToHistory(id: String, messages: List<ChatMessage>, updatedAt: Long) {
    val firstQuestion = messages.firstOrNull { it is UserChatMessage } as? UserChatMessage ?: return
    val title = firstQuestion.text.trim().lineSequence().firstOrNull().orEmpty()
        .let { if (it.length > 60) it.take(57).trimEnd() + "…" else it }
        .ifBlank { "Photo" }
    val entry = PersistedSavedChat(id = id, title = title, updatedAt = updatedAt, messages = encodeMessages(messages))
    val others = readHistory().chats.filterNot { it.id == id }
    writeHistory(PersistedChatHistory((listOf(entry) + others).sortedByDescending { it.updatedAt }.take(MAX_SAVED_CHATS)))
}

/** The messages of a past chat, or null if it's gone. */
suspend fun loadSavedChat(id: String): List<ChatMessage>? =
    readHistory().chats.firstOrNull { it.id == id }?.let { decodeMessages(it.messages) }

suspend fun deleteSavedChat(id: String) {
    val history = readHistory()
    writeHistory(history.copy(chats = history.chats.filterNot { it.id == id }))
}

// ---- Encoding ----

private suspend fun encodeMessages(messages: List<ChatMessage>): List<String> =
    messages.mapNotNull { m ->
        when (m) {
            is UserChatMessage -> json.encodeToString(
                PersistedUserMessage(
                    id = m.id,
                    text = m.text,
                    status = m.status.name,
                    attachmentFile = m.imageBytes?.let { saveAttachmentFile(m.id, it) }
                )
            )
            is ModelChatMessage.LoadedModelMessage -> json.encodeToString(
                PersistedModelMessage(id = m.id, text = m.text, type = "loaded")
            )
            is ModelChatMessage.ErrorMessage -> json.encodeToString(
                PersistedModelMessage(id = m.id, text = m.text, type = "error")
            )
            else -> null // skip streaming/loading messages for persistence
        }
    }

suspend fun loadChatState(): List<ChatMessage> = loadChatStateWithId().first

private suspend fun decodeMessages(encoded: List<String>): List<ChatMessage> {
    val out = mutableListOf<ChatMessage>()
    for (s in encoded) {
        // try user
        try {
            val u = json.decodeFromString<PersistedUserMessage>(s)
            val bytes = u.attachmentFile?.let { readAttachmentFile(it) }
            val status = try { MessageStatus.valueOf(u.status) } catch (_: Exception) { MessageStatus.SENT }
            out.add(UserChatMessage(text = u.text, id = u.id, status = status, imageBytes = bytes))
            continue
        } catch (_: Exception) {}
        try {
            val m = json.decodeFromString<PersistedModelMessage>(s)
            if (m.type == "loaded") out.add(ModelChatMessage.LoadedModelMessage(text = m.text, id = m.id))
            else out.add(ModelChatMessage.ErrorMessage(text = m.text, id = m.id))
        } catch (_: Exception) {}
    }
    return out
}
