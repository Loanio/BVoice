package dev.breenottshook.hook

import dev.breenottshook.config.TtsConfig
import dev.breenottshook.session.OriginalCall
import dev.breenottshook.session.TtsCallbacks
import dev.breenottshook.session.TtsInvocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BreenoEngineRuntime(
    private val configProvider: () -> TtsConfig,
    private val submit: suspend (TtsInvocation) -> Unit,
    private val cancel: suspend (String) -> Unit,
    private val scope: CoroutineScope? = null,
    private val diagnostics: (String) -> Unit = {}
) {
    private val accumulator = StreamUtteranceAccumulator()
    private var streamStartOriginal: (() -> Unit)? = null
    private var streamChunkOriginals = mutableListOf<() -> Unit>()
    private var streamEndOriginal: (() -> Unit)? = null

    fun onSpeak(text: String, listener: Any?, original: () -> Unit): Boolean {
        if (!configProvider().enabled) return false
        launch {
            submit(invocation(text, BreenoHostCallbacks.normal(listener), original))
        }
        return true
    }

    fun onStreamStart(listener: Any?, bundle: Any?, original: () -> Unit): Boolean {
        if (!configProvider().enabled) return false
        accumulator.start(listener, bundle)?.let { replay(it, streamStartOriginal, streamChunkOriginals, streamEndOriginal) }
        streamStartOriginal = original
        streamChunkOriginals = mutableListOf()
        streamEndOriginal = null
        return true
    }

    fun onStreamChunk(text: String, original: () -> Unit): Boolean {
        if (!configProvider().enabled) return false
        accumulator.append(text)
        streamChunkOriginals += original
        return true
    }

    fun onStreamEnd(original: () -> Unit): Boolean {
        if (!configProvider().enabled) return false
        streamEndOriginal = original
        when (val finished = accumulator.finish()) {
            FinishedStream.Empty -> Unit
            is FinishedStream.Ready -> launch {
                submit(invocation(
                    finished.text,
                    BreenoHostCallbacks.stream(finished.fallback.listener),
                    { replayCall(finished.fallback, streamStartOriginal, streamChunkOriginals, streamEndOriginal).resume() }
                ))
            }
            is FinishedStream.Overflow -> replay(finished.fallback, streamStartOriginal, streamChunkOriginals, streamEndOriginal)
        }
        clearStreamOriginals()
        return true
    }

    fun cancel(reason: String) {
        accumulator.cancel()?.let { replay(it, streamStartOriginal, streamChunkOriginals, streamEndOriginal) }
        clearStreamOriginals()
        launch { cancel(reason) }
    }

    private fun invocation(text: String, callbacks: TtsCallbacks, original: () -> Unit) =
        TtsInvocation(text, OriginalCall(original), callbacks)

    private fun replay(
        fallback: StreamFallback,
        start: (() -> Unit)?,
        chunks: List<() -> Unit>,
        end: (() -> Unit)?
    ) {
        replayCall(fallback, start, chunks, end).resume()
    }

    private fun replayCall(
        fallback: StreamFallback,
        start: (() -> Unit)?,
        chunks: List<() -> Unit>,
        end: (() -> Unit)?
    ): OriginalCall = OriginalCall {
        start?.invoke()
        chunks.forEachIndexed { index, call ->
            call.invoke()
            diagnostics("fallback chunkChars=${fallback.chunks.getOrNull(index)?.length ?: 0}")
        }
        end?.invoke()
    }

    private fun clearStreamOriginals() {
        streamStartOriginal = null
        streamChunkOriginals = mutableListOf()
        streamEndOriginal = null
    }

    private fun launch(block: suspend () -> Unit) {
        (scope ?: CoroutineScope(Dispatchers.Unconfined)).launch { runCatching { block() } }
    }
}
