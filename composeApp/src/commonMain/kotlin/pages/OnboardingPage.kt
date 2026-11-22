package pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import components.ui.ContentWidth
import components.ui.LocalWindowSize
import components.ui.centeredMaxWidth
import components.ui.platformScaffoldInsets
import Authentication.LoginScreen
import HeroScreen
import androidx.navigation.NavController
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.launch
import utils.DEFAULT_DAILY_GOAL_MINUTES
import utils.SettingsManager

const val OnboardingScreen = "Onboarding"

/** Where the user goes when onboarding ends. */
enum class OnboardingExit {
    /** Signed out and chose to sign in or create an account. */
    SignIn,

    /** Signed out and chose to look around first, or already signed in. */
    App,
}

/**
 * The Onboarding destination: shows [OnboardingPage], saves the goal and the "done" flag, then
 * replaces itself with Login or the tab host so Back can't return to it.
 */
@Composable
fun OnboardingRoute(navController: NavController) {
    val scope = rememberCoroutineScope()
    val isSignedIn = remember { runCatching { Firebase.auth.currentUser != null }.getOrDefault(false) }
    OnboardingPage(isSignedIn = isSignedIn) { goalMinutes, reminders, exit ->
        scope.launch {
            runCatching {
                SettingsManager.saveDailyGoalMinutes(goalMinutes)
                service.WidgetSync.refresh()
                SettingsManager.saveOnboardingCompleted(true)
            }
            // Reminders chosen on the last page: ask for notifications only now, and only if wanted
            if (reminders.morning || reminders.evening) {
                runCatching {
                    val allowed = service.SessionNotifications.ensurePermission()
                    SettingsManager.saveNotificationsEnabled(allowed)
                    val current = service.DailyReminders.load()
                    service.DailyReminders.save(
                        current.copy(morningEnabled = reminders.morning, eveningEnabled = reminders.evening)
                    )
                }
            }
            val destination = if (exit == OnboardingExit.SignIn) LoginScreen else HeroScreen
            navController.navigate(destination) {
                popUpTo(OnboardingScreen) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

/** Start destination for the nav host: onboarding until it's been completed once, or null while loading. */
@Composable
fun rememberStartDestination(): String? {
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val done = runCatching { SettingsManager.loadOnboardingCompleted() }.getOrDefault(true)
        start = if (done) HeroScreen else OnboardingScreen
    }
    return start
}

private data class IntroPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val container: @Composable () -> Color,
    val content: @Composable () -> Color,
)

/** The daily nudges offered on the last onboarding page */
data class OnboardingReminders(val morning: Boolean, val evening: Boolean)

private val GoalOptions = listOf(
    5 to "Light",
    10 to "Steady",
    15 to "Focused",
    20 to "Deep",
)

/**
 * First-launch introduction: four short pages about what the app does, then a daily goal picker.
 * [onFinish] gets the chosen goal (minutes) and where to go next; the caller saves both.
 */
@Composable
fun OnboardingPage(
    isSignedIn: Boolean,
    onFinish: (goalMinutes: Int, reminders: OnboardingReminders, exit: OnboardingExit) -> Unit,
) {
    val intro = listOf(
        IntroPage(
            icon = Icons.Filled.Spa,
            title = "Welcome to MyHealthBuddy",
            body = "Small, steady steps toward a calmer, healthier you. Here's a quick look around.",
            container = { MaterialTheme.colorScheme.primaryContainer },
            content = { MaterialTheme.colorScheme.onPrimaryContainer },
        ),
        IntroPage(
            icon = Icons.Outlined.SelfImprovement,
            title = "Pause and breathe",
            body = "Guided meditations, a focus timer and short reflections for whenever you need a reset.",
            container = { MaterialTheme.colorScheme.secondaryContainer },
            content = { MaterialTheme.colorScheme.onSecondaryContainer },
        ),
        IntroPage(
            icon = Icons.Outlined.TaskAlt,
            title = "Build habits that stick",
            body = "Track the small things you do each day and watch your streak grow.",
            container = { MaterialTheme.colorScheme.tertiaryContainer },
            content = { MaterialTheme.colorScheme.onTertiaryContainer },
        ),
        IntroPage(
            icon = Icons.AutoMirrored.Outlined.Chat,
            title = "A coach in your pocket",
            body = "Ask questions, get tips and talk things through. It's here to support you, not to replace your doctor.",
            container = { MaterialTheme.colorScheme.primaryContainer },
            content = { MaterialTheme.colorScheme.onPrimaryContainer },
        ),
    )
    val goalPage = intro.size
    val pageCount = intro.size + 2 // + the goal picker and the reminders
    val lastPage = pageCount - 1
    var morningReminder by remember { mutableStateOf(false) }
    var eveningReminder by remember { mutableStateOf(false) }
    val reminders = OnboardingReminders(morningReminder, eveningReminder)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    var goalMinutes by remember { mutableIntStateOf(DEFAULT_DAILY_GOAL_MINUTES) }

    val compactHeight = LocalWindowSize.current.isCompactHeight
    val artSize = if (compactHeight) 112.dp else 200.dp

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(platformScaffoldInsets()),
    ) {
        Column(
            modifier = Modifier.centeredMaxWidth(ContentWidth.Form).fillMaxSize(),
        ) {
            // Skip jumps to the goal picker rather than past it, so everyone still sets a goal.
            // (The reminders after it are a page of their own, so they're easy to pass by.)
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (pagerState.currentPage < goalPage) {
                    TextButton(onClick = { goTo(goalPage) }) { Text("Skip") }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                if (page < intro.size) {
                    val p = intro[page]
                    PageLayout(
                        art = { BreathingArt(p.icon, p.container(), p.content(), artSize) },
                        title = p.title,
                        body = p.body,
                        compactHeight = compactHeight,
                    )
                } else if (page == lastPage) {
                    PageLayout(
                        art = {
                            BreathingArt(
                                Icons.Outlined.NotificationsActive,
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.onTertiaryContainer,
                                if (compactHeight) 88.dp else 140.dp,
                            )
                        },
                        title = "A gentle nudge?",
                        body = "A daily reminder makes it easier to keep going. Pick any you'd like; you can change them later in Profile.",
                        compactHeight = compactHeight,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReminderOption(
                                icon = Icons.Outlined.WbSunny,
                                title = "Morning meditation",
                                time = service.formatReminderTime(8, 0),
                                checked = morningReminder,
                                onCheckedChange = { morningReminder = it },
                            )
                            ReminderOption(
                                icon = Icons.Outlined.Bedtime,
                                title = "Evening check-in",
                                time = service.formatReminderTime(20, 30),
                                checked = eveningReminder,
                                onCheckedChange = { eveningReminder = it },
                            )
                        }
                    }
                } else {
                    PageLayout(
                        art = {
                            BreathingArt(
                                Icons.Outlined.Flag,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                                if (compactHeight) 88.dp else 140.dp,
                            )
                        },
                        title = "Set a daily goal",
                        body = "How many mindful minutes would you like each day? Start small: showing up matters more than how long.",
                        compactHeight = compactHeight,
                    ) {
                        GoalPicker(selected = goalMinutes, onSelect = { goalMinutes = it })
                    }
                }
            }

            PageDots(
                count = pageCount,
                current = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp),
            )

            // Bottom actions. The last page swaps Back/Next for the ways out.
            AnimatedContent(
                targetState = pagerState.currentPage == lastPage,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
                label = "onboardingActions",
            ) { onLastPage ->
                if (onLastPage) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = {
                                onFinish(goalMinutes, reminders, if (isSignedIn) OnboardingExit.App else OnboardingExit.SignIn)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Text(
                                if (isSignedIn) "Let's begin" else "Get started",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (!isSignedIn) {
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = { onFinish(goalMinutes, reminders, OnboardingExit.App) }) {
                                Text("Look around first")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pagerState.currentPage > 0) {
                            TextButton(onClick = { goTo(pagerState.currentPage - 1) }) { Text("Back") }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        Button(
                            onClick = { goTo(pagerState.currentPage + 1) },
                            modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 140.dp),
                        ) {
                            Text("Next", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageLayout(
    art: @Composable () -> Unit,
    title: String,
    body: String,
    compactHeight: Boolean,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        art()
        Spacer(Modifier.height(if (compactHeight) 20.dp else 40.dp))
        Text(
            text = title,
            style = if (compactHeight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (extra != null) {
            Spacer(Modifier.height(24.dp))
            extra()
        }
    }
}

/** A tonal disc with a soft halo that slowly breathes in and out, like the meditation timer. */
@Composable
private fun BreathingArt(icon: ImageVector, container: Color, content: Color, size: Dp) {
    val breath = rememberInfiniteTransition(label = "breath")
    val scale by breath.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathScale",
    )
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(container.copy(alpha = 0.45f), CircleShape),
        )
        Surface(
            shape = CircleShape,
            color = container,
            contentColor = content,
            modifier = Modifier.size(size * 0.72f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.3f))
            }
        }
    }
}

/** One reminder on the last onboarding page: the whole row toggles it */
@Composable
private fun ReminderOption(
    icon: ImageVector,
    title: String,
    time: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The row is the control; the switch just shows its state
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun GoalPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GoalOptions.forEach { (minutes, label) ->
            val isSelected = minutes == selected
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(minutes) }),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$minutes", style = MaterialTheme.typography.headlineSmall)
                    Text("min", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(if (active) 24.dp else 8.dp, label = "dotWidth")
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}
