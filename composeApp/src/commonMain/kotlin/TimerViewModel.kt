import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmk.kmpnotifier.notification.NotifierManager
import utils.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimerViewModel: ViewModel() {
    private val _timer = MutableStateFlow(0L)
    val timer = _timer.asStateFlow()

    private var timerJob: Job? = null

    fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_timer.value > 0) {
                delay(1000L)
                _timer.update { it - 1 }
                if(_timer.value == 0L) {
                    sendTimerEndNotification()
                }
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
    }

    fun setTimer(time: Long) {
        _timer.value = time
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    fun stopTimer() {
        timerJob?.cancel()
        _timer.value = 0
    }
}

// Add this function to send a notification
suspend fun sendTimerEndNotification() {
    // Check user preference before sending notifications
    val enabled = runCatching { SettingsManager.loadNotificationsEnabled() }.getOrDefault(true)
    if (!enabled) return

    val notifier = runCatching { NotifierManager.getLocalNotifier() }.getOrNull()
    runCatching {
        notifier?.notify("Timer Finished", "Your timer has finished")
    }
}