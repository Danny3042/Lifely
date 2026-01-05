package tts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object TtsSettings {
    // Compose-aware state for whether TTS is enabled across the app
    var enabled by mutableStateOf(false)
}

