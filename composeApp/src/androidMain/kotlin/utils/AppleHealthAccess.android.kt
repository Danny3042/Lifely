package utils

// Android uses Health Connect, which has its own permission flow
actual fun healthAccessNeedsRequest(onResult: (Boolean) -> Unit) = onResult(false)

actual fun requestHealthAccess(onDone: (Boolean) -> Unit) = onDone(true)

actual fun saveMindfulMinutes(startEpochMillis: Long, endEpochMillis: Long) {}
