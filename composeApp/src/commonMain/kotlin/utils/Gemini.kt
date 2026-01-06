package utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import service.GenerativeAiService

suspend fun getGeminiSuggestions(results: List<String>, sleepRating: Int, moodRating: Int): List<String> {
    return withContext(Dispatchers.Default) {
        // Use the centralized service factory so the model name is controlled in one place
        val instance = GenerativeAiService.createService(maxTokens = 60)

        try {
            val promptList = results + listOf("Sleep Rating: $sleepRating", "Mood Rating: $moodRating")
            val response = instance.getSuggestions(promptList)
            response?.split(",")?.map { it.trim() } ?: emptyList()
        } catch (_: Exception) {
            // failure suppressed - logging removed
            emptyList()
        }
    }
}