package utils

import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import platform.readTextFile
import platform.writeTextFile

/**
 * A "How are you feeling?" check-in, modelled on State of Mind in Apple's Health app: how pleasant
 * or unpleasant the feeling is, the words that describe it, and what's affecting it most.
 */
@Serializable
data class MoodCheckIn(
    val atEpochMillis: Long,
    /** -3 (very unpleasant) to 3 (very pleasant) */
    val valence: Int,
    val feelings: List<String> = emptyList(),
    val impacts: List<String> = emptyList(),
)

object MoodScale {
    val labels = listOf(
        "Very unpleasant", "Unpleasant", "Slightly unpleasant", "Neutral",
        "Slightly pleasant", "Pleasant", "Very pleasant",
    )

    fun label(valence: Int): String = labels[(valence + 3).coerceIn(0, 6)]

    /** Words to choose from, matched to how pleasant the feeling is. */
    fun feelingsFor(valence: Int): List<String> = when {
        valence <= -2 -> listOf("Angry", "Anxious", "Drained", "Frustrated", "Hopeless", "Lonely", "Overwhelmed", "Sad", "Scared", "Stressed", "Worried", "Ashamed")
        valence == -1 -> listOf("Annoyed", "Discouraged", "Disappointed", "Drained", "Irritated", "Restless", "Stressed", "Tired", "Worried")
        valence == 0 -> listOf("Calm", "Content", "Indifferent", "Peaceful", "Tired", "Focused", "Unsure")
        valence == 1 -> listOf("Calm", "Content", "Focused", "Grateful", "Hopeful", "Peaceful", "Relieved", "Satisfied")
        else -> listOf("Amazed", "Confident", "Energised", "Excited", "Grateful", "Happy", "Hopeful", "Joyful", "Proud", "Relieved")
    }

    val impacts = listOf(
        "Health", "Fitness", "Sleep", "Self-care", "Family", "Friends", "Partner", "Work",
        "Education", "Money", "Tasks", "Hobbies", "Weather", "Current events",
    )
}

/** Saved check-ins, newest first. Kept on the device. */
object MoodCheckInStore {
    private const val FILE = "mood_checkins.json"
    private const val MAX = 3000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(MoodCheckIn.serializer())

    /** Observable list for the UI. Call [load] once to fill it. */
    val checkIns = mutableStateListOf<MoodCheckIn>()

    fun load() {
        val saved = runCatching { json.decodeFromString(serializer, readTextFile(FILE)) }.getOrDefault(emptyList())
        checkIns.clear()
        checkIns.addAll(saved.sortedByDescending { it.atEpochMillis })
    }

    fun add(checkIn: MoodCheckIn, refreshWidgets: Boolean = true) {
        // Keep newest first even when an older check-in arrives late (from a widget)
        val index = checkIns.indexOfFirst { it.atEpochMillis < checkIn.atEpochMillis }.let { if (it < 0) checkIns.size else it }
        checkIns.add(index, checkIn)
        while (checkIns.size > MAX) checkIns.removeAt(checkIns.lastIndex)
        runCatching { writeTextFile(FILE, json.encodeToString(serializer, checkIns.toList())) }
        if (refreshWidgets) service.WidgetSync.refresh()
    }
}
