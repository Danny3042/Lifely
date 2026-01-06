package utils

import platform.Foundation.NSUserDefaults

actual object SettingsManager {
    actual suspend fun saveDarkMode(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = "dark_mode")
    }

    actual suspend fun loadDarkMode(): Boolean {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey("dark_mode") != null) {
            defaults.boolForKey("dark_mode")
        } else {
            false
        }
    }

    actual suspend fun saveUseSystemDefault(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = "use_system_default")
    }

    actual suspend fun loadUseSystemDefault(): Boolean {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey("use_system_default") != null) {
            defaults.boolForKey("use_system_default")
        } else {
            true
        }
    }

    actual suspend fun saveDevMode(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = "dev_mode")
    }

    actual suspend fun loadDevMode(): Boolean {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey("dev_mode") != null) {
            defaults.boolForKey("dev_mode")
        } else {
            false
        }
    }

    actual suspend fun saveNotificationsEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = "notifications_enabled")
    }

    actual suspend fun loadNotificationsEnabled(): Boolean {
        val defaults = NSUserDefaults.standardUserDefaults
        return if (defaults.objectForKey("notifications_enabled") != null) {
            defaults.boolForKey("notifications_enabled")
        } else {
            true
        }
    }
}