package rewriter

import kotlinx.coroutines.delay
import service.GenerativeAiService
import tts.getTtsService
import tts.TtsSettings

/**
 * Simple, local rewriter that makes short, user-friendly edits.
 * Tries to use Gemini (GenerativeAiService.rewriteText) when available, falls back to local rules.
 */
suspend fun reformatToFriendly(input: String): String {
    // tiny debounce / simulate work
    delay(120)
    if (input.isBlank()) return input

    // Try Gemini first (networked). If it fails or is disabled, fallback to local rules.
    try {
        val rewritten = GenerativeAiService.instance.rewriteText(input)
        if (!rewritten.isNullOrBlank()) {
            // Optionally auto-speak on platforms that have TTS enabled — avoid platform-specific APIs here.
            // We'll leave it to callers to trigger TTS if desired.
            return rewritten
        }
    } catch (_: Throwable) {
        // fallthrough to local fallback
    }

    // Local fallback: basic normalization: trim, collapse spaces, ensure punctuation, sentence case
    val collapsed = input.trim().replace(Regex("\\s+"), " ")

    // ensure ends with punctuation
    val ended = if (collapsed.last() in listOf('.', '!', '?')) collapsed else "$collapsed."

    // sentence case (capitalize first letter)
    val result = ended.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    return result
}
