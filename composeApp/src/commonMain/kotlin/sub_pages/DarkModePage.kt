package sub_pages

import components.ui.bottomBarOverlap
import components.ui.topBarOverlap
import Colors.DarkColors
import Colors.LightColors
import Colors.platformDynamicColorScheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import components.ui.ContentWidth
import components.ui.IconBadge
import components.ui.PagePadding
import components.ui.centeredMaxWidth
import utils.isAndroid

const val DarkModeSettingsPageScreen = "DarkModeSettingsScreen"

private enum class ThemeChoice(val label: String, val icon: ImageVector) {
    System("System", Icons.Outlined.PhoneIphone),
    Light("Light", Icons.Outlined.LightMode),
    Dark("Dark", Icons.Outlined.DarkMode),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkModeSettingsPage(
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    useSystemDefault: Boolean,
    onUseSystemDefaultToggle: (Boolean) -> Unit,
    navController: NavController? = null,
    /** Shown inside another page (the Profile detail pane): no back bar of its own. */
    embedded: Boolean = false,
) {
    val current = when {
        useSystemDefault -> ThemeChoice.System
        isDarkMode -> ThemeChoice.Dark
        else -> ThemeChoice.Light
    }

    fun choose(choice: ThemeChoice) {
        when (choice) {
            ThemeChoice.System -> onUseSystemDefaultToggle(true)
            ThemeChoice.Light -> {
                onUseSystemDefaultToggle(false)
                onDarkModeToggle(false)
            }
            ThemeChoice.Dark -> {
                onUseSystemDefaultToggle(false)
                onDarkModeToggle(true)
            }
        }
    }

    // Previews use the same colours the app will actually use (wallpaper colours on Android 12+)
    val dynamicLight = platformDynamicColorScheme(darkTheme = false)
    val lightScheme = dynamicLight ?: LightColors
    val darkScheme = platformDynamicColorScheme(darkTheme = true) ?: DarkColors
    val systemIsDark = isSystemInDarkTheme()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isAndroid() && !embedded) {
                TopAppBar(
                    title = { Text("Appearance") },
                    navigationIcon = {
                        navController?.let {
                            IconButton(onClick = { it.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
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
                if (!isAndroid() || embedded) {
                    // The iOS navigation bar keeps the tab's title (and a pane has no bar), so name the page here
                    Text("Appearance", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    "Choose how MyHealthBuddy looks.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ThemeChoice.entries.forEach { choice ->
                        ThemeOptionCard(
                            choice = choice,
                            selected = choice == current,
                            lightScheme = lightScheme,
                            darkScheme = darkScheme,
                            onClick = { choose(choice) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // What the current choice means
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            icon = current.icon,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                when (current) {
                                    ThemeChoice.System -> "Matches your device"
                                    ThemeChoice.Light -> "Always light"
                                    ThemeChoice.Dark -> "Always dark"
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                when (current) {
                                    ThemeChoice.System ->
                                        "Switches with your device's setting. It's ${if (systemIsDark) "dark" else "light"} right now."
                                    ThemeChoice.Light -> "Stays light whatever your device is set to."
                                    ThemeChoice.Dark -> "Stays dark whatever your device is set to. Easier on the eyes at night."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (dynamicLight != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Colours follow your wallpaper.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionCard(
    choice: ThemeChoice,
    selected: Boolean,
    lightScheme: ColorScheme,
    darkScheme: ColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    )
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp)
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainerLow
    )

    Surface(
        // Clip first so the press highlight follows the card's rounded shape
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.62f)) {
                when (choice) {
                    ThemeChoice.Light -> AppPreview(lightScheme)
                    ThemeChoice.Dark -> AppPreview(darkScheme)
                    ThemeChoice.System -> {
                        // Half light, half dark
                        AppPreview(lightScheme)
                        AppPreview(darkScheme, modifier = Modifier.clip(RightHalfShape))
                    }
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                choice.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val RightHalfShape = GenericShape { size, _ ->
    addRect(Rect(left = size.width / 2f, top = 0f, right = size.width, bottom = size.height))
}

/** A tiny sketch of the Home screen drawn in the given colour scheme. */
@Composable
private fun AppPreview(scheme: ColorScheme, modifier: Modifier = Modifier) {
    val outer = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(outer)
            .background(scheme.background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Greeting
        Box(Modifier.fillMaxWidth(0.65f).height(7.dp).clip(CircleShape).background(scheme.onBackground.copy(alpha = 0.85f)))
        Box(Modifier.fillMaxWidth(0.4f).height(4.dp).clip(CircleShape).background(scheme.onSurfaceVariant.copy(alpha = 0.6f)))
        // Hero card with a button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.primaryContainer)
                .padding(5.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.fillMaxWidth(0.5f).height(4.dp).clip(CircleShape).background(scheme.onPrimaryContainer.copy(alpha = 0.7f)))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(scheme.primary))
        }
        // Stat tiles
        Row(
            modifier = Modifier.fillMaxWidth().weight(0.7f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(scheme.tertiaryContainer))
            Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(scheme.secondaryContainer))
        }
        // List row
        Box(Modifier.fillMaxWidth().weight(0.45f).clip(RoundedCornerShape(6.dp)).background(scheme.surfaceContainerHigh))
        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(scheme.surfaceContainer),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(if (i == 0) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(if (i == 0) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f))
                )
            }
        }
    }
}
