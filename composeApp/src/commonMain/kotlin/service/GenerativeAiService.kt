package service

import dev.shreyaspatil.ai.client.generativeai.Chat
import dev.shreyaspatil.ai.client.generativeai.GenerativeModel
import dev.shreyaspatil.ai.client.generativeai.type.Content
import dev.shreyaspatil.ai.client.generativeai.type.RequestOptions
import kotlin.time.Duration.Companion.minutes
import org.danielramzani.HealthCompose.BuildKonfig

/**
 * Service for Generative AI operations that can interact with text as well as images.
 */
class GenerativeAiService(
     private val visionModel: GenerativeModel,
     @Suppress("unused") private val maxTokens: Int,
     private val enabled: Boolean = true
) {

    /**
     * Creates a chat instance which internally tracks the ongoing conversation with the model
     *
     * @param history History of conversation
     */
    fun startChat(history: List<Content>): Chat {
        return visionModel.startChat(history)
    }

    /** A chat that streams quickly (zero thinking delay) with short timeouts for real-time interaction. */
    fun startFastChat(systemInstruction: (() -> String?)? = null): GeminiChatSession =
        GeminiChatSession(
            apiKey = GEMINI_API_KEY,
            modelName = FAST_CHAT_MODEL,
            fallbackModelName = FAST_CHAT_FALLBACK,
            thinkingLevel = "low",
            systemInstruction = systemInstruction,
            firstReplyTimeoutMillis = 10_000L,
        )

    /** A session configured for deeper analysis (weekly reports, coaching tips) with thinking enabled and relaxed timeout. */
    fun startDeepAnalysisSession(systemInstruction: (() -> String?)? = null): GeminiChatSession =
        GeminiChatSession(
            apiKey = GEMINI_API_KEY,
            modelName = DEEP_ANALYSIS_MODEL,
            fallbackModelName = FAST_CHAT_MODEL,
            thinkingLevel = "low",
            systemInstruction = systemInstruction,
            firstReplyTimeoutMillis = 20_000L,
        )

    suspend fun getSuggestions(results: List<String>): String? {
        if (!enabled) {
            throw IllegalStateException(
                "GEMINI_API_KEY is not configured. Please add 'gemini_api_key=YOUR_KEY' to project local.properties or set the GEMINI_API_KEY environment variable, then rebuild."
            )
        }

        // Deep analysis for weekly suggestions uses the reasoning tier with a relaxed timeout
        val prompt = "Based on the following results: ${results.joinToString(", ")}. Provide suggestions for the week."
        return retryOnTimeout(retries = 1) {
            val text = StringBuilder()
            startDeepAnalysisSession().sendMessageStream(prompt).collect { text.append(it) }
            text.toString()
        }.ifBlank { null }
    }

    /**
     * Rewrite arbitrary text to a friendlier, concise form using the generative model.
     * Returns rewritten text on success or null on failure / if model is disabled.
     */
    suspend fun rewriteText(text: String): String? {
        if (!enabled) return null
        try {
            val prompt = "Rewrite the following user message to be concise, friendly, and appropriate for a health & meditation app. Keep meaning and intent intact. Return only the rewritten text: \"$text\""
            val response = visionModel.generateContent(
                prompt = prompt,
            )
            return response.text?.trim()
        } catch (_: Exception) {
            return null
        }
    }
    companion object {
        @Suppress("ktlint:standard:property-naming")
        // Use BuildKonfig (set at build time). Do not call JVM-specific APIs in commonMain.
        var GEMINI_API_KEY: String = BuildKonfig.GEMINI_API_KEY

        // Tier 1: Real-time interactive chat (low latency)
        var FAST_CHAT_MODEL: String = "gemini-2.5-flash"
        var FAST_CHAT_FALLBACK: String = "gemini-3.5-flash-lite"

        // Tier 2: Deep reasoning for background analysis, suggestions, and coaching tips
        var DEEP_ANALYSIS_MODEL: String = "gemini-3.5-flash-lite"

        // Backwards compatibility properties
        var MODEL_NAME: String
            get() = FAST_CHAT_MODEL
            set(value) { FAST_CHAT_MODEL = value }

        var FALLBACK_MODEL_NAME: String
            get() = FAST_CHAT_FALLBACK
            set(value) { FAST_CHAT_FALLBACK = value }

        // Backing instance that can be recreated if model name is changed.
        private var _instance: GenerativeAiService? = null

        // Lazily-created instance that uses the current MODEL_NAME and GEMINI_API_KEY
        val instance: GenerativeAiService
            get() {
                if (_instance == null) {
                    _instance = createService(maxTokens = 200)
                }
                return _instance!!
            }

        // Create a service using the current MODEL_NAME and provided maxTokens
        fun createService(maxTokens: Int = 200): GenerativeAiService {
            val enabled = GEMINI_API_KEY.isNotBlank()
            val model = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = GEMINI_API_KEY,
                requestOptions = RequestOptions(timeout = 3.minutes),
            )
            return GenerativeAiService(
                visionModel = model,
                maxTokens = maxTokens,
                enabled = enabled
            )
        }

        // Change model name at runtime (next access to `instance` will recreate with the new model)
        fun setModelName(newModelName: String, recreateInstance: Boolean = true) {
            MODEL_NAME = newModelName
            if (recreateInstance) _instance = createService()
        }
     }
 }

/** True if [this] (or anything it wraps) is a network or request timeout. */
fun Throwable.isTimeout(): Boolean =
    generateSequence(this) { it.cause }.take(8).any { e ->
        val name = e::class.simpleName.orEmpty()
        val message = e.message.orEmpty()
        name.contains("Timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("allotted time", ignoreCase = true)
    }

/** Runs [block], trying again up to [retries] more times if it fails with a timeout. */
suspend fun <T> retryOnTimeout(retries: Int = 1, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            if (!e.isTimeout() || attempt >= retries) throw e
            attempt++
        }
    }
}

/** A message for people rather than a stack trace: what went wrong and what to do. */
fun friendlyGeminiError(error: Throwable): String = when {
    error.isTimeout() -> "Gemini took too long to reply. Check your connection and try again."
    error is GeminiApiException && error.isBusy -> "Gemini is very busy right now. Please try again in a moment."
    else -> error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong talking to Gemini. Please try again."
}
