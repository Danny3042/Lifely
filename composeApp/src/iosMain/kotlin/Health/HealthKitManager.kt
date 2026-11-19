package Health

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantitySample
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierDistanceWalkingRunning
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionNone
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKUnit
import platform.HealthKit.countUnit
import platform.HealthKit.predicateForSamplesWithStartDate

class HealthKitManager {
    private val healthStore = HKHealthStore()

    fun requestAuthorization(completion: (Boolean) -> Unit) {
        val readDataTypes = setOf(
            HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount),
            HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceWalkingRunning),
            HKObjectType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned),
            HKObjectType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis)
        )

        healthStore.requestAuthorizationToShareTypes(null, readDataTypes) { success, error ->
            completion(success)
        }
    }

    fun getSteps(completion: (String) -> Unit) {
        HKQuantityTypeIdentifierStepCount?.let { fetchData(it, completion) }
    }

    fun getActiveMinutes(completion: (String) -> Unit) {
        HKQuantityTypeIdentifierActiveEnergyBurned?.let { fetchData(it, completion) }
    }

    fun getDistance(completion: (String) -> Unit) {
        HKQuantityTypeIdentifierDistanceWalkingRunning?.let { fetchData(it, completion) }
    }

    fun getSleepDuration(completion: (String) -> Unit) {
        HKCategoryTypeIdentifierSleepAnalysis?.let { fetchData(it, completion) }
    }

    private fun fetchData(identifier: String, completion: (String) -> Unit) {
        val dataType = HKObjectType.quantityTypeForIdentifier(identifier)
        if (dataType == null) {
            completion("0")
            return
        }

        val now = NSDate()
        val calendar = NSCalendar.currentCalendar()

        val components = NSDateComponents()
        components.day = -1

        val startDate = calendar.dateByAddingComponents(components, toDate = now, options = 0u)
        val predicate = HKQuery.predicateForSamplesWithStartDate(startDate, endDate = now, options = HKQueryOptionNone)

        val query = HKSampleQuery(sampleType = dataType, predicate = predicate, limit = 0u, sortDescriptors = null) { _, results, _ ->
            val data = results?.sumOf { (it as HKQuantitySample).quantity.doubleValueForUnit(HKUnit.countUnit()) }
            completion(data?.toInt().toString())
        }

        healthStore.executeQuery(query)
    }
}