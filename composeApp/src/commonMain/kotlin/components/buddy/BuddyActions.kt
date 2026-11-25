package components.buddy

/** Something the Buddy offers under a reply, written by Gemini as a tag (see [systemInstruction]). */
sealed interface BuddyAction {
    data class Breathe(val seconds: Int) : BuddyAction
    data class Meditate(val minutes: Int) : BuddyAction
    data class AddHabit(val name: String) : BuddyAction
    data object LogMood : BuddyAction
}

object BuddyActions {
    private val tag = Regex("""\[\[\s*(breathe|meditate|habit|mood)\s*(?::\s*([^\]]*?))?\s*]]""", RegexOption.IGNORE_CASE)
    /** A tag still arriving at the end of a streamed reply ("[[medi"), hidden until it's complete */
    private val partialTag = Regex("""\[\[[^\]]*]?$""")

    /** The reply text without its tags, and the cards they ask for (at most two, no repeats). */
    fun parse(text: String): Pair<String, List<BuddyAction>> {
        val actions = tag.findAll(text).mapNotNull { m ->
            val arg = m.groupValues[2].trim()
            when (m.groupValues[1].lowercase()) {
                "breathe" -> BuddyAction.Breathe((arg.toIntOrNull() ?: 60).coerceIn(30, 180))
                "meditate" -> BuddyAction.Meditate((arg.toIntOrNull() ?: 5).coerceIn(1, 30))
                "habit" -> arg.trim('"', '\'', ' ').take(40).takeIf { it.isNotBlank() }?.let { BuddyAction.AddHabit(it) }
                "mood" -> BuddyAction.LogMood
                else -> null
            }
        }.distinct().take(2).toList()
        return clean(text) to actions
    }

    /** Text to show or read aloud: tags (and a half-arrived one) removed. */
    fun clean(text: String): String =
        text.replace(tag, "").replace(partialTag, "").trimEnd()
}
