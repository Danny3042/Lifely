package utils

import androidx.compose.runtime.mutableStateListOf

object HabitRepository {
    // SnapshotStateLists so Compose recomposes when they change

    /** Habits the user is working on. */
    val activeHabits = mutableStateListOf<String>().apply { addAll(HabitStorage.loadHabits()) }

    /** Habits the user has ticked off (shown on Completed Habits and Home's recent activity). */
    val completedHabits = mutableStateListOf<String>().apply { addAll(HabitStorage.loadCompletedHabits()) }

    val habits: List<String> get() = activeHabits

    fun addHabit(habit: String) {
        if (habit.isNotBlank() && !activeHabits.contains(habit)) {
            activeHabits.add(habit)
            HabitStorage.saveHabits(activeHabits)
        }
    }

    /** Ticks a habit off: moves it from the active list to the completed list. */
    fun completeHabit(habit: String) {
        if (activeHabits.remove(habit)) HabitStorage.saveHabits(activeHabits)
        if (!completedHabits.contains(habit)) {
            completedHabits.add(habit)
            HabitStorage.saveCompletedHabits(completedHabits)
        }
    }

    fun addCompletedHabit(habit: String) = completeHabit(habit)

    fun removeHabit(habit: String) {
        activeHabits.remove(habit)
        HabitStorage.saveHabits(activeHabits)
    }
}
