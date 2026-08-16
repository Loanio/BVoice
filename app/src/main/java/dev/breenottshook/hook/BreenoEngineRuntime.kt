package dev.breenottshook.hook

import dev.breenottshook.config.TtsConfig
import dev.breenottshook.session.OriginalCall
import dev.breenottshook.session.TtsCallbacks
import dev.breenottshook.session.TtsInvocation
import dev.breenottshook.session.TtsUtterance
import dev.breenottshook.session.splitUtterances
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BreenoEngineRuntime(
    private val configProvider: () -> TtsConfig,
    private val submit: suspend (TtsInvocation) -> Unit,
    private val submitStream: suspend (List<TtsUtterance>, TtsCallbacks, OriginalCall) -> Unit = { utterances, callbacks, original ->
        submit(TtsInvocation(utterances.joinToString("") { it.text }, original, callbacks))
    },
    private val cancelHandler: suspend (String) -> Unit,
    private val scope: CoroutineScope? = null,
    private val diagnostics: (String) -> Unit = {}
) {
    private val accumulator = StreamUtteranceAccumulator()
    private var streamStartOriginal: (() -> Unit)? = null
    private var streamChunkOriginals = mutableListOf<() -> Unit>()
    private var streamEndOriginal: (() -> Unit)? = null

    fun onSpeak(text: String, listener: Any?, original: () -> Unit): Boolean {
        val config = configProvider()
        diagnostics("engine_speak enabled=${config.enabled};chars=${text.length}")
        if (!config.enabled) return false
        launch {
            submit(invocation(text, BreenoHostCallbacks.normal(listener), original))
        }
        return true
    }

    fun onStreamStart(listener: Any?, bundle: Any?, original: () -> Unit): Boolean {
        val config = configProvider()
        diagnostics("engine_stream_start enabled=${config.enabled}")
        if (!config.enabled) return false
        accumulator.start(listener, bundle)?.let { replay(it, streamStartOriginal, streamChunkOriginals, streamEndOriginal) }
        streamStartOriginal = original
        streamChunkOriginals = mutableListOf()
        streamEndOriginal = null
        return true
    }

    fun onStreamChunk(text: String, original: () -> Unit): Boolean {
        val config = configProvider()
        diagnostics("engine_stream_chunk enabled=${config.enabled};chars=${text.length}")
        if (!config.enabled) return false
        if (accumulator.append(text) == AppendResult.Ignored) {
            // The host's “read full text” action can emit O0/J0 without P0.
            // Create an implicit generation so this path still uses third-party TTS.
            accumulator.start(newListener = null, newBundle = null)
            streamStartOriginal = null
            streamChunkOriginals = mutableListOf()
            streamEndOriginal = null
            accumulator.append(text)
        }
        streamChunkOriginals += original
        return true
    }

    fun onStreamEnd(original: () -> Unit): Boolean {
        val config = configProvider()
        diagnostics("engine_stream_end enabled=${config.enabled}")
        if (!config.enabled) return false
        streamEndOriginal = original
        val start = streamStartOriginal
        val chunks = streamChunkOriginals.toList()
        val end = streamEndOriginal
        val intercepted = when (val finished = accumulator.finish()) {
            FinishedStream.Empty -> false
            is FinishedStream.Ready -> launch {
                val callbacks = BreenoHostCallbacks.stream(finished.fallback.listener)
                submitStream(
                    splitUtterances(listOf(finished.text)),
                    callbacks,
                    { replayCall(finished.fallback, start, chunks, end).resume() }
                )
            }.let { true }
            is FinishedStream.Overflow -> {
                replay(finished.fallback, start, chunks, end)
                true
            }
        }
        clearStreamOriginals()
        return intercepted
    }

    fun cancel(reason: String) {
        accumulator.cancel()?.let { replay(it, streamStartOriginal, streamChunkOriginals, streamEndOriginal) }
        clearStreamOriginals()
        launch { cancelHandler(reason) }
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
