package org.danielramzani.HealthCompose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import platform.PlatformBridge
import service.MEDITATION_FINISHED_NOTIFICATION
import service.MeditationLiveActivity
import service.SessionNotifications

/** The Stop button on the meditation Live Update. */
class MeditationStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(MeditationLiveActivity.NOTIFICATION_ID)
        // The receiver can run with the app closed, before MainActivity has set appContext
        try { utils.appContext } catch (_: UninitializedPropertyAccessException) { utils.appContext = context.applicationContext }
        // The session won't finish now, so its "complete" notification shouldn't arrive
        runCatching { SessionNotifications.cancel(MEDITATION_FINISHED_NOTIFICATION) }
        // Tell the meditation page, if it's open, to stop
        PlatformBridge.meditationStopSignal = PlatformBridge.meditationStopSignal + 1
    }
}
