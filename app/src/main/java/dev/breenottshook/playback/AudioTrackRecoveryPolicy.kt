package dev.breenottshook.playback

class AudioTrackRecoveryPolicy(
    private val maxRecoveries: Int
) {
    private var recoveries = 0

    fun shouldRecover(wasStarted: Boolean, errorMessage: String?): Boolean {
        if (!wasStarted || errorMessage != "AudioTrack write failed: 0" || recoveries >= maxRecoveries) {
            return false
        }
        recoveries++
        return true
    }
}
