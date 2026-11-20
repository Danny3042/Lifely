package sub_pages

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import components.ui.ContentWidth
import components.ui.IconBadge
import components.ui.PagePadding
import components.ui.SectionHeader
import components.ui.centeredMaxWidth
import utils.isAndroid

const val AboutPageScreen = "About"

private data class AboutItem(val icon: ImageVector, val title: String, val subtitle: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(navController: NavController, versionNumber: String) {
    val features = listOf(
        AboutItem(Icons.Outlined.SelfImprovement, "Guided meditation", "A calming timer with streaks and weekly insights"),
        AboutItem(Icons.Outlined.Checklist, "Habit coaching", "Build healthy habits with practical tips"),
        AboutItem(Icons.Outlined.AutoAwesome, "Gemini chat", "Ask about sleep, stress and everyday wellbeing"),
        AboutItem(
            Icons.Outlined.FavoriteBorder,
            "Health data",
            if (isAndroid()) "Connects with Health Connect" else "Connects with Apple Health",
        ),
    )
    val builtWith = listOf(
        AboutItem(Icons.Outlined.Code, "Kotlin & Compose Multiplatform", "One shared codebase for Android and iOS"),
        AboutItem(Icons.Outlined.Palette, "Material 3 Expressive", "Google's latest design system"),
        AboutItem(Icons.Outlined.Psychology, "Google Gemini", "Powers the chat and personalised tips"),
        AboutItem(Icons.Outlined.Cloud, "Firebase", "Sign-in and crash reporting"),
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isAndroid()) {
                TopAppBar(
                    title = { Text("About") },
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
                AppHeader(versionNumber)

                SectionHeader("What's inside")
                AboutGroup(
                    items = features,
                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                    iconContent = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                SectionHeader("Built with")
                AboutGroup(
                    items = builtWith,
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconContent = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                Spacer(Modifier.height(24.dp))
                WellbeingNote()

                // Footer
                Spacer(Modifier.height(32.dp))
                Text(
                    "Made by Daniel Ramzani",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "© 2026 MyHealthBuddy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun AppHeader(versionNumber: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(
            icon = Icons.Filled.Spa,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 88.dp,
            shape = MaterialTheme.shapes.extraLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text("MyHealthBuddy", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Mindfulness, habits and AI coaching in one calm place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (versionNumber.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    "Version $versionNumber",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** A rounded group of rows separated by thin dividers, like iOS/Android settings lists. */
@Composable
private fun AboutGroup(items: List<AboutItem>, iconContainer: Color, iconContent: Color) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = item.icon,
                        containerColor = iconContainer,
                        contentColor = iconContent,
                        size = 40.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            item.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WellbeingNote() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "MyHealthBuddy supports your wellbeing but isn't medical advice. For any health concerns, talk to a doctor or other healthcare professional.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
