package utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

actual object HabitStorage {
    private val defaults = NSUserDefaults.standardUserDefaults
    // Active habits keep their original key so existing users don't lose them
    private const val KEY = "completed_habits"
    private const val DONE_KEY = "done_habits"

    actual fun saveHabits(habits: List<String>) {
        defaults.setObject(Json.encodeToString(habits), forKey = KEY)
    }

    actual fun loadHabits(): List<String> {
        val json = defaults.stringForKey(KEY) ?: return emptyList()
        return try { Json.decodeFromString(json) } catch (_: Exception) { emptyList() }
    }

    actual fun saveCompletedHabits(habits: List<String>) {
        defaults.setObject(Json.encodeToString(habits), forKey = DONE_KEY)
    }

    actual fun loadCompletedHabits(): List<String> {
        val json = defaults.stringForKey(DONE_KEY) ?: return emptyList()
        return try { Json.decodeFromString(json) } catch (_: Exception) { emptyList() }
    }
}
