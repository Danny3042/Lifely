package model

import components.ChatUiState
import components.ModelChatMessage
import components.MutableChatUiState
import components.UserChatMessage
import components.MessageStatus
import dev.shreyaspatil.ai.client.generativeai.type.content
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import components.saveChatState
import components.loadChatState

class ChatViewModel(aiService: GenerativeAiService) {
    private val coroutineScope = MainScope()

    private val chat = aiService.startChat(
        history = listOf(
            content(role = "user") { text("Hello AI.") },
            content(role = "model") { text("Great to meet you. What would you like to know?") },
        ),
    )

    private val _uiState = MutableChatUiState()
    val uiState: ChatUiState = _uiState

    // Save state when messages change
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // restore persisted state
        saveScope.launch {
            val msgs = loadChatState()
            msgs.forEach { _uiState.addMessage(it) }
        }

        // Observe and persist changes to messages list periodically
        saveScope.launch {
            // very simple: periodically snapshot messages and save (can be optimized)
            while (true) {
                try {
                    saveChatState(_uiState.messages)
                } catch (_: Throwable) {}
                kotlinx.coroutines.delay(3000)
            }
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

        val base = if (imageBytes != null) {
            val content = content {
                image(imageBytes)
                text(prompt)
            }
            chat.sendMessageStream(content)
        } else {
            chat.sendMessageStream(prompt)
        }

        val modelMessage = ModelChatMessage.LoadingModelMessage(
            base.map { it.text ?: "" }
                .onEach { completeText.append(it) }
                .onStart { _uiState.canSendMessage = false }
                .onCompletion {
                    _uiState.setLastModelMessageAsLoaded(completeText.toString())
                    _uiState.canSendMessage = true
                }
                .catch {
                    _uiState.setLastMessageAsError(it.toString())
                    _uiState.canSendMessage = true
                },
        )

        // Launch the streaming job and track it so we can cancel/retry
        val job = coroutineScope.launch(Dispatchers.Default) {
            try {
                _uiState.addMessage(modelMessage)
                _uiState.markUserMessageSent(tempId)
                // consumption is done by LoadingText via Flow subscription; completion handled in model flow operators
            } catch (e: Throwable) {
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

    fun onCleared() {
        coroutineScope.cancel()
    }
}