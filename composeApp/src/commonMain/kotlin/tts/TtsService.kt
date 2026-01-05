package tts

/**
 * Cross-platform TTS service.
 */
interface TtsService {
    /** Speak the provided text. If useGemini==true and the platform has Gemini support implemented,
     *  the implementation should try to fetch cloud audio and play it; otherwise fallback to local TTS. */
    fun speak(text: String, useGemini: Boolean = false, voice: String? = null)

    /** Stop any ongoing playback or synthesis */
    fun stop()
}

/** Simple expect function to obtain platform implementation */
expect fun getTtsService(): TtsService
