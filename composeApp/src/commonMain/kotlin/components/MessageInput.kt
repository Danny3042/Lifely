package components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MessageInput(
    enabled: Boolean,
    onSendMessage: (prompt: String, image: ByteArray?) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    onNewChat: (() -> Unit)? = null,
)
