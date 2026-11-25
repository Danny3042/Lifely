package tts

import android.media.AudioAttributes
import android.media.MediaPlayer
import platform.AppContextHolder
import java.io.File

actual object GuideAudio {
    private var player: MediaPlayer? = null

    actual fun play(filename: String) {
        stop()
        val file = File(AppContextHolder.context.filesDir, filename)
        if (!file.exists()) return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { it.release(); if (player === it) player = null }
                prepare()
                start()
            }
        }.getOrNull()
    }

    actual fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }
}
