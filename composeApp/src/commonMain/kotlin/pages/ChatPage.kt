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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import components.VoiceModeDialog
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import components.buddy.BuddyActions
import components.buddy.BuddyPrivacy
import components.buddy.GuidedCheckInSheet
import components.buddy.NowRow
import components.buddy.TalkToPersonRed
import components.buddy.TalkToPersonSheet
import components.buddy.TodayTimelineHeader
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import components.buddy.BuddyDay
import components.buddy.suggestions
import components.buddy.systemInstruction
import components.ui.BreathingBuddy
import components.ui.BuddyExpression
import components.ui.SkyScene
import utils.DEFAULT_DAILY_GOAL_MINUTES
import utils.MoodCheckInStore
import model.ChatViewModel
import service.GenerativeAiService
import components.ModelChatMessage
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import tts.getTtsService
import tts.TtsSettings
import platform.registerComposeNewChatListener
import service.weather.BuddyWeather
import service.weather.pillText
import service.weather.rememberWeatherOptIn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    // Every message carries the Buddy's instructions with today's context (read fresh each time)
    chatViewModel: ChatViewModel = remember {
        ChatViewModel(GenerativeAiService.instance) { BuddyDay.now(BuddyGoal.minutes).systemInstruction() }
    },
) {
    val chatUiState = chatViewModel.uiState
    // What the Buddy knows about today: the goal and check-ins are loaded once, the rest is read live
    LaunchedEffect(Unit) {
        BuddyPrivacy.load()
        // Local weather, only if the user turned it on (does nothing otherwise)
        launch { BuddyWeather.refresh() }
        BuddyGoal.minutes = BuddyDay.loadGoal()
        runCatching { if (MoodCheckInStore.checkIns.isEmpty()) MoodCheckInStore.load() }
    }

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
    // Automatic scrolls are reported through nested scrolling like a drag, so the native large title
    // collapses with them. A plain programmatic scroll left the title sitting over the messages.
    val scrollDispatcher = remember { NestedScrollDispatcher() }
    suspend fun scrollToEnd() {
        repeat(30) {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return
            val viewportEnd = info.viewportEndOffset - info.afterContentPadding
            val distance = if (last.index == info.totalItemsCount - 1) {
                (last.offset + last.size - viewportEnd).toFloat()
            } else {
                info.viewportSize.height * 0.8f
            }
            if (distance <= 1f) return
            val consumed = listState.animateScrollBy(distance)
            scrollDispatcher.dispatchPostScroll(Offset(0f, -consumed), Offset.Zero, NestedScrollSource.UserInput)
            if (consumed < 1f) return
        }
    }
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
    // After a finished reply the thread ends with a "Now" row of ways to carry on
    val showNowRow = messages.lastOrNull() is ModelChatMessage.LoadedModelMessage && chatUiState.canSendMessage
    // When messages change, auto-scroll only if the user is already near the bottom.
    LaunchedEffect(messages.size, TtsSettings.enabled) {
        if (messages.isEmpty()) return@LaunchedEffect
        val leadingItems = if (messages.isNotEmpty()) 1 else 0
        val trailingItems = if (showNowRow) 1 else 0
        val lastIndex = messages.size - 1 + leadingItems
        // The list's true last item (the "Now" row counts when it's showing)
        val listLastIndex = lastIndex + trailingItems
        // Determine currently visible last item; if it's within 1 item of the end, auto-scroll.
        val visible = listState.layoutInfo.visibleItemsInfo
        val lastVisibleIndex = visible.lastOrNull()?.index ?: -1
        val shouldAuto = lastVisibleIndex >= (listLastIndex - 1)
        if (shouldAuto) {
            // scroll to the latest message
            coroutineScope.launch { scrollToEnd() }
        }

        // Speak latest model message when TTS is enabled
        val lastMessage = messages.lastOrNull()
        if (TtsSettings.enabled && lastMessage is ModelChatMessage.LoadedModelMessage) {
            // Use Gemini TTS where available
            try {
                getTtsService().speak(
                    BuddyActions.clean(lastMessage.text),
                    useGemini = true,
                    voice = TtsSettings.selectedVoice.googleVoiceName,
                )
            } catch (_: Throwable) {}
        }
    }

    // When a reply finishes it can grow (its cards appear): bring its end into view if the
    // conversation was being followed
    val lastId = messages.lastOrNull()?.id
    // A new (empty) chat is back at the top: let the native large title expand again
    LaunchedEffect(messages.isEmpty()) {
        if (messages.isEmpty()) scrollDispatcher.dispatchPostScroll(Offset.Zero, Offset(0f, 1f), NestedScrollSource.UserInput)
    }
    val lastWasLoading = remember { mutableStateOf(false) }
    LaunchedEffect(lastId) {
        // Only a reply finishing now (loading -> loaded), not a chat restored or reopened
        val finishedNow = lastWasLoading.value && messages.lastOrNull() is ModelChatMessage.LoadedModelMessage
        lastWasLoading.value = messages.lastOrNull() is ModelChatMessage.LoadingModelMessage
        if (!finishedNow) return@LaunchedEffect
        kotlinx.coroutines.delay(150)
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisible >= messages.lastIndex + 1) scrollToEnd()
    }

    // The Now row appears once a reply is done: keep it in view if the conversation was being followed
    LaunchedEffect(showNowRow) {
        if (!showNowRow) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisible >= messages.lastIndex + 1) scrollToEnd()
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
            // Re-read when the chat empties (a new chat) or a mood is logged, so the greeting stays current
            val day = remember(messages.isEmpty(), BuddyGoal.minutes, MoodCheckInStore.checkIns.size, BuddyWeather.current) {
                BuddyDay.now(BuddyGoal.minutes)
            }
            val showSidebar = maxWidth >= 760.dp
            val sidebarWidth = if (maxWidth >= 1000.dp) 320.dp else 280.dp
            val sendMessage: (String, ByteArray?) -> Unit = { inputText, image ->
                chatViewModel.sendMessage(inputText, image)
                // After sending, attempt to scroll to latest (the LaunchedEffect above will also handle this).
                coroutineScope.launch {
                    val last = chatUiState.messages.size
                    if (last > 0) scrollToEnd()
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
                        modifier = Modifier.centeredMaxWidth(ContentWidth.Reading).fillMaxSize()
                            .nestedScroll(remember { object : NestedScrollConnection {} }, scrollDispatcher),
                        state = listState,
                        reverseLayout = false,
                        // Keep the last message clear of the input bar and the New chat button above it
                        contentPadding = PaddingValues(top = 8.dp + topBarOverlap(), bottom = 176.dp + bottomBarOverlap()),
                    ) {
                        if (chatUiState.messages.isNotEmpty()) {
                            item(key = "day-thread-context") {
                                TodayTimelineHeader()
                            }
                        }
                        itemsIndexed(
                            items = chatUiState.messages,
                            key = { _, message -> message.id },
                        ) { index, message ->
                            ChatBubbleItem(
                                chatMessage = message,
                                // Failed messages are user messages: retry resubmits the text (no image)
                                onRetry = { id, text -> chatViewModel.retryMessage(id, text, null) },
                                isLatest = index == chatUiState.messages.lastIndex,
                                isNight = day.isNight,
                                canSend = chatUiState.canSendMessage,
                                onSendMessage = { text -> sendMessage(text, null) },
                                day = day,
                            )
                        }
                        if (showNowRow) {
                            item(key = "now-row") {
                                NowRow(day = day, onSend = { text -> sendMessage(text, null) })
                            }
                        }
                    }
                    var showCheckIn by remember { mutableStateOf(false) }
                    var showTalkToPerson by remember { mutableStateOf(false) }
                    if (showCheckIn) {
                        GuidedCheckInSheet(
                            onDismiss = { showCheckIn = false },
                            onComplete = { summary ->
                                showCheckIn = false
                                sendMessage(summary, null)
                            },
                            onTypeInstead = { showCheckIn = false },
                        )
                    }
                    if (showTalkToPerson) TalkToPersonSheet(onDismiss = { showTalkToPerson = false })
                    if (messages.isEmpty()) {
                        ChatEmptyState(
                            day = day,
                            onSuggestion = { prompt -> sendMessage(prompt, null) },
                            onStartCheckIn = { showCheckIn = true },
                            onTalkToPerson = { showTalkToPerson = true },
                            enabled = chatUiState.canSendMessage,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .widthIn(max = ContentWidth.Reading)
                                // Clear of the input and the Past chats / New chat buttons above it
                                .padding(top = topBarOverlap(), bottom = 132.dp + bottomBarOverlap()),
                        )
                    }
                    var showLiveVoiceMode by remember { mutableStateOf(false) }
                    LaunchedEffect(PlatformBridge.showLiveVoiceMode) {
                        if (PlatformBridge.showLiveVoiceMode) {
                            showLiveVoiceMode = true
                            PlatformBridge.showLiveVoiceMode = false
                        }
                    }
                    if (showLiveVoiceMode) {
                        VoiceModeDialog(
                            onResult = { prompt ->
                                sendMessage(prompt, null)
                                showLiveVoiceMode = false
                            },
                            onDismiss = { showLiveVoiceMode = false }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .widthIn(max = ContentWidth.Reading)
                    ) {
                        // Voice mode, Past chats, and "New chat" sit just above the input's trailing end.
                        // With the sidebar, Past chats and New chat live there, so only voice mode stays.
                        val hasHistory = chatViewModel.savedChats.isNotEmpty()
                        run {
                            Row(
                                modifier = Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GlassIconButton(
                                    iosSymbol = "waveform.circle.fill",
                                    icon = Icons.Default.GraphicEq,
                                    contentDescription = "Voice mode with Buddy",
                                    onClick = { showLiveVoiceMode = true },
                                    prominent = true,
                                )
                                if (!showSidebar && hasHistory) {
                                    GlassIconButton(
                                        iosSymbol = "clock.arrow.circlepath",
                                        icon = Icons.Outlined.History,
                                        contentDescription = "Past chats",
                                        onClick = { showHistory = true },
                                        prominent = false,
                                    )
                                }
                                if (!showSidebar && messages.isNotEmpty()) {
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

/** The daily goal, for the Buddy's context (loaded when the chat opens) */
private object BuddyGoal {
    var minutes by mutableStateOf(DEFAULT_DAILY_GOAL_MINUTES)
}

/**
 * An empty chat: the Buddy greets you with something that fits your day, shows what it knows
 * (minutes today, streak, last mood) and offers starters picked for right now. Tap the Buddy to
 * make it happy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatEmptyState(
    day: BuddyDay,
    onSuggestion: (String) -> Unit,
    onStartCheckIn: () -> Unit,
    onTalkToPerson: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var cheered by remember { mutableStateOf(false) }
    LaunchedEffect(cheered) {
        if (cheered) {
            kotlinx.coroutines.delay(1_800)
            cheered = false
        }
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(30.dp)),
        ) {
            SkyScene(isNight = day.isNight, elapsed = day.hour / 24f)
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
            ) {
                Text(
                    "Buddy",
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (day.isNight) Color.White else Color(0xFF123148),
                )
                Text(
                    "Here with you this ${day.partOfDay}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (day.isNight) Color.White.copy(alpha = 0.88f) else Color(0xFF234A63),
                )
            }
            BreathingBuddy(
                breath = 0f,
                isRunning = false,
                isNight = day.isNight,
                expression = if (cheered) BuddyExpression.Happy else BuddyExpression.Calm,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 10.dp)
                    .size(76.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Say hello",
                    ) { cheered = true },
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("A LITTLE CONTEXT FOR TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (BuddyPrivacy.shareMinutes) {
                DayPill(if (day.goalMet) "Goal reached" else "${day.minutesToday}/${day.goalMinutes} min today")
                if (day.streakDays > 0) DayPill("${day.streakDays}-day streak")
            }
            if (BuddyPrivacy.shareMood) day.lastMoodLabel?.let { DayPill("Feeling: ${it.lowercase()}") }
            if (BuddyPrivacy.shareWeather) day.weather?.let { DayPill(it.pillText()) }
        }
        WeatherOptInCard()
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("What feels most present?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pick a place to start, or tell Buddy in your own words.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onStartCheckIn,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Start a 1-minute check-in") }
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    listOf("Work", "Sleep", "Money", "Family", "Health", "Something else").forEach { topic ->
                        SuggestionChip(
                            onClick = {
                                val prompt = if (topic == "Something else") {
                                    "I'd like to talk about something else."
                                } else {
                                    "I'd like to talk about $topic."
                                }
                                onSuggestion(prompt)
                            },
                            enabled = enabled,
                            label = { Text(topic) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Or try one of these", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            day.suggestions().forEach { prompt ->
                SuggestionChip(
                    onClick = { onSuggestion(prompt) },
                    enabled = enabled,
                    label = { Text(prompt) },
                )
            }
        }
        TextButton(onClick = onTalkToPerson, modifier = Modifier.heightIn(min = 44.dp)) {
            Icon(Icons.Outlined.Phone, contentDescription = null, tint = TalkToPersonRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Talk to a person", color = TalkToPersonRed)
        }
    }
}

@Composable
private fun DayPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

private const val WEATHER_PROMPT_FILE = "weather_prompt_dismissed.txt"

/** One-time offer to turn on local weather. Gone once it's on, or once "Not now" is tapped. */
@Composable
private fun WeatherOptInCard() {
    var dismissed by remember {
        mutableStateOf(runCatching { platform.readTextFile(WEATHER_PROMPT_FILE).trim() == "1" }.getOrDefault(false))
    }
    var denied by remember { mutableStateOf(false) }
    val enable = rememberWeatherOptIn(onDenied = { denied = true })
    if (BuddyPrivacy.shareWeather || dismissed) return
    fun dismiss() {
        dismissed = true
        runCatching { platform.writeTextFile(WEATHER_PROMPT_FILE, "1") }
    }
    Spacer(Modifier.height(12.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Match Buddy to your weather?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Uses your approximate location to check the weather. Never stored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (denied) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Location is off for MyHealthBuddy. You can turn it on in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { denied = false; enable() }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Turn on") }
                TextButton(onClick = { dismiss() }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Not now") }
            }
        }
    }
}
