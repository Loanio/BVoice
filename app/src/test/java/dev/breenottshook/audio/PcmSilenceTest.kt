package dev.breenottshook.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmSilenceTest {
    @Test
    fun `creates frame aligned zero PCM for requested duration`() {
        val format = PcmFormat(sampleRate = 24_000, channels = 1, bitsPerSample = 16)

        val segment = requireNotNull(PcmSilence.create(format, 250))

        assertEquals(format, segment.format)
        assertEquals(12_000, segment.bytes.size)
        assertTrue(segment.bytes.all { it == 0.toByte() })
    }

    @Test
    fun `keeps stereo 24 bit silence frame aligned`() {
        val format = PcmFormat(sampleRate = 48_000, channels = 2, bitsPerSample = 24)

        val segment = requireNotNull(PcmSilence.create(format, 1))

        assertEquals(288, segment.bytes.size)
        assertEquals(0, segment.bytes.size % 6)
    }

    @Test
    fun `zero interval produces no segment`() {
        assertNull(PcmSilence.create(PcmFormat(24_000, 1, 16), 0))
    }
}
