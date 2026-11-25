package tts

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
actual object GuideAudio {
    private var player: AVAudioPlayer? = null
    private var sessionReady = false

    actual fun play(filename: String) {
        stop()
        if (!sessionReady) {
            // Playback category: the guide is heard even with the silent switch on, mixed with other audio
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionCategoryOptionMixWithOthers, null)
            session.setActive(true, null)
            sessionReady = true
        }
        val url = NSURL.fileURLWithPath(NSHomeDirectory() + "/Documents/" + filename)
        player = AVAudioPlayer(contentsOfURL = url, error = null)?.apply {
            prepareToPlay()
            play()
        }
    }

    actual fun stop() {
        player?.stop()
        player = null
    }
}
