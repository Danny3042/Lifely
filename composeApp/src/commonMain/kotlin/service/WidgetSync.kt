package service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import utils.DEFAULT_DAILY_GOAL_MINUTES
import utils.MeditationHistory
import utils.MoodCheckIn
import utils.MoodCheckInStore
import utils.SettingsManager

/** What the home screen widgets show. Written by the app whenever it changes. */
@Serializable
data class WidgetSnapshot(
    /** The day the numbers are for (yyyy-mm-dd): a widget showing an older day treats today as empty */
    val day: String,
    val todayMinutes: Int,
    val goalMinutes: Int,
    val sessionsToday: Int,
    val streakDays: Int,
    val lastMoodValence: Int? = null,
    val lastMoodAtEpochMillis: Long? = null,
)

/** A check-in logged from the iOS widget, waiting for the app to add it to its own store. */
@Serializable
data class PendingWidgetCheckIn(val atEpochMillis: Long, val valence: Int)

/** Keeps the home screen widgets up to date. */
object WidgetSync {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // Main: the stores it reads are changed on the main thread
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Rebuilds the snapshot and hands it to the platform's widgets. Safe to call often. */
    fun refresh() {
        scope.launch {
            ingestWidgetCheckIns()
            runCatching {
                val goal = runCatching { SettingsManager.loadDailyGoalMinutes() }.getOrDefault(DEFAULT_DAILY_GOAL_MINUTES)
                val last = MoodCheckInStore.checkIns.firstOrNull()
                val snapshot = WidgetSnapshot(
                    day = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString(),
                    todayMinutes = MeditationHistory.todayMinutes(),
                    goalMinutes = goal.coerceAtLeast(1),
                    sessionsToday = MeditationHistory.todaySessions(),
                    streakDays = MeditationHistory.streakDays(),
                    lastMoodValence = last?.valence,
                    lastMoodAtEpochMillis = last?.atEpochMillis,
                )
                WidgetPlatform.publish(json.encodeToString(WidgetSnapshot.serializer(), snapshot))
            }
        }
    }

    /**
     * When the app opens: takes in check-ins logged from a widget while it was closed (iOS widgets
     * can't reach the app's store), then refreshes the widgets.
     */
    fun onAppActive() {
        runCatching { if (MoodCheckInStore.checkIns.isEmpty()) MoodCheckInStore.load() }
        refresh()
    }

    private fun ingestWidgetCheckIns() {
        runCatching {
            val pending = WidgetPlatform.takePendingCheckIns()
            if (!pending.isNullOrBlank()) {
                val items = json.decodeFromString(ListSerializer(PendingWidgetCheckIn.serializer()), pending)
                val known = MoodCheckInStore.checkIns.map { it.atEpochMillis }.toSet()
                items.filter { it.atEpochMillis !in known }.sortedBy { it.atEpochMillis }.forEach {
                    MoodCheckInStore.add(MoodCheckIn(atEpochMillis = it.atEpochMillis, valence = it.valence), refreshWidgets = false)
                }
            }
        }
    }
}

/** The platform side of the widgets: where the snapshot goes. */
expect object WidgetPlatform {
    fun publish(snapshotJson: String)
    /** Check-ins logged from a widget since last time (a JSON list of PendingWidgetCheckIn), or null. */
    fun takePendingCheckIns(): String?
}
