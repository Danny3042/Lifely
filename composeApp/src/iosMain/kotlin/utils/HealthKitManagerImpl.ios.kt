package utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.HealthKit.HKAuthorizationStatusSharingAuthorized
import platform.HealthKit.HKCategorySample
import platform.HealthKit.HKCategoryTypeIdentifier
import platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleep
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKObjectQueryNoLimit
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantityTypeIdentifier
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierAppleExerciseTime
import platform.HealthKit.HKQuantityTypeIdentifierDistanceWalkingRunning
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionNone
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKStatisticsOptionCumulativeSum
import platform.HealthKit.HKStatisticsQuery
import platform.HealthKit.HKUnit
import platform.HealthKit.countUnit
import platform.HealthKit.kilocalorieUnit
import platform.HealthKit.meterUnit
import platform.HealthKit.minuteUnit
import platform.HealthKit.predicateForSamplesWithStartDate

actual interface HealthKitService {
    actual fun requestAuthorization(): Boolean
    actual fun checkPermissions(): Boolean
    actual fun readData(): Flow<HealthData>
}

class IOSHealthKitServiceImpl : HealthKitService {
    private val healthStore = HKHealthStore()

override fun requestAuthorization(): Boolean {
    var isAuthorized = false
    val typesToRead: Set<HKObjectType> = setOf(
        HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount),
        HKObjectType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis),
        HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierAppleExerciseTime),
        HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceWalkingRunning)
    ).filterNotNull().toSet()

    healthStore.requestAuthorizationToShareTypes(null, typesToRead) { success, error ->
        isAuthorized = success
    }
    return isAuthorized
}

override fun checkPermissions(): Boolean {
    val typesToRead: Set<HKObjectType> = setOf(
        HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount),
        HKObjectType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis),
        HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierAppleExerciseTime),
        HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceWalkingRunning)
    ).filterNotNull().toSet()

    return typesToRead.all { type ->
    healthStore.authorizationStatusForType(type) == HKAuthorizationStatusSharingAuthorized
}
}

    override fun readData(): Flow<HealthData> = flow {
        val endDate = NSDate()
        val startDate = NSCalendar.currentCalendar.dateByAddingUnit(NSCalendarUnitDay, -1, endDate, 0u)!!

        val stepCount = readQuantityData(HKQuantityTypeIdentifierStepCount, startDate, endDate)
        val sleepDuration = readCategoryData(HKCategoryTypeIdentifierSleepAnalysis, startDate, endDate)
        val exerciseDuration = readQuantityData(HKQuantityTypeIdentifierAppleExerciseTime, startDate, endDate)
        val distance = readQuantityData(HKQuantityTypeIdentifierDistanceWalkingRunning, startDate, endDate)
        val calories = readQuantityData(HKQuantityTypeIdentifierActiveEnergyBurned, startDate, endDate)

        emit(
            HealthData(
                timestamp = endDate.toKotlinInstant(),
                stepCount = stepCount?.toInt(),
                sleepDurationMinutes = sleepDuration?.toInt(),
                exerciseDurationMinutes = exerciseDuration?.toInt(),
                distanceMeters = distance,
                calories = calories?.toInt()
            )
        )
    }

    private suspend fun readQuantityData(typeIdentifier: HKQuantityTypeIdentifier, startDate: NSDate, endDate: NSDate): Double? {
        val type = HKObjectType.quantityTypeForIdentifier(typeIdentifier) ?: return null
        val predicate = HKQuery.predicateForSamplesWithStartDate(startDate, endDate, HKQueryOptionNone)
        var result: Double? = null

        val unit = when (typeIdentifier) {
            HKQuantityTypeIdentifierStepCount -> HKUnit.countUnit()
            HKQuantityTypeIdentifierAppleExerciseTime -> HKUnit.minuteUnit()
            HKQuantityTypeIdentifierDistanceWalkingRunning -> HKUnit.meterUnit()
            HKQuantityTypeIdentifierActiveEnergyBurned -> HKUnit.kilocalorieUnit()
            else -> return null
        }

        val semaphore = kotlinx.coroutines.sync.Semaphore(1)
        semaphore.acquire()

        val query = HKStatisticsQuery(type, predicate, HKStatisticsOptionCumulativeSum) { _, stats, _ ->
            result = stats?.sumQuantity()?.doubleValueForUnit(unit)
            semaphore.release()
        }
        healthStore.executeQuery(query)

        semaphore.acquire()
        semaphore.release()

        return result
    }

    private suspend fun readCategoryData(typeIdentifier: HKCategoryTypeIdentifier, startDate: NSDate, endDate: NSDate): Double? {
        val type = HKObjectType.categoryTypeForIdentifier(typeIdentifier) ?: return null
        val predicate = HKQuery.predicateForSamplesWithStartDate(startDate, endDate, HKObjectQueryNoLimit)
        var result: Double? = null

        val semaphore = kotlinx.coroutines.sync.Semaphore(1)
        semaphore.acquire()

        val query = HKSampleQuery(type, predicate, HKObjectQueryNoLimit, null) { _, samples, _ ->
            result = samples?.sumOf {
                (it as? HKCategorySample)?.let { sample ->
                    if (sample.value == HKCategoryValueSleepAnalysisAsleep) {
                        (sample.endDate.timeIntervalSince1970 - sample.startDate.timeIntervalSince1970) / 60.0
                    } else 0.0
                } ?: 0.0
            }
            semaphore.release()
        }
        healthStore.executeQuery(query)

        semaphore.acquire()
        semaphore.release()

        return result
    }
}

fun NSDate.toKotlinInstant(): Instant = Instant.fromEpochMilliseconds((this.timeIntervalSince1970 * 1000).toLong())