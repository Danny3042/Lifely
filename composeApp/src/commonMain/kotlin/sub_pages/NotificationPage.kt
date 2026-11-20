package sub_pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import components.ui.ContentWidth
import components.ui.IconBadge
import components.ui.PagePadding
import components.ui.PlatformSwitch
import components.ui.SectionHeader
import components.ui.centeredMaxWidth
import kotlinx.coroutines.launch
import utils.SettingsManager
import utils.isAndroid

const val NotificationPageScreen = "Notification"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPage(navController: NavController) {
    val scope = rememberCoroutineScope()
    // null until the saved setting has loaded, so the switch doesn't flicker
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        enabled = runCatching { SettingsManager.loadNotificationsEnabled() }.getOrDefault(true)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        scope.launch { runCatching { SettingsManager.saveNotificationsEnabled(value) } }
    }

    val isOn = enabled == true

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            Column(
                modifier = Modifier
                    .centeredMaxWidth(ContentWidth.Reading)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PagePadding, vertical = 16.dp),
            ) {
                if (!isAndroid()) {
                    // The iOS navigation bar keeps the tab's title, so name the page here
                    Text("Notifications", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    "Gentle alerts so you know when a session is done, without having to watch the clock.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))

                // Main switch: the whole row toggles
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .toggleable(
                                value = isOn,
                                enabled = enabled != null,
                                role = Role.Switch,
                                onValueChange = ::setEnabled,
                            )
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            icon = if (isOn) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                            containerColor = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 48.dp,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow notifications", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when (enabled) {
                                    null -> "Loading…"
                                    true -> "On. You'll be told when a session ends."
                                    false -> "Off. MyHealthBuddy won't send any alerts."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        // Android: the row handles taps and the switch just shows the state.
                        // iOS: the native switch takes its own taps so its Liquid Glass thumb responds.
                        PlatformSwitch(
                            checked = isOn,
                            onCheckedChange = if (isAndroid()) null else ::setEnabled,
                            enabled = enabled != null,
                        )
                    }
                }

                SectionHeader("What you'll get")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().alpha(if (isOn) 1f else 0.55f),
                ) {
                    Column {
                        NotificationKindRow(
                            icon = Icons.Outlined.SelfImprovement,
                            title = "Meditation complete",
                            subtitle = "When your meditation timer finishes",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        NotificationKindRow(
                            icon = Icons.Outlined.Timer,
                            title = "Timer finished",
                            subtitle = "When a timer you've set runs out",
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isAndroid()) {
                            "Not getting alerts? Check that notifications for MyHealthBuddy are allowed in your phone's Settings > Apps."
                        } else {
                            "Not getting alerts? Check that notifications for MyHealthBuddy are allowed in Settings > Notifications."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationKindRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = icon,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 40.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
