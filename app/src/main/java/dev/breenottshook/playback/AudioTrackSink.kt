package dev.breenottshook.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import dev.breenottshook.audio.PcmFormat
import dev.breenottshook.audio.PcmSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AudioTrackSink(context: Context) : AudioSink {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setAcceptsDelayedFocusGain(false)
        .build()

    private var track: AudioTrack? = null
    private var format: PcmFormat? = null
    private var writtenFrames = 0L
    private var started = false
    private val writeLoop = PcmWriteLoop()
    private val startupGate = StartupBufferGate(STARTUP_BUFFER_MS)
    private val recoveryPolicy = AudioTrackRecoveryPolicy(MAX_RECOVERIES)

    override suspend fun open(format: PcmFormat) = withContext(Dispatchers.IO) {
        release()
        this@AudioTrackSink.format = format
    }

    override suspend fun write(segment: PcmSegment) = withContext(Dispatchers.IO) {
        check(segment.format == format) { "PCM format changed without reopening sink" }
        val frameSize = segment.format.channels * segment.format.bitsPerSample / 8
        val frames = segment.bytes.size.toLong() / frameSize
        if (!AudioTrackStartPolicy.shouldStartOnWrite(segment.bytes)) return@withContext
        if (!started) createTrack(segment.format, startImmediately = true)
        writeWithRecovery(segment.bytes, segment.format)
        writtenFrames += frames
    }

    private suspend fun writeWithRecovery(bytes: ByteArray, activeFormat: PcmFormat) {
        try {
            writeSegment(bytes)
        } catch (error: IllegalStateException) {
            if (!recoveryPolicy.shouldRecover(started, error.message)) throw error
            Log.w(LOG_TAG, "audio_track_recover;started=$started")
            releaseTrackOnly()
            createTrack(activeFormat, startImmediately = true)
            writeSegment(bytes)
        }
    }

    private suspend fun writeSegment(bytes: ByteArray) {
        val activeTrack = checkNotNull(track) { "AudioTrack is not open" }
        writeLoop.writeAll(bytes) { offset, size ->
            activeTrack.write(bytes, offset, size, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun createTrack(format: PcmFormat, startImmediately: Boolean) {
        val channelMask = when (format.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> error("Unsupported channel count: ${format.channels}")
        }
        val encoding = when (format.bitsPerSample) {
            8 -> AudioFormat.ENCODING_PCM_8BIT
            16 -> AudioFormat.ENCODING_PCM_16BIT
            24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            32 -> AudioFormat.ENCODING_PCM_32BIT
            else -> error("Unsupported PCM depth: ${format.bitsPerSample}")
        }
        val minBuffer = AudioTrack.getMinBufferSize(format.sampleRate, channelMask, encoding)
        check(minBuffer > 0) { "AudioTrack rejected PCM format" }
        val frameSize = format.channels * format.bitsPerSample / 8
        val startupBufferBytes = (format.sampleRate.toLong() * frameSize * STARTUP_BUFFER_MS / 1_000L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val focusResult = audioManager.requestAudioFocus(focusRequest)
        check(focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "Unable to obtain assistant audio focus"
        }
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(format.sampleRate)
            .setChannelMask(channelMask)
            .setEncoding(encoding)
            .build()
        track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(audioFormat)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer * 2, startupBufferBytes))
            .build()
        if (startImmediately) {
            track?.play()
            started = true
        }
    }

    override suspend fun complete() = withContext(Dispatchers.IO) {
        val activeTrack = track ?: return@withContext
        val activeFormat = format ?: return@withContext
        val expectedMs = writtenFrames * 1_000L / activeFormat.sampleRate + 500L
        val deadline = System.currentTimeMillis() + expectedMs
        while (activeTrack.playbackHeadPosition.toLong() < writtenFrames &&
            System.currentTimeMillis() < deadline
        ) {
            delay(20)
        }
        release()
    }

    override suspend fun cancel() = withContext(Dispatchers.IO) {
        release()
    }

    private fun release() {
        releaseTrackOnly()
        format = null
        writtenFrames = 0
    }

    private fun releaseTrackOnly() {
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.stop() }
            it.release()
        }
        track = null
        started = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private companion object {
        const val LOG_TAG = "BreenoTTSHook"
        const val STARTUP_BUFFER_MS = 800L
        const val MAX_RECOVERIES = 2
    }
}
