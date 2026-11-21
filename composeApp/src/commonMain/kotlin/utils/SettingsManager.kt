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

    // First-launch onboarding
    suspend fun saveOnboardingCompleted(completed: Boolean)
    suspend fun loadOnboardingCompleted(): Boolean

    // Daily mindful-minutes goal shown on the Home card (chosen during onboarding)
    suspend fun saveDailyGoalMinutes(minutes: Int)
    suspend fun loadDailyGoalMinutes(): Int
}

/** Default daily mindful-minutes goal when the user hasn't picked one. */
const val DEFAULT_DAILY_GOAL_MINUTES = 10
