package dev.breenottshook.playback

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmWriteLoopTest {

    @Test
    fun `retries a temporary zero byte AudioTrack write`() = runTest {
        val offsets = mutableListOf<Int>()
        val loop = PcmWriteLoop(retryDelayMs = 0, maxZeroWrites = 2)

        loop.writeAll(ByteArray(6)) { offset, size ->
            offsets += offset
            when (offsets.size) {
                1 -> 0
                2 -> 2
                else -> size
            }
        }

        assertEquals(listOf(0, 0, 2), offsets)
    }

    @Test
    fun `default loop tolerates stream startup backpressure longer than one second`() = runTest {
        var attempts = 0

        PcmWriteLoop().writeAll(ByteArray(1)) { _, size ->
            attempts++
            if (attempts <= 100) 0 else size
        }

        assertEquals(101, attempts)
    }
}
