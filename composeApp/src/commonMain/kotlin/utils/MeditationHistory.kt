package utils

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant
import platform.readTextFile
import platform.writeTextFile

/** One finished meditation. */
@Serializable
data class SessionRecord(val atEpochMillis: Long, val minutes: Int)

/**
 * Finished meditations, kept on the device so stats and widgets survive a restart (they used to be
 * held only in memory). Newest last.
 */
object MeditationHistory {
    private const val FILE = "meditation_sessions.json"
    private const val MAX = 5000
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SessionRecord.serializer())
    private val zone get() = TimeZone.currentSystemDefault()

    private var loaded = false
    private val records = mutableListOf<SessionRecord>()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        records.clear()
        records.addAll(
            runCatching { json.decodeFromString(serializer, readTextFile(FILE)) }.getOrDefault(emptyList())
                .sortedBy { it.atEpochMillis }
        )
    }

    fun add(record: SessionRecord) {
        ensureLoaded()
        records.add(record)
        while (records.size > MAX) records.removeAt(0)
        runCatching { writeTextFile(FILE, json.encodeToString(serializer, records.toList())) }
    }

    private fun dateOf(record: SessionRecord): LocalDate =
        Instant.fromEpochMilliseconds(record.atEpochMillis).toLocalDateTime(zone).date

    private fun today(): LocalDate = Clock.System.todayIn(zone)

    /** This week (Monday first), one list of sessions per day, the way InsightsViewModel shows them. */
    fun thisWeek(): List<List<Session>> {
        ensureLoaded()
        val today = today()
        val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        return List(7) { offset ->
            val day = monday.plus(DatePeriod(days = offset))
            records.filter { dateOf(it) == day }.map { r ->
                val t = Instant.fromEpochMilliseconds(r.atEpochMillis).toLocalDateTime(zone)
                Session(time = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}", duration = r.minutes)
            }
        }
    }

    fun todayMinutes(): Int {
        ensureLoaded()
        val today = today()
        return records.filter { dateOf(it) == today }.sumOf { it.minutes }
    }

    /** Today's finished meditations, earliest first. */
    fun todayRecords(): List<SessionRecord> {
        ensureLoaded()
        val today = today()
        return records.filter { dateOf(it) == today }.sortedBy { it.atEpochMillis }
    }

    fun todaySessions(): Int {
        ensureLoaded()
        val today = today()
        return records.count { dateOf(it) == today }
    }

    /** Days in a row with a session, counting back from today (or from yesterday if today has none yet). */
    fun streakDays(): Int {
        ensureLoaded()
        val days = records.map { dateOf(it) }.toSet()
        var day = today()
        if (day !in days) day = day.minus(DatePeriod(days = 1))
        var streak = 0
        while (day in days) {
            streak++
            day = day.minus(DatePeriod(days = 1))
        }
        return streak
    }
}
