package service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One step of a coach tip. */
data class CoachTipStep(val title: String, val detail: String)

/** A coach tip as structured data, shown as a rich card instead of a block of text. */
data class CoachTip(
    val title: String,
    val summary: String,
    val minutes: Int?,
    val difficulty: String,
    val frequency: String,
    val steps: List<CoachTipStep>,
    val why: String,
    val encouragement: String,
    /** A short habit name, for "Add to my habits". */
    val habitName: String,
)

enum class CoachTopic { Habit, Stress }

/** Asks Gemini for a structured tip about [subject] (a habit to build, or a way to manage stress). */
suspend fun generateCoachTip(subject: String, topic: CoachTopic): CoachTip {
    val ask = when (topic) {
        CoachTopic.Habit -> "The user wants to build this habit: \"$subject\". Give one practical tip to get started and make it stick."
        CoachTopic.Stress -> "The user wants to manage stress with: \"$subject\". Give one practical tip for doing that well."
    }
    val prompt = """
        You are a warm, practical wellness coach in a health app. $ask
        Keep it short and concrete:
        - title: under 6 words
        - summary: one sentence
        - steps: 3 or 4 steps; each title under 8 words, each detail one short sentence
        - minutes: roughly how many minutes a day it takes
        - difficulty: Easy, Medium or Hard
        - frequency: how often, e.g. "Daily" or "3× a week"
        - why: one or two sentences on why it works
        - encouragement: one short, kind line
        - habitName: a short name for this as a habit (at most 4 words)
        This is general wellbeing guidance, not medical advice. Only mention seeing a professional if it's genuinely relevant.
    """.trimIndent()

    val raw = retryOnTimeout {
        val text = StringBuilder()
        GenerativeAiService.instance.startFastChat()
            .sendMessageStream(prompt, responseSchema = COACH_TIP_SCHEMA)
            .collect { text.append(it) }
        text.toString()
    }
    return parseCoachTip(raw) ?: throw IllegalStateException("Couldn't read the coach's tip. Please try again.")
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseCoachTip(raw: String): CoachTip? = runCatching {
    // Tolerate a stray ```json fence even though JSON mode shouldn't add one
    val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val o = json.parseToJsonElement(cleaned).jsonObject
    fun s(key: String) = o[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val steps = (o["steps"] as? JsonArray).orEmpty().mapNotNull { e ->
        val step = e.jsonObject
        val title = step["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isEmpty()) null else CoachTipStep(title, step["detail"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty())
    }
    CoachTip(
        title = s("title"),
        summary = s("summary"),
        minutes = o["minutes"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
        difficulty = s("difficulty"),
        frequency = s("frequency"),
        steps = steps,
        why = s("why"),
        encouragement = s("encouragement"),
        habitName = s("habitName"),
    ).takeIf { it.title.isNotEmpty() && it.steps.isNotEmpty() }
}.getOrNull()

private fun stringProp(description: String) = buildJsonObject {
    put("type", "STRING"); put("description", description)
}

/** Response schema in Gemini's OpenAPI-style subset. */
private val COACH_TIP_SCHEMA: JsonObject = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
        put("title", stringProp("Short tip title, under 6 words"))
        put("summary", stringProp("One-sentence summary"))
        putJsonObject("minutes") { put("type", "INTEGER"); put("description", "Minutes per day") }
        putJsonObject("difficulty") {
            put("type", "STRING")
            putJsonArray("enum") { add(JsonPrimitive("Easy")); add(JsonPrimitive("Medium")); add(JsonPrimitive("Hard")) }
        }
        put("frequency", stringProp("How often, e.g. Daily"))
        putJsonObject("steps") {
            put("type", "ARRAY")
            putJsonObject("items") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    put("title", stringProp("Step, under 8 words"))
                    put("detail", stringProp("One short sentence"))
                }
                putJsonArray("required") { add(JsonPrimitive("title")); add(JsonPrimitive("detail")) }
            }
        }
        put("why", stringProp("Why it works, one or two sentences"))
        put("encouragement", stringProp("One short, kind line"))
        put("habitName", stringProp("Short habit name, at most 4 words"))
    }
    putJsonArray("required") {
        listOf("title", "summary", "minutes", "difficulty", "frequency", "steps", "why", "encouragement", "habitName")
            .forEach { add(JsonPrimitive(it)) }
    }
}
