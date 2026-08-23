package dev.breenottshook.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupBufferGateTest {

    @Test
    fun `does not start playback until buffered PCM reaches the startup window`() {
        val gate = StartupBufferGate(startupBufferMs = 800)

        assertFalse(gate.shouldStart(bufferedFrames = 25_599, sampleRate = 32_000))
        assertTrue(gate.shouldStart(bufferedFrames = 25_600, sampleRate = 32_000))
    }
}
