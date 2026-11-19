package utils

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant


expect interface HealthKitService {
    fun requestAuthorization(): Boolean
    fun checkPermissions(): Boolean
    fun readData(): Flow<HealthData>
}

data class HealthData(
    val timestamp: Instant,
    val stepCount: Int? = null,
    val sleepDurationMinutes: Int? = null,
    val exerciseDurationMinutes: Int? = null,
    val distanceMeters: Double? = null,
    val calories: Int?
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "timestamp" to timestamp.toString(),
        "stepCount" to stepCount,
        "sleepDurationMinutes" to sleepDurationMinutes,
        "exerciseDurationMinutes" to exerciseDurationMinutes,
        "distanceMeters" to distanceMeters,
        "calories" to calories
    )
}