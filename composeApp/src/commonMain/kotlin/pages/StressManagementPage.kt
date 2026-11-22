package pages


import components.ui.bottomBarOverlap
import components.ui.topBarOverlap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import components.ui.CoachPromptCard
import components.ui.RichCoachTipCard
import service.CoachTip
import service.CoachTopic
import service.generateCoachTip
import components.ui.IconBadge
import components.ui.PagePadding
import components.ui.QuoteCard
import components.ui.SectionHeader
import components.ui.ContentWidth
import components.ui.centeredMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import keyboardUtil.hideKeyboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import service.GenerativeAiService
import sub_pages.MEDITATION_PAGE_ROUTE
import platform.PlatformBridge
import utils.isAndroid

const val STRESS_MANAGEMENT_PAGE_ROUTE = "stress_management"
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StressManagementPage(navController: NavController) {
    val scope = rememberCoroutineScope()
    var aiTip by remember { mutableStateOf<CoachTip?>(null) }
    var tipSubject by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var userActivity by remember { mutableStateOf("") }
    // New state to request navigation to meditation safely from composition
    var navigateToMeditation by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun loadTip(subject: String) {
        if (isLoading) return
        scope.launch {
            isLoading = true
            error = null
            try {
                aiTip = generateCoachTip(subject, CoachTopic.Stress)
                tipSubject = subject
            } catch (e: Exception) {
                error = service.friendlyGeminiError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun generateTipAndAddActivity() {
        try {
            if (userActivity.isBlank() || isLoading) return
            // hideKeyboard may throw on some platforms; protect it
            runCatching { hideKeyboard() }.onFailure { println("StressManagementPage: hideKeyboard failed: ${it.message}") }

            if (userActivity.trim().equals("meditate", ignoreCase = true)) {
                println("StressManagementPage: user requested meditation navigation (input='meditate')")
                // Request navigation via Compose state to avoid calling navController.navigate directly
                navigateToMeditation = true
                return
            }
            val subject = userActivity.trim()
            userActivity = ""
            loadTip(subject)
        } catch (e: Throwable) {
            println("StressManagementPage: unexpected error in generateTipAndAddActivity: ${e.message}")
        }
    }

    // Perform the navigation as a side effect when requested
    LaunchedEffect(navigateToMeditation) {
        if (navigateToMeditation) {
            // Log current navController state for debugging
            try {
                val current = try { navController.currentDestination?.route } catch (e: Throwable) { null }
                println("StressManagementPage: navigating to meditation. currentDestination=$current currentBackStackEntry=${navController.currentBackStackEntry}")

                // Use the remembered coroutine scope to call navigate on the proper context
                scope.launch {
                    try {
                        // Small delay to avoid navigation race conditions on iOS
                        delay(100)

                        // Avoid navigating if already at the route
                        val nowDest = try { navController.currentDestination?.route } catch (e: Throwable) { null }

                        if (nowDest == MEDITATION_PAGE_ROUTE) {
                            println("StressManagementPage: already at meditation route; skipping navigate")
                        } else if (!isAndroid()) {
                            // On iOS, avoid direct navController navigation — delegate to PlatformBridge
                            println("StressManagementPage: running on iOS; delegating meditation navigation to PlatformBridge")
                            try {
                                PlatformBridge.requestedRoute = MEDITATION_PAGE_ROUTE
                                PlatformBridge.requestedRouteSignal = PlatformBridge.requestedRouteSignal + 1
                                println("StressManagementPage: PlatformBridge.requestedRoute set to $MEDITATION_PAGE_ROUTE")
                            } catch (e: Throwable) {
                                println("StressManagementPage: failed to set PlatformBridge.requestedRoute: ${e.message}")
                                e.printStackTrace()
                            }
                        } else {
                            navController.navigate(MEDITATION_PAGE_ROUTE) {
                                launchSingleTop = true
                            }
                            println("StressManagementPage: navigate to meditation succeeded")
                        }
                    } catch (e: Throwable) {
                        println("StressManagementPage: failed to navigate to meditation: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } catch (e: Throwable) {
                println("StressManagementPage: LaunchedEffect navigation wrapper failed: ${e.message}")
                e.printStackTrace()
            } finally {
                // Ensure flag cleared so it can be retried
                navigateToMeditation = false
            }
        }
    }

    val quickIdeas = listOf("Deep breathing", "A short walk", "Journaling", "Calming music")

    Column(
        modifier = Modifier
            .centeredMaxWidth(ContentWidth.Reading)
            .verticalScroll(scrollState)
            .padding(top = topBarOverlap(), bottom = bottomBarOverlap())
            .padding(start = PagePadding, end = PagePadding, top = 16.dp, bottom = 32.dp),
    ) {
        // Primary action: the guided meditation timer
        MeditationHeroCard(onStart = { navigateToMeditation = true })

        SectionHeader("Ask your stress coach")
        CoachPromptCard(
            title = "Find what helps you unwind",
            subtitle = "Pick an idea or type your own, and get a practical tip for using it.",
            icon = Icons.Outlined.Spa,
            value = userActivity,
            onValueChange = { userActivity = it },
            placeholder = "e.g. Stretching before bed",
            buttonLabel = "Get a tip",
            isLoading = isLoading,
            onSubmit = ::generateTipAndAddActivity,
            extraContent = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    quickIdeas.forEach { idea ->
                        SuggestionChip(
                            onClick = {
                                userActivity = idea
                                generateTipAndAddActivity()
                            },
                            enabled = !isLoading,
                            label = { Text(idea) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                }
            },
        )

        Spacer(Modifier.height(12.dp))
        // No "Add to my habits" here: stress tips are for the moment, not habits to track
        RichCoachTipCard(
            tip = aiTip,
            isLoading = isLoading,
            error = error,
            emptyText = "Feeling overwhelmed? Slow your breathing and give yourself a short break. Your coach's tips will appear here.",
            onNewTip = tipSubject?.let { { loadTip(it) } },
        )

        Spacer(Modifier.height(16.dp))
        QuoteCard("You can't always control what goes on outside. But you can always control what goes on inside.")
    }
}

@Composable
private fun MeditationHeroCard(onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Take a mindful break", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A guided timer to help you slow down and breathe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconBadge(
                    icon = Icons.Filled.SelfImprovement,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    size = 52.dp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start meditation")
            }
        }
    }
}
