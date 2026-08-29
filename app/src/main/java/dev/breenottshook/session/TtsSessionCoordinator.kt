package dev.breenottshook.session

import dev.breenottshook.api.GptSovitsClient
import dev.breenottshook.audio.DecodeFinish
import dev.breenottshook.audio.PcmFormat
import dev.breenottshook.audio.PcmSegment
import dev.breenottshook.audio.PcmSilence
import dev.breenottshook.audio.StreamingWavDecoder
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.playback.AudioSink
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
        val utterances: List<TtsUtterance>,
        val callbacks: TtsCallbacks,
        val originalCall: OriginalCall,
        val reportUtteranceProgress: Boolean,
        val sink: AudioSink,
        val terminal: AtomicBoolean,
        val sinkCancelled: AtomicBoolean,
        var job: Job? = null
    )

    private data class PlaybackState(
        var openedFormat: PcmFormat? = null,
        var played: Boolean = false
    )

    private val mutex = Mutex()
    private var generationCounter = 0L
    private var active: ActiveSession? = null
    private val mutableState = MutableStateFlow<TtsSessionState>(TtsSessionState.Idle)
    val state: StateFlow<TtsSessionState> = mutableState.asStateFlow()

    suspend fun submit(invocation: TtsInvocation): Long = submitSession(
        utterances = listOf(TtsUtterance(index = 0, text = invocation.text)),
        callbacks = invocation.callbacks,
        originalCall = invocation.originalCall,
        reportUtteranceProgress = false
    )

    suspend fun submitStream(
        utterances: List<TtsUtterance>,
        callbacks: TtsCallbacks,
        originalCall: OriginalCall
    ): Long = submitSession(
        utterances = utterances,
        callbacks = callbacks,
        originalCall = originalCall,
        reportUtteranceProgress = true
    )

    private suspend fun submitSession(
        utterances: List<TtsUtterance>,
        callbacks: TtsCallbacks,
        originalCall: OriginalCall,
        reportUtteranceProgress: Boolean
    ): Long = mutex.withLock {
        cancelLocked("superseded")
        val generation = ++generationCounter
        val session = ActiveSession(
            generation = generation,
            utterances = utterances,
            callbacks = callbacks,
            originalCall = originalCall,
            reportUtteranceProgress = reportUtteranceProgress,
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
            previous.callbacks.onCancelled(reason)
            mutableState.value = TtsSessionState.Cancelled(previous.generation, reason)
        }
    }

    private suspend fun runSession(session: ActiveSession, config: TtsConfig) {
        val playback = PlaybackState()
        try {
            mutableState.value = TtsSessionState.Buffering(session.generation)
            if (session.reportUtteranceProgress) {
                runConcurrentStream(session, config, playback)
            } else {
                runImmediateUtterance(session, session.utterances.single(), config, playback)
            }
            check(playback.played) { "Synthesis produced no playable PCM" }
            session.sink.complete()
            if (session.terminal.compareAndSet(false, true)) {
                session.callbacks.onCompleted()
                mutableState.value = TtsSessionState.Completed(session.generation)
            }
        } catch (cancelled: CancellationException) {
            cancelSinkOnce(session)
            if (session.terminal.compareAndSet(false, true)) {
                session.callbacks.onCancelled(cancelled.message ?: "cancelled")
                mutableState.value = TtsSessionState.Cancelled(
                    session.generation,
                    cancelled.message ?: "cancelled"
                )
            }
        } catch (error: Throwable) {
            cancelSinkOnce(session)
            if (session.terminal.compareAndSet(false, true)) {
                if (!playback.played && config.fallbackToOriginal && !config.strictMode) {
                    mutableState.value = TtsSessionState.Failed(session.generation, "fallback")
                    session.originalCall.resume()
                } else {
                    session.callbacks.onError(error)
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

    private suspend fun runImmediateUtterance(
        session: ActiveSession,
        utterance: TtsUtterance,
        config: TtsConfig,
        playback: PlaybackState
    ) {
        ensureCurrent(session.generation)
        val decoder = StreamingWavDecoder()
        var utterancePlayed = false
        synthesisEngine.synthesize(utterance.text, config) { bytes ->
            if (!isCurrent(session.generation)) return@synthesize
            for (segment in decoder.feed(bytes)) {
                if (segment.bytes.isEmpty()) continue
                writeSegment(session, segment, playback)
                utterancePlayed = true
            }
        }
        ensureCurrent(session.generation)
        check(decoder.finish() == DecodeFinish.Complete) { "Truncated WAV response" }
        check(utterancePlayed) { "Synthesis produced no playable PCM" }
    }

    private suspend fun runConcurrentStream(
        session: ActiveSession,
        config: TtsConfig,
        playback: PlaybackState
    ) = supervisorScope {
        val utterances = session.utterances
        check(utterances.isNotEmpty()) { "Stream contains no utterances" }
        val windowSize = minOf(config.maxConcurrentSynthesis, utterances.size)
        check(windowSize > 0) { "Synthesis concurrency must be positive" }
        val jobs = MutableList<Deferred<Result<PreparedUtterance>>?>(utterances.size) { null }

        fun launchPreparation(position: Int): Deferred<Result<PreparedUtterance>> = async {
            try {
                Result.success(prepareUtterance(session, utterances[position], config))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }

        repeat(windowSize) { position ->
            jobs[position] = launchPreparation(position)
        }

        try {
            utterances.indices.forEach { position ->
                ensureCurrent(session.generation)
                val prepared = checkNotNull(jobs[position]).await().getOrThrow()
                val nextPosition = position + windowSize
                if (nextPosition < utterances.size) {
                    jobs[nextPosition] = launchPreparation(nextPosition)
                }
                playPrepared(
                    session = session,
                    prepared = prepared,
                    config = config,
                    playback = playback,
                    hasNext = position < utterances.lastIndex
                )
            }
        } finally {
            jobs.filterNotNull().forEach { job ->
                if (job.isActive) job.cancel()
            }
        }
    }

    private suspend fun prepareUtterance(
        session: ActiveSession,
        utterance: TtsUtterance,
        config: TtsConfig
    ): PreparedUtterance {
        val decoder = StreamingWavDecoder()
        val segments = mutableListOf<PcmSegment>()
        synthesisEngine.synthesize(utterance.text, config) { bytes ->
            ensureCurrent(session.generation)
            segments += decoder.feed(bytes)
        }
        ensureCurrent(session.generation)
        check(decoder.finish() == DecodeFinish.Complete) { "Truncated WAV response" }
        check(segments.any { it.bytes.isNotEmpty() }) { "Synthesis produced no playable PCM" }
        return PreparedUtterance(utterance, segments)
    }

    private suspend fun playPrepared(
        session: ActiveSession,
        prepared: PreparedUtterance,
        config: TtsConfig,
        playback: PlaybackState,
        hasNext: Boolean
    ) {
        var utteranceStarted = false
        var lastFormat: PcmFormat? = null
        prepared.segments.forEach { segment ->
            if (segment.bytes.isEmpty()) return@forEach
            writeSegment(session, segment, playback)
            lastFormat = segment.format
            if (!utteranceStarted) {
                utteranceStarted = true
                session.callbacks.onUtteranceStarted(prepared.utterance)
            }
        }
        check(utteranceStarted) { "Synthesis produced no playable PCM" }
        if (hasNext) {
            PcmSilence.create(checkNotNull(lastFormat), config.playbackIntervalMs)?.let { silence ->
                writeSegment(session, silence, playback, reportStarted = false)
            }
        }
    }

    private suspend fun writeSegment(
        session: ActiveSession,
        segment: PcmSegment,
        playback: PlaybackState,
        reportStarted: Boolean = true
    ) {
        ensureCurrent(session.generation)
        if (playback.openedFormat != segment.format) {
            session.sink.open(segment.format)
            playback.openedFormat = segment.format
        }
        ensureCurrent(session.generation)
        session.sink.write(segment)
        ensureCurrent(session.generation)
        if (reportStarted && !playback.played) {
            playback.played = true
            mutableState.value = TtsSessionState.Playing(session.generation)
            session.callbacks.onStarted()
        }
    }

    private suspend fun isCurrent(generation: Long): Boolean =
        mutex.withLock { active?.generation == generation }

    private suspend fun ensureCurrent(generation: Long) {
        if (!isCurrent(generation)) throw CancellationException("superseded")
    }

    private suspend fun cancelSinkOnce(session: ActiveSession) {
        if (session.sinkCancelled.compareAndSet(false, true)) {
            runCatching { session.sink.cancel() }
        }
    }
}
