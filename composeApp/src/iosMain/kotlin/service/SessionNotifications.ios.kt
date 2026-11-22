package service

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNAuthorizationOptionBadge
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

    // iOS shows its permission prompt only the first time; after that this just reports the answer
    actual suspend fun ensurePermission(): Boolean = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
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
