package service

/**
 * Shows a running meditation outside the app: on iOS a Live Activity (the Dynamic Island on
 * iPhones that have one, the Lock Screen and a banner on the rest). Nothing on Android yet.
 */
expect object MeditationLiveActivity {
    /** A session started (or resumed) and will finish at [endAtEpochMillis]. */
    fun start(endAtEpochMillis: Long, totalSeconds: Int)
    /** The session ended: [completed] when it ran to the end, false when it was stopped. */
    fun end(completed: Boolean)
}
