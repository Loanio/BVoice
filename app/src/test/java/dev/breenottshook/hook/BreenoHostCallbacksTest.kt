package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Test
import dev.breenottshook.session.TtsUtterance

class BreenoHostCallbacksTest {
    @Test
    fun `normal bridge emits start and one terminal completion`() {
        val events = mutableListOf<String>()
        val callbacks = BreenoHostCallbacks.normal(NormalListener(events))
        callbacks.onStarted()
        callbacks.onCompleted()
        callbacks.onCompleted()
        assertEquals(listOf("start", "complete"), events)
    }

    @Test
    fun `normal bridge maps cancellation and error`() {
        val events = mutableListOf<String>()
        val callbacks = BreenoHostCallbacks.normal(NormalListener(events))
        callbacks.onCancelled("user")
        callbacks.onError(IllegalStateException())
        assertEquals(events.toString(), listOf("interrupt:1"), events)
    }

    @Test
    fun `stream bridge ends before completion and is terminal once`() {
        val events = mutableListOf<String>()
        val callbacks = BreenoHostCallbacks.stream(StreamListener(events))
        callbacks.onStarted()
        callbacks.onCompleted()
        callbacks.onCompleted()
        assertEquals(listOf("begin", "end", "completed:null"), events)
    }

    @Test
    fun `stream bridge sends native slice information`() {
        val events = mutableListOf<String>()
        val callbacks = BreenoHostCallbacks.stream(StreamListener(events))
        callbacks.onUtteranceStarted(TtsUtterance(index = 3, text = "第三句"))
        assertEquals(listOf("slice:3:第三句:3"), events)
    }

    @Test
    fun `null and incomplete listeners are safe`() {
        val callbacks = BreenoHostCallbacks.normal(null)
        callbacks.onStarted()
        callbacks.onCompleted()
        callbacks.onError(RuntimeException())
        callbacks.onCancelled("cancel")
    }

    class NormalListener(private val events: MutableList<String>) {
        fun onSpeakStart() { events += "start" }
        fun onSpeakCompleted() { events += "complete" }
        fun onSpeakInterrupted(reason: Int) { events += "interrupt:$reason" }
        fun onTtsError(code: Int, message: String) { events += "error:$code:$message" }
    }

    class StreamListener(private val events: MutableList<String>) {
        fun onSpeakBegin() { events += "begin" }
        fun onNextSliceStart(info: SliceInfo) { events += "slice:${info.paraIndex}:${info.text}:${info.paraLength}" }
        fun onEnd() { events += "end" }
        fun onCompleted(error: Any?) { events += "completed:${error?.javaClass?.simpleName ?: "null"}" }
    }

    class SliceInfo(val paraIndex: Int, val text: String, val paraLength: Long)
}
