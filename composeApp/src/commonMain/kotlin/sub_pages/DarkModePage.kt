package sub_pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import utils.isAndroid


const val DarkModeSettingsPageScreen = "DarkModeSettingsScreen"




@Composable
fun PhoneIllustrationLarge(bgColor: Color) {
    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 280.dp)
            .background(Color.Black, shape = RoundedCornerShape(36.dp)) // Bezel
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor, shape = RoundedCornerShape(28.dp))
        ) {
            // Hole punch (camera)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            )
            // Home bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .size(width = 48.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.DarkGray)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkModeSettingsPage(
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    useSystemDefault: Boolean,
    onUseSystemDefaultToggle: (Boolean) -> Unit,
    navController: NavController? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Determine the illustration color based on selection
        val phoneBgColor = when {
            useSystemDefault -> MaterialTheme.colorScheme.surfaceVariant
            isDarkMode -> Color(0xFF222222)
            else -> Color(0xFFF5F5F5)
        }

        // Use a scroll state for the content, keeping the TopAppBar fixed at the top
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxSize()) {
            if (isAndroid()) {
                TopAppBar(
                    title = { Text("Dark Mode Settings") },
                    navigationIcon = {
                        navController?.let {
                            IconButton(onClick = { it.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp)
            ) {
                // Checkboxes row at the top
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // System option - make whole column clickable for a larger tap target
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(role = Role.RadioButton) {
                                onUseSystemDefaultToggle(true)
                                // When switching to system default, delegate to caller to update actual mode
                            }
                    ) {
                        RadioButton(
                            selected = useSystemDefault,
                            onClick = {
                                onUseSystemDefaultToggle(true)
                            }
                        )
                        Icon(Icons.Default.Brightness4, contentDescription = "System Default", tint = MaterialTheme.colorScheme.onBackground)
                        Text("System", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
                    }

                    // Light option
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(role = Role.RadioButton) {
                                onUseSystemDefaultToggle(false)
                                onDarkModeToggle(false)
                            }
                    ) {
                        RadioButton(
                            selected = !useSystemDefault && !isDarkMode,
                            onClick = {
                                onUseSystemDefaultToggle(false)
                                onDarkModeToggle(false)
                            }
                        )
                        Icon(Icons.Default.Brightness7, contentDescription = "Light Mode", tint = MaterialTheme.colorScheme.onBackground)
                        Text("Light", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
                    }

                    // Dark option
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(role = Role.RadioButton) {
                                onUseSystemDefaultToggle(false)
                                onDarkModeToggle(true)
                            }
                    ) {
                        RadioButton(
                            selected = !useSystemDefault && isDarkMode,
                            onClick = {
                                onUseSystemDefaultToggle(false)
                                onDarkModeToggle(true)
                            }
                        )
                        Icon(Icons.Default.Brightness2, contentDescription = "Dark Mode", tint = MaterialTheme.colorScheme.onBackground)
                        Text("Dark", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Large phone illustration in the center
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    PhoneIllustrationLarge(bgColor = phoneBgColor)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}