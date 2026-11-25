package utils

import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.HealthKit.HKAuthorizationRequestStatusShouldRequest
import platform.HealthKit.HKAuthorizationStatusSharingAuthorized
import platform.HealthKit.HKCategorySample
import platform.HealthKit.HKCategoryTypeIdentifierMindfulSession
import platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis
import platform.HealthKit.HKCategoryValueNotApplicable
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierAppleExerciseTime
import platform.HealthKit.HKQuantityTypeIdentifierDistanceWalkingRunning
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Everything the app touches in Apple Health, asked for in one request. */
internal object AppleHealth {
    val store: HKHealthStore by lazy { HKHealthStore() }
    val isAvailable: Boolean get() = HKHealthStore.isHealthDataAvailable()

    private val mindful get() = HKObjectType.categoryTypeForIdentifier(HKCategoryTypeIdentifierMindfulSession)

    /** Read: shown on "Today in Health" (steps, sleep, exercise, calories), distance, and mindful minutes for the chart */
    val readTypes: Set<HKObjectType>
        get() = listOfNotNull(
            HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount),
            HKObjectType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis),
            HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierAppleExerciseTime),
            HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceWalkingRunning),
            HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned),
            mindful,
        ).toSet()

    /** Write: finished meditations, as Mindful Minutes */
    val shareTypes: Set<HKObjectType> get() = listOfNotNull(mindful).toSet()

    fun main(block: () -> Unit) = dispatch_async(dispatch_get_main_queue()) { block() }

    fun needsRequest(onResult: (Boolean) -> Unit) {
        if (!isAvailable) return onResult(false)
        store.getRequestStatusForAuthorizationToShareTypes(shareTypes, readTypes) { status, _ ->
            main { onResult(status == HKAuthorizationRequestStatusShouldRequest) }
        }
    }

    fun request(onDone: (Boolean) -> Unit) {
        if (!isAvailable) return onDone(false)
        store.requestAuthorizationToShareTypes(shareTypes, readTypes) { success, _ ->
            main { onDone(success) }
        }
    }

    fun saveMindful(startEpochMillis: Long, endEpochMillis: Long) {
        val type = mindful ?: return
        if (!isAvailable || endEpochMillis <= startEpochMillis) return
        // Never prompts here: only saves if the person already allowed it
        if (store.authorizationStatusForType(type) != HKAuthorizationStatusSharingAuthorized) return
        val sample = HKCategorySample.categorySampleWithType(
            type,
            HKCategoryValueNotApplicable,
            NSDate.dateWithTimeIntervalSince1970(startEpochMillis / 1000.0),
            NSDate.dateWithTimeIntervalSince1970(endEpochMillis / 1000.0),
        )
        store.saveObject(sample) { _, _ -> }
    }
}

actual fun healthAccessNeedsRequest(onResult: (Boolean) -> Unit) = AppleHealth.needsRequest(onResult)

actual fun requestHealthAccess(onDone: (Boolean) -> Unit) = AppleHealth.request(onDone)

actual fun saveMindfulMinutes(startEpochMillis: Long, endEpochMillis: Long) =
    AppleHealth.saveMindful(startEpochMillis, endEpochMillis)
