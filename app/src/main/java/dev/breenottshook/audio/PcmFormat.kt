package dev.breenottshook.audio

data class PcmFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int
)
