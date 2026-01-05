package tts

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class TtsInput(val text: String)

@Serializable
private data class TtsVoice(val languageCode: String = "en-US", val name: String? = null)

@Serializable
private data class TtsAudioConfig(val audioEncoding: String = "MP3")

@Serializable
private data class TtsRequest(val input: TtsInput, val voice: TtsVoice, val audioConfig: TtsAudioConfig)

@Serializable
private data class TtsResponse(val audioContent: String?)

suspend fun synthesizeGoogleTtsBase64(apiKey: String, text: String, voice: String? = null): String? {
    if (apiKey.isBlank()) return null

    return withContext(Dispatchers.Default) {
        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        try {
            val url = "https://texttospeech.googleapis.com/v1/text:synthesize?key=${apiKey}"
            val req = TtsRequest(
                input = TtsInput(text),
                voice = TtsVoice(name = voice),
                audioConfig = TtsAudioConfig()
            )

            val resp = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(req)
            }

            if (resp.status.value in 200..299) {
                val body: TtsResponse = resp.body()
                body.audioContent
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            client.close()
        }
    }
}
