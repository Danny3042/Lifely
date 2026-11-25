package utils

/**
 * One way into Apple Health for the whole app, following Apple's HealthKit guidelines:
 * - a single request covering everything the app reads and writes, made only when the person
 *   asks for it (never at launch)
 * - finished meditations saved as Mindful Minutes, only if the person allowed it
 * Android goes through Health Connect separately, so these do nothing there.
 */

/** Calls back on the main thread with true if the system permission sheet hasn't been shown yet. */
expect fun healthAccessNeedsRequest(onResult: (Boolean) -> Unit)

/** Shows the system permission sheet (if it still needs showing), then calls back on the main thread. */
expect fun requestHealthAccess(onDone: (Boolean) -> Unit)

/** Saves a finished meditation to Apple Health as Mindful Minutes, if the person allowed writing them. */
expect fun saveMindfulMinutes(startEpochMillis: Long, endEpochMillis: Long)
