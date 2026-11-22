package service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.danielramzani.HealthCompose.MeditationStopReceiver
import org.danielramzani.HealthCompose.R
import utils.appContext

/**
 * Android's take on the iOS Live Activity: a Live Update. An ongoing notification with a countdown
 * that Android 16+ promotes to the top of the shade, the lock screen and a timer chip in the status
 * bar. On older versions it's a pinned notification with the same countdown.
 */
actual object MeditationLiveActivity {
    const val CHANNEL_ID = "meditation_live"
    const val NOTIFICATION_ID = 4120

    actual fun start(endAtEpochMillis: Long, totalSeconds: Int) {
        val context = runCatching { appContext }.getOrNull() ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val minutes = (totalSeconds / 60).coerceAtLeast(1)
        val remaining = (endAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val stop = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            Intent(context, MeditationStopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Meditating")
            .setContentText("$minutes min session · breathe in… and out")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$minutes min session · breathe in… and out"))
            // The countdown: Android draws it itself, in the notification and the status bar chip
            .setWhen(endAtEpochMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Clears itself when the session ends, even if the app isn't running by then
            // (the "Meditation complete" notification takes over)
            .setTimeoutAfter(remaining + 1_000)
            .addAction(R.drawable.ic_notification, "Stop", stop)
            .apply { open?.let { setContentIntent(it) } }
            // Ask Android 16+ to promote it to a Live Update (needs POST_PROMOTED_NOTIFICATIONS)
            .addExtras(android.os.Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    actual fun end(completed: Boolean) {
        val context = runCatching { appContext }.getOrNull() ?: return
        // A finished session gets the "Meditation complete" notification instead
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            // Not IMPORTANCE_MIN: Live Updates can't be promoted from a min-importance channel
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Meditation in progress", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Shows the countdown while a meditation is running"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }

    // Notification.EXTRA_REQUEST_PROMOTED_ONGOING (Android 16); spelled out so older SDKs compile it
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
}
