package widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColor
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import org.danielramzani.HealthCompose.MainActivity
import org.danielramzani.HealthCompose.R
import service.WidgetPlatform
import service.WidgetSnapshot
import utils.MoodCheckIn
import utils.MoodCheckInStore

private val SNAPSHOT_KEY = stringPreferencesKey("snapshot")
private val json = Json { ignoreUnknownKeys = true }

// The app's greens, light and dark
private object Palette {
    val background = ColorProvider(day = Color(0xFF9FE1CB), night = Color(0xFF0E2E27))
    val card = ColorProvider(day = Color(0xFFE1F5EE), night = Color(0xFF1F4A41))
    val text = ColorProvider(day = Color(0xFF04342C), night = Color(0xFFE1F5EE))
    val secondary = ColorProvider(day = Color(0xFF085041), night = Color(0xFF9FE1CB))
    val accent = ColorProvider(day = Color(0xFF0F6E56), night = Color(0xFF5DCAA5))
    val onAccent = ColorProvider(day = Color(0xFFE1F5EE), night = Color(0xFF04342C))
    val flame = ColorProvider(day = Color(0xFF993C1D), night = Color(0xFFF0997B))
}

/** The check-in scale's colours (moodColor in MoodCheckInCard.kt), for the five quick choices */
private val quickMoods = listOf(
    -3 to Color(0xFF5B3FD9),
    -2 to Color(0xFF7A5AF8),
    0 to Color(0xFF3A9BDC),
    2 to Color(0xFFF2B233),
    3 to Color(0xFFF28A1A),
)
private val moodLabels = mapOf(-3 to "Very unpleasant", -2 to "Unpleasant", -1 to "Slightly unpleasant", 0 to "Neutral", 1 to "Slightly pleasant", 2 to "Pleasant", 3 to "Very pleasant")

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)

/**
 * Today's mindful minutes, your streak, a Meditate button, and (when wide) a row of colours to log
 * how you feel. Resizes between 2×2 and 4×2. The app pushes a new snapshot whenever its numbers change.
 */
class MindfulWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // A widget just added has no state yet: start from the app's last snapshot
        val saved = context.getSharedPreferences(WidgetPlatform.PREFS, Context.MODE_PRIVATE).getString(WidgetPlatform.KEY, null)
        provideContent {
            val text = currentState<Preferences>()[SNAPSHOT_KEY] ?: saved
            val snapshot = text?.let { runCatching { json.decodeFromString(WidgetSnapshot.serializer(), it) }.getOrNull() }
            Content(context, snapshot)
        }
    }

    @Composable
    private fun Content(context: Context, snapshot: WidgetSnapshot?) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val isToday = snapshot?.day == today
        val minutes = if (isToday) snapshot?.todayMinutes ?: 0 else 0
        val goal = (snapshot?.goalMinutes ?: 10).coerceAtLeast(1)
        val streak = snapshot?.streakDays ?: 0
        val wide = LocalSize.current.width >= WIDE.width

        val meditate = actionStartActivity(
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_MEDITATE)
        )
        val checkIn = actionStartActivity(
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CHECK_IN)
        )

        Column(
            modifier = GlanceModifier.fillMaxSize().background(Palette.background).cornerRadius(28.dp).padding(14.dp),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val last = snapshot?.lastMoodValence
                Text(
                    when {
                        !wide -> "Mindful today"
                        last != null -> "Last: ${moodLabels[last] ?: "logged"} · how now?"
                        else -> "How are you feeling?"
                    },
                    maxLines = 1,
                    style = TextStyle(color = Palette.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (streak > 0) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_flame),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Palette.flame),
                        modifier = GlanceModifier.size(14.dp),
                    )
                    Text(" $streak", style = TextStyle(color = Palette.flame, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                }
            }

            Spacer(GlanceModifier.defaultWeight())

            if (wide) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = GlanceModifier.defaultWeight().background(Palette.card).cornerRadius(18.dp).padding(horizontal = 12.dp, vertical = 8.dp).clickable(meditate),
                    ) {
                        Text("Today", style = TextStyle(color = Palette.accent, fontSize = 11.sp))
                        Text("$minutes of $goal min", style = TextStyle(color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    SquareButton(R.drawable.ic_widget_play, "Meditate", Palette.accent, Palette.onAccent, 52, meditate)
                }
                Spacer(GlanceModifier.defaultWeight())
                // Five colours across the full width, each with a 44dp-tall tap area
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    quickMoods.forEach { (valence, color) ->
                        Box(
                            modifier = GlanceModifier.defaultWeight().height(44.dp)
                                .clickable(actionRunCallback<LogMoodAction>(actionParametersOf(LogMoodAction.VALENCE to valence)))
                                .semantics { contentDescription = "Log ${moodLabels[valence] ?: "feeling"}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = GlanceModifier.size(30.dp).cornerRadius(15.dp)
                                    .background(ColorProvider(day = color, night = color)),
                            ) {}
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$minutes", style = TextStyle(color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                    Text(" / $goal min", style = TextStyle(color = Palette.secondary, fontSize = 13.sp))
                }
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = (minutes.toFloat() / goal).coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = Palette.accent,
                    backgroundColor = Palette.card,
                )
                Spacer(GlanceModifier.defaultWeight())
                Row {
                    SquareButton(R.drawable.ic_widget_play, "Meditate", Palette.accent, Palette.onAccent, 44, meditate)
                    Spacer(GlanceModifier.width(8.dp))
                    SquareButton(R.drawable.ic_widget_mood, "Check in", Palette.card, Palette.secondary, 44, checkIn)
                }
            }
        }
    }

    @Composable
    private fun SquareButton(icon: Int, label: String, container: GlanceColor, content: GlanceColor, sizeDp: Int, action: androidx.glance.action.Action) {
        Box(
            modifier = GlanceModifier.size(sizeDp.dp).background(container).cornerRadius((sizeDp * 0.32f).dp).clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = label,
                colorFilter = ColorFilter.tint(content),
                modifier = GlanceModifier.size((sizeDp / 2).dp),
            )
        }
    }

    companion object {
        /** Gives every placed widget the app's latest snapshot and redraws it. */
        suspend fun push(context: Context, snapshotJson: String) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(MindfulWidget::class.java).forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply { this[SNAPSHOT_KEY] = snapshotJson }
                }
                MindfulWidget().update(context, id)
            }
        }
    }
}

/** A colour tapped on the widget: saves a check-in without opening the app. */
class LogMoodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val valence = parameters[VALENCE] ?: return
        // The widget can run with the app closed, before MainActivity has set appContext
        try { utils.appContext } catch (_: UninitializedPropertyAccessException) { utils.appContext = context.applicationContext }
        if (MoodCheckInStore.checkIns.isEmpty()) MoodCheckInStore.load()
        // Saving refreshes every widget (WidgetSync), which shows it as the last check-in
        MoodCheckInStore.add(MoodCheckIn(atEpochMillis = Clock.System.now().toEpochMilliseconds(), valence = valence))
    }

    companion object {
        val VALENCE = ActionParameters.Key<Int>("valence")
    }
}
