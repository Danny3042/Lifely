package service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import utils.appContext
import widgets.MindfulWidget

actual object WidgetPlatform {
    const val PREFS = "widget_snapshot"
    const val KEY = "snapshot"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    actual fun publish(snapshotJson: String) {
        val context: Context = runCatching { appContext.applicationContext }.getOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, snapshotJson).apply()
        scope.launch { runCatching { MindfulWidget.push(context, snapshotJson) } }
    }

    // Android widget actions run inside the app, so they add check-ins directly
    actual fun takePendingCheckIns(): String? = null
}
