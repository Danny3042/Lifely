package service

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults

/** Shared with the widget extension through the App Group (both have the entitlement). */
const val WIDGET_APP_GROUP = "group.org.example.project.HealthApp"

actual object WidgetPlatform {
    private val defaults: NSUserDefaults?
        get() = NSUserDefaults(suiteName = WIDGET_APP_GROUP)

    actual fun publish(snapshotJson: String) {
        defaults?.setObject(snapshotJson, forKey = "snapshot")
        // WidgetKit is Swift-only: Swift reloads the widgets when it hears this
        NSOperationQueue.mainQueue.addOperationWithBlock {
            NSNotificationCenter.defaultCenter.postNotificationName(aName = "WidgetSnapshotChanged", `object` = null)
        }
    }

    actual fun takePendingCheckIns(): String? {
        val d = defaults ?: return null
        val pending = d.stringForKey("pendingCheckIns")
        d.removeObjectForKey("pendingCheckIns")
        return pending
    }
}
