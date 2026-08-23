package dev.breenottshook.hook

import dev.breenottshook.config.TtsConfig
import dev.breenottshook.session.TtsInvocation
import kotlinx.coroutines.test.runTest
import dev.breenottshook.session.TtsCallbacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreenoEngineRuntimeTest {
    @Test
    fun `normal speak records a privacy safe disabled diagnostic`() = runTest {
        val diagnostics = mutableListOf<String>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = false) },
            submit = {},
            cancelHandler = {},
            diagnostics = { diagnostics += it }
        )

        assertFalse(runtime.onSpeak("text", null) {})

        assertEquals(listOf("engine_speak enabled=false;chars=4"), diagnostics)
    }

    @Test
    fun `disabled config leaves every host method untouched`() = runTest {
        val submitted = mutableListOf<TtsInvocation>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = false) },
            submit = { submitted += it },
            cancelHandler = {}
        )
        assertFalse(runtime.onSpeak("text", null) {})
        assertFalse(runtime.onStreamStart(null, null) {})
        assertFalse(runtime.onStreamChunk("chunk") {})
        assertFalse(runtime.onStreamEnd {})
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `normal speak submits one invocation and suppresses original`() = runTest {
        val submitted = mutableListOf<TtsInvocation>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = true) },
            submit = { submitted += it },
            cancelHandler = {}
        )
        assertTrue(runtime.onSpeak("hello", null) {})
        assertEquals(listOf("hello"), submitted.map { it.text })
    }

    @Test
    fun `stream chunks submit one concatenated invocation at end`() = runTest {
        val submitted = mutableListOf<TtsInvocation>()
        val diagnostics = mutableListOf<String>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = true) },
            submit = { submitted += it },
            cancelHandler = {},
            diagnostics = { diagnostics += it }
        )
        assertTrue(runtime.onStreamStart(null, null) {})
        assertTrue(runtime.onStreamChunk("第一段") {})
        assertTrue(runtime.onStreamChunk("第二段") {})
        assertTrue(runtime.onStreamEnd {})
        assertEquals(listOf("第一段第二段"), submitted.map { it.text })
        assertEquals(
            listOf(
                "engine_stream_start enabled=true",
                "engine_stream_chunk enabled=true;chars=3",
                "engine_stream_chunk enabled=true;chars=3",
                "engine_stream_end enabled=true"
            ),
            diagnostics
        )
    }

    @Test
    fun `stream chunks are split into utterances and submitted as a stream`() = runTest {
        val submitted = mutableListOf<List<String>>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = true) },
            submit = {},
            submitStream = { utterances, _, _ -> submitted += utterances.map { it.text } },
            cancelHandler = {}
        )

        runtime.onStreamStart(null, null) {}
        runtime.onStreamChunk("第一句。第二句！") {}
        runtime.onStreamEnd {}

        assertEquals(listOf(listOf("第一句。", "第二句！")), submitted)
    }

    @Test
    fun `stream invocation fallback retains original calls after stream end`() = runTest {
        val submitted = mutableListOf<TtsInvocation>()
        val events = mutableListOf<String>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = true) },
            submit = { submitted += it },
            cancelHandler = {}
        )

        runtime.onStreamStart(null, null) { events += "start" }
        runtime.onStreamChunk("第一段") { events += "chunk-1" }
        runtime.onStreamChunk("第二段") { events += "chunk-2" }
        runtime.onStreamEnd { events += "end" }
        submitted.single().originalCall.resume()

        assertEquals(listOf("start", "chunk-1", "chunk-2", "end"), events)
    }

    @Test
    fun `superseded stream replays original start chunks and end in order`() = runTest {
        val events = mutableListOf<String>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = true) },
            submit = {},
            cancelHandler = {}
        )
        runtime.onStreamStart(null, null) { events += "start" }
        runtime.onStreamChunk("a") { events += "chunk-a" }
        runtime.onStreamChunk("b") { events += "chunk-b" }
        runtime.onStreamStart(null, null) { events += "new-start" }
        assertEquals(listOf("start", "chunk-a", "chunk-b"), events)
    }

    @Test
    fun `cancel delegates to coordinator exactly once`() = runTest {
        val reasons = mutableListOf<String>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = true) },
            submit = {},
            cancelHandler = { reasons += it }
        )

        runtime.cancel("user stop")

        assertEquals(listOf("user stop"), reasons)
    }

    @Test
    fun `stream chunk without a start creates an implicit third party stream`() = runTest {
        val submitted = mutableListOf<TtsInvocation>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = true) },
            submit = { submitted += it },
            cancelHandler = {}
        )

        assertTrue(runtime.onStreamChunk("全文", {}))
        assertTrue(runtime.onStreamEnd {})
        assertEquals(listOf("全文"), submitted.map { it.text })
    }

    @Test
    fun `implicit stream keeps native highlight listener`() = runTest {
        val events = mutableListOf<String>()
        val listener = object {
            fun onNextSliceStart(info: SliceInfo) { events += "${info.index}:${info.text}:${info.length}" }
        }
        var callbacks: TtsCallbacks? = null
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = true) },
            submit = {},
            submitStream = { _, received, _ -> callbacks = received },
            cancelHandler = {},
            implicitListenerProvider = { listener }
        )
        runtime.onStreamChunk("全文", {})
        runtime.onStreamEnd {}
        callbacks!!.onUtteranceStarted(dev.breenottshook.session.TtsUtterance(0, "全文"))
        assertEquals(listOf("0:全文:2"), events)
    }

    class SliceInfo(val index: Int, val text: String, val length: Long)
}
