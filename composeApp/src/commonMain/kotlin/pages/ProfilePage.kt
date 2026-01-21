package pages

import Authentication.LoginScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import components.SettingsListItem
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.launch
import sub_pages.AboutPageScreen
import sub_pages.NotificationPageScreen
import sub_pages.DarkModeSettingsPageScreen
import utils.SettingsManager
import utils.deleteUser

@Composable
fun ProfilePage(navController: NavController) {
    val auth = Firebase.auth
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    // val user = auth.currentUser (unused on this screen)
    // notificationsEnabled state not used yet

    // Dev mode (was accidentally removed during edits)
    var devModeEnabled by remember { mutableStateOf(false) }

    // (Dark mode is handled in its own screen; navigate to it to modify settings)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                SettingsListItem(
                    title = "Account",
                    onClick = { /* Navigate to Account settings */ },
                    leadingIcon = {
                        Icon(Icons.Outlined.Badge, contentDescription = "Account Icon")
                    }
                ) {
                    Column {
                        Button(onClick = {
                            coroutineScope.launch {
                                auth.signOut()
                                navController.navigate(LoginScreen)
                            }
                        }) {
                            Text("Sign Out")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Delete Account", color = Color.White)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            }
            item {
                SettingsListItem(
                    title = "Notifications",
                    onClick = { navController.navigate(NotificationPageScreen) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = "Notifications Icon"
                        )
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            }
            item {
                SettingsListItem(
                    title = "About",
                    onClick = { navController.navigate(AboutPageScreen) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Info, contentDescription = "About Icon")
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            }
            item {
                SettingsListItem(
                    title = "Dark Mode",
                    onClick = {
                        try {
                            navController.navigate(DarkModeSettingsPageScreen)
                        } catch (e: Throwable) {
                            // logging removed
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.DarkMode, contentDescription = "Dark Mode Icon")
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            }
            // Developer mode toggle (runtime persisted)
            item {
                SettingsListItem(
                    title = "Developer",
                    onClick = { /* no-op, toggle in content */ },
                    leadingIcon = {
                        Icon(Icons.Outlined.Badge, contentDescription = "Developer Icon")
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Dev Mode")
                        Switch(
                            checked = devModeEnabled,
                            onCheckedChange = { checked ->
                                devModeEnabled = checked
                                coroutineScope.launch {
                                    try {
                                        SettingsManager.saveDevMode(checked)
                                    } catch (_: Exception) {
                                        // ignore
                                    }
                                }
                            }
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            }
            if (showDeleteDialog) {
                item {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Confirm Account Deletion") },
                        text = { Text("Are you sure you want to delete your account? This action cannot be undone.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    // Perform deletion and navigate; handle errors inside
                                    coroutineScope.launch {
                                        try {
                                            deleteUser(auth, navController, snackbarHostState)
                                            navController.navigate(LoginScreen)
                                        } catch (_: Exception) {
                                            // ignore or surface in snackbar
                                        }
                                    }
                                    showDeleteDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("Confirm", color = Color.White)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showDeleteDialog = false }) {
                                Text("Dismiss")
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}