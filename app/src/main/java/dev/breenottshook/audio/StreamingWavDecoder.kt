package dev.breenottshook.audio

class StreamingWavDecoder(
    private val maxWaveBytes: Int = 32 * 1024 * 1024
) {
    private var buffered = ByteArray(0)

    fun feed(bytes: ByteArray): List<PcmSegment> {
        if (bytes.isNotEmpty()) buffered += bytes
        val output = mutableListOf<PcmSegment>()
        while (buffered.size >= RIFF_HEADER_SIZE) {
            requireAscii(buffered, 0, "RIFF", "Missing RIFF signature")
            requireAscii(buffered, 8, "WAVE", "Missing WAVE signature")
            val riffSize = uint32Le(buffered, 4)
            if (riffSize < 4) {
                throw AudioDecodeException(
                    AudioDecodeError.InvalidContainer("RIFF size is smaller than WAVE signature")
                )
            }
            val totalSize = riffSize + 8
            if (totalSize > maxWaveBytes) {
                throw AudioDecodeException(AudioDecodeError.WaveTooLarge(totalSize, maxWaveBytes))
            }
            if (buffered.size < totalSize.toInt()) break
            output += parseWave(buffered.copyOfRange(0, totalSize.toInt()))
            buffered = buffered.copyOfRange(totalSize.toInt(), buffered.size)
        }
        return output
    }

    fun finish(): DecodeFinish =
        if (buffered.isEmpty()) DecodeFinish.Complete else DecodeFinish.Truncated(buffered.size)

    private fun parseWave(wave: ByteArray): List<PcmSegment> {
        var offset = RIFF_HEADER_SIZE
        var format: PcmFormat? = null
        val output = mutableListOf<PcmSegment>()
        while (offset < wave.size) {
            if (offset + CHUNK_HEADER_SIZE > wave.size) {
                invalid("Truncated chunk header")
            }
            val id = ascii(wave, offset, 4)
            val size = uint32Le(wave, offset + 4)
            if (size > Int.MAX_VALUE) invalid("Chunk is too large")
            val dataStart = offset + CHUNK_HEADER_SIZE
            val dataEnd = dataStart.toLong() + size
            val paddedEnd = dataEnd + (size and 1)
            if (paddedEnd > wave.size) invalid("Chunk $id exceeds RIFF boundary")

            when (id) {
                "fmt " -> format = parseFormat(wave, dataStart, size.toInt())
                "data" -> {
                    val pcmFormat = format ?: invalid("data chunk appears before fmt chunk")
                    output += PcmSegment(
                        pcmFormat,
                        wave.copyOfRange(dataStart, dataEnd.toInt())
                    )
                }
            }
            offset = paddedEnd.toInt()
        }
        if (format == null) invalid("Missing fmt chunk")
        if (output.isEmpty()) invalid("Missing data chunk")
        return output
    }

    private fun parseFormat(wave: ByteArray, offset: Int, size: Int): PcmFormat {
        if (size < PCM_FMT_SIZE) invalid("PCM fmt chunk is shorter than 16 bytes")
        val formatCode = uint16Le(wave, offset)
        if (formatCode != PCM_FORMAT_CODE) {
            throw AudioDecodeException(AudioDecodeError.UnsupportedCompression(formatCode))
        }
        val channels = uint16Le(wave, offset + 2)
        val sampleRate = uint32Le(wave, offset + 4).toInt()
        val bitsPerSample = uint16Le(wave, offset + 14)
        if (channels !in 1..2 || sampleRate !in 8_000..192_000 ||
            bitsPerSample !in setOf(8, 16, 24, 32)
        ) {
            throw AudioDecodeException(
                AudioDecodeError.UnsupportedPcmFormat(channels, bitsPerSample, sampleRate)
            )
        }
        return PcmFormat(sampleRate, channels, bitsPerSample)
    }

    private fun requireAscii(bytes: ByteArray, offset: Int, expected: String, reason: String) {
        if (ascii(bytes, offset, expected.length) != expected) invalid(reason)
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        bytes.copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private fun uint16Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun uint32Le(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)

    private fun invalid(reason: String): Nothing =
        throw AudioDecodeException(AudioDecodeError.InvalidContainer(reason))

    private companion object {
        const val RIFF_HEADER_SIZE = 12
        const val CHUNK_HEADER_SIZE = 8
        const val PCM_FMT_SIZE = 16
        const val PCM_FORMAT_CODE = 1
    }
}
