package utils

import Health.HealthConnectUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant

actual class iOSHealthKitManager actual constructor() : HealthKitService {
    actual override fun requestAuthorization(): Boolean {
        // We cannot launch a UI permission flow from here; return whether permissions are already granted.
        return try {
            runBlocking { HealthConnectUtils.checkPermissions() }
        } catch (_: Throwable) {
            false
        }
    }

    actual override fun checkPermissions(): Boolean {
        return try {
            runBlocking { HealthConnectUtils.checkPermissions() }
        } catch (_: Throwable) {
            false
        }
    }

    actual override fun readData(): Flow<HealthData> = flow {
        try {
            // Read recent (1-day) aggregates from Health Connect utilities.
            val stepsList = HealthConnectUtils.readStepsForInterval(1)
            val minsList = HealthConnectUtils.readMinsForInterval(1)
            val sleepList = HealthConnectUtils.readSleepSessionsForInterval(1)
            val distanceList = HealthConnectUtils.readDistanceForInterval(1)

            val steps = stepsList.lastOrNull()?.metricValue?.toIntOrNull()
            val mins = minsList.lastOrNull()?.metricValue?.toIntOrNull()
            val sleepDuration = sleepList.lastOrNull()?.metricValue?.toIntOrNull()
            val distance = distanceList.lastOrNull()?.metricValue?.toDoubleOrNull()

            val now: Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            emit(
                HealthData(
                    timestamp = now,
                    stepCount = steps,
                    sleepDurationMinutes = sleepDuration,
                    exerciseDurationMinutes = mins,
                    distanceMeters = distance,
                    calories = null
                )
            )
        } catch (_: Throwable) {
            // On error, emit an empty/zeroed HealthData so consumers can handle gracefully
            val now: Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            emit(
                HealthData(
                    timestamp = now,
                    stepCount = null,
                    sleepDurationMinutes = null,
                    exerciseDurationMinutes = null,
                    distanceMeters = null,
                    calories = null
                )
            )
        }
    }
}