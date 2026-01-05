package utils

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import platform.ChartPublisher

data class Session(val time: String, val duration: Int)

class InsightsViewModel : ViewModel() {
    private val _sessionsPerDay = mutableStateOf(List(7) { emptyList<Session>() })
    val sessionsPerDay: State<List<List<Session>>> = _sessionsPerDay


    fun addSession(session: Session) {
        val currentDayIndex = getTodayIndex()
        _sessionsPerDay.value = _sessionsPerDay.value.toMutableList().also { list ->
            list[currentDayIndex] = list[currentDayIndex] + session
        }

        // Compute totals and publish to platform chart bridge so native views can update
        publishTotalsToPlatform()
    }

    private fun publishTotalsToPlatform() {
        val totals = _sessionsPerDay.value.map { dayList -> dayList.sumOf { it.duration }.toFloat() }
        try {
            ChartPublisher.publishTotals(totals)
        } catch (e: Throwable) {
            println("InsightsViewModel: failed to publish totals to platform: ${e.message}")
        }
    }

    private fun getTodayIndex(): Int {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return (today.dayOfWeek.ordinal + 6) % 7
    }
}