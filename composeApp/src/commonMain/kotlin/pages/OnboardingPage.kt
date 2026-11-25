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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import utils.isAndroid
import components.ui.GlassPageIndicator
import components.ui.GlassTextButton
import components.ui.IconSky
import components.ui.PlatformSwitch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import components.ui.systemBarsContentPadding
import androidx.compose.ui.graphics.luminance

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
    val allIntro = listOf(
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
    // iPhone and iPad: a shorter introduction (Apple's onboarding guidance: brief, nothing to memorise),
    // with meditation and habits on one page. Android keeps its four pages.
    val intro = if (isAndroid()) allIntro else listOf(
        allIntro[0],
        IntroPage(
            icon = Icons.Outlined.SelfImprovement,
            title = "Breathe and build habits",
            body = "Guided meditations and short reflections for whenever you need a reset, plus simple habit tracking to keep your streak growing.",
            container = { MaterialTheme.colorScheme.secondaryContainer },
            content = { MaterialTheme.colorScheme.onSecondaryContainer },
        ),
        allIntro[3],
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
    // Start from what's already set, so seeing the introduction again (from About) changes nothing
    // unless the person changes it. On first launch these are the defaults.
    LaunchedEffect(Unit) {
        runCatching { goalMinutes = SettingsManager.loadDailyGoalMinutes() }
        runCatching {
            val saved = service.DailyReminders.load()
            morningReminder = saved.morningEnabled
            eveningReminder = saved.eveningEnabled
        }
    }

    val compactHeight = LocalWindowSize.current.isCompactHeight
    val artSize = if (compactHeight) 112.dp else 200.dp

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    // iPhone and iPad: native Liquid Glass over the app icon's sky. Android keeps the Material layout below.
    if (!isAndroid()) {
        GlassOnboarding(
            intro = intro,
            pagerState = pagerState,
            goalPage = goalPage,
            lastPage = lastPage,
            goalMinutes = goalMinutes,
            onGoalChange = { goalMinutes = it },
            morningReminder = morningReminder,
            onMorningChange = { morningReminder = it },
            eveningReminder = eveningReminder,
            onEveningChange = { eveningReminder = it },
            isSignedIn = isSignedIn,
            goTo = ::goTo,
            onFinish = { exit -> onFinish(goalMinutes, reminders, exit) },
        )
        return
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


// ---------------------------------------------------------------------------------------------
// iOS: Liquid Glass onboarding (iPhone and iPad)
// ---------------------------------------------------------------------------------------------

@Composable
private fun GlassOnboarding(
    intro: List<IntroPage>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    goalPage: Int,
    lastPage: Int,
    goalMinutes: Int,
    onGoalChange: (Int) -> Unit,
    morningReminder: Boolean,
    onMorningChange: (Boolean) -> Unit,
    eveningReminder: Boolean,
    onEveningChange: (Boolean) -> Unit,
    isSignedIn: Boolean,
    goTo: (Int) -> Unit,
    onFinish: (OnboardingExit) -> Unit,
) {
    val window = LocalWindowSize.current
    // iPad in landscape and phones on their side: art and text side by side
    val twoPane = window.useTwoPane
    // iPad (either way up): more room, so larger art and type
    val roomy = !window.isCompactWidth && !window.isCompactHeight
    val artSize = when {
        window.isCompactHeight -> 120.dp
        roomy -> 280.dp
        else -> 200.dp
    }
    val isNight = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val current = pagerState.currentPage
    // One side margin for the top and bottom controls
    val edge = 20.dp

    Box(Modifier.fillMaxSize()) {
        // Opening: the hills rise and the sun comes up when onboarding first appears, then the
        // hills keep drifting very slowly
        val reveal = remember { Animatable(0f) }
        LaunchedEffect(Unit) { reveal.animateTo(1f, tween(2200, easing = LinearEasing)) }
        val drift by rememberInfiniteTransition(label = "skyDrift").animateFloat(
            initialValue = 0f,
            targetValue = 2f * kotlin.math.PI.toFloat(),
            animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
            label = "skyDriftPhase",
        )
        // The sky runs under the status bar and home indicator; content stays clear of them
        IconSky(
            isNight = isNight,
            modifier = Modifier.fillMaxSize(),
            reveal = reveal.value,
            phase = drift,
        )
        Column(Modifier.fillMaxSize().systemBarsContentPadding()) {
            // Skip ends onboarding straight away with the defaults (a 10-minute goal, no reminders), so it's
            // truly optional, per Apple's onboarding guidance. The goal and reminders can be changed later.
            // Same side margin as the bottom bar, so Skip and Next line up on the trailing edge.
            Box(
                // Side by side: Skip lines up with the page's text column, like Next below it
                modifier = (if (twoPane) Modifier.centeredMaxWidth(ContentWidth.Wide).fillMaxWidth().padding(horizontal = 40.dp)
                    else Modifier.fillMaxWidth().padding(horizontal = edge)).height(56.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (current < lastPage) {
                    GlassTextButton("Skip", onClick = { onFinish(OnboardingExit.App) }, modifier = Modifier.width(76.dp).height(40.dp))
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                when {
                    page < intro.size -> GlassPageLayout(
                        art = { scale -> SkyArt(intro[page].icon, artSize * scale) },
                        title = intro[page].title,
                        body = intro[page].body,
                        twoPane = twoPane,
                        roomy = roomy,
                    )
                    page == lastPage -> GlassPageLayout(
                        art = { scale -> SkyArt(Icons.Outlined.NotificationsActive, artSize * 0.75f * scale) },
                        title = "A gentle nudge?",
                        body = "A daily reminder makes it easier to keep going. Pick any you'd like; you can change them later in Profile.",
                        twoPane = twoPane,
                        roomy = roomy,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkyReminderOption(
                                icon = Icons.Outlined.WbSunny,
                                title = "Morning meditation",
                                time = service.formatReminderTime(8, 0),
                                checked = morningReminder,
                                onCheckedChange = onMorningChange,
                            )
                            SkyReminderOption(
                                icon = Icons.Outlined.Bedtime,
                                title = "Evening check-in",
                                time = service.formatReminderTime(20, 30),
                                checked = eveningReminder,
                                onCheckedChange = onEveningChange,
                            )
                        }
                    }
                    else -> GlassPageLayout(
                        art = { scale -> SkyArt(Icons.Outlined.Flag, artSize * 0.75f * scale) },
                        title = "Set a daily goal",
                        body = "How many mindful minutes would you like each day? Start small: showing up matters more than how long.",
                        twoPane = twoPane,
                        roomy = roomy,
                    ) {
                        SkyGoalPicker(selected = goalMinutes, onSelect = onGoalChange)
                    }
                }
            }

            val dots: @Composable () -> Unit = {
                GlassPageIndicator(count = lastPage + 1, current = current, onSelect = goTo)
            }
            // iPad: actions centred at reading width. Phones and iPhone Duo: the full width with the
            // same side margin as Skip, so every control lines up on the same edges.
            // Side by side (iPhone Duo open, phones on their side, iPad landscape): the bar spans the
            // page's own content width, so Back sits under the art and Next under the text.
            val barModifier = when {
                twoPane -> Modifier.centeredMaxWidth(ContentWidth.Wide).fillMaxWidth().padding(horizontal = 40.dp)
                roomy -> Modifier.centeredMaxWidth(ContentWidth.Form).fillMaxWidth().padding(horizontal = edge)
                else -> Modifier.fillMaxWidth().padding(horizontal = edge)
            }
            // Side by side, the dots share the buttons' row so the page keeps its height.
            // Otherwise they sit centred above the buttons.
            val inlineDots = twoPane
            if (!inlineDots) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { dots() }
            }
            Box(barModifier.padding(top = if (inlineDots) 8.dp else 0.dp, bottom = 16.dp)) {
                if (current == lastPage) {
                    val primary: @Composable (Modifier) -> Unit = { m ->
                        GlassTextButton(
                            title = if (isSignedIn) "Let's Begin" else "Get Started",
                            onClick = { onFinish(if (isSignedIn) OnboardingExit.App else OnboardingExit.SignIn) },
                            modifier = m.height(50.dp),
                            prominent = true,
                        )
                    }
                    val secondary: @Composable (Modifier) -> Unit = { m ->
                        GlassTextButton(
                            title = "Look Around First",
                            onClick = { onFinish(OnboardingExit.App) },
                            modifier = m.height(46.dp),
                        )
                    }
                    if (inlineDots) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) { dots() }
                            if (!isSignedIn) secondary(Modifier.width(170.dp))
                            primary(Modifier.width(150.dp))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            primary(Modifier.fillMaxWidth())
                            if (!isSignedIn) secondary(Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Equal-width sides keep the dots exactly centred between Back and Next
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (current > 0) {
                                GlassTextButton("Back", onClick = { goTo(current - 1) }, modifier = Modifier.width(96.dp).height(48.dp))
                            }
                        }
                        if (inlineDots) dots()
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            GlassTextButton(
                                "Next",
                                onClick = { goTo(current + 1) },
                                modifier = Modifier.width(112.dp).height(48.dp),
                                prominent = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One onboarding page: stacked on phones and iPad portrait, side by side when wide and short. */
@Composable
private fun GlassPageLayout(
    /** The page's art at the given scale (smaller on short screens) */
    art: @Composable (scale: Float) -> Unit,
    title: String,
    body: String,
    twoPane: Boolean,
    roomy: Boolean,
    extra: (@Composable () -> Unit)? = null,
) {
    val titleStyle = if (roomy) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium
    val bodyStyle = if (roomy) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
    if (twoPane) {
        Row(
            modifier = Modifier.centeredMaxWidth(ContentWidth.Wide).fillMaxSize().padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { art(1f) }
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(title, style = titleStyle, color = Color.White, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(12.dp))
                Text(body, style = bodyStyle, color = Color.White.copy(alpha = 0.88f))
                if (extra != null) {
                    Spacer(Modifier.height(24.dp))
                    extra()
                }
            }
        }
    } else BoxWithConstraints(Modifier.fillMaxSize()) {
        // Short screens (iPhone Duo folded, small phones): fit the page instead of scrolling it.
        // The native switches on the reminders page aren't clipped by Compose, so an overflowing
        // page showed them on top of the buttons.
        val short = !roomy && maxHeight < 560.dp
        val showArt = !(short && extra != null && maxHeight < 470.dp)
        val pageTitleStyle = if (short) MaterialTheme.typography.headlineSmall else titleStyle
        val pageBodyStyle = if (short) MaterialTheme.typography.bodyMedium else bodyStyle
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (short) 20.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showArt) {
                art(if (short) 0.65f else 1f)
                Spacer(Modifier.height(if (roomy) 48.dp else if (short) 12.dp else 32.dp))
            }
            Column(
                modifier = Modifier.centeredMaxWidth(if (roomy) ContentWidth.Reading else ContentWidth.Form),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = pageTitleStyle,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(if (short) 8.dp else 12.dp))
                Text(body, style = pageBodyStyle, color = Color.White.copy(alpha = 0.88f), textAlign = TextAlign.Center)
                if (extra != null) {
                    Spacer(Modifier.height(if (short) 16.dp else 28.dp))
                    extra()
                }
            }
        }
    }
}

/**
 * The page's symbol in a soft translucent disc that breathes like the meditation timer. Content,
 * not a control, so it isn't Liquid Glass.
 */
@Composable
private fun SkyArt(icon: ImageVector, size: Dp) {
    val breath = rememberInfiniteTransition(label = "skyBreath")
    val scale by breath.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skyBreathScale",
    )
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(Color.White.copy(alpha = 0.14f), CircleShape),
        )
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            modifier = Modifier.size(size * 0.68f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.28f))
            }
        }
    }
}

/** Selected: solid white with the sky's purple text; not selected: a light translucent card. */
private val SkyInk = Color(0xFF3B2F7A)

@Composable
private fun SkyGoalPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GoalOptions.forEach { (minutes, label) ->
            val isSelected = minutes == selected
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.18f),
                contentColor = if (isSelected) SkyInk else Color.White,
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
private fun SkyReminderOption(
    icon: ImageVector,
    title: String,
    time: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (checked) Color.White else Color.White.copy(alpha = 0.18f),
        contentColor = if (checked) SkyInk else Color.White,
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
                Text(time, style = MaterialTheme.typography.bodyMedium)
            }
            // The native UISwitch: Liquid Glass on iOS 26+. Tapping the row or the switch toggles it.
            PlatformSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
