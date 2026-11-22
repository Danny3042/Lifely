package service

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.time.TimeSource

/**
 * A Gemini chat that talks to the REST API directly instead of going through the generative-ai
 * SDK. The SDK (0.5.0) can't set Gemini 3's thinking level and hard-codes an 80 s socket timeout:
 * Gemini 3 thinks at "high" by default and streams nothing while it thinks, so long thinks hit that
 * timeout ("Socket timeout has expired ... socket_timeout=80000"). Here thinking is set to "low"
 * (fast, recommended for chat) and the timeouts are ours.
 */
class GeminiChatSession(
    private val apiKey: String,
    private val modelName: String,
    /** Tried when [modelName] stays busy (503) or rate limited (429). Null for no fallback. */
    private val fallbackModelName: String? = null,
    private val thinkingLevel: String = "low",
) {
    // Completed turns only: a failed send isn't recorded, so it can simply be sent again.
    private val history = mutableListOf<JsonObject>()

    /**
     * Gives the session an earlier conversation (e.g. a chat reopened from the history), so
     * Gemini has its context. Each turn is (isUser, text); empty turns are skipped.
     */
    fun restoreHistory(turns: List<Pair<Boolean, String>>) {
        history.clear()
        turns.filter { it.second.isNotBlank() }.forEach { (isUser, text) ->
            history += buildJsonObject {
                put("role", if (isUser) "user" else "model")
                putJsonArray("parts") { addJsonObject { put("text", text) } }
            }
        }
    }

    /** Sends [prompt] (with an optional photo) and emits the reply's text as it streams in. */
    fun sendMessageStream(
        prompt: String,
        image: ByteArray? = null,
        /** If set, Gemini replies with JSON matching this schema (Gemini's OpenAPI-style subset). */
        responseSchema: JsonObject? = null,
    ): Flow<String> = flow {
        check(apiKey.isNotBlank()) {
            "GEMINI_API_KEY is not configured. Add 'gemini_api_key=YOUR_KEY' to local.properties and rebuild."
        }
        val userTurn = buildJsonObject {
            put("role", "user")
            putJsonArray("parts") {
                if (image != null) {
                    addJsonObject {
                        putJsonObject("inline_data") {
                            put("mime_type", mimeTypeOf(image))
                            put("data", Base64.Default.encode(image))
                        }
                    }
                }
                addJsonObject { put("text", prompt) }
            }
        }
        val body = buildJsonObject {
            putJsonArray("contents") {
                history.forEach { add(it) }
                add(userTurn)
            }
            putJsonObject("generationConfig") {
                putJsonObject("thinkingConfig") { put("thinkingLevel", thinkingLevel) }
                if (responseSchema != null) {
                    put("responseMimeType", "application/json")
                    put("responseSchema", responseSchema)
                }
            }
        }

        val answer = StringBuilder()
        var signature: String? = null

        // Busy (503) or rate limited (429) before any text arrives: wait and retry, then try the
        // fallback model. If the main model sends nothing for FIRST_REPLY_TIMEOUT_MS (it can sit
        // thinking for minutes), go straight to the fallback rather than waiting it out.
        // Once text has been shown, errors are passed on as they are.
        val fallback = fallbackModelName?.takeIf { it != modelName }
        val attempts = listOf(modelName to 0L, modelName to 1_500L, modelName to 3_000L) +
            listOfNotNull(fallback?.let { it to 0L })
        var index = 0
        while (index < attempts.size) {
            val (model, wait) = attempts[index]
            if (wait > 0) delay(wait)
            val isFallback = model == fallback
            try {
                streamOnce(
                    model = model,
                    body = body.toString(),
                    // Only the main model gets a first-reply deadline; the fallback is the last resort
                    firstReplyTimeoutMillis = if (fallback != null && !isFallback) FIRST_REPLY_TIMEOUT_MS else null,
                ) { text, sig ->
                    sig?.let { signature = it }
                    if (text.isNotEmpty()) {
                        answer.append(text)
                        emit(text)
                    }
                }
                break
            } catch (e: GeminiApiException) {
                if (!e.isBusy || answer.isNotEmpty() || index == attempts.lastIndex) throw e
                index++
            } catch (e: Throwable) {
                // A slow first reply from the main model: skip its retries and ask the fallback
                // (A real cancellation, e.g. the user starting a new chat, leaves this coroutine inactive.)
                val slowStart = currentCoroutineContext().isActive && (e is FirstReplyTimeout || e.isTimeout())
                if (!slowStart || isFallback || answer.isNotEmpty() || fallback == null) throw e
                index = attempts.lastIndex
            }
        }

        history += userTurn
        history += buildJsonObject {
            put("role", "model")
            putJsonArray("parts") {
                addJsonObject {
                    put("text", answer.toString())
                    // Gemini 3+ recommends sending its thought signature back with the next turn
                    signature?.let { put("thoughtSignature", it) }
                }
            }
        }
    }

    /** One streaming request; calls [onChunk] with each piece of answer text (and any thought signature). */
    private suspend fun streamOnce(
        model: String,
        body: String,
        /** If set, give up when no answer text has arrived within this time (see [FirstReplyTimeout]). */
        firstReplyTimeoutMillis: Long? = null,
        onChunk: suspend (String, String?) -> Unit,
    ) {
        val started = TimeSource.Monotonic.markNow()
        var gotText = false
        client.preparePost("$BASE_URL/$model:streamGenerateContent?alt=sse") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(body)
            // Silence on the connection for this long (the model thinking without sending anything)
            // ends the request. Once text flows, chunks arrive far more often than this.
            if (firstReplyTimeoutMillis != null) {
                timeout { socketTimeoutMillis = firstReplyTimeoutMillis }
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw GeminiApiException(response.status.value, apiErrorMessage(response.bodyAsText()))
            }
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line(Int.MAX_VALUE) ?: break
                if (!line.startsWith("data:")) continue
                val chunk = runCatching { json.parseToJsonElement(line.removePrefix("data:").trim()).jsonObject }
                    .getOrNull() ?: continue
                val parts = chunk["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: continue
                for (part in parts) {
                    val p = part.jsonObject
                    val sig = p["thoughtSignature"]?.jsonPrimitive?.contentOrNull
                    if (p["thought"]?.jsonPrimitive?.booleanOrNull == true) {
                        if (sig != null) onChunk("", sig)
                        continue
                    }
                    val text = p["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (text.isNotEmpty()) gotText = true
                    onChunk(text, sig)
                }
                // Data is arriving but still no answer (e.g. only thought signatures): same deadline
                if (!gotText && firstReplyTimeoutMillis != null &&
                    started.elapsedNow().inWholeMilliseconds > firstReplyTimeoutMillis
                ) {
                    throw FirstReplyTimeout(model)
                }
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * How long the main model may take to start answering before the fallback is asked instead.
         * gemini-4-hydrogen sometimes thinks for minutes; gemini-3.5-flash answers in a few seconds.
         */
        const val FIRST_REPLY_TIMEOUT_MS = 25_000L
        private val json = Json { ignoreUnknownKeys = true }

        // Shared client. Socket timeout = longest gap allowed between bytes; request timeout = whole reply.
        private val client by lazy {
            HttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 20_000
                    socketTimeoutMillis = 120_000
                    requestTimeoutMillis = 180_000
                }
            }
        }

        private fun mimeTypeOf(bytes: ByteArray): String = when {
            bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
            bytes.size > 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "image/webp"
            bytes.size > 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
            else -> "image/jpeg"
        }

        private fun apiErrorMessage(body: String): String =
            runCatching {
                json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: body.take(300)
    }
}

/** An error response from the Gemini API (bad key, quota, unsupported request...). */
class GeminiApiException(val status: Int, message: String) : Exception("Gemini error $status: $message") {
    /** Temporarily overloaded or rate limited: worth retrying shortly. */
    val isBusy: Boolean get() = status == 503 || status == 429
}

/** The main model didn't start answering in time; the session moves on to the fallback model. */
class FirstReplyTimeout(model: String) : Exception("$model didn't start replying in time")
