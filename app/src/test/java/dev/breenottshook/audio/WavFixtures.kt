package dev.breenottshook.audio

import java.io.ByteArrayOutputStream

object WavFixtures {
    fun streamingPcmWavHeader(
        sampleRate: Int = 24_000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        return ByteArrayOutputStream().apply {
            writeAscii("RIFF")
            // The provider emits a streaming header with unknown final sizes.
            writeIntLe(36)
            writeAscii("WAVE")
            writeAscii("fmt ")
            writeIntLe(16)
            writeShortLe(1)
            writeShortLe(channels)
            writeIntLe(sampleRate)
            writeIntLe(byteRate)
            writeShortLe(blockAlign)
            writeShortLe(bitsPerSample)
            writeAscii("data")
            writeIntLe(0)
        }.toByteArray()
    }

    fun pcmWav(
        pcm: ByteArray,
        sampleRate: Int = 24_000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
        formatCode: Int = 1,
        extraChunk: Pair<String, ByteArray>? = null
    ): ByteArray {
        val chunks = ByteArrayOutputStream()
        extraChunk?.let { (id, bytes) -> chunks.writeChunk(id, bytes) }
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        chunks.writeChunk("fmt ", ByteArrayOutputStream().apply {
            writeShortLe(formatCode)
            writeShortLe(channels)
            writeIntLe(sampleRate)
            writeIntLe(byteRate)
            writeShortLe(blockAlign)
            writeShortLe(bitsPerSample)
        }.toByteArray())
        chunks.writeChunk("data", pcm)

        val chunkBytes = chunks.toByteArray()
        return ByteArrayOutputStream().apply {
            writeAscii("RIFF")
            writeIntLe(4 + chunkBytes.size)
            writeAscii("WAVE")
            write(chunkBytes)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeChunk(id: String, bytes: ByteArray) {
        writeAscii(id)
        writeIntLe(bytes.size)
        write(bytes)
        if (bytes.size % 2 != 0) write(0)
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) =
        write(value.toByteArray(Charsets.US_ASCII))

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 24) and 0xff)
    }
}
