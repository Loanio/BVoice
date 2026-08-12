package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUtteranceAccumulatorTest {
    @Test
    fun `concatenates nonblank chunks and preserves fallback order`() {
        val accumulator = StreamUtteranceAccumulator()
        val listener = Any()
        val bundle = Any()

        accumulator.start(listener, bundle)
        assertEquals(AppendResult.Accepted, accumulator.append("第一段"))
        assertEquals(AppendResult.Accepted, accumulator.append("第二段"))

        val ready = accumulator.finish() as FinishedStream.Ready
        assertEquals("第一段第二段", ready.text)
        assertEquals(listOf("第一段", "第二段"), ready.fallback.chunks)
        assertTrue(ready.fallback.listener === listener)
        assertTrue(ready.fallback.bundle === bundle)
    }

    @Test
    fun `ignores blank chunks and reports empty finish`() {
        val accumulator = StreamUtteranceAccumulator()
        accumulator.start(null, null)
        assertEquals(AppendResult.Ignored, accumulator.append("  \n"))
        assertEquals(FinishedStream.Empty, accumulator.finish())
    }

    @Test
    fun `overflow retains exact original chunks`() {
        val accumulator = StreamUtteranceAccumulator(maxChars = 3)
        accumulator.start("listener", "bundle")
        accumulator.append("ab")
        accumulator.append("cd")
        val overflow = accumulator.finish() as FinishedStream.Overflow
        assertEquals(listOf("ab", "cd"), overflow.fallback.chunks)
    }

    @Test
    fun `cancel returns fallback once`() {
        val accumulator = StreamUtteranceAccumulator()
        accumulator.start("listener", "bundle")
        accumulator.append("text")
        assertEquals(listOf("text"), accumulator.cancel()?.chunks)
        assertEquals(null, accumulator.cancel())
    }

    @Test
    fun `starting a new stream returns superseded fallback`() {
        val accumulator = StreamUtteranceAccumulator()
        accumulator.start("first", "bundle1")
        accumulator.append("old")
        val superseded = accumulator.start("second", "bundle2")
        assertEquals(listOf("old"), superseded?.chunks)
        assertEquals(FinishedStream.Empty, accumulator.finish())
    }
}
