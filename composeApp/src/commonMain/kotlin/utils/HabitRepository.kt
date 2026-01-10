package utils

import androidx.compose.runtime.mutableStateListOf

object HabitRepository {
    // Use a SnapshotStateList so Compose observers recompose when this changes
    var completedHabits = mutableStateListOf<String>().apply { addAll(HabitStorage.loadHabits()) }
    val habits: List<String> get() = completedHabits

    fun addCompletedHabit(habit: String) {
        if (!completedHabits.contains(habit)) {
            completedHabits.add(habit)
            HabitStorage.saveHabits(completedHabits)
        }
    }

    fun addHabit(habit: String) {
        if (habit.isNotBlank() && !habits.contains(habit)) {
            completedHabits.add(habit)
            HabitStorage.saveHabits(completedHabits)
        }
    }
    fun removeHabit(habit: String) {
        completedHabits.remove(habit)
        HabitStorage.saveHabits(completedHabits)
    }
}