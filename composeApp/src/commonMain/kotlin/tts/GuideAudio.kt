package tts

/** Plays a spoken guidance file (saved under the app's files) over any sounds already playing. */
expect object GuideAudio {
    /** Plays [filename] (relative to the app's files, e.g. "attachments/guide-....wav"), replacing any line still playing. */
    fun play(filename: String)

    fun stop()
}
