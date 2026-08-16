package dev.breenottshook.playback

class StartupBufferGate(
    private val startupBufferMs: Long
) {
    fun shouldStart(bufferedFrames: Long, sampleRate: Int): Boolean =
        bufferedFrames * 1_000L >= sampleRate.toLong() * startupBufferMs
}
