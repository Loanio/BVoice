package dev.breenottshook.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackStartPolicyTest {
    @Test
    fun `starts on first non-empty PCM write without waiting for complete`() {
        assertTrue(AudioTrackStartPolicy.shouldStartOnWrite(ByteArray(4)))
        assertFalse(AudioTrackStartPolicy.shouldStartOnWrite(ByteArray(0)))
    }
}
