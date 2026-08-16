package dev.breenottshook.playback

import kotlinx.coroutines.delay

/** Retries the transient zero-byte result returned by AudioTrack in stream mode. */
class PcmWriteLoop(
    private val retryDelayMs: Long = 25,
    private val maxZeroWrites: Int = 400
) {
    suspend fun writeAll(bytes: ByteArray, write: (offset: Int, size: Int) -> Int) {
        var offset = 0
        var zeroWrites = 0
        while (offset < bytes.size) {
            val written = write(offset, bytes.size - offset)
            when {
                written > 0 -> {
                    offset += written
                    zeroWrites = 0
                }

                written == 0 && zeroWrites < maxZeroWrites -> {
                    zeroWrites++
                    delay(retryDelayMs)
                }

                else -> error("AudioTrack write failed: $written")
            }
        }
    }
}
