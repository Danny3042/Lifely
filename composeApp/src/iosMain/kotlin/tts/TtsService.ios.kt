package tts

import platform.Foundation.NSNotificationCenter
import org.danielramzani.HealthCompose.BuildKonfig
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSOperationQueue
import kotlin.random.Random
import kotlin.io.encoding.Base64
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class IosTtsService : TtsService {
    override fun speak(text: String, useGemini: Boolean, voice: String?) {
        if (useGemini) {
            // Try to fetch TTS audio via Google TTS REST and play it via native side
            val apiKey = BuildKonfig.GEMINI_API_KEY
            // Use a coroutine to call the suspend synth function
            GlobalScope.launch {
                try {
                    val base64 = synthesizeGoogleTtsBase64(apiKey, text, voice)
                    if (!base64.isNullOrBlank()) {
                        val bytes = Base64.decode(base64)
                        val id = "tts-${Random.nextLong().toString(36)}"
                        val filename = platform.FileIO.saveAttachment(id, bytes, "mp3")
                        // Post notification with filename to Swift on the main queue; Swift TtsHandler will play it
                        NSOperationQueue.mainQueue.addOperationWithBlock {
                            NSNotificationCenter.defaultCenter.postNotificationName("TtsSpeakFile", filename, null)
                        }
                        return@launch
                    }
                } catch (e: Throwable) {
                    // fallthrough -> fallback to native TTS
                }
                // Fallback: notify to speak text using local AVSpeechSynthesizer on main
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    NSNotificationCenter.defaultCenter.postNotificationName("TtsSpeak", text, null)
                }
            }
            return
        }
        // Non-gemini path: ensure posting happens on main thread as well
        NSOperationQueue.mainQueue.addOperationWithBlock {
            NSNotificationCenter.defaultCenter.postNotificationName("TtsSpeak", text, null)
        }
    }

    override fun stop() {
        NSOperationQueue.mainQueue.addOperationWithBlock {
            NSNotificationCenter.defaultCenter.postNotificationName("TtsStop", null, null)
        }
    }
}

actual fun getTtsService(): TtsService = IosTtsService()
