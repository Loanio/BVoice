package dev.breenottshook.audio

object PcmSilence {
    fun create(format: PcmFormat, durationMs: Long): PcmSegment? {
        require(durationMs >= 0) { "Silence duration must not be negative" }
        if (durationMs == 0L) return null

        val frameSize = format.channels * format.bitsPerSample / 8
        val frames = format.sampleRate.toLong() * durationMs / 1_000L
        val byteCount = Math.multiplyExact(frames, frameSize.toLong())
        require(byteCount <= Int.MAX_VALUE) { "Silence segment is too large" }
        return PcmSegment(format, ByteArray(byteCount.toInt()))
    }
}
