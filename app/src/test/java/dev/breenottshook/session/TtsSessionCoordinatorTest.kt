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
    fun `stream synthesizes utterances and reports each start after its first PCM write`() = runTest {
        val events = mutableListOf<String>()
        val callbacks = RecordingCallbacks(events)
        val coordinator = coordinator(
            config = TtsConfig(),
            sink = RecordingSink(events),
            engine = SynthesisEngine { text, _, onBytes ->
                events += "synthesize:$text"
                val pcm = if (text == "first") byteArrayOf(1, 0) else byteArrayOf(2, 0)
                onBytes(WavFixtures.pcmWav(pcm))
            }
        )

        coordinator.submitStream(
            utterances = listOf(TtsUtterance(3, "first"), TtsUtterance(7, "second")),
            callbacks = callbacks,
            originalCall = OriginalCall { error("original fallback") }
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                "synthesize:first", "write:1", "started:3",
                "synthesize:second", "write:2", "started:7", "completed"
            ),
            events
        )
    }

    @Test
    fun `stream completes only after the final utterance finishes`() = runTest {
        val secondEntered = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val callbacks = RecordingCallbacks()
        val coordinator = coordinator(
            config = TtsConfig(),
            sink = RecordingSink(),
            engine = SynthesisEngine { text, _, onBytes ->
                if (text == "second") {
                    secondEntered.complete(Unit)
                    releaseSecond.await()
                }
                onBytes(WavFixtures.pcmWav(byteArrayOf(1, 0)))
            }
        )

        coordinator.submitStream(
            listOf(TtsUtterance(0, "first"), TtsUtterance(1, "second")),
            callbacks,
            OriginalCall { error("original fallback") }
        )
        secondEntered.await()

        assertEquals(listOf(0), callbacks.utteranceStarts)
        assertEquals(0, callbacks.completeCount)

        releaseSecond.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(0, 1), callbacks.utteranceStarts)
        assertEquals(1, callbacks.completeCount)
    }

    @Test
    fun `cancelling between stream utterances emits one terminal cancellation`() = runTest {
        val secondEntered = CompletableDeferred<Unit>()
        val waitForCancellation = CompletableDeferred<Unit>()
        val callbacks = RecordingCallbacks()
        val sink = RecordingSink()
        val coordinator = coordinator(
            config = TtsConfig(),
            sink = sink,
            engine = SynthesisEngine { text, _, onBytes ->
                if (text == "second") {
                    secondEntered.complete(Unit)
                    waitForCancellation.await()
                }
                onBytes(WavFixtures.pcmWav(byteArrayOf(1, 0)))
            }
        )

        coordinator.submitStream(
            listOf(TtsUtterance(0, "first"), TtsUtterance(1, "second")),
            callbacks,
            OriginalCall { error("original fallback") }
        )
        secondEntered.await()
        coordinator.cancelActive("user cancelled")
        advanceUntilIdle()

        assertEquals(listOf(0), callbacks.utteranceStarts)
        assertEquals(1, callbacks.cancelCount)
        assertEquals(0, callbacks.completeCount)
        assertEquals(0, callbacks.errorCount)
        assertEquals(1, sink.cancelCount)
    }

    @Test
    fun `stream failure before PCM resumes original once when fallback is enabled`() = runTest {
        var originalCalls = 0
        val callbacks = RecordingCallbacks()
        val coordinator = coordinator(
            config = TtsConfig(fallbackToOriginal = true),
            sink = RecordingSink(),
            engine = SynthesisEngine { _, _, _ -> throw IOException("offline") }
        )

        coordinator.submitStream(
            listOf(TtsUtterance(0, "first"), TtsUtterance(1, "second")),
            callbacks,
            OriginalCall { originalCalls++ }
        )
        advanceUntilIdle()

        assertEquals(1, originalCalls)
        assertEquals(0, callbacks.errorCount)
        assertEquals(emptyList<Int>(), callbacks.utteranceStarts)
        assertEquals(TtsSessionState.Failed(1, "fallback"), coordinator.state.value)
    }

    @Test
    fun `stream failure after PCM starts never replays original`() = runTest {
        var originalCalls = 0
        val callbacks = RecordingCallbacks()
        val coordinator = coordinator(
            config = TtsConfig(fallbackToOriginal = true),
            sink = RecordingSink(),
            engine = SynthesisEngine { text, _, onBytes ->
                if (text == "second") throw IOException("stream dropped")
                onBytes(WavFixtures.pcmWav(byteArrayOf(1, 0)))
            }
        )

        coordinator.submitStream(
            listOf(TtsUtterance(0, "first"), TtsUtterance(1, "second")),
            callbacks,
            OriginalCall { originalCalls++ }
        )
        advanceUntilIdle()

        assertEquals(0, originalCalls)
        assertEquals(listOf(0), callbacks.utteranceStarts)
        assertEquals(1, callbacks.errorCount)
        assertEquals(0, callbacks.completeCount)
    }

    @Test
    fun `superseded stream cannot affect the new generation`() = runTest {
        val oldEntered = CompletableDeferred<Unit>()
        val oldCallbacks = RecordingCallbacks()
        val newCallbacks = RecordingCallbacks()
        val coordinator = coordinator(
            config = TtsConfig(),
            sink = RecordingSink(),
            engine = SynthesisEngine { text, _, onBytes ->
                if (text == "old") {
                    oldEntered.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
                onBytes(WavFixtures.pcmWav(byteArrayOf(2, 0)))
            }
        )

        coordinator.submitStream(
            listOf(TtsUtterance(0, "old")),
            oldCallbacks,
            OriginalCall { error("old original fallback") }
        )
        oldEntered.await()
        coordinator.submitStream(
            listOf(TtsUtterance(0, "new")),
            newCallbacks,
            OriginalCall { error("new original fallback") }
        )
        advanceUntilIdle()

        assertEquals(1, oldCallbacks.cancelCount)
        assertEquals(0, oldCallbacks.completeCount)
        assertEquals(0, oldCallbacks.errorCount)
        assertEquals(emptyList<Int>(), oldCallbacks.utteranceStarts)
        assertEquals(listOf(0), newCallbacks.utteranceStarts)
        assertEquals(1, newCallbacks.completeCount)
    }

    @Test
    fun `superseded stream cannot write a later decoded segment after an in flight write`() = runTest {
        val firstWriteEntered = CompletableDeferred<Unit>()
        val oldSink = CancellationIgnoringFirstWriteSink(firstWriteEntered)
        val newSink = RecordingSink()
        var sinkRequests = 0
        val oldCallbacks = RecordingCallbacks()
        val newCallbacks = RecordingCallbacks()
        val coordinator = coordinator(
            config = TtsConfig(),
            engine = SynthesisEngine { text, _, onBytes ->
                if (text == "old") {
                    onBytes(
                        WavFixtures.pcmWav(byteArrayOf(1, 0)) +
                            WavFixtures.pcmWav(byteArrayOf(2, 0))
                    )
                } else {
                    onBytes(WavFixtures.pcmWav(byteArrayOf(3, 0)))
                }
            },
            sinkProvider = { if (sinkRequests++ == 0) oldSink else newSink }
        )

        coordinator.submitStream(
            listOf(TtsUtterance(0, "old")),
            oldCallbacks,
            OriginalCall { error("old original fallback") }
        )
        firstWriteEntered.await()
        coordinator.submitStream(
            listOf(TtsUtterance(0, "new")),
            newCallbacks,
            OriginalCall { error("new original fallback") }
        )
        advanceUntilIdle()

        assertEquals(listOf<Byte>(1, 0), oldSink.bytes)
        assertEquals(emptyList<Int>(), oldCallbacks.utteranceStarts)
        assertEquals(listOf(0), newCallbacks.utteranceStarts)
        assertEquals(1, newCallbacks.completeCount)
    }

    @Test
    fun `stream reuses one sink so utterance PCM remains continuous`() = runTest {
        val sink = RecordingSink()
        var sinkRequests = 0
        val coordinator = coordinator(
            config = TtsConfig(),
            engine = SynthesisEngine { text, _, onBytes ->
                val pcm = if (text == "first") byteArrayOf(1, 0) else byteArrayOf(2, 0)
                onBytes(WavFixtures.pcmWav(pcm))
            },
            sinkProvider = {
                sinkRequests++
                sink
            }
        )

        coordinator.submitStream(
            listOf(TtsUtterance(0, "first"), TtsUtterance(1, "second")),
            RecordingCallbacks(),
            OriginalCall { error("original fallback") }
        )
        advanceUntilIdle()

        assertEquals(1, sinkRequests)
        assertArrayEquals(byteArrayOf(1, 0, 2, 0), sink.bytes.toByteArray())
        assertEquals(1, sink.completeCount)
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
    ) = coordinator(config, engine) { sink }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        config: TtsConfig,
        engine: SynthesisEngine,
        sinkProvider: () -> AudioSink
    ) = TtsSessionCoordinator(
        scope = this,
        configProvider = { config },
        synthesisEngine = engine,
        sinkProvider = sinkProvider
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

    private class RecordingSink(private val events: MutableList<String>? = null) : AudioSink {
        var openedFormat: PcmFormat? = null
        val bytes = mutableListOf<Byte>()
        var completeCount = 0
        var cancelCount = 0

        override suspend fun open(format: PcmFormat) {
            openedFormat = format
        }

        override suspend fun write(segment: PcmSegment) {
            bytes += segment.bytes.toList()
            events?.add("write:${segment.bytes.firstOrNull()?.toInt() ?: -1}")
        }

        override suspend fun complete() {
            completeCount++
        }

        override suspend fun cancel() {
            cancelCount++
        }
    }

    private class RecordingCallbacks(private val events: MutableList<String>? = null) : TtsCallbacks {
        var completeCount = 0
        var errorCount = 0
        var cancelCount = 0
        override fun onStarted() = Unit
        val utteranceStarts = mutableListOf<Int>()
        override fun onUtteranceStarted(utterance: TtsUtterance) {
            utteranceStarts += utterance.index
            events?.add("started:${utterance.index}")
        }
        override fun onCompleted() {
            completeCount++
            events?.add("completed")
        }

        override fun onError(error: Throwable) {
            errorCount++
        }

        override fun onCancelled(reason: String) {
            cancelCount++
        }
    }

    private class CancellationIgnoringFirstWriteSink(
        private val firstWriteEntered: CompletableDeferred<Unit>
    ) : AudioSink {
        val bytes = mutableListOf<Byte>()
        private var firstWrite = true

        override suspend fun open(format: PcmFormat) = Unit

        override suspend fun write(segment: PcmSegment) {
            if (firstWrite) {
                firstWrite = false
                firstWriteEntered.complete(Unit)
                try {
                    CompletableDeferred<Unit>().await()
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Simulates a write already accepted by the audio backend when cancellation arrives.
                }
            }
            bytes += segment.bytes.toList()
        }

        override suspend fun complete() = Unit
        override suspend fun cancel() = Unit
    }
}
