package sub_pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import platform.rememberSafeAreaInsets
import androidx.navigation.NavController
import utils.SettingsManager
import utils.isAndroid

const val NotificationPageScreen = "Notification"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPage(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val scrollState = rememberScrollState()
        val safeInsets = rememberSafeAreaInsets()
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(safeInsets)
        ) {
            if (isAndroid()) {
                TopAppBar(
                    title = { Text("Notifications") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
            // Notifications toggle
            val coroutineScope = rememberCoroutineScope()
            var notificationsEnabled by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                try {
                    notificationsEnabled = SettingsManager.loadNotificationsEnabled()
                } catch (_: Throwable) {
                    notificationsEnabled = true
                }
            }

            Card(modifier = Modifier.padding(10.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        text = "Enable notifications",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (notificationsEnabled) "Notifications are enabled" else "Notifications are disabled")
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { checked ->
                                notificationsEnabled = checked
                                coroutineScope.launch {
                                    try {
                                        SettingsManager.saveNotificationsEnabled(checked)
                                    } catch (_: Throwable) {}
                                }
                                // Best-effort: attempt to access the local notifier (do not call unknown APIs).
                                // Prefer gating notification sending at the source (check SettingsManager before notify).
                                runCatching<Unit> {
                                    com.mmk.kmpnotifier.notification.NotifierManager.getLocalNotifier()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}