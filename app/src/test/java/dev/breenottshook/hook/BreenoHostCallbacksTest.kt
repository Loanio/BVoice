package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Test

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
        fun onEnd() { events += "end" }
        fun onCompleted(error: Any?) { events += "completed:${error?.javaClass?.simpleName ?: "null"}" }
    }
}
