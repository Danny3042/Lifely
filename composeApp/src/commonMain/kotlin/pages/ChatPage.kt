import androidx.compose.runtime.CompositionLocalProvider
import components.ui.LocalTopBarOverlap
import platform.PlatformBridge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.VerticalDivider
import components.ChatSidebar
import components.ui.bottomBarOverlap
import components.ui.topBarOverlap
import androidx.compose.foundation.layout.Box
import components.ui.platformScaffoldInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import components.ChatHistorySheet
import components.ui.GlassIconButton
import androidx.compose.foundation.layout.fillMaxWidth
import components.ui.ContentWidth
import components.ui.centeredMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import components.ui.IconBadge
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import components.ChatBubbleItem
import components.MessageInput
import kotlinx.coroutines.launch
import model.ChatViewModel
import service.GenerativeAiService
import components.ModelChatMessage
import tts.getTtsService
import tts.TtsSettings
import platform.registerComposeNewChatListener

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = remember { ChatViewModel(GenerativeAiService.instance) },
) {
    val chatUiState = chatViewModel.uiState

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Register a platform listener so native hosts (iOS FAB) can trigger new chat.
    DisposableEffect(Unit) {
        val unregister = registerComposeNewChatListener {
            // Ensure this runs on the main coroutine scope
            coroutineScope.launch { chatViewModel.newChat() }
        }
        onDispose { unregister() }
    }

    val listState = rememberLazyListState()
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) {
        ChatHistorySheet(
            chats = chatViewModel.savedChats,
            onOpen = { id ->
                showHistory = false
                chatViewModel.openSavedChat(id)
            },
            onDelete = { id -> chatViewModel.deleteChat(id) },
            onDismiss = { showHistory = false },
        )
    }
    // removed unused showNewMessageChip state (not used currently)
    val messages = chatUiState.messages
    // When messages change, auto-scroll only if the user is already near the bottom.
    LaunchedEffect(messages.size, TtsSettings.enabled) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastIndex = messages.size - 1
        // Determine currently visible last item; if it's within 1 item of the end, auto-scroll.
        val visible = listState.layoutInfo.visibleItemsInfo
        val lastVisibleIndex = visible.lastOrNull()?.index ?: -1
        val shouldAuto = lastVisibleIndex >= (lastIndex - 1)
        if (shouldAuto) {
            // scroll to the latest message
            coroutineScope.launch { listState.animateScrollToItem(lastIndex) }
        }

        // Speak latest model message when TTS is enabled
        val lastMessage = messages.getOrNull(lastIndex)
        if (TtsSettings.enabled && lastMessage is ModelChatMessage.LoadedModelMessage) {
            // Use Gemini TTS where available
            try {
                getTtsService().speak(lastMessage.text, useGemini = true)
            } catch (_: Throwable) {}
        }
    }

    Scaffold(
        contentWindowInsets = platformScaffoldInsets(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        // The app shell (HeroScreen) already shows the "Chat" title bar on Android.
    ) { paddingValues ->
        // Large screens (tablets, unfolded phones) get the chat list beside the conversation instead
        // of the Past chats sheet. Measured here, so it's the space the chat page itself has.
        BoxWithConstraints(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            val showSidebar = maxWidth >= 760.dp
            val sidebarWidth = if (maxWidth >= 1000.dp) 320.dp else 280.dp
            val sendMessage: (String, ByteArray?) -> Unit = { inputText, image ->
                chatViewModel.sendMessage(inputText, image)
                // After sending, attempt to scroll to latest (the LaunchedEffect above will also handle this).
                coroutineScope.launch {
                    val last = chatUiState.messages.size
                    if (last > 0) listState.animateScrollToItem(last - 1)
                }
            }
            val startNewChat: () -> Unit = {
                chatViewModel.newChat()
                coroutineScope.launch { snackbarHostState.showSnackbar("New chat started — the last one is in Past chats") }
            }

            @Composable
            fun Conversation(modifier: Modifier) {
                Box(modifier = modifier) {
                    LazyColumn(
                        // Messages stay at a comfortable reading width on large screens
                        modifier = Modifier.centeredMaxWidth(ContentWidth.Reading).fillMaxSize(),
                        state = listState,
                        reverseLayout = false,
                        // Keep the last message clear of the input bar and the New chat button above it
                        contentPadding = PaddingValues(top = 8.dp + topBarOverlap(), bottom = 176.dp + bottomBarOverlap()),
                    ) {
                        items(
                            items = chatUiState.messages,
                            key = { it.id },
                        ) { message ->
                            ChatBubbleItem(message) { id, text ->
                                // find the message and retry via viewModel
                                // we assume failed messages are user messages and retry will resubmit
                                // Note: no image support in retry here
                                chatViewModel.retryMessage(id, text, null)
                            }
                        }
                    }
                    if (messages.isEmpty()) {
                        ChatEmptyState(
                            onSuggestion = { prompt -> sendMessage(prompt, null) },
                            enabled = chatUiState.canSendMessage,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .widthIn(max = ContentWidth.Reading)
                                .padding(top = topBarOverlap(), bottom = 96.dp + bottomBarOverlap()),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .widthIn(max = ContentWidth.Reading)
                    ) {
                        // Past chats and "New chat" sit just above the input's trailing end, so they move
                        // with the input on every screen size. Liquid Glass on iOS, small FABs on Android.
                        // New chat is hidden while the chat is empty (it would do nothing); history shows
                        // once there's something in it. With the sidebar, both live there instead.
                        val hasHistory = chatViewModel.savedChats.isNotEmpty()
                        if (!showSidebar && (messages.isNotEmpty() || hasHistory)) {
                            Row(
                                modifier = Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (hasHistory) {
                                    GlassIconButton(
                                        iosSymbol = "clock.arrow.circlepath",
                                        icon = Icons.Outlined.History,
                                        contentDescription = "Past chats",
                                        onClick = { showHistory = true },
                                        prominent = false,
                                    )
                                }
                                if (messages.isNotEmpty()) {
                                    GlassIconButton(
                                        iosSymbol = "plus",
                                        icon = Icons.Filled.Add,
                                        contentDescription = "New chat",
                                        onClick = startNewChat,
                                    )
                                }
                            }
                        }
                        // TTS toggle moved into the message input box (MessageInput.trailings)
                        // New chat is provided inside the MessageInput now
                        MessageInput(
                            enabled = chatUiState.canSendMessage,
                            onSendMessage = sendMessage,
                            modifier = Modifier,
                            snackbarHostState = snackbarHostState,
                            onNewChat = { chatViewModel.newChat() },
                        )
                        // Small breathing room under the input, plus the floating tab bar on iOS (pages run
                        // underneath it there, so the input has to sit above it).
                        Spacer(modifier = Modifier.height(8.dp + bottomBarOverlap()))
                    }
                }
            }

            // With the sidebar only the conversation scrolls, so the shared page title stays large and
            // fixed (as in Messages or Mail on iPad) instead of collapsing over just one column.
            DisposableEffect(showSidebar) {
                PlatformBridge.linkScrollToBars = !showSidebar
                onDispose { PlatformBridge.linkScrollToBars = true }
            }

            if (showSidebar) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ChatSidebar(
                        chats = chatViewModel.savedChats,
                        activeChatId = chatViewModel.chatId,
                        currentChatHasMessages = messages.isNotEmpty(),
                        onNewChat = { chatViewModel.newChat() },
                        onOpen = { id -> chatViewModel.openSavedChat(id) },
                        onDelete = { id -> chatViewModel.deleteChat(id) },
                        modifier = Modifier
                            .width(sidebarWidth)
                            .fillMaxHeight(),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // The conversation starts below the title and scrolls within its own area
                    val titleSpace = topBarOverlap()
                    CompositionLocalProvider(LocalTopBarOverlap provides 0.dp) {
                        Conversation(Modifier.weight(1f).fillMaxHeight().padding(top = titleSpace))
                    }
                }
            } else {
                Conversation(Modifier.fillMaxSize())
            }
        }
     }
 }

/** Starter prompts for an empty chat: one tap sends them. */
private val chatSuggestions = listOf(
    "Help me wind down for sleep",
    "A 2-minute stress reset",
    "Ideas for a morning routine",
    "How do I stick to a new habit?",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatEmptyState(
    onSuggestion: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(
            icon = Icons.Filled.AutoAwesome,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 72.dp,
            shape = MaterialTheme.shapes.extraLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text("Ask Gemini", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Get tips on sleep, stress, habits and more. Attach a photo for extra context.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chatSuggestions.forEach { prompt ->
                SuggestionChip(
                    onClick = { onSuggestion(prompt) },
                    enabled = enabled,
                    label = { Text(prompt) },
                )
            }
        }
    }
}
