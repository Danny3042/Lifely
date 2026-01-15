import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import components.ChatBubbleItem
import components.MessageInput
import kotlinx.coroutines.launch
import model.ChatViewModel
import service.GenerativeAiService
import utils.isAndroid
import components.ModelChatMessage
import tts.getTtsService
import tts.TtsSettings
import platform.registerComposeNewChatListener
import platform.PlatformBridge
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalMaterial3Api::class)
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
    // removed unused showNewMessageChip state (not used currently)
    val messages = chatUiState.messages
    // Observe platform safe area bottom (iOS) so FAB can be offset dynamically above the home indicator/tab bar
    var platformBottomInset by remember { mutableStateOf(0.0) }
    LaunchedEffect(Unit) {
        snapshotFlow { PlatformBridge.safeAreaBottom }.collectLatest { value ->
            platformBottomInset = value
        }
    }

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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (isAndroid()) {
                TopAppBar(
                    title = { Text("Gemini Chat") },
                    navigationIcon = {
                        Icon(
                            Icons.Default.AutoAwesome,
                            "Gemini Chat",
                            modifier = Modifier.padding(4.dp)
                        )
                    },
                    // removed external New chat action - new chat is now in the message input
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = false,
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
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                // TTS toggle moved into the message input box (MessageInput.trailings)
                // New chat is provided inside the MessageInput now
                MessageInput(
                     enabled = chatUiState.canSendMessage,
                     onSendMessage = { inputText, image ->
                         chatViewModel.sendMessage(inputText, image)
                         // After sending, attempt to scroll to latest (the LaunchedEffect above will also handle this).
                         coroutineScope.launch {
                            val last = chatUiState.messages.size
                            if (last > 0) listState.animateScrollToItem(last - 1)
                        }
                     },
                     modifier = Modifier,
                     snackbarHostState = snackbarHostState,
                     onNewChat = { chatViewModel.newChat() },
                 )
                // Reserve minimal vertical space on iOS: use the platform safe area bottom so the input
                // sits closer to the bottom edge (lower) compared to the previous extra offset.
                Spacer(modifier = if (isAndroid()) Modifier.height(56.dp) else Modifier.height(platformBottomInset.toFloat().dp))
            }
            // Floating action button for New Chat anchored above the input on the bottom end
            FloatingActionButton(
                onClick = {
                    chatViewModel.newChat()
                    coroutineScope.launch { snackbarHostState.showSnackbar("New chat started") }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = (100.dp + platformBottomInset.toFloat().dp)),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New chat")
            }
         }
     }
 }
