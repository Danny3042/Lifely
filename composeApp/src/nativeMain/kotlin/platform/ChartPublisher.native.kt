package platform

import kotlinx.datetime.Clock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

// Native implementation used by iOS/other native targets. Converts per-day totals into
// timestamped data and posts a ChartDataUpdated notification. iOS-specific actual may
// forward to ChartBridge instead for tighter integration.
actual object ChartPublisher {
    actual fun publishTotals(totals: List<Float>) {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val points = totals.mapIndexed { idx, v ->
            val offsetDays = 6 - idx
            val tsMillis = nowMillis - offsetDays * oneDayMillis
            tsMillis to v.toDouble()
        }

        // Post notification with bridged list of maps { "x": epochMillis, "y": value }
        try {
            val bridgedList = points.map { p -> mapOf("x" to p.first.toDouble(), "y" to p.second) }
            val userInfo: Map<Any?, Any?> = mapOf("data" to bridgedList)
            NSOperationQueue.mainQueue.addOperationWithBlock {
                NSNotificationCenter.defaultCenter.postNotificationName(
                    aName = "ChartDataUpdated",
                    `object` = null,
                    userInfo = userInfo
                )
            }
        } catch (_: Throwable) {
            // failure suppressed - logging removed
        }
    }
}
