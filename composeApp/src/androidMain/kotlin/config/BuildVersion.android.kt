package config

import android.content.pm.PackageManager
import android.os.Build
import utils.appContext

actual fun appVersionName(): String = try {
    val pm = appContext.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(appContext.packageName, 0)
    }
    info.versionName ?: ""
} catch (_: Throwable) {
    ""
}
