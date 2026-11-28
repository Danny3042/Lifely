package org.danielramzani.HealthCompose

import App
import android.content.Intent
import android.os.Bundle
import platform.PlatformBridge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
import com.mmk.kmpnotifier.permission.permissionUtil
import utils.HabitStorage
import utils.appContext

class MainActivity : ComponentActivity() {
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContext = this
        HabitStorage.init(this)

        Firebase.initialize(context = this)
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        val permissionUtil by permissionUtil()
        permissionUtil.askNotificationPermission()
        // Remember it was asked, so Permissions can tell "never asked" from "said no"
        platform.PermissionAsked.mark(platform.AppPermission.Notifications)

        NotifierManager.initialize(
            configuration = NotificationPlatformConfiguration.Android(
                notificationIconResId = R.drawable.ic_notification,
                showPushNotification = true,
            )
        )

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        // Log an app_open event
        firebaseAnalytics.logEvent("app_open") {
            param("full_text", "The app is opened")
        }

        handleShortcut(intent)

        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        // Today's numbers may have moved on (a new day) since the widgets last updated
        service.WidgetSync.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    /** The app shortcuts (res/xml/shortcuts.xml): open the page, and for Meditate start a session. */
    private fun handleShortcut(intent: Intent?) {
        val route = when (intent?.action) {
            ACTION_MEDITATE -> {
                PlatformBridge.meditationStartMinutes = 5
                "meditate_timer"
            }
            ACTION_CHECK_IN -> "check_in"
            ACTION_CHECK_IN_CALENDAR -> "check_in_calendar"
            else -> return
        }
        PlatformBridge.requestedRoute = route
        PlatformBridge.requestedRouteSignal = PlatformBridge.requestedRouteSignal + 1
        // Handled: don't repeat it if the activity is recreated
        intent?.action = Intent.ACTION_MAIN
    }

    companion object {
        const val ACTION_MEDITATE = "org.danielramzani.HealthCompose.action.MEDITATE"
        const val ACTION_CHECK_IN = "org.danielramzani.HealthCompose.action.CHECK_IN"
        const val ACTION_CHECK_IN_CALENDAR = "org.danielramzani.HealthCompose.action.CHECK_IN_CALENDAR"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}