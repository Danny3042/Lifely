package components.buddy

import kotlinx.datetime.TimeZone
import service.GeminiLiveSession
import service.weather.BuddyWeather
import service.weather.WeatherKind
import service.weather.WeatherNow
import kotlin.math.roundToInt
import kotlinx.datetime.toLocalDateTime
import utils.DEFAULT_DAILY_GOAL_MINUTES
import utils.HabitRepository
import utils.MeditationHistory
import utils.MoodCheckInStore
import utils.MoodScale
import utils.SettingsManager
import kotlin.time.Clock

/**
 * What the Buddy knows about your day: read from what the app already keeps on the device
 * (meditations, check-ins, habits, goal). Used for the chat's greeting and suggestions, and given
 * to Gemini so its replies fit your day.
 */
data class BuddyDay(
    val hour: Int,
    val minutesToday: Int,
    val goalMinutes: Int,
    val streakDays: Int,
    /** -3..3, from the latest check-in today or yesterday; null if there's none */
    val lastMoodValence: Int?,
    val lastMoodFeelings: List<String>,
    val habits: List<String>,
    /** Local weather, only when the user has chosen to share it; null otherwise. */
    val weather: WeatherNow? = null,
) {
    val isNight: Boolean get() = hour >= 21 || hour < 5
    val partOfDay: String
        get() = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
    val goalMet: Boolean get() = minutesToday >= goalMinutes
    val lastMoodLabel: String? get() = lastMoodValence?.let { MoodScale.label(it) }

    companion object {
        private const val RECENT_MOOD_MILLIS = 36 * 60 * 60 * 1000L

        /** Today as it stands now. Cheap enough to call before each message. */
        fun now(goalMinutes: Int): BuddyDay {
            val now = Clock.System.now()
            val hour = now.toLocalDateTime(TimeZone.currentSystemDefault()).hour
            val recent = MoodCheckInStore.checkIns.firstOrNull()
                ?.takeIf { now.toEpochMilliseconds() - it.atEpochMillis < RECENT_MOOD_MILLIS }
            return BuddyDay(
                hour = hour,
                minutesToday = runCatching { MeditationHistory.todayMinutes() }.getOrDefault(0),
                goalMinutes = goalMinutes,
                streakDays = runCatching { MeditationHistory.streakDays() }.getOrDefault(0),
                lastMoodValence = recent?.valence,
                lastMoodFeelings = recent?.feelings.orEmpty(),
                habits = HabitRepository.activeHabits.toList(),
                weather = if (BuddyPrivacy.shareWeather) BuddyWeather.current else null,
            )
        }

        suspend fun loadGoal(): Int =
            runCatching { SettingsManager.loadDailyGoalMinutes() }.getOrDefault(DEFAULT_DAILY_GOAL_MINUTES)
    }
}

private val WeatherNow.isWet: Boolean
    get() = kind == WeatherKind.Drizzle || kind == WeatherKind.Rain || kind == WeatherKind.HeavyRain || kind == WeatherKind.Thunder

private val WeatherNow.goodForWalk: Boolean
    get() = isDaylight && (kind == WeatherKind.Clear || kind == WeatherKind.PartlyCloudy) && temperatureC in 6.0..29.0

/** "light rain, 8°C, dark outside" for prompts. */
private fun WeatherNow.promptText(): String =
    "${description.lowercase()}, ${temperatureC.roundToInt()}°C, ${if (isDaylight) "light outside" else "dark outside"}"

/** A friendly opening line for the empty chat. */
fun BuddyDay.greeting(): String = when (partOfDay) {
    "morning" -> "Good morning!"
    "afternoon" -> "Good afternoon!"
    "evening" -> "Good evening!"
    else -> "Still up?"
}

/** One sentence that shows the Buddy noticed how your day is going. */
fun BuddyDay.noticing(): String = when {
    lastMoodValence != null && lastMoodValence <= -1 ->
        "You checked in feeling ${lastMoodLabel!!.lowercase()} earlier. Want to talk it through, or take a breather together?"
    goalMet && streakDays >= 2 -> "You've hit your goal and you're on a $streakDays-day streak. Lovely work."
    goalMet -> "You've already reached today's $goalMinutes minutes. How are you feeling?"
    minutesToday > 0 -> "$minutesToday of $goalMinutes mindful minutes so far today. What's on your mind?"
    weather?.isWet == true && isNight -> "It's raining where you are. A good night to slow down together."
    isNight -> "It's late. I can help you wind down whenever you're ready."
    streakDays >= 2 -> "You're on a $streakDays-day streak. Shall we keep it going?"
    else -> "I'm here for stress, sleep, habits, or just a chat."
}

/** Starter prompts chosen for right now, most relevant first. */
fun BuddyDay.suggestions(): List<String> = buildList {
    if (lastMoodValence != null && lastMoodValence <= -1) {
        val word = lastMoodFeelings.firstOrNull()?.lowercase() ?: lastMoodLabel!!.lowercase()
        add("I'm still feeling $word")
    }
    if (isNight) add(if (weather?.isWet == true) "Help me wind down to the rain" else "Help me wind down for sleep")
    add("How am I doing today?")
    if (weather?.goodForWalk == true) add("Ideas for a short walk outside")
    if (!goalMet) {
        val left = (goalMinutes - minutesToday).coerceAtLeast(1)
        add("Help me fit in $left more minutes today")
    }
    add("A quick breathing exercise")
    if (partOfDay == "morning") add("Plan a calm start to my day")
    habits.firstOrNull()?.let { add("Tips for my habit: $it") }
    if (habits.isEmpty()) add("Suggest a small habit to start")
    if (streakDays >= 2) add("How do I keep my streak going?")
}.distinct().take(3)

/** A short, honest reason a card was offered, built only from what this day supports and what Buddy may see. */
fun BuddyDay.reasonFor(action: BuddyAction): String = when (action) {
    is BuddyAction.Breathe -> when {
        BuddyPrivacy.shareMinutes && isNight && minutesToday == 0 -> "It's late and you haven't meditated today."
        BuddyPrivacy.shareMood && lastMoodFeelings.any { it.equals("stressed", ignoreCase = true) } -> "You mentioned feeling stressed."
        BuddyPrivacy.shareMood && lastMoodValence != null && lastMoodValence <= -1 ->
            "You checked in feeling ${lastMoodLabel!!.lowercase()} earlier."
        BuddyPrivacy.shareWeather && weather?.isWet == true && isNight -> "It's raining outside, a good time to slow down."
        else -> "A quick reset can help right now."
    }
    is BuddyAction.Meditate -> when {
        BuddyPrivacy.shareMinutes && !goalMet -> "You're ${(goalMinutes - minutesToday).coerceAtLeast(1)} min from today's goal."
        BuddyPrivacy.shareMinutes && streakDays >= 1 -> "Keeps your $streakDays-day streak going."
        else -> "A short pause can help you reset."
    }
    is BuddyAction.AddHabit -> "Small habits are easier to keep."
    BuddyAction.LogMood -> "A quick check-in helps Buddy understand."
}

/**
 * The Buddy's standing instructions for Gemini: who it is, how to talk, what it knows about
 * today, and the action tags that turn into cards in the chat (see [BuddyAction]).
 */
fun BuddyDay.systemInstruction(): String = buildString {
    appendLine("You are Buddy, the small, gentle sprout companion inside MyHealthBuddy, a mindfulness and habits app.")
    appendLine("Talk like a warm, encouraging friend: concise replies (usually 1 to 3 sentences), plain language, light Markdown only when a short list really helps. No headings.")
    appendLine("You support wellbeing (stress, sleep, mood, meditation, habits). You are not a doctor or therapist: for medical worries suggest a professional, and if someone may be in danger urge them to contact local emergency services or a crisis line right away.")
    appendLine("Use what you know about their day only when it's relevant; don't recite it. If they ask how they're doing, reflect their day back warmly in a sentence or two and invite a mood check-in.")
    appendLine()
    appendLine("Their day so far (from the app):")
    appendLine("- It's $partOfDay (hour $hour).")
    if (BuddyPrivacy.shareMinutes) {
        appendLine("- Mindful minutes today: $minutesToday of a $goalMinutes-minute daily goal.")
        appendLine("- Meditation streak: $streakDays day(s).")
    }
    if (BuddyPrivacy.shareMood) {
        lastMoodLabel?.let { label ->
            val feelings = if (lastMoodFeelings.isNotEmpty()) " (${lastMoodFeelings.joinToString()})" else ""
            appendLine("- Latest mood check-in: $label$feelings.")
        } ?: appendLine("- No recent mood check-in.")
    }
    if (BuddyPrivacy.shareHabits && habits.isNotEmpty()) appendLine("- Habits they're building: ${habits.take(6).joinToString()}.")
    weather?.let {
        appendLine("- Weather where they are right now: ${it.promptText()}. Mention it only when it's relevant (e.g. suggesting a walk or a wind-down).")
    }
    if (!BuddyPrivacy.shareMinutes || !BuddyPrivacy.shareMood || !BuddyPrivacy.shareHabits) {
        appendLine("- They've chosen not to share some details of their day, so don't guess at them.")
    }
    appendLine()
    appendLine("The app can turn special tags into interactive cards under your reply. When one would genuinely help, put at most two tags at the very end of your reply, each on its own line:")
    appendLine("[[breathe:SECONDS]] a guided breathing exercise right in the chat (30 to 180 seconds)")
    appendLine("[[meditate:MINUTES]] a button that starts the meditation timer (1 to 30 minutes)")
    appendLine("[[habit:NAME]] offers to add a habit to their list (a short name, under 40 characters)")
    appendLine("[[mood]] asks them to log how they feel right now")
    appendLine("Refer to the card naturally (for example \"tap below when you're ready\") but never show or explain the tag syntax.")
}

/**
 * Instruction for the live voice chat: the Buddy persona plus a line of today's context
 * (only what the user lets Buddy see), phrased for speech.
 */
fun BuddyDay.voiceInstruction(base: String = GeminiLiveSession.DEFAULT_INSTRUCTION): String {
    val parts = buildList {
        add("it's $partOfDay")
        weather?.let { add("${it.description.lowercase()} outside, ${it.temperatureC.roundToInt()}°C${if (it.isDaylight) "" else ", dark out"}") }
        if (BuddyPrivacy.shareMinutes) add("they've done $minutesToday of $goalMinutes mindful minutes today")
        if (BuddyPrivacy.shareMood) lastMoodLabel?.let { add("latest mood check-in: ${it.lowercase()}") }
    }
    return "$base Context: ${parts.joinToString(", ")}. Use it only when relevant; don't recite it."
}
