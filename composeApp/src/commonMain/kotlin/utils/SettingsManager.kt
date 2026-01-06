package utils

expect object SettingsManager {
    suspend fun saveDarkMode(enabled: Boolean)
    suspend fun loadDarkMode(): Boolean

    suspend fun saveUseSystemDefault(enabled: Boolean)
    suspend fun loadUseSystemDefault(): Boolean

    // runtime developer mode toggle persisted locally
    suspend fun saveDevMode(enabled: Boolean)
    suspend fun loadDevMode(): Boolean

    // Persist user preference for local notifications
    suspend fun saveNotificationsEnabled(enabled: Boolean)
    suspend fun loadNotificationsEnabled(): Boolean
}