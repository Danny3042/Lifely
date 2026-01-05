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
    val messages: List<String> // JSON strings of either PersistedUserMessage or PersistedModelMessage tagged
)

private val json = Json { encodeDefaults = true; prettyPrint = false }

private const val CHAT_STATE_FILE = "chat_state.json"

suspend fun saveChatState(messages: List<ChatMessage>) {
    val list = messages.mapNotNull { m ->
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
    val state = PersistedChatState(messages = list)
    writeTextFile(CHAT_STATE_FILE, json.encodeToString(state))
}

suspend fun loadChatState(): List<ChatMessage> {
    val content = try { readTextFile(CHAT_STATE_FILE) } catch (e: Exception) { null } ?: return emptyList()
    val state = try { json.decodeFromString<PersistedChatState>(content) } catch (e: Exception) { return emptyList() }
    val out = mutableListOf<ChatMessage>()
    for (s in state.messages) {
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
