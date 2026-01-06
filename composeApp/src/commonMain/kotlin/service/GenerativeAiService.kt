package service

import dev.shreyaspatil.ai.client.generativeai.Chat
import dev.shreyaspatil.ai.client.generativeai.GenerativeModel
import dev.shreyaspatil.ai.client.generativeai.type.Content
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

    suspend fun getSuggestions(results: List<String>): String? {
        if (!enabled) {
            throw IllegalStateException(
                "GEMINI_API_KEY is not configured. Please add 'gemini_api_key=YOUR_KEY' to project local.properties or set the GEMINI_API_KEY environment variable, then rebuild."
            )
        }

        val response = visionModel.generateContent(
            prompt = "Based on the following results: ${results.joinToString(", ")}. Provide suggestions for the week.",
            // maxTokens is currently passed implicitly by model/client - keep for future
        )
        return response.text
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
        var MODEL_NAME: String = "gemini-2.5-flash"

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
