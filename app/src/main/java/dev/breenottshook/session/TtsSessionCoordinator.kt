package dev.breenottshook.session

import dev.breenottshook.api.GptSovitsClient
import dev.breenottshook.audio.DecodeFinish
import dev.breenottshook.audio.PcmFormat
import dev.breenottshook.audio.StreamingWavDecoder
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.playback.AudioSink
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface SynthesisEngine {
    suspend fun synthesize(
        text: String,
        config: TtsConfig,
        onBytes: suspend (ByteArray) -> Unit
    )
}

class GptSovitsEngine(private val client: GptSovitsClient) : SynthesisEngine {
    override suspend fun synthesize(
        text: String,
        config: TtsConfig,
        onBytes: suspend (ByteArray) -> Unit
    ) {
        client.synthesize(text, config, onBytes)
    }
}

class TtsSessionCoordinator(
    private val scope: CoroutineScope,
    private val configProvider: () -> TtsConfig,
    private val synthesisEngine: SynthesisEngine,
    private val sinkProvider: () -> AudioSink
) {
    private data class ActiveSession(
        val generation: Long,
        val invocation: TtsInvocation,
        val sink: AudioSink,
        val terminal: AtomicBoolean,
        val sinkCancelled: AtomicBoolean,
        var job: Job? = null
    )

    private val mutex = Mutex()
    private var generationCounter = 0L
    private var active: ActiveSession? = null
    private val mutableState = MutableStateFlow<TtsSessionState>(TtsSessionState.Idle)
    val state: StateFlow<TtsSessionState> = mutableState.asStateFlow()

    suspend fun submit(invocation: TtsInvocation): Long = mutex.withLock {
        cancelLocked("superseded")
        val generation = ++generationCounter
        val session = ActiveSession(
            generation = generation,
            invocation = invocation,
            sink = sinkProvider(),
            terminal = AtomicBoolean(false),
            sinkCancelled = AtomicBoolean(false)
        )
        active = session
        mutableState.value = TtsSessionState.Requesting(generation)
        session.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runSession(session, configProvider())
        }
        generation
    }

    suspend fun cancelActive(reason: String) = mutex.withLock {
        cancelLocked(reason)
    }

    private suspend fun cancelLocked(reason: String) {
        val previous = active ?: return
        active = null
        previous.job?.cancel(CancellationException(reason))
        cancelSinkOnce(previous)
        if (previous.terminal.compareAndSet(false, true)) {
            previous.invocation.callbacks.onCancelled(reason)
            mutableState.value = TtsSessionState.Cancelled(previous.generation, reason)
        }
    }

    private suspend fun runSession(session: ActiveSession, config: TtsConfig) {
        val decoder = StreamingWavDecoder()
        var openedFormat: PcmFormat? = null
        var played = false
        try {
            mutableState.value = TtsSessionState.Buffering(session.generation)
            synthesisEngine.synthesize(session.invocation.text, config) { bytes ->
                if (!isCurrent(session.generation)) return@synthesize
                for (segment in decoder.feed(bytes)) {
                    if (openedFormat != segment.format) {
                        session.sink.open(segment.format)
                        openedFormat = segment.format
                    }
                    session.sink.write(segment)
                    if (!played) {
                        played = true
                        mutableState.value = TtsSessionState.Playing(session.generation)
                        session.invocation.callbacks.onStarted()
                    }
                }
            }
            check(decoder.finish() == DecodeFinish.Complete) { "Truncated WAV response" }
            check(played) { "Synthesis produced no playable PCM" }
            session.sink.complete()
            if (session.terminal.compareAndSet(false, true)) {
                session.invocation.callbacks.onCompleted()
                mutableState.value = TtsSessionState.Completed(session.generation)
            }
        } catch (cancelled: CancellationException) {
            cancelSinkOnce(session)
            if (session.terminal.compareAndSet(false, true)) {
                session.invocation.callbacks.onCancelled(cancelled.message ?: "cancelled")
                mutableState.value = TtsSessionState.Cancelled(
                    session.generation,
                    cancelled.message ?: "cancelled"
                )
            }
        } catch (error: Throwable) {
            cancelSinkOnce(session)
            if (session.terminal.compareAndSet(false, true)) {
                if (!played && config.fallbackToOriginal && !config.strictMode) {
                    session.invocation.originalCall.resume()
                } else {
                    session.invocation.callbacks.onError(error)
                    mutableState.value = TtsSessionState.Failed(
                        session.generation,
                        error.message ?: error::class.java.simpleName
                    )
                }
            }
        } finally {
            mutex.withLock {
                if (active?.generation == session.generation) active = null
            }
        }
    }

    private suspend fun isCurrent(generation: Long): Boolean =
        mutex.withLock { active?.generation == generation }

    private suspend fun cancelSinkOnce(session: ActiveSession) {
        if (session.sinkCancelled.compareAndSet(false, true)) {
            runCatching { session.sink.cancel() }
        }
    }
}
