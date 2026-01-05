package platform

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

// Simple helper to publish chart data to iOS using NotificationCenter
// Expected payload: userInfo with key "data" -> List of Map { "x": epochMillis (Double), "y": Double }
object ChartBridge {
    // Publish list of (epochMillis, value) pairs to iOS
    fun publishChartData(points: List<Pair<Long, Double>>) {
        val list = points.map { p -> mapOf("x" to p.first.toDouble(), "y" to p.second) }

        val userInfo: Map<Any?, Any?> = mapOf("data" to list)

        NSOperationQueue.mainQueue.addOperationWithBlock {
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = "ChartDataUpdated",
                `object` = null,
                userInfo = userInfo
            )
        }
    }

    // Convenience to publish sample/random data
    fun publishSample() {
        val now = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
        val points = (0 until 7).map { i ->
            val t = now - (6 - i) * 24 * 60 * 60 * 1000L
            Pair(t, (20 + (i * 10)).toDouble())
        }
        publishChartData(points)
    }
}
