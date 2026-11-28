package pages
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import components.ui.LocalAppearanceControls
import components.ui.LocalTopBarOverlap
import config.appVersionName
import platform.PlatformBridge
import sub_pages.AboutPage
import sub_pages.DarkModeSettingsPage
import sub_pages.NotificationPage
import sub_pages.AccountPage
import sub_pages.AccountPageScreen
import androidx.compose.material.icons.outlined.ManageAccounts
import components.ui.bottomBarOverlap
import components.ui.topBarOverlap
import components.ui.PlatformAlertDialog

import components.ui.ContentWidth
import components.ui.centeredMaxWidth
import Authentication.LoginScreen
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import components.ui.IconBadge
import components.ui.PagePadding
import components.ui.SectionHeader
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.launch
import sub_pages.AboutPageScreen
import sub_pages.DarkModeSettingsPageScreen
import sub_pages.NotificationPageScreen
import sub_pages.PermissionsPage
import sub_pages.PermissionsPageScreen
import androidx.compose.material.icons.outlined.Security
import utils.deleteUser

/** The settings that open from the Profile page: on their own screen on phones, beside the list on large screens. */
private enum class ProfileSection(val title: String, val subtitle: String, val icon: ImageVector, val route: String) {
    Notifications("Notifications", "Reminders and alerts", Icons.Outlined.Notifications, NotificationPageScreen),
    Permissions("Permissions", "Microphone, location, health and more", Icons.Outlined.Security, PermissionsPageScreen),
    Appearance("Appearance", "Light, dark or system", Icons.Outlined.DarkMode, DarkModeSettingsPageScreen),
    About("About", "Version and credits", Icons.Outlined.Info, AboutPageScreen),
    Account("Account", "Sign out or delete your account", Icons.Outlined.ManageAccounts, AccountPageScreen),
}

@Composable
fun ProfilePage(navController: NavController) {
    val auth = Firebase.auth
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val email = remember { runCatching { auth.currentUser?.email }.getOrNull() }
    val displayName = remember { runCatching { auth.currentUser?.displayName }.getOrNull() }
    val appearance = LocalAppearanceControls.current

    // Large screens (tablets, unfolded phones): the list on the left and the chosen setting on the
    // right, like iPad Settings. Measured here, so it's the space the page itself has.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 760.dp
        val listWidth = if (maxWidth >= 1000.dp) 380.dp else 340.dp
        var selected by rememberSaveable { mutableStateOf(ProfileSection.Notifications) }

        // Both panes scroll on their own, so the page title stays large and fixed rather than
        // collapsing over one of them
        DisposableEffect(twoPane) {
            PlatformBridge.linkScrollToBars = !twoPane
            onDispose { PlatformBridge.linkScrollToBars = true }
        }

        val open: (ProfileSection) -> Unit = { section ->
            // Appearance needs the app's theme controls; without them it opens on its own screen
            if (twoPane && (section != ProfileSection.Appearance || appearance != null)) {
                selected = section
            } else {
                try { navController.navigate(section.route) } catch (_: Throwable) {}
            }
        }

        if (twoPane) {
            val titleSpace = topBarOverlap()
            CompositionLocalProvider(LocalTopBarOverlap provides 0.dp) {
                Row(modifier = Modifier.fillMaxSize().padding(top = titleSpace)) {
                    ProfileList(
                        displayName = displayName,
                        email = email,
                        selected = selected,
                        onOpen = open,
                        modifier = Modifier.width(listWidth).fillMaxHeight(),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (selected) {
                            ProfileSection.Notifications -> NotificationPage(navController, embedded = true)
                            ProfileSection.Permissions -> PermissionsPage(navController, embedded = true)
                            ProfileSection.Appearance -> appearance?.let {
                                DarkModeSettingsPage(
                                    isDarkMode = it.isDarkMode,
                                    onDarkModeToggle = it.onDarkModeToggle,
                                    useSystemDefault = it.useSystemDefault,
                                    onUseSystemDefaultToggle = it.onUseSystemDefaultToggle,
                                    navController = navController,
                                    embedded = true,
                                )
                            }
                            ProfileSection.About -> AboutPage(navController, versionNumber = appVersionName(), embedded = true)
                            ProfileSection.Account -> AccountPage(navController, embedded = true)
                        }
                    }
                }
            }
        } else {
            ProfileList(
                displayName = displayName,
                email = email,
                selected = null,
                onOpen = open,
                modifier = Modifier.centeredMaxWidth(ContentWidth.Reading).fillMaxSize(),
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** The profile card, settings list and account buttons. [selected] is highlighted (two-pane only). */
@Composable
private fun ProfileList(
    displayName: String?,
    email: String?,
    selected: ProfileSection?,
    onOpen: (ProfileSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 16.dp + topBarOverlap(), bottom = 16.dp + bottomBarOverlap()),
    ) {
        // Profile header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (displayName ?: email ?: "?").take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName?.takeIf { it.isNotBlank() } ?: "Your profile",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (email != null) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // Preferences
        item { SectionHeader("Preferences") }
        item {
            SettingsGroup {
                ProfileSection.entries.forEachIndexed { index, section ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = section.title,
                        subtitle = section.subtitle,
                        icon = section.icon,
                        selected = section == selected,
                        // In two-pane the setting opens beside the list, so no "goes somewhere" chevron
                        showChevron = selected == null,
                        onClick = { onOpen(section) },
                    )
                }
            }
        }

    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            IconBadge(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                size = 40.dp,
            )
        },
        trailingContent = if (showChevron) {
            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

