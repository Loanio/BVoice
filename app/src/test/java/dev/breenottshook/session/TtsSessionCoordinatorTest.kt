package dev.breenottshook.session

import dev.breenottshook.audio.PcmFormat
import dev.breenottshook.audio.PcmSegment
import dev.breenottshook.audio.WavFixtures
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.playback.AudioSink
import dev.breenottshook.playback.CompositeAudioSink
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsSessionCoordinatorTest {

    @Test
    fun `successful synthesis plays PCM and completes once`() = runTest {
        val sink = RecordingSink()
        val callbacks = RecordingCallbacks()
        val coordinator = coordinator(
            config = TtsConfig(),
            sink = sink,
            engine = SynthesisEngine { _, _, onBytes ->
                onBytes(WavFixtures.pcmWav(byteArrayOf(1, 2, 3, 4)))
            }
        )

        val generation = coordinator.submit(invocation(callbacks = callbacks))
        advanceUntilIdle()

        assertEquals(1, generation)
        assertEquals(PcmFormat(24_000, 1, 16), sink.openedFormat)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), sink.bytes.toByteArray())
        assertEquals(1, sink.completeCount)
        assertEquals(1, callbacks.completeCount)
        assertEquals(0, callbacks.errorCount)
    }

    @Test
    fun `failure before first PCM resumes original when fallback enabled`() = runTest {
        var originalCalls = 0
        val callbacks = RecordingCallbacks()
        val coordinator = coordinator(
            config = TtsConfig(fallbackToOriginal = true),
            sink = RecordingSink(),
            engine = SynthesisEngine { _, _, _ -> throw IOException("offline") }
        )

        coordinator.submit(
            invocation(callbacks = callbacks, original = { originalCalls++ })
        )
        advanceUntilIdle()

        assertEquals(1, originalCalls)
        assertEquals(0, callbacks.errorCount)
    }

    @Test
    fun `strict mode reports error instead of original fallback`() = runTest {
        var originalCalls = 0
        val callbacks = RecordingCallbacks()
        val coordinator = coordinator(
            config = TtsConfig(fallbackToOriginal = true, strictMode = true),
            sink = RecordingSink(),
            engine = SynthesisEngine { _, _, _ -> throw IOException("offline") }
        )

        coordinator.submit(
            invocation(callbacks = callbacks, original = { originalCalls++ })
        )
        advanceUntilIdle()

        assertEquals(0, originalCalls)
        assertEquals(1, callbacks.errorCount)
    }

    @Test
    fun `failure after PCM starts never replays original sentence`() = runTest {
        var originalCalls = 0
        val callbacks = RecordingCallbacks()
        val sink = RecordingSink()
        val coordinator = coordinator(
            config = TtsConfig(fallbackToOriginal = true),
            sink = sink,
            engine = SynthesisEngine { _, _, onBytes ->
                onBytes(WavFixtures.pcmWav(byteArrayOf(1, 2)))
                throw IOException("stream dropped")
            }
        )

        coordinator.submit(
            invocation(callbacks = callbacks, original = { originalCalls++ })
        )
        advanceUntilIdle()

        assertEquals(0, originalCalls)
        assertEquals(1, callbacks.errorCount)
        assertTrue(sink.bytes.isNotEmpty())
    }

    @Test
    fun `new request cancels previous generation`() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        var calls = 0
        val sink = RecordingSink()
        val firstCallbacks = RecordingCallbacks()
        val secondCallbacks = RecordingCallbacks()
        val coordinator = coordinator(
            config = TtsConfig(),
            sink = sink,
            engine = SynthesisEngine { _, _, onBytes ->
                calls++
                if (calls == 1) firstGate.await()
                onBytes(WavFixtures.pcmWav(byteArrayOf(calls.toByte(), 0)))
            }
        )

        coordinator.submit(invocation(text = "first", callbacks = firstCallbacks))
        coordinator.submit(invocation(text = "second", callbacks = secondCallbacks))
        advanceUntilIdle()

        assertEquals(1, firstCallbacks.cancelCount)
        assertEquals(1, secondCallbacks.completeCount)
        assertEquals(1, sink.cancelCount)
    }

    @Test
    fun `composite sink downgrades to fallback when primary open fails`() = runTest {
        val fallback = RecordingSink()
        val composite = CompositeAudioSink(
            primary = object : AudioSink {
                override suspend fun open(format: PcmFormat) = error("unsupported")
                override suspend fun write(segment: PcmSegment) = Unit
                override suspend fun complete() = Unit
                override suspend fun cancel() = Unit
            },
            fallback = fallback
        )
        val format = PcmFormat(24_000, 1, 16)
        val segment = PcmSegment(format, byteArrayOf(7, 8))

        composite.open(format)
        composite.write(segment)
        composite.complete()

        assertEquals(format, fallback.openedFormat)
        assertArrayEquals(byteArrayOf(7, 8), fallback.bytes.toByteArray())
        assertEquals(1, fallback.completeCount)
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        config: TtsConfig,
        sink: AudioSink,
        engine: SynthesisEngine
    ) = TtsSessionCoordinator(
        scope = this,
        configProvider = { config },
        synthesisEngine = engine,
        sinkProvider = { sink }
    )

    private fun invocation(
        text: String = "你好",
        callbacks: RecordingCallbacks = RecordingCallbacks(),
        original: () -> Unit = {}
    ) = TtsInvocation(
        text = text,
        originalCall = OriginalCall(original),
        callbacks = callbacks
    )

    private class RecordingSink : AudioSink {
        var openedFormat: PcmFormat? = null
        val bytes = mutableListOf<Byte>()
        var completeCount = 0
        var cancelCount = 0

        override suspend fun open(format: PcmFormat) {
            openedFormat = format
        }

        override suspend fun write(segment: PcmSegment) {
            bytes += segment.bytes.toList()
        }

        override suspend fun complete() {
            completeCount++
        }

        override suspend fun cancel() {
            cancelCount++
        }
    }

    private class RecordingCallbacks : TtsCallbacks {
        var completeCount = 0
        var errorCount = 0
        var cancelCount = 0
        override fun onStarted() = Unit
        override fun onCompleted() {
            completeCount++
        }

        override fun onError(error: Throwable) {
            errorCount++
        }

        override fun onCancelled(reason: String) {
            cancelCount++
        }
    }
}
