package service

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

actual object SessionNotifications {
    private val center: UNUserNotificationCenter
        get() = UNUserNotificationCenter.currentNotificationCenter()

    /**
     * Each notification's category (registered in iOSApp.swift): gives it generic text for when
     * previews are hidden, and the evening check-in its quick mood buttons.
     */
    private fun categoryFor(id: String) = when (id) {
        DailyReminders.MORNING_ID -> "MORNING_REMINDER"
        DailyReminders.EVENING_ID -> "EVENING_CHECKIN"
        else -> "MEDITATION_FINISHED"
    }

    // iOS shows its permission prompt only the first time; after that this just reports the answer.
    // No badge: the app never shows one.
    actual suspend fun ensurePermission(): Boolean = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound
        ) { granted, _ ->
            if (cont.isActive) cont.resume(granted)
        }
    }

    actual fun schedule(id: String, afterSeconds: Int, title: String, body: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
        if (afterSeconds <= 0) return
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
            setCategoryIdentifier(categoryFor(id))
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(afterSeconds.toDouble(), repeats = false)
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(id, content, trigger),
            withCompletionHandler = null,
        )
    }

    actual fun scheduleDaily(id: String, hour: Int, minute: Int, title: String, body: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
            setCategoryIdentifier(categoryFor(id))
        }
        val time = NSDateComponents().apply {
            setHour(hour.toLong())
            setMinute(minute.toLong())
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(time, repeats = true)
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(id, content, trigger),
            withCompletionHandler = null,
        )
    }

    actual fun cancel(id: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
    }
}
