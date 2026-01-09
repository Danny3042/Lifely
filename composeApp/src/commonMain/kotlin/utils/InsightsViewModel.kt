package utils

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import platform.ChartPublisher

data class Session(val time: String, val duration: Int)

class InsightsViewModel : ViewModel() {
    companion object {
        // Shared backing store so multiple ViewModel instances observe the same data
        private val SHARED_SESSIONS = mutableStateOf(List(7) { emptyList<Session>() })
    }

    // Instance reference to the shared state
    private val _sessionsPerDay = SHARED_SESSIONS
    val sessionsPerDay: State<List<List<Session>>> = _sessionsPerDay


    fun addSession(session: Session) {
        val currentDayIndex = getTodayIndex()
        _sessionsPerDay.value = _sessionsPerDay.value.toMutableList().also { list ->
            list[currentDayIndex] = list[currentDayIndex] + session
        }

        // Debug logging to help verify session addition during testing
        println("InsightsViewModel: added session dayIndex=$currentDayIndex session=$session totals=${_sessionsPerDay.value.map { it.sumOf { s -> s.duration } }}")

        // Compute totals and publish to platform chart bridge so native views can update
        publishTotalsToPlatform()
    }

    private fun publishTotalsToPlatform() {
        val totals = _sessionsPerDay.value.map { dayList -> dayList.sumOf { it.duration }.toFloat() }
        try {
            ChartPublisher.publishTotals(totals)
        } catch (e: Throwable) {
            // logging removed
        }
    }

    private fun getTodayIndex(): Int {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return (today.dayOfWeek.ordinal + 6) % 7
    }
}