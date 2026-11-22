package sub_pages

import Authentication.LoginScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import components.ui.ContentWidth
import components.ui.PagePadding
import components.ui.PlatformAlertDialog
import components.ui.SectionHeader
import components.ui.bottomBarOverlap
import components.ui.centeredMaxWidth
import components.ui.topBarOverlap
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.launch
import utils.deleteUser
import utils.isAndroid

const val AccountPageScreen = "Account"

/** Who's signed in, with Sign out and Delete account. Opens from Profile > Preferences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPage(
    navController: NavController,
    /** Shown inside another page (the Profile detail pane): no back bar of its own. */
    embedded: Boolean = false,
) {
    val auth = Firebase.auth
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val email = remember { runCatching { auth.currentUser?.email }.getOrNull() }
    val displayName = remember { runCatching { auth.currentUser?.displayName }.getOrNull() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isAndroid() && !embedded) {
                    TopAppBar(
                        title = { Text("Account") },
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
                        .padding(top = topBarOverlap(), bottom = bottomBarOverlap())
                        .padding(horizontal = PagePadding, vertical = 16.dp),
                ) {
                    if (embedded) {
                        // A pane in Profile has no bar of its own, so name the page here (the bars show the title otherwise)
                        Text("Account", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        "The account MyHealthBuddy uses to keep your habits, chats and progress.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(20.dp))

                    // Who's signed in
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (displayName ?: email ?: "?").take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Signed in as",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = email ?: displayName ?: "Unknown account",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                auth.signOut()
                                navController.navigate(LoginScreen)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out")
                    }

                    SectionHeader("Delete account")
                    Text(
                        "Permanently removes your account and everything saved with it. This can't be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete account")
                    }
                }
            }

            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showDeleteDialog) {
        PlatformAlertDialog(
            title = "Delete your account?",
            message = "This permanently removes your account and data. This action cannot be undone.",
            confirmText = "Delete",
            destructive = true,
            icon = Icons.Outlined.DeleteForever,
            onConfirm = {
                scope.launch {
                    try {
                        deleteUser(auth, navController, snackbarHostState)
                        navController.navigate(LoginScreen)
                    } catch (_: Exception) {
                        // surfaced by deleteUser via snackbar where possible
                    }
                }
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
