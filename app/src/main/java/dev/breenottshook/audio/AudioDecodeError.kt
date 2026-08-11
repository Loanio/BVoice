package dev.breenottshook.audio

sealed interface AudioDecodeError {
    data class InvalidContainer(val reason: String) : AudioDecodeError
    data class UnsupportedCompression(val formatCode: Int) : AudioDecodeError
    data class UnsupportedPcmFormat(
        val channels: Int,
        val bitsPerSample: Int,
        val sampleRate: Int
    ) : AudioDecodeError
    data class WaveTooLarge(val declaredBytes: Long, val maxBytes: Int) : AudioDecodeError
}

class AudioDecodeException(val error: AudioDecodeError) :
    IllegalArgumentException(error.toString())
