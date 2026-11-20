package utils
import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual object HabitStorage {
    private lateinit var prefs: SharedPreferences
    // Active habits keep their original key so existing users don't lose them
    private const val KEY = "completed_habits"
    private const val DONE_KEY = "done_habits"

    fun init(context: Context) {
        prefs = context.getSharedPreferences("habits", Context.MODE_PRIVATE)
    }

    actual fun saveHabits(habits: List<String>) {
        prefs.edit().putString(KEY, Json.encodeToString(habits)).apply()
    }

    actual fun loadHabits(): List<String> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try { Json.decodeFromString(json) } catch (_: Exception) { emptyList() }
    }

    actual fun saveCompletedHabits(habits: List<String>) {
        prefs.edit().putString(DONE_KEY, Json.encodeToString(habits)).apply()
    }

    actual fun loadCompletedHabits(): List<String> {
        val json = prefs.getString(DONE_KEY, null) ?: return emptyList()
        return try { Json.decodeFromString(json) } catch (_: Exception) { emptyList() }
    }
}
