package dev.breenottshook.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackRecoveryPolicyTest {
    @Test
    fun `recovers started track at most twice for zero writes`() {
        val policy = AudioTrackRecoveryPolicy(maxRecoveries = 2)

        assertTrue(policy.shouldRecover(wasStarted = true, errorMessage = "AudioTrack write failed: 0"))
        assertTrue(policy.shouldRecover(wasStarted = true, errorMessage = "AudioTrack write failed: 0"))
        assertFalse(policy.shouldRecover(wasStarted = true, errorMessage = "AudioTrack write failed: 0"))
        assertFalse(policy.shouldRecover(wasStarted = false, errorMessage = "AudioTrack write failed: 0"))
        assertFalse(policy.shouldRecover(wasStarted = true, errorMessage = "AudioTrack write failed: -6"))
    }
}
