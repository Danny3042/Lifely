package service

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

// The Live Activity itself is Swift (ActivityKit has no Kotlin bindings): MeditationLiveActivityController
// listens for this notification.
actual object MeditationLiveActivity {
    private fun post(info: Map<Any?, Any?>) {
        NSOperationQueue.mainQueue.addOperationWithBlock {
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = "MeditationLiveActivity",
                `object` = null,
                userInfo = info,
            )
        }
    }

    actual fun start(endAtEpochMillis: Long, totalSeconds: Int) {
        post(mapOf("action" to "start", "endAt" to endAtEpochMillis.toDouble() / 1000.0, "total" to totalSeconds))
    }

    actual fun end(completed: Boolean) {
        post(mapOf("action" to "end", "completed" to completed))
    }
}
