package sub_pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import platform.rememberSafeAreaInsets
import androidx.navigation.NavController
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
            Card(
                modifier = Modifier.padding(10.dp),
            ) {
                Text(
                    text = "Notifications can be turned on and off in the settings app.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}