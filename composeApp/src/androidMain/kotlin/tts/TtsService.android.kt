package tts

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.AppContextHolder
import java.util.Base64 as JavaBase64

class AndroidTtsService(private val context: Context) : TtsService {
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    override fun speak(text: String, useGemini: Boolean, voice: String?) {
        if (useGemini) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val apiKey = org.danielramzani.HealthCompose.BuildKonfig.GEMINI_API_KEY
                    val base64 = synthesizeGoogleTtsBase64(apiKey, text, voice)
                    if (!base64.isNullOrBlank()) {
                        val bytes = JavaBase64.getDecoder().decode(base64)
                        val id = "tts-${System.currentTimeMillis().toString(36)}"
                        val filename = platform.FileIO.saveAttachment(id, bytes, "mp3")
                        // play file via MediaPlayer from internal storage
                        try {
                            mediaPlayer?.release()
                            mediaPlayer = MediaPlayer()
                            val file = java.io.File(context.filesDir, filename)
                            mediaPlayer?.setDataSource(file.absolutePath)
                            mediaPlayer?.prepare()
                            mediaPlayer?.start()
                            return@launch
                        } catch (_: Exception) {
                            // fallback
                        }
                    }
                } catch (_: Exception) {
                    // fallback
                }
                // fallback to local TTS
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts-${System.currentTimeMillis()}")
            }
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts-${System.currentTimeMillis()}")
    }

    override fun stop() {
        tts?.stop()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

actual fun getTtsService(): TtsService = AndroidTtsService(AppContextHolder.context.applicationContext)
