package service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import org.danielramzani.HealthCompose.SessionFinishedReceiver
import utils.appContext
import java.util.Calendar

actual object SessionNotifications {
    // The app asks for POST_NOTIFICATIONS at launch (MainActivity); this reports the answer
    actual suspend fun ensurePermission(): Boolean =
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    actual fun schedule(id: String, afterSeconds: Int, title: String, body: String) {
        cancel(id)
        if (afterSeconds <= 0) return
        setAlarm(appContext, System.currentTimeMillis() + afterSeconds * 1000L, pendingIntent(appContext, id, title, body, null))
    }

    actual fun scheduleDaily(id: String, hour: Int, minute: Int, title: String, body: String) {
        cancel(id)
        scheduleNextDaily(appContext, id, hour, minute, title, body)
    }

    actual fun cancel(id: String) {
        val alarms = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pendingIntent(appContext, id, "", "", null))
    }

    /**
     * Sets the next daily reminder. The receiver calls this again after showing one, so it repeats
     * (one exact alarm at a time is more punctual than Android's inexact repeating alarms).
     * Takes a Context because the receiver can run before the app's own UI has started.
     */
    fun scheduleNextDaily(context: Context, id: String, hour: Int, minute: Int, title: String, body: String) {
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        setAlarm(context, next.timeInMillis, pendingIntent(context, id, title, body, hour to minute))
    }

    private fun setAlarm(context: Context, at: Long, pending: PendingIntent) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Exact when the user allows exact alarms; otherwise Android may deliver it a little late
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun pendingIntent(context: Context, id: String, title: String, body: String, daily: Pair<Int, Int>?): PendingIntent {
        val intent = Intent(context, SessionFinishedReceiver::class.java).apply {
            // The action makes intents with the same id match, so cancel() finds the scheduled one
            action = "org.danielramzani.HealthCompose.SESSION_FINISHED.$id"
            putExtra(SessionFinishedReceiver.EXTRA_ID, id)
            putExtra(SessionFinishedReceiver.EXTRA_TITLE, title)
            putExtra(SessionFinishedReceiver.EXTRA_BODY, body)
            if (daily != null) {
                putExtra(SessionFinishedReceiver.EXTRA_DAILY_HOUR, daily.first)
                putExtra(SessionFinishedReceiver.EXTRA_DAILY_MINUTE, daily.second)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
