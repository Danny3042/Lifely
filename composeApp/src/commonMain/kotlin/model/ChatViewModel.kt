package model

import service.friendlyGeminiError
import service.isTimeout
import components.ChatUiState
import components.ModelChatMessage
import components.MutableChatUiState
import dev.shreyaspatil.ai.client.generativeai.type.content
import dev.shreyaspatil.ai.client.generativeai.type.Content
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import service.GenerativeAiService
import utils.getUUIDString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import components.saveChatState
import components.loadChatStateWithId
import components.loadChatHistory
import components.saveChatToHistory
import components.loadSavedChat
import components.deleteSavedChat
import components.SavedChatSummary
import components.ChatMessage
import components.UserChatMessage
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class ChatViewModel(private val aiService: GenerativeAiService) {
    private val coroutineScope = MainScope()

    // make the chat session mutable so we can reset for a new conversation
    // Start with an empty history (same as "New chat"). Gemini 3+ models reject scripted/prefilled model turns.
    // Direct REST chat with low thinking (see GeminiChatSession for why not the SDK's Chat)
    private var chat = aiService.startFastChat()

    private val _uiState = MutableChatUiState()
    val uiState: ChatUiState = _uiState

    /** Id of the open conversation; it keeps this id in the chat history. */
    private var chatId: String = getUUIDString()

    /** Past chats for the history sheet, most recent first. */
    val savedChats = mutableStateListOf<SavedChatSummary>()

    // Save state when messages change
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // restore persisted state
        saveScope.launch {
            val (msgs, savedId) = loadChatStateWithId()
            if (savedId != null) chatId = savedId
            msgs.forEach { _uiState.addMessage(it) }
            // Give Gemini the restored conversation too, so follow-up questions keep their context
            chat.restoreHistory(turnsOf(msgs))
            refreshHistory()
        }

        // Observe and persist changes to messages list periodically
        saveScope.launch {
            // very simple: periodically snapshot messages and save (can be optimized)
            while (true) {
                try {
                    saveChatState(_uiState.messages.toList(), chatId)
                } catch (_: Throwable) {}
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    private fun turnsOf(messages: List<ChatMessage>): List<Pair<Boolean, String>> = messages.mapNotNull {
        when (it) {
            is UserChatMessage -> true to it.text
            is ModelChatMessage.LoadedModelMessage -> false to it.text
            else -> null
        }
    }

    private suspend fun refreshHistory() {
        val list = runCatching { loadChatHistory() }.getOrDefault(emptyList())
        withContext(Dispatchers.Main) {
            savedChats.clear()
            savedChats.addAll(list)
        }
    }

    /** Keeps the open conversation in the history (if it has a question in it). */
    private suspend fun archiveCurrentChat() {
        val messages = _uiState.messages.toList()
        if (messages.none { it is UserChatMessage }) return
        runCatching { saveChatToHistory(chatId, messages, Clock.System.now().toEpochMilliseconds()) }
    }

    private suspend fun cancelActiveJobs() {
        jobsMutex.withLock {
            for (j in activeJobs.values) j.cancel()
            activeJobs.clear()
        }
        attachmentStore.clear()
    }

    /** Opens a past chat: the current one is kept in the history first. */
    fun openSavedChat(id: String) {
        if (id == chatId) return
        coroutineScope.launch {
            cancelActiveJobs()
            archiveCurrentChat()
            val messages = runCatching { loadSavedChat(id) }.getOrNull() ?: run { refreshHistory(); return@launch }
            _uiState.clearAllMessages()
            messages.forEach { _uiState.addMessage(it) }
            _uiState.canSendMessage = true
            chatId = id
            chat = aiService.startFastChat().also { it.restoreHistory(turnsOf(messages)) }
            saveScope.launch { runCatching { saveChatState(messages, id) } }
            refreshHistory()
        }
    }

    /** Removes a past chat. Deleting the one that's open also starts a fresh chat. */
    fun deleteChat(id: String) {
        coroutineScope.launch {
            runCatching { deleteSavedChat(id) }
            if (id == chatId) startFresh(archive = false)
            refreshHistory()
        }
    }

    // Track active send jobs per tempId so we can cancel streams when needed
    private val activeJobs = mutableMapOf<String, Job>()
    private val attachmentStore = mutableMapOf<String, ByteArray?>()
    private val jobsMutex = Mutex()

    fun sendMessage(prompt: String, imageBytes: ByteArray?) {
        val tempId = "temp-${getUUIDString()}"
        // Add pending user message immediately (optimistic UI)
        _uiState.addPendingUserMessage(prompt, tempId)
        if (imageBytes != null) attachmentStore[tempId] = imageBytes

        val completeText = StringBuilder()

        val base = chat.sendMessageStream(prompt, imageBytes)

        val modelMessage = ModelChatMessage.LoadingModelMessage(
            base
                .onEach { completeText.append(it) }
                .onStart { _uiState.canSendMessage = false }
                .onCompletion {
                    _uiState.setLastModelMessageAsLoaded(completeText.toString())
                    _uiState.canSendMessage = true
                }
                .catch {
                    println("ChatViewModel: Gemini request failed: ${describeError(it)}")
                    it.printStackTrace()
                    // Timeouts get a plain-language message (the retry button is right there);
                    // anything else keeps the detailed description.
                    _uiState.setLastMessageAsError(if (it.isTimeout()) friendlyGeminiError(it) else describeError(it))
                    _uiState.canSendMessage = true
                },
        )

        // Launch the streaming job and track it so we can cancel/retry
        val job = coroutineScope.launch(Dispatchers.Default) {
            try {
                _uiState.addMessage(modelMessage)
                _uiState.markUserMessageSent(tempId)
                // consumption is done by LoadingText via Flow subscription; completion handled in model flow operators
            } catch (_: Throwable) {
                _uiState.markUserMessageFailed(tempId)
            } finally {
                // remove attachment store to free memory if present
                attachmentStore.remove(tempId)
                jobsMutex.withLock { activeJobs.remove(tempId) }
            }
        }

        coroutineScope.launch {
            jobsMutex.withLock { activeJobs[tempId] = job }
        }
    }

    fun retryMessage(tempId: String, text: String, imageBytes: ByteArray?) {
        // Remove the failed message and re-run send with the same tempId
        _uiState.removeMessage(tempId)
        val bytes = imageBytes ?: attachmentStore[tempId]
        // re-add pending with same id
        _uiState.addPendingUserMessage(text, tempId)
        sendMessage(text, bytes)
    }

    fun cancelSend(tempId: String) {
        coroutineScope.launch {
            jobsMutex.withLock {
                activeJobs[tempId]?.cancel()
                activeJobs.remove(tempId)
            }
            attachmentStore.remove(tempId)
            _uiState.markUserMessageFailed(tempId)
        }
    }

    /**
     * Start a fresh chat: cancel active jobs, clear UI state and persistence, and recreate the chat session.
     */
    fun newChat() {
        coroutineScope.launch { startFresh(archive = true) }
    }

    private suspend fun startFresh(archive: Boolean) {
        cancelActiveJobs()
        // The finished conversation goes to the history rather than being thrown away
        if (archive) archiveCurrentChat()
        _uiState.clearAllMessages()
        _uiState.canSendMessage = true
        chatId = getUUIDString()
        val newId = chatId
        saveScope.launch {
            try { saveChatState(emptyList(), newId) } catch (_: Throwable) {}
        }
        chat = aiService.startFastChat()
        refreshHistory()
    }

    fun onCleared() {
        coroutineScope.cancel()
    }
}

/**
 * The Gemini SDK wraps many failures as "Something unexpected happened." and keeps the real reason
 * in the cause chain. Walk the chain so the chat shows (and the console logs) what actually went wrong.
 */
internal fun describeError(error: Throwable): String {
    val chain = generateSequence(error) { it.cause }.take(6).toList()
    val root = chain.last()
    val outer = error.message?.takeIf { it.isNotBlank() } ?: (error::class.simpleName ?: "Error")
    if (root === error) return outer
    val rootText = listOfNotNull(root::class.simpleName, root.message?.takeIf { it.isNotBlank() })
        .joinToString(": ")
    return "$outer\nCause: $rootText"
}
