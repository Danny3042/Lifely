package service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.readTextFile
import platform.writeTextFile
import utils.SettingsManager

/** The morning meditation and evening check-in reminders: whether each is on, and when. */
@Serializable
data class ReminderSettings(
    val morningEnabled: Boolean = false,
    val morningHour: Int = 8,
    val morningMinute: Int = 0,
    val eveningEnabled: Boolean = false,
    val eveningHour: Int = 20,
    val eveningMinute: Int = 30,
)

/**
 * Daily reminders: a morning nudge to meditate and an evening one to check in on how the day went.
 * They're repeating system notifications, so they arrive whether or not the app is open. They only
 * run while Notifications is switched on in the app.
 */
object DailyReminders {
    const val MORNING_ID = "daily-morning-meditation"
    const val EVENING_ID = "daily-evening-checkin"
    private const val FILE = "daily_reminders.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(): ReminderSettings =
        runCatching { json.decodeFromString<ReminderSettings>(readTextFile(FILE)) }.getOrDefault(ReminderSettings())

    /** Saves [settings] and updates the scheduled notifications to match. */
    suspend fun save(settings: ReminderSettings) {
        runCatching { writeTextFile(FILE, json.encodeToString(ReminderSettings.serializer(), settings)) }
        sync(settings)
    }

    /**
     * Makes the scheduled notifications match the settings (and the app-wide Notifications switch).
     * Run at launch too: some phones drop scheduled alarms after a restart.
     */
    suspend fun sync(settings: ReminderSettings? = null) {
        val s = settings ?: load()
        val allowed = runCatching { SettingsManager.loadNotificationsEnabled() }.getOrDefault(true)
        if (allowed && s.morningEnabled) {
            SessionNotifications.scheduleDaily(
                id = MORNING_ID,
                hour = s.morningHour,
                minute = s.morningMinute,
                title = "Start Your Day Calmly",
                body = "A few minutes of meditation can set the tone for your day.",
            )
        } else {
            SessionNotifications.cancel(MORNING_ID)
        }
        if (allowed && s.eveningEnabled) {
            SessionNotifications.scheduleDaily(
                id = EVENING_ID,
                hour = s.eveningHour,
                minute = s.eveningMinute,
                title = "Time to Check In",
                body = "How was your day? Take a moment to notice how you're feeling.",
            )
        } else {
            SessionNotifications.cancel(EVENING_ID)
        }
    }
}

/** "8:00 am" style, in the 12-hour format the time pickers use. */
fun formatReminderTime(hour: Int, minute: Int): String {
    val h = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$h:${minute.toString().padStart(2, '0')} ${if (hour < 12) "am" else "pm"}"
}
