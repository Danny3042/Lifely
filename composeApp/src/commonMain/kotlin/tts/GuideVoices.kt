package tts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.danielramzani.HealthCompose.BuildKonfig
import platform.FileIO
import kotlin.io.encoding.Base64

/** A voice that can guide a meditation: a friendly name, its character, and the Gemini voice behind it. */
data class GuideVoice(val id: String, val name: String, val character: String, val geminiVoice: String)

/**
 * Spoken guidance for meditation sessions, in natural AI voices (Gemini text-to-speech).
 * Each phrase is generated once per voice and kept on the device, so later sessions play instantly
 * and work offline.
 */
object GuideVoices {
    val all = listOf(
        GuideVoice("willow", "Willow", "Soft", "Achernar"),
        GuideVoice("ember", "Ember", "Warm", "Sulafat"),
        GuideVoice("river", "River", "Breathy", "Enceladus"),
        GuideVoice("cedar", "Cedar", "Smooth", "Algieba"),
    )

    private const val CHOICE_FILE = "guide_voice.txt"

    /** The chosen voice's id, or null for no spoken guidance (the default). */
    var selectedId: String? by mutableStateOf(
        runCatching { platform.readTextFile(CHOICE_FILE).trim() }.getOrNull()?.takeIf { id -> all.any { it.id == id } }
    )
        private set

    val selected: GuideVoice? get() = all.firstOrNull { it.id == selectedId }

    fun select(id: String?) {
        selectedId = id
        runCatching { platform.writeTextFile(CHOICE_FILE, id ?: "off") }
    }

    /** What a voice says when you pick it, so you can hear it first. */
    fun sampleLine(voice: GuideVoice) = "Hi, I'm ${voice.name}. I'll guide you gently through your session."

    // Gemini TTS models, newest first (the preview names change; the second is the fallback)
    private val models = listOf("gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts")
    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    /**
     * The audio file (relative to the app's files) for [text] in [voice], generating and saving it
     * the first time. Null if it couldn't be made (no key, offline, API error).
     */
    suspend fun audioFor(voice: GuideVoice, text: String): String? = withContext(Dispatchers.Default) {
        val id = "guide-${voice.id}-${text.hashCode().toUInt().toString(16)}"
        val filename = "attachments/$id.wav"
        if (runCatching { FileIO.readAttachment(filename) }.getOrNull()?.isNotEmpty() == true) return@withContext filename
        val wav = synthesize(voice, text) ?: return@withContext null
        runCatching { FileIO.saveAttachment(id, wav, "wav") }.getOrNull()
    }

    private suspend fun synthesize(voice: GuideVoice, text: String): ByteArray? {
        val apiKey = BuildKonfig.GEMINI_API_KEY
        if (apiKey.isBlank()) return null
        // Natural-language direction for the delivery; the TTS model reads only the line itself
        val prompt = "Read this slowly, softly and calmly, like a gentle meditation guide, leaving small pauses:\n$text"
        val body = buildJsonObject {
            putJsonArray("contents") { addJsonObject { putJsonArray("parts") { addJsonObject { put("text", prompt) } } } }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") {
                        putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice.geminiVoice) }
                    }
                }
            }
        }.toString()
        for (model in models) {
            val result = runCatching {
                val response = client.post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent") {
                    header("x-goog-api-key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                if (!response.status.isSuccess()) {
                    println("GuideVoices: $model answered ${response.status.value}")
                    return@runCatching null
                }
                val part = json.parseToJsonElement(response.bodyAsText()).jsonObject["candidates"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
                    ?.firstOrNull { it.jsonObject.containsKey("inlineData") }?.jsonObject?.get("inlineData")?.jsonObject
                    ?: return@runCatching null
                val data = part["data"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                val mime = part["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val audio = Base64.Default.decode(data)
                // Gemini returns raw 16-bit PCM ("audio/L16;codec=pcm;rate=24000"): wrap it as WAV to play it
                if (mime.contains("wav", ignoreCase = true)) audio
                else wav(audio, sampleRate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24_000)
            }.onFailure { println("GuideVoices: $model failed: ${it.message}") }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    /** A 44-byte WAV header in front of mono 16-bit PCM. */
    private fun wav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArray(44 + pcm.size)
        fun putInt(at: Int, v: Int) { for (i in 0..3) out[at + i] = (v shr (8 * i)).toByte() }
        fun putShort(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte() }
        "RIFF".encodeToByteArray().copyInto(out, 0)
        putInt(4, 36 + pcm.size)
        "WAVEfmt ".encodeToByteArray().copyInto(out, 8)
        putInt(16, 16)            // fmt chunk size
        putShort(20, 1)           // PCM
        putShort(22, 1)           // mono
        putInt(24, sampleRate)
        putInt(28, sampleRate * 2) // byte rate
        putShort(32, 2)           // block align
        putShort(34, 16)          // bits per sample
        "data".encodeToByteArray().copyInto(out, 36)
        putInt(40, pcm.size)
        pcm.copyInto(out, 44)
        return out
    }
}

/** One spoken line in a session, [atSecond] seconds after it starts. */
data class GuideCue(val atSecond: Int, val text: String)

/**
 * The guidance for a session of [totalSeconds]: a welcome, settling in, gentle reminders spread
 * through the middle, a halfway note on longer sessions, and a soft close just before the end.
 * Fixed wording, so each line is generated once per voice and then reused.
 */
fun guideScript(totalSeconds: Int): List<GuideCue> {
    val cues = mutableListOf<GuideCue>()
    cues += GuideCue(0, "Welcome. Find a comfortable position, and let your eyes close, or rest your gaze softly. Take a slow breath in, and let it go.")
    val closeAt = (totalSeconds - 15).coerceAtLeast(20)
    if (totalSeconds >= 90) cues += GuideCue(25, "Let your breathing settle into its own rhythm. There's nothing you need to do right now.")
    val reminders = listOf(
        "Notice the air moving in, and moving out.",
        "If your mind wanders, that's okay. Gently bring it back to your breath.",
        "Let your shoulders soften. Unclench your jaw.",
        "Feel the weight of your body, supported where you are.",
        "With each breath out, let go a little more.",
        "Thoughts may come and go, like clouds. Let them pass.",
    )
    val half = totalSeconds / 2
    var at = 90
    var i = 0
    while (at < closeAt - 30) {
        if (totalSeconds >= 240 && kotlin.math.abs(at - half) < 45) {
            cues += GuideCue(half, "You're halfway there. Keep resting with the breath.")
        } else {
            cues += GuideCue(at, reminders[i % reminders.size])
            i++
        }
        at += if (totalSeconds >= 600) 120 else 75
    }
    if (totalSeconds >= 60) {
        cues += GuideCue(closeAt, "In a moment we'll finish. Notice the sounds around you, and when you're ready, gently open your eyes. Well done for taking this time.")
    }
    return cues.distinctBy { it.atSecond }.sortedBy { it.atSecond }
}
