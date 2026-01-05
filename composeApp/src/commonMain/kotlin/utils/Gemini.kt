package utils

import dev.shreyaspatil.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.danielramzani.HealthCompose.BuildKonfig
import service.GenerativeAiService

suspend fun getGeminiSuggestions(results: List<String>, sleepRating: Int, moodRating: Int): List<String> {
    return withContext(Dispatchers.IO) {
        val GEMINI_API_KEY = BuildKonfig.GEMINI_API_KEY

        val instance = GenerativeAiService(
            visionModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = GEMINI_API_KEY,
            ),
            maxTokens = 20
        )

        try {
            val promptList = results + listOf("Sleep Rating: $sleepRating", "Mood Rating: $moodRating")
            val response = instance.getSuggestions(promptList)
            response?.split(",")?.map { it.trim() } ?: emptyList()
        } catch (e: Exception) {
            // failure suppressed - logging removed
            emptyList()
        }
    }
}