package service

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
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
    /** Read before every request, so instructions that carry live context (today's stats) stay current. */
    private val systemInstruction: (() -> String?)? = null,
    private val firstReplyTimeoutMillis: Long = FIRST_REPLY_TIMEOUT_MS,
) {
    // Completed turns only: a failed send isn't recorded, so it can simply be sent again.
    private val history = mutableListOf<JsonObject>()

    /**
     * Gives the session an earlier conversation (e.g. a chat reopened from the history), so
     * Gemini has its context. Each turn is (isUser, text); empty turns are skipped.
     */
    fun restoreHistory(turns: List<Pair<Boolean, String>>) {
        history.clear()
        turns.filter { it.second.isNotBlank() }
            .takeLast(MAX_HISTORY_MESSAGES)
            .forEach { (isUser, text) ->
                history += buildJsonObject {
                    put("role", if (isUser) "user" else "model")
                    putJsonArray("parts") { addJsonObject { put("text", text) } }
                }
            }
    }

    /** Sends [prompt] (with an optional photo) and emits the reply's text as it streams in. */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun sendMessageStream(
        prompt: String,
        image: ByteArray? = null,
        /** If set, Gemini replies with JSON matching this schema (Gemini's OpenAPI-style subset). */
        responseSchema: JsonObject? = null,
        maxTokens: Int? = null,
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
        val system = systemInstruction?.invoke()?.takeIf { it.isNotBlank() }
        fun buildBody(targetModel: String): String = buildJsonObject {
            if (system != null) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", system) } }
                }
            }
            putJsonArray("contents") {
                history.takeLast(MAX_HISTORY_MESSAGES).forEach { add(it) }
                add(userTurn)
            }
            putJsonObject("generationConfig") {
                putJsonObject("thinkingConfig") {
                    if (targetModel.contains("2.5") || targetModel.contains("2.0")) {
                        put("thinkingBudget", 0)
                    } else {
                        put("thinkingLevel", thinkingLevel)
                    }
                }
                val tokenBudget = maxTokens ?: if (responseSchema != null) 2048 else DEFAULT_MAX_OUTPUT_TOKENS
                put("maxOutputTokens", tokenBudget)
                if (responseSchema != null) {
                    put("responseMimeType", "application/json")
                    put("responseSchema", responseSchema)
                }
            }
        }.toString()

        val answer = StringBuilder()
        var signature: String? = null

        // Make one short retry for transient overload, then fail over. Bound first response from
        // both models so a slow fallback cannot leave the chat waiting indefinitely.
        val fallback = fallbackModelName?.takeIf { it != modelName }
        val attempts = listOf(modelName to 0L, modelName to BUSY_RETRY_DELAY_MS) +
            listOfNotNull(fallback?.let { it to 0L })
        var index = 0
        while (index < attempts.size) {
            val (model, wait) = attempts[index]
            if (wait > 0) delay(wait)
            val isFallback = model == fallback
            try {
                streamOnce(
                    model = model,
                    body = buildBody(model),
                    firstReplyTimeoutMillis = if (isFallback) {
                        FALLBACK_FIRST_REPLY_TIMEOUT_MS
                    } else {
                        firstReplyTimeoutMillis
                    },
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
            while (history.size > MAX_HISTORY_MESSAGES) history.removeAt(0)
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
        coroutineScope {
            // Covers the whole request (connecting, headers, first words), not just reading lines.
            // Thrown from this child, FirstReplyTimeout comes out of coroutineScope as itself.
            val watchdog = firstReplyTimeoutMillis?.let { limit ->
                launch {
                    delay(limit)
                    if (!gotText) throw FirstReplyTimeout(model)
                }
            }
            try {
                client.preparePost("$BASE_URL/$model:streamGenerateContent?alt=sse") {
                    header("x-goog-api-key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        throw GeminiApiException(response.status.value, apiErrorMessage(response.bodyAsText()))
                    }
                    val channel = response.bodyAsChannel()
                    while (true) {
                        val line = if (!gotText && firstReplyTimeoutMillis != null) {
                            val remainingMillis = firstReplyTimeoutMillis - started.elapsedNow().inWholeMilliseconds
                            if (remainingMillis <= 0) throw FirstReplyTimeout(model)
                            val pendingLine = withTimeoutOrNull(remainingMillis) {
                                channel.readUTF8Line(Int.MAX_VALUE)
                            }
                            if (pendingLine == null) {
                                if (!gotText) throw FirstReplyTimeout(model)
                                break
                            }
                            pendingLine
                        } else {
                            channel.readUTF8Line(Int.MAX_VALUE)
                        } ?: break
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
                    }
                }
            } finally {
                watchdog?.cancel()
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * How long either model may stay silent before failing over or showing an error.
         */
        const val FIRST_REPLY_TIMEOUT_MS = 10_000L
        private const val FALLBACK_FIRST_REPLY_TIMEOUT_MS = 10_000L
        private const val BUSY_RETRY_DELAY_MS = 500L
        private const val MAX_HISTORY_MESSAGES = 12
        private const val DEFAULT_MAX_OUTPUT_TOKENS = 768
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
