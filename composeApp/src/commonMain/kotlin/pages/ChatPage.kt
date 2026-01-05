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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import components.ChatBubbleItem
import components.MessageInput
import kotlinx.coroutines.launch
import model.ChatViewModel
import service.GenerativeAiService
import utils.isAndroid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = remember { ChatViewModel(GenerativeAiService.instance) },
) {
    val chatUiState = chatViewModel.uiState

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showNewMessageChip by remember { mutableStateOf(false) }
    val messages = chatUiState.messages

    // When messages change, auto-scroll only if the user is already near the bottom.
    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastIndex = messages.size - 1
        // Determine currently visible last item; if it's within 1 item of the end, auto-scroll.
        val visible = listState.layoutInfo.visibleItemsInfo
        val lastVisibleIndex = visible.lastOrNull()?.index ?: -1
        val shouldAuto = lastVisibleIndex >= (lastIndex - 1)
        if (shouldAuto) {
            // scroll to the latest message
            coroutineScope.launch { listState.animateScrollToItem(lastIndex) }
            showNewMessageChip = false
        } else {
            // show new message affordance
            showNewMessageChip = true
        }
    }

    Scaffold(
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
                // Show a small button when new messages arrive and user is scrolled up
                if (showNewMessageChip) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val last = chatUiState.messages.size - 1
                                if (last >= 0) listState.animateScrollToItem(last)
                                showNewMessageChip = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("New messages", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
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
                    modifier = Modifier
                )
                Spacer(modifier = Modifier.height(56.dp)) // Height of your bottom nav bar
            }
        }
    }
}
