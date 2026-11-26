package tts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object TtsSettings {
    var enabled by mutableStateOf(false)

    val voices = listOf(
        BuddyVoice("willow", "Willow", "soft", "en-US-Neural2-F", 1.5),
        BuddyVoice("ember", "Ember", "warm", "en-US-Neural2-D", 0.5),
        BuddyVoice("river", "River", "bright", "en-US-Neural2-C", 1.0),
        BuddyVoice("cedar", "Cedar", "deep", "en-US-Neural2-A", -1.0),
    )

    var selectedVoiceId by mutableStateOf(
        runCatching { platform.readTextFile(VOICE_FILE).trim() }
            .getOrNull()
            ?.takeIf { id -> voices.any { it.id == id } }
            ?: "willow",
    )
    val selectedVoice: BuddyVoice
        get() = voices.firstOrNull { it.id == selectedVoiceId } ?: voices.first()

    fun selectVoice(id: String) {
        if (voices.none { it.id == id }) return
        selectedVoiceId = id
        runCatching { platform.writeTextFile(VOICE_FILE, id) }
    }

    const val speechRate = 1.15

    private const val VOICE_FILE = "chat_voice.txt"
}

data class BuddyVoice(
    val id: String,
    val name: String,
    val character: String,
    val googleVoiceName: String,
    val pitch: Double,
)
