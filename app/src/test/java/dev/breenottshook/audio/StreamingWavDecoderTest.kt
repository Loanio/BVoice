package dev.breenottshook.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingWavDecoderTest {

    @Test
    fun `decodes complete mono PCM WAV`() {
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)
        val decoder = StreamingWavDecoder()

        val segments = decoder.feed(WavFixtures.pcmWav(pcm))

        assertEquals(PcmFormat(24_000, 1, 16), segments.single().format)
        assertArrayEquals(pcm, segments.single().bytes)
        assertEquals(DecodeFinish.Complete, decoder.finish())
    }

    @Test
    fun `decodes header split at every byte offset`() {
        val pcm = ByteArray(32) { it.toByte() }
        val wav = WavFixtures.pcmWav(pcm)

        for (split in 0..wav.size) {
            val decoder = StreamingWavDecoder()
            val output = decoder.feed(wav.copyOfRange(0, split)) +
                decoder.feed(wav.copyOfRange(split, wav.size))
            assertArrayEquals("split=$split", pcm, output.flatMap { it.bytes.toList() }.toByteArray())
            assertEquals("split=$split", DecodeFinish.Complete, decoder.finish())
        }
    }

    @Test
    fun `skips unknown odd sized chunk and its padding`() {
        val pcm = byteArrayOf(9, 8, 7, 6)
        val wav = WavFixtures.pcmWav(
            pcm = pcm,
            extraChunk = "JUNK" to byteArrayOf(11, 12, 13)
        )

        val segment = StreamingWavDecoder().feed(wav).single()

        assertArrayEquals(pcm, segment.bytes)
    }

    @Test
    fun `decodes concatenated sentence WAV files with format changes`() {
        val first = WavFixtures.pcmWav(byteArrayOf(1, 2), sampleRate = 24_000)
        val second = WavFixtures.pcmWav(byteArrayOf(3, 4, 5, 6), sampleRate = 32_000)
        val decoder = StreamingWavDecoder()

        val output = decoder.feed(first + second)

        assertEquals(listOf(24_000, 32_000), output.map { it.format.sampleRate })
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), output.flatMap { it.bytes.toList() }.toByteArray())
    }

    @Test
    fun `reports truncated final WAV`() {
        val wav = WavFixtures.pcmWav(byteArrayOf(1, 2, 3, 4))
        val decoder = StreamingWavDecoder()
        decoder.feed(wav.copyOf(wav.size - 2))

        assertTrue(decoder.finish() is DecodeFinish.Truncated)
    }

    @Test
    fun `rejects unsupported compressed WAV`() {
        val wav = WavFixtures.pcmWav(byteArrayOf(1, 2), formatCode = 3)

        val failure = runCatching { StreamingWavDecoder().feed(wav) }.exceptionOrNull()

        assertEquals(AudioDecodeError.UnsupportedCompression(3), (failure as AudioDecodeException).error)
    }

    @Test
    fun `rejects RIFF smaller than WAVE signature`() {
        val wav = WavFixtures.pcmWav(byteArrayOf(1, 2)).copyOf()
        wav[4] = 3
        wav[5] = 0
        wav[6] = 0
        wav[7] = 0

        val failure = runCatching { StreamingWavDecoder().feed(wav) }.exceptionOrNull()

        assertTrue((failure as AudioDecodeException).error is AudioDecodeError.InvalidContainer)
    }
}
