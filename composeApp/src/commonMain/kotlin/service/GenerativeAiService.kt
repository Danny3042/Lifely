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

    /** A chat that streams quickly (Gemini 3 thinking set to "low") with sensible timeouts. */
    fun startFastChat(): GeminiChatSession = GeminiChatSession(GEMINI_API_KEY, MODEL_NAME, FALLBACK_MODEL_NAME)

    suspend fun getSuggestions(results: List<String>): String? {
        if (!enabled) {
            throw IllegalStateException(
                "GEMINI_API_KEY is not configured. Please add 'gemini_api_key=YOUR_KEY' to project local.properties or set the GEMINI_API_KEY environment variable, then rebuild."
            )
        }

        // Gemini 3 models think before answering, so a reply can occasionally be slow enough to time
        // out on a weak connection. One quiet retry fixes most of those.
        // Same fast path as the chat: low thinking, our own timeouts (the SDK's 80 s socket timeout
        // was being hit while Gemini 3 thought at its default "high" level).
        val prompt = "Based on the following results: ${results.joinToString(", ")}. Provide suggestions for the week."
        return retryOnTimeout {
            val text = StringBuilder()
            startFastChat().sendMessageStream(prompt).collect { text.append(it) }
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
        // Runtime-configurable model name. You can change this at runtime via `setModelName("...")`.
        // Gemini 4 Hydrogen: Google's fast everyday Gemini 4 model (Nov 2026); replied in ~2-4 s in
        // testing, though new models are sometimes briefly "experiencing high demand" (HTTP 503), so
        // GeminiChatSession retries and then falls back to FALLBACK_MODEL_NAME.
        // (gemini-3.8-flash is listed for this API key but never answered: requests hung.)
        // Gemini 3+ ignores temperature/top_p/top_k and rejects prefilled model turns, so don't add them.
        var MODEL_NAME: String = "gemini-4-hydrogen"

        /** Used when [MODEL_NAME] is busy or rate limited. gemini-3.5-flash is GA and answered in ~3 s. */
        var FALLBACK_MODEL_NAME: String = "gemini-3.5-flash"

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
                // Generous limit: thinking models can take a while before the first words arrive.
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

