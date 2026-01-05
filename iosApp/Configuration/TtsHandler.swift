import Foundation
import AVFoundation

// Swift-side handler for TtsSpeak and TtsStop notifications from shared Kotlin code
@objcMembers
class TtsHandler: NSObject {
    static let shared = TtsHandler()
    private let synth = AVSpeechSynthesizer()
    private var audioPlayer: AVAudioPlayer?

    override init() {
        super.init()
        NotificationCenter.default.addObserver(self, selector: #selector(handleSpeak(_:)), name: Notification.Name("TtsSpeak"), object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(handleStop(_:)), name: Notification.Name("TtsStop"), object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(handleSpeakFile(_:)), name: Notification.Name("TtsSpeakFile"), object: nil)
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func handleSpeak(_ note: Notification) {
        guard let text = note.object as? String else { return }
        // stop existing audio if playing
        if audioPlayer?.isPlaying == true {
            audioPlayer?.stop()
            audioPlayer = nil
        }
        if synth.isSpeaking {
            synth.stopSpeaking(at: .immediate)
        }
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        synth.speak(utterance)
    }

    @objc private func handleSpeakFile(_ note: Notification) {
        // note.object should be a filename relative to Documents (e.g., "attachments/tts-abc123.mp3")
        guard let filename = note.object as? String else { return }

        // Stop any speech synthesis
        if synth.isSpeaking {
            synth.stopSpeaking(at: .immediate)
        }
        // Stop existing audio if playing
        if audioPlayer?.isPlaying == true {
            audioPlayer?.stop()
            audioPlayer = nil
        }

        // Resolve file URL in app Documents directory
        let fm = FileManager.default
        if let docs = fm.urls(for: .documentDirectory, in: .userDomainMask).first {
            let fileURL = docs.appendingPathComponent(filename)
            do {
                audioPlayer = try AVAudioPlayer(contentsOf: fileURL)
                audioPlayer?.prepareToPlay()
                audioPlayer?.play()
            } catch {
                // playback failed - do nothing (fallback could be local TTS but skipped to avoid double speak)
                print("TtsHandler: failed to play audio file: \(error)")
            }
        }
    }

    @objc private func handleStop(_ note: Notification) {
        if synth.isSpeaking {
            synth.stopSpeaking(at: .immediate)
        }
        audioPlayer?.stop()
        audioPlayer = nil
    }
}

// Initialize on load
let _ = TtsHandler.shared
