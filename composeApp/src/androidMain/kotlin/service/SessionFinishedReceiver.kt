package org.danielramzani.HealthCompose

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Shows the "session finished" notification scheduled by SessionNotifications. */
class SessionFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // A daily reminder: set up tomorrow's before anything else
        val dailyHour = intent.getIntExtra(EXTRA_DAILY_HOUR, -1)
        val dailyMinute = intent.getIntExtra(EXTRA_DAILY_MINUTE, -1)
        if (dailyHour >= 0 && dailyMinute >= 0) {
            service.SessionNotifications.scheduleNextDaily(
                context,
                intent.getStringExtra(EXTRA_ID).orEmpty(),
                dailyHour,
                dailyMinute,
                intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                intent.getStringExtra(EXTRA_BODY).orEmpty(),
            )
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sessions and reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When a session you started finishes, and your daily meditation and check-in reminders"
                }
            )
        }

        // Tapping it opens the app
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(intent.getStringExtra(EXTRA_BODY))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .apply { open?.let { setContentIntent(it) } }
            .build()
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "session_finished"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_DAILY_HOUR = "daily_hour"
        const val EXTRA_DAILY_MINUTE = "daily_minute"
    }
}
