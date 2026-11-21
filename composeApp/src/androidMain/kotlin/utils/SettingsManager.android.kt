package utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("settings")

actual object SettingsManager {
    actual suspend fun saveDarkMode(enabled: Boolean) {
        val key = booleanPreferencesKey("dark_mode")
        appContext.dataStore.edit { it[key] = enabled }
    }

    actual suspend fun loadDarkMode(): Boolean {
        val key = booleanPreferencesKey("dark_mode")
        val prefs = appContext.dataStore.data.first()
        return prefs[key] ?: false
    }

    actual suspend fun saveUseSystemDefault(enabled: Boolean) {
        val key = booleanPreferencesKey("use_system_default")
        appContext.dataStore.edit { it[key] = enabled }
    }

    actual suspend fun loadUseSystemDefault(): Boolean {
        val key = booleanPreferencesKey("use_system_default")
        val prefs = appContext.dataStore.data.first()
        return prefs[key] ?: true
    }

    actual suspend fun saveDevMode(enabled: Boolean) {
        val key = booleanPreferencesKey("dev_mode")
        appContext.dataStore.edit { it[key] = enabled }
    }

    actual suspend fun loadDevMode(): Boolean {
        val key = booleanPreferencesKey("dev_mode")
        val prefs = appContext.dataStore.data.first()
        return prefs[key] ?: false
    }

    actual suspend fun saveNotificationsEnabled(enabled: Boolean) {
        val key = booleanPreferencesKey("notifications_enabled")
        appContext.dataStore.edit { it[key] = enabled }
    }

    actual suspend fun loadNotificationsEnabled(): Boolean {
        val key = booleanPreferencesKey("notifications_enabled")
        val prefs = appContext.dataStore.data.first()
        return prefs[key] ?: true
    }

    actual suspend fun saveOnboardingCompleted(completed: Boolean) {
        val key = booleanPreferencesKey("onboarding_completed")
        appContext.dataStore.edit { it[key] = completed }
    }

    actual suspend fun loadOnboardingCompleted(): Boolean {
        val key = booleanPreferencesKey("onboarding_completed")
        return appContext.dataStore.data.first()[key] ?: false
    }

    actual suspend fun saveDailyGoalMinutes(minutes: Int) {
        val key = intPreferencesKey("daily_goal_minutes")
        appContext.dataStore.edit { it[key] = minutes }
    }

    actual suspend fun loadDailyGoalMinutes(): Int {
        val key = intPreferencesKey("daily_goal_minutes")
        return appContext.dataStore.data.first()[key] ?: DEFAULT_DAILY_GOAL_MINUTES
    }
}
