package dev.breenottshook.hook

import dev.breenottshook.config.TtsConfig
import dev.breenottshook.session.TtsInvocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreenoEngineRuntimeTest {
    @Test
    fun `disabled config leaves every host method untouched`() = runTest {
        val submitted = mutableListOf<TtsInvocation>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = false) },
            submit = { submitted += it },
            cancel = {}
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
            cancel = {}
        )
        assertTrue(runtime.onSpeak("hello", null) {})
        assertEquals(listOf("hello"), submitted.map { it.text })
    }

    @Test
    fun `stream chunks submit one concatenated invocation at end`() = runTest {
        val submitted = mutableListOf<TtsInvocation>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = true) },
            submit = { submitted += it },
            cancel = {}
        )
        assertTrue(runtime.onStreamStart(null, null) {})
        assertTrue(runtime.onStreamChunk("第一段") {})
        assertTrue(runtime.onStreamChunk("第二段") {})
        assertTrue(runtime.onStreamEnd {})
        assertEquals(listOf("第一段第二段"), submitted.map { it.text })
    }

    @Test
    fun `superseded stream replays original start chunks and end in order`() = runTest {
        val events = mutableListOf<String>()
        val runtime = BreenoEngineRuntime(
            configProvider = { TtsConfig(enabled = true) },
            submit = {},
            cancel = {}
        )
        runtime.onStreamStart(null, null) { events += "start" }
        runtime.onStreamChunk("a") { events += "chunk-a" }
        runtime.onStreamChunk("b") { events += "chunk-b" }
        runtime.onStreamStart(null, null) { events += "new-start" }
        assertEquals(listOf("start", "chunk-a", "chunk-b"), events)
    }
}
