package service

/**
 * "Your session has finished" notifications, handed to the operating system when a session starts
 * so they arrive on time even if the app is in the background or the phone is locked.
 */
expect object SessionNotifications {
    /** Asks for permission to notify if it hasn't been decided yet; true if notifications may be shown. */
    suspend fun ensurePermission(): Boolean

    /** Shows [title] / [body] in [afterSeconds] seconds, replacing any pending one with the same [id]. */
    fun schedule(id: String, afterSeconds: Int, title: String, body: String)

    /** Shows [title] / [body] every day at [hour]:[minute] (local time), replacing any with the same [id]. */
    fun scheduleDaily(id: String, hour: Int, minute: Int, title: String, body: String)

    /** Cancels the pending notification with this [id], if any. */
    fun cancel(id: String)
}

/** The meditation timer's "session finished" notification. */
const val MEDITATION_FINISHED_NOTIFICATION = "meditation-finished"
