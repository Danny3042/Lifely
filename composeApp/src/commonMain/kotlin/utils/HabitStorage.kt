package utils

expect object HabitStorage {
    /** Habits the user is working on. (Stored under the legacy key "completed_habits".) */
    fun saveHabits(habits: List<String>)
    fun loadHabits(): List<String>

    /** Habits the user has ticked off. */
    fun saveCompletedHabits(habits: List<String>)
    fun loadCompletedHabits(): List<String>
}
