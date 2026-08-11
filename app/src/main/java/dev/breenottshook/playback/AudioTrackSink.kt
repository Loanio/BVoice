package dev.breenottshook.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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

    override suspend fun open(format: PcmFormat) = withContext(Dispatchers.IO) {
        cancel()
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
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(format.sampleRate)
            .setChannelMask(channelMask)
            .setEncoding(encoding)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(format.sampleRate, channelMask, encoding)
        check(minBuffer > 0) { "AudioTrack rejected PCM format" }
        val focusResult = audioManager.requestAudioFocus(focusRequest)
        check(focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "Unable to obtain assistant audio focus"
        }
        track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(audioFormat)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer * 2)
            .build()
            .also { it.play() }
        this@AudioTrackSink.format = format
        writtenFrames = 0
    }

    override suspend fun write(segment: PcmSegment) = withContext(Dispatchers.IO) {
        check(segment.format == format) { "PCM format changed without reopening sink" }
        val activeTrack = checkNotNull(track) { "AudioTrack is not open" }
        var offset = 0
        while (offset < segment.bytes.size) {
            val written = activeTrack.write(
                segment.bytes,
                offset,
                segment.bytes.size - offset,
                AudioTrack.WRITE_BLOCKING
            )
            check(written > 0) { "AudioTrack write failed: $written" }
            offset += written
        }
        val frameSize = segment.format.channels * segment.format.bitsPerSample / 8
        writtenFrames += segment.bytes.size / frameSize
    }

    override suspend fun complete() {
        val activeTrack = track ?: return
        val activeFormat = format ?: return
        val expectedMs = writtenFrames * 1_000L / activeFormat.sampleRate + 500L
        val deadline = System.currentTimeMillis() + expectedMs
        while (activeTrack.playbackHeadPosition.toLong() < writtenFrames &&
            System.currentTimeMillis() < deadline
        ) {
            delay(20)
        }
        release()
    }

    override suspend fun cancel() {
        release()
    }

    private fun release() {
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.stop() }
            it.release()
        }
        track = null
        format = null
        writtenFrames = 0
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
